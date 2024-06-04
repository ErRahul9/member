package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    private val gson = GsonBuilder()
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

                if (oracleMembership.currentSegments != null && oracleMembership.dataSource in tpaCacheSources) {
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

                // if the currentSegments is empty, we should not write into device metadata and delete the ip
                results += if (oracleMembership.currentSegments.isNotEmpty()) {
                    async {
                        writeDeviceMetadata(oracleMembership)
                    }
                } else {
                    async {
                        redisConnectionDeviceInfo.sync().del(oracleMembership.ip)
                    }
                }

                results.awaitAll()
            } catch (ex: JsonSyntaxException) {
                meterRegistry.counter("invalid.audience.records").increment()
            } finally {
                lock.release()
            }
        }
    }

    fun writeDeviceMetadata(message: MembershipUpdateMessage) {
        val stopwatch = Stopwatch.createStarted()

        // TODO: Delete after migration
        val ipKey = message.ip
        val ipValue = mapOf(
            "geo_version" to message.geoVersion,
        ).filterValues { it != null }
        if (ipValue.isNotEmpty()) {
            redisConnectionDeviceInfo.sync().hset(ipKey, ipValue)
            redisConnectionDeviceInfo.sync().expire(ipKey, redisConfig.membershipTTL!!)
        }

        // Insert geo version
        val ipGeoVersionKey = "${message.ip}:geo_version"
        val ipGeoVersionValue = message.geoVersion
        if (!ipGeoVersionValue.isNullOrEmpty()) {
            redisConnectionDeviceInfo.sync().set(ipGeoVersionKey, ipGeoVersionValue)
            redisConnectionDeviceInfo.sync().expire(ipGeoVersionKey, redisConfig.membershipTTL!!)
        }

        // Insert household score
        val ipHouseholdScoreKey = "${message.ip}:household_score:campaign"
        val ipHouseholdScoreValue = message.metadataInfo?.filterKeys {
            it.startsWith("cs_")
        }?.mapKeys {
            it.key.split("_").last()
        }
        if (!ipHouseholdScoreValue.isNullOrEmpty()) {
            redisConnectionDeviceInfo.sync().hset(ipHouseholdScoreKey, ipHouseholdScoreValue)
            redisConnectionDeviceInfo.sync().expire(ipHouseholdScoreKey, redisConfig.membershipTTL!!)
        }

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.user.score.latency")
            .record(Duration.ofMillis(responseTime))
    }
}
