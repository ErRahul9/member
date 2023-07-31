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

    @KafkaListener(topics = ["vastimpression"], autoStartup = "\${membership.impressionConsumer:false}")
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

        if (impression.remoteIp != null && impression.cid != null && impression.epoch != null &&
            impression.tdImpressionId != null
        ) {
            impression.apply { impression.epoch /= 1000 } // Convert epoch micro to millis
            redisConnectionFrequencyCap.sync().evalsha<String>(
                appConfig.frequencySha,
                ScriptOutputType.VALUE,
                arrayOf(impression.remoteIp + ":" + impression.cid.toString()),
                impression.epoch.toString(),
                expirationWindow.toString(),
                appConfig.frequencyDeviceIDTTLSeconds.toString(),
                impression.tdImpressionId.toString(),
            )
        } else {
            log.info("impression message has null values impression object $impression")
        }

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.frequency.latency").record(Duration.ofMillis(responseTime))
    }
}
