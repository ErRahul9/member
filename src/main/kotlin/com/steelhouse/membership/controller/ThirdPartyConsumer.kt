package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
        val oracleMembership = gson.fromJson(message, MembershipUpdateMessage::class.java)

        lock.acquire()

        CoroutineScope(context).launch {
            try {
                launch {
                    if (oracleMembership.currentSegments != null) {
                        val segments = oracleMembership.currentSegments.map { it.toString() }.toTypedArray()

                        if (oracleMembership.dataSource in tpaCacheSources) {
                            val overwrite = oracleMembership?.isDelta ?: true
                            writeMemberships(
                                oracleMembership.ip.orEmpty(),
                                segments,
                                "ip",
                                !overwrite,
                            )
                        }
                    }
                }

                launch {
                    writeDeviceMetadata(oracleMembership)
                }
            } finally {
                lock.release()
            }
        }
    }

    fun writeDeviceMetadata(message: MembershipUpdateMessage) {
        val ip = message.ip

        val stopwatch = Stopwatch.createStarted()

        val valuesToSet = mutableMapOf<String, String>()

        if (message.householdScore != null) {
            valuesToSet["household_score"] = message.householdScore.toString()
        }

        if (message.geoVersion != null) {
            valuesToSet["geo_version"] = message.geoVersion
        }

        if (message.metadataInfo.isNotEmpty()) {
            valuesToSet["metadata_info"] = Gson().toJson(message.metadataInfo)
        }
        redisConnectionDeviceInfo.sync().hset(ip, valuesToSet)

        if (message.geoVersion != null || message.householdScore != null) {
            redisConnectionDeviceInfo.sync().expire(ip, redisConfig.membershipTTL!!)
        }

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.user.score.latency")
            .record(Duration.ofMillis(responseTime))
    }
}
