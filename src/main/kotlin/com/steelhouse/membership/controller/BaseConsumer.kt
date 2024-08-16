package com.steelhouse.membership.controller

import com.aerospike.client.AerospikeClient
import com.aerospike.client.Bin
import com.aerospike.client.Key
import com.aerospike.client.policy.WritePolicy
import com.google.common.base.Stopwatch
import com.steelhouse.membership.configuration.AerospikeConfig
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.newFixedThreadPoolContext
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Service
abstract class BaseConsumer(
    private val meterRegistry: MeterRegistry,
    private val aerospikeClient: AerospikeClient,
    private val aerospikeConfig: AerospikeConfig,
    private val writePolicy: WritePolicy,
) {

    val context = newFixedThreadPoolContext(30, "write-membership-thread-pool")
    val lock = Semaphore(2000)

    val tpaCacheSources = setOf(3) // TPA datasources

    @Throws(IOException::class)
    abstract fun consume(message: String)

    fun writeMemberships(ip: String, currentSegments: List<Int>, cookieType: String, overwrite: Boolean) {
        if (overwrite || currentSegments.isEmpty()) {
            deleteIp(ip)
        }

        if (currentSegments.isNotEmpty()) {
            val stopwatch = Stopwatch.createStarted()

            val key = Key(aerospikeConfig.namespace, aerospikeConfig.setName, ip)
            val segments = Bin("segments", currentSegments.joinToString(",") { it.toString() })

            aerospikeClient.put(writePolicy, key, segments)

            val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
            meterRegistry.timer(
                "write.membership.match.latency",
                "cookieType",
                cookieType,
            ).record(Duration.ofMillis(responseTime))
        }
    }

    fun deleteIp(ip: String) {
        val stopwatch = Stopwatch.createStarted()

        val key = Key(aerospikeConfig.namespace, aerospikeConfig.setName, ip)
        aerospikeClient.delete(writePolicy, key)

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("delete.membership.match.latency").record(Duration.ofMillis(responseTime))
    }
}
