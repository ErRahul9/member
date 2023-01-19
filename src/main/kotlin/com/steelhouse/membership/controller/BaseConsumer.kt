package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.steelhouse.membership.configuration.AppConfig
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
                                        private val appConfig: AppConfig,
                                        private val meterRegistry: MeterRegistry,
                                        @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
                                        private val redisConfig: RedisConfig) {

    val context = newFixedThreadPoolContext(30, "write-membership-thread-pool")
    val lock = Semaphore(2000)

    @Throws(IOException::class)
    abstract open fun consume(message: String)

    enum class Audiencetype(name: String) {
        oracle("oracle"),
        steelhouse("steelhouse")
    }


    fun writeMemberships(guid: String, currentSegments: Array<String>, cookieType: String, audienceType: String) {

        if(currentSegments.isNotEmpty()) {
            val stopwatch = Stopwatch.createStarted()

            redisConnectionMembershipTpa.sync().sadd(guid, *currentSegments)
            redisConnectionMembershipTpa.sync().expire(guid, redisConfig.membershipTTL!!)

            val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
            meterRegistry.timer("write.membership.match.latency", "cookieType", cookieType, "audienceType",
                    audienceType).record(Duration.ofMillis(responseTime))
        }
    }

    fun deleteMemberships(guid: String, deletedSegments: Array<String>, cookieType: String, audienceType: String) {
        if(deletedSegments.isNotEmpty()) {
            val stopwatch = Stopwatch.createStarted()

            redisConnectionMembershipTpa.sync().srem(guid, *deletedSegments)

            val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
            meterRegistry.timer("delete.membership.match.latency", "cookieType", cookieType, "audienceType",
                    audienceType).record(Duration.ofMillis(responseTime))
        }
    }

}
