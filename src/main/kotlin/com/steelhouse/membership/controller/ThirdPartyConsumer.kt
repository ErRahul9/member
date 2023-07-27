package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.AppConfig
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
class ThirdPartyConsumer constructor(
    @Qualifier("app") private val log: Log,
    private val meterRegistry: MeterRegistry,
    val appConfig: AppConfig,
    @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
    @Qualifier("redisConnectionDeviceInfo") private val redisConnectionDeviceInfo: StatefulRedisClusterConnection<String, String>,
    private val redisConfig: RedisConfig,
) : BaseConsumer(
    log = log,
    meterRegistry = meterRegistry,
    redisConnectionMembershipTpa = redisConnectionMembershipTpa,
    redisConfig = redisConfig,
    appConfig = appConfig,
) {

    val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @KafkaListener(topics = ["sh-dw-generated-audiences"], autoStartup = "\${membership.oracleConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {
        val oracleMembership = gson.fromJson(message, MembershipUpdateMessage::class.java)
        val results = mutableListOf<Deferred<Any>>()

        lock.acquire()

        CoroutineScope(context).launch {
            try {
                if (oracleMembership.currentSegments != null) {
                    val segments = oracleMembership.currentSegments.map { it.toString() }.toTypedArray()

                    if (oracleMembership.dataSource in tpaCacheSources) {
                        val overwrite = oracleMembership?.isDelta ?: true

                        results += async {
                            writeMemberships(
                                oracleMembership.ip.orEmpty(),
                                segments,
                                "ip",
                                !overwrite,
                            )
                        }
                    }
                }

                results += async {
                    writeDeviceMetadata(oracleMembership)
                }

                results.forEach { it.await() }
            } finally {
                lock.release()
            }
        }
    }

    fun writeDeviceMetadata(message: MembershipUpdateMessage) {
        val ip = message.ip

        if (ip != null) {
            val stopwatch = Stopwatch.createStarted()

            if (message.householdScore != null) {
                redisConnectionDeviceInfo.sync().hset(ip, "household_score", message.householdScore.toString())
            }

            if (message.geoVersion != null) {
                redisConnectionDeviceInfo.sync().hset(ip, "geo_version", message.geoVersion)
            }

            if (message.geoVersion != null || message.householdScore != null) {
                redisConnectionDeviceInfo.sync().expire(ip, redisConfig.membershipTTL!!)
            }

            val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
            meterRegistry.timer("write.user.score.latency")
                .record(Duration.ofMillis(responseTime))
        }
    }
}
