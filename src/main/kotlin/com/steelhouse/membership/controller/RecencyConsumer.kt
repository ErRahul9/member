package com.steelhouse.membership.controller

import com.google.common.base.Stopwatch
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.model.RecencyMessage
import com.steelhouse.membership.util.Util
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Service
class RecencyConsumer(
    private val meterRegistry: MeterRegistry,
    val appConfig: AppConfig,
    val util: Util,
    @Qualifier("redisConnectionRecency") private val redisConnectionRecency: StatefulRedisClusterConnection<String, String>,
) {

    val context = newFixedThreadPoolContext(30, "write-membership-thread-pool")
    val lock = Semaphore(2000)

    val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @KafkaListener(topics = ["vast_impression", "guidv2"], autoStartup = "\${membership.recencyConsumer:false}")
    @Throws(IOException::class)
    fun consume(message: ConsumerRecord<String, String>) {
        val recencyMessage = extractRecency(message.value())

        lock.acquire()

        CoroutineScope(context).launch {
            try {
                if (recencyMessage.advertiserID != null && recencyMessage.ip != null && recencyMessage.epoch != null) {
                    val epochMillis = if (message.topic().equals("guidv2")) {
                        recencyMessage.epoch / 1000
                    } else {
                        recencyMessage.epoch
                    }

                    val ip = if (message.topic().equals("guidv2")) {
                        recencyMessage.ip
                    } else {
                        recencyMessage.ip + "_vast"
                    }

                    writeRecency(
                        deviceID = ip,
                        advertiserID = recencyMessage.advertiserID.toString(),
                        recencyEpoch = epochMillis.toString(),
                    )
                }
            } finally {
                lock.release()
            }
        }
    }

    fun writeRecency(deviceID: String, advertiserID: String, recencyEpoch: String) {
        val stopwatch = Stopwatch.createStarted()

        val expirationWindow = util.getEpochInMillis() - appConfig.recencyExpirationWindowMilliSeconds!!

        try {
            writeToRecencyRedis(deviceID, advertiserID, recencyEpoch, expirationWindow)
        } catch (e: RedisNoScriptException) {
            // Reload the script and save sha if the script is not in the cache
            val script = File("./recency.lua").readText(StandardCharsets.UTF_8)
            val newSha = redisConnectionRecency.sync().scriptLoad(script)
            // sha should be same if script stays same, here to store sha in appConfig until next restart
            appConfig.recencySha = newSha
            writeToRecencyRedis(deviceID, advertiserID, recencyEpoch, expirationWindow)
        }


        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.recency.latency").record(Duration.ofMillis(responseTime))
    }

    fun extractRecency(message: String): RecencyMessage {
        var eventMap: Map<String, Any> = gson.fromJson(message, object : TypeToken<Map<String, Any>>() {}.type)

        var ip = eventMap.get("ip")

        if (ip == null) {
            ip = eventMap.get("IP")
        }

        if (ip == null) {
            ip = eventMap.get("REMOTE_IP")
        }

        // The epoch key in kafka topics guidv2 and impression is EPOCH
        var epoch = eventMap.get("EPOCH").toString().toDoubleOrNull()?.toLong()
        // The epoch key in Kafka topic vast_impression is epoch
        if (epoch == null) {
            epoch = eventMap.get("epoch").toString().toDoubleOrNull()?.toLong()
        }

        // The advertiser id key in kafka topic guidv2 and impression and impression is AID
        var advertiserId = eventMap.get("AID").toString().toDoubleOrNull()?.toInt()
        // The advertiser id key in kafka topic vast_impression and impression is advertiser_id
        if (advertiserId == null) {
            advertiserId = eventMap.get("advertiser_id").toString().toDoubleOrNull()?.toInt()
        }

        return RecencyMessage(ip = ip.toString(), advertiserID = advertiserId, epoch = epoch)
    }

    private fun writeToRecencyRedis(
        deviceID: String,
        advertiserID: String,
        recencyEpoch: String,
        expirationWindow: Long
    ) {
        redisConnectionRecency.sync().evalsha<String>(
            appConfig.recencySha,
            ScriptOutputType.VALUE,
            arrayOf(deviceID),
            advertiserID,
            recencyEpoch,
            expirationWindow.toString(),
            appConfig.recencyDeviceIDTTLSeconds.toString(),
        )
    }
}
