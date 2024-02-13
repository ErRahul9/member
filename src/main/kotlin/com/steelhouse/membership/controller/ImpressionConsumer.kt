package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.model.AgentParams
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

    @KafkaListener(topics = ["beeswax-spend-logs-prod"], autoStartup = "\${membership.impressionConsumer:false}")
    @Throws(IOException::class)
    fun consume(message: String) {
        val impression = try {
            gson.fromJson(message, ImpressionMessage::class.java)
        } catch (_: Exception) {
            log.warn("failed to convert json message $message")
            meterRegistry.counter("frequency.message.error").increment()
            return
        }

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

        val agentParams = gson.fromJson(impression.agentParams, AgentParams::class.java)
        val campaignId = agentParams?.campaignId
        val campaignGroupId = agentParams?.campaignGroupId

        if (!impression.deviceIp.isNullOrEmpty() && !impression.impressionId.isNullOrEmpty() && impression.impressionTime != null) {
            val epoch = impression.impressionTime / 1000 // convert micro epoch to millis
            if (campaignId != null) {
                redisConnectionFrequencyCap.sync().evalsha<String>(
                    appConfig.frequencySha,
                    ScriptOutputType.VALUE,
                    arrayOf("${impression.deviceIp}:${campaignId}_cid"),
                    epoch.toString(),
                    expirationWindow.toString(),
                    appConfig.frequencyDeviceIDTTLSeconds.toString(),
                    impression.impressionId.toString(),
                )
            }
            if (campaignGroupId != null) {
                redisConnectionFrequencyCap.sync().evalsha<String>(
                    appConfig.frequencySha,
                    ScriptOutputType.VALUE,
                    arrayOf("${impression.deviceIp}:${campaignGroupId}_cgid"),
                    epoch.toString(),
                    expirationWindow.toString(),
                    appConfig.frequencyDeviceIDTTLSeconds.toString(),
                    impression.impressionId.toString(),
                )
            }
        } else {
            log.info("impression message has null values impression object $impression")
        }

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.frequency.latency").record(Duration.ofMillis(responseTime))
    }
}
