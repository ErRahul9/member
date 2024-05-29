package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.steelhouse.membership.configuration.RedisConfig
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.newFixedThreadPoolContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Service
abstract class BaseConsumer(
    private val meterRegistry: MeterRegistry,
    @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
    private val redisConfig: RedisConfig,
) {

    val context = newFixedThreadPoolContext(30, "write-membership-thread-pool")
    val lock = Semaphore(2000)

    val tpaCacheSources = setOf(3) // TPA datasources

    @Throws(IOException::class)
    abstract fun consume(message: String)

    fun writeMemberships(ip: String, currentSegments: List<Int>, cookieType: String, overwrite: Boolean) {
        if (overwrite) {
            deleteIp(ip)
        }

        if (currentSegments.isNotEmpty()) {
            val stopwatch = Stopwatch.createStarted()

            redisConnectionMembershipTpa.sync().set(ip, currentSegments.joinToString(",") { it.toString() })
            redisConnectionMembershipTpa.sync().expire(ip, redisConfig.membershipTTL!!)

            val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
            meterRegistry.timer(
                "write.membership.match.latency",
                "cookieType",
                cookieType,
            ).record(Duration.ofMillis(responseTime))
        } else {
            if (!overwrite) {
                deleteIp(ip)
            }
        }
    }

    fun deleteIp(ip: String) {
        val stopwatch = Stopwatch.createStarted()

        redisConnectionMembershipTpa.sync().del(ip)

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("delete.membership.match.latency").record(Duration.ofMillis(responseTime))
    }
}
