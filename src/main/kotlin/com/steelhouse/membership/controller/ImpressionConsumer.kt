package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.model.ImpressionMessage
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Service
class ImpressionConsumer(
    @Qualifier("app") private val log: Log,
    private val meterRegistry: MeterRegistry,
    val appConfig: AppConfig,
    @Qualifier("redisConnectionFrequencyCap") private val redisConnectionFrequencyCap: StatefulRedisClusterConnection<String, String>,
) {

    val gson = GsonBuilder().create()

    val context = newFixedThreadPoolContext(1, "write-impression-thread-pool")
    val lock = Semaphore(4000)

    @KafkaListener(topics = ["vastimpression", "impression"], autoStartup = "\${membership.impressionConsumer:false}")
    @Throws(IOException::class)
    fun consume(message: String) {
        val impression = gson.fromJson(message, ImpressionMessage::class.java)

        lock.acquire()

        CoroutineScope(context).launch {
            try {
                writeFrequencyCap(impression = impression)
            } finally {
                lock.release()
            }
        }
    }

    fun writeFrequencyCap(impression: ImpressionMessage) {
        val stopwatch = Stopwatch.createStarted()

        val expirationWindow = System.currentTimeMillis() - appConfig.frequencyExpirationWindowMilliSeconds!!
        val auctionEpoch = try {
            impression.tdImpressionId?.split(".")?.get(0)?.toLong()
        } catch (e: Exception) {
            log.error("Failed to get auction epoch from ${impression.tdImpressionId}", e)
            null
        }

        if (impression.remoteIp.isNotEmpty() && impression.cid != null && auctionEpoch != null && !impression.tdImpressionId.isNullOrEmpty()
        ) {
            val auctionEpochMillis = auctionEpoch / 1000 // convert micro epoch to millis
            redisConnectionFrequencyCap.sync().evalsha<String>(
                appConfig.frequencySha,
                ScriptOutputType.VALUE,
                arrayOf("${impression.remoteIp}:${impression.cid}_cid"),
                auctionEpochMillis.toString(),
                expirationWindow.toString(),
                appConfig.frequencyDeviceIDTTLSeconds.toString(),
                impression.tdImpressionId.toString(),
            )
            if (impression.cgid != null) {
                redisConnectionFrequencyCap.sync().evalsha<String>(
                    appConfig.frequencySha,
                    ScriptOutputType.VALUE,
                    arrayOf("${impression.remoteIp}:${impression.cgid}_cgid"),
                    auctionEpochMillis.toString(),
                    expirationWindow.toString(),
                    appConfig.frequencyDeviceIDTTLSeconds.toString(),
                    impression.tdImpressionId.toString(),
                )
            }
        } else {
            log.info("impression message has null values impression object $impression")
        }

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.frequency.latency").record(Duration.ofMillis(responseTime))
    }
}
