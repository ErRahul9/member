package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class ThirdPartyConsumer(
    private val meterRegistry: MeterRegistry,
    @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
    @Qualifier("redisConnectionDeviceInfo") private val redisConnectionDeviceInfo: StatefulRedisClusterConnection<String, String>,
    private val redisConfig: RedisConfig,
    @Qualifier("app") private val log: Log,
) : BaseConsumer(
    meterRegistry = meterRegistry,
    redisConnectionMembershipTpa = redisConnectionMembershipTpa,
    redisConfig = redisConfig,
) {

    val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @KafkaListener(topics = ["sh-dw-generated-audiences"], autoStartup = "\${membership.oracleConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {
        lock.acquire()
        CoroutineScope(context).launch {
            try {
                val oracleMembership = gson.fromJson(message, MembershipUpdateMessage::class.java)
                val results = mutableListOf<Deferred<Any>>()
                if (oracleMembership.currentSegments != null) {
                    if (oracleMembership.dataSource in tpaCacheSources) {
                        val overwrite = !(oracleMembership?.isDelta ?: true)
                        results += async {
                            writeMemberships(
                                oracleMembership.ip,
                                oracleMembership.currentSegments,
                                "ip",
                                overwrite,
                            )
                        }
                    }
                }

                results += async {
                    writeDeviceMetadata(oracleMembership)
                }

                results.forEach { it.await() }
            } catch (ex: JsonSyntaxException){
                meterRegistry.counter("invalid.audience.records").increment()
            }finally {
                lock.release()
            }
        }
    }

    fun writeDeviceMetadata(message: MembershipUpdateMessage) {
        val stopwatch = Stopwatch.createStarted()

        var metadata: MutableMap<String, String?> =
            if (!message.metadataInfo.isNullOrEmpty()) message.metadataInfo.toMutableMap() else mutableMapOf()
        metadata.putIfAbsent("household_score", message.householdScore?.toString())
        metadata.putIfAbsent("geo_version", message.geoVersion)

        metadata = metadata.filterValues { it != null }.toMutableMap()

        val valuesToSet = mapOf(
            "metadata_info" to if (metadata.isNotEmpty()) Gson().toJson(metadata) else null,
        ).filterValues { it != null }

        if (valuesToSet.isNotEmpty()) {
            val ip = message.ip
            redisConnectionDeviceInfo.sync().hset(ip, valuesToSet)
            redisConnectionDeviceInfo.sync().expire(ip, redisConfig.membershipTTL!!)
        }

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.user.score.latency")
            .record(Duration.ofMillis(responseTime))
    }
}
