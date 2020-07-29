package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.steelhouse.membership.configuration.RedisConfig
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.newFixedThreadPoolContext
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


@Service
abstract class BaseConsumer constructor(@Qualifier("app") private val log: Log,
                                 private val meterRegistry: MeterRegistry,
                                 @Qualifier("redisConnectionPartner") private val redisConnectionPartner: StatefulRedisClusterConnection<String, String>,
                                 @Qualifier("redisConnectionMembership") private val redisConnectionMembership: StatefulRedisClusterConnection<String, String>,
                                 private val redisConfig: RedisConfig) {

    val context = newFixedThreadPoolContext(1, "write-membership-thread-pool")
    val lock = Semaphore(7000)

    @Throws(IOException::class)
    abstract open fun consume(message: String)

    enum class Audiencetype(name: String) {
        oracle("oracle"),
        steelhouse("steelhouse")
    }


    fun writeMemberships(guid: String, currentSegments: String, aid: String, cookieType: String, audienceType: String) {
        val stopwatch = Stopwatch.createStarted()
        val results = redisConnectionMembership.async().hset(guid, aid, currentSegments)
        redisConnectionMembership.async().expire(guid, redisConfig.membershipTTL!!)
        results.get()
        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.membership.match.latency", "cookieType", cookieType, "audienceType", audienceType).record(Duration.ofMillis(responseTime))
    }

    fun retrievePartnerId(guid: String, audienceType: String): MutableMap<String, String>? {
        val stopwatch = Stopwatch.createStarted()
        val asyncResults = redisConnectionPartner.async().hgetall(guid)
        val results = asyncResults.get()
        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.partner.match.latency", "audienceType", audienceType).record(Duration.ofMillis(responseTime))
        return results
    }
}
