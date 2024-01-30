package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyArray
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.configuration.AppConfig
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.logging.Log
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImpressionConsumerTest {
    private var redisConnectionFrequencyCap: StatefulRedisClusterConnection<String, String> = mock()
    var frequencyCapSyncCommands: RedisAdvancedClusterCommands<String, String> = mock()

    private val meterRegistry = SimpleMeterRegistry()

    private val log: Log = mock()
    private val appConfig = AppConfig()
    private val impressionConsumer = ImpressionConsumer(log, meterRegistry, appConfig, redisConnectionFrequencyCap)

    @BeforeEach
    fun init() {
        whenever(redisConnectionFrequencyCap.sync()).thenReturn(frequencyCapSyncCommands)
    }

    @Test
    fun testConsume() {
        val remoteIP = "172.1.1"
        val cid = 1
        val cgid = 2
        val ttdImpressionId = "1706220285992216.59847714.9356.steelhouse"
        val message = "{\"GUID\":\"1\", \"EPOCH\":\"1000000\", \"CID\":\"$cid\", \"AID\":\"1\", \"REMOTE_IP\":\"$remoteIP\"" +
                ", \"TTD_IMPRESSION_ID\":\"$ttdImpressionId\", \"CGID\":\"$cgid\"}"
        appConfig.frequencySha = "d0092a4b68842a839daa2cf020983b8c0872f0db"
        appConfig.frequencyDeviceIDTTLSeconds = 604800
        appConfig.frequencyExpirationWindowMilliSeconds = 55444

        impressionConsumer.consume(message)

        runBlocking {
            delay(100)
        }
        verify(frequencyCapSyncCommands).evalsha<String>(
            eq(appConfig.frequencySha),
            eq(ScriptOutputType.VALUE),
            eq(arrayOf("$remoteIP:${cid}_cid")),
            eq("1706220285992"),
            any(),
            eq(appConfig.frequencyDeviceIDTTLSeconds.toString()),
            eq(ttdImpressionId)
        )
        verify(frequencyCapSyncCommands).evalsha<String>(
            eq(appConfig.frequencySha),
            eq(ScriptOutputType.VALUE),
            eq(arrayOf("$remoteIP:${cgid}_cgid")),
            eq("1706220285992"),
            any(),
            eq(appConfig.frequencyDeviceIDTTLSeconds.toString()),
            eq(ttdImpressionId)
        )
    }

    @Test
    fun testConsumeWhenMissingCGID() {
        val remoteIP = "172.1.1"
        val cid = 1
        val ttdImpressionId = "1706220285992216.59847714.9356.steelhouse"
        val message = "{\"GUID\":\"1\", \"EPOCH\":\"1000000\", \"CID\":\"$cid\", \"AID\":\"1\", \"REMOTE_IP\":\"$remoteIP\"" +
                ", \"TTD_IMPRESSION_ID\":\"$ttdImpressionId\"}"
        appConfig.frequencySha = "d0092a4b68842a839daa2cf020983b8c0872f0db"
        appConfig.frequencyDeviceIDTTLSeconds = 604800
        appConfig.frequencyExpirationWindowMilliSeconds = 55444

        impressionConsumer.consume(message)

        runBlocking {
            delay(100)
        }
        verify(frequencyCapSyncCommands).evalsha<String>(
            eq(appConfig.frequencySha),
            eq(ScriptOutputType.VALUE),
            eq(arrayOf("$remoteIP:${cid}_cid")),
            eq("1706220285992"),
            any(),
            eq(appConfig.frequencyDeviceIDTTLSeconds.toString()),
            eq(ttdImpressionId)
        )
    }

    @Test
    fun testConsumeWhenMissingCID() {
        val remoteIP = "172.1.1"
        val cid = 1
        val ttdImpressionId = "1706220285992216.59847714.9356.steelhouse"
        val message = "{\"GUID\":\"1\", \"EPOCH\":\"1000000\", \"AID\":\"1\", \"REMOTE_IP\":\"$remoteIP\"" +
                ", \"TTD_IMPRESSION_ID\":\"$ttdImpressionId\"}"
        appConfig.frequencySha = "d0092a4b68842a839daa2cf020983b8c0872f0db"
        appConfig.frequencyDeviceIDTTLSeconds = 604800
        appConfig.frequencyExpirationWindowMilliSeconds = 55444

        impressionConsumer.consume(message)

        runBlocking {
            delay(100)
        }
        verify(frequencyCapSyncCommands, never()).evalsha<String>(
            any(),
            any(),
            anyArray(),
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun testConsumeWithInvalidTtdImpressionId() {
        val remoteIP = "172.1.1"
        val cid = 1
        val ttdImpressionId = "a.59847714.9356.steelhouse"
        val message = "{\"GUID\":\"1\", \"EPOCH\":\"1000000\", \"CID\":\"$cid\", \"AID\":\"1\", \"REMOTE_IP\":\"$remoteIP\"" +
                ", \"TTD_IMPRESSION_ID\":\"$ttdImpressionId\"}"
        appConfig.frequencySha = "d0092a4b68842a839daa2cf020983b8c0872f0db"
        appConfig.frequencyDeviceIDTTLSeconds = 604800
        appConfig.frequencyExpirationWindowMilliSeconds = 55444

        impressionConsumer.consume(message)

        runBlocking {
            delay(100)
        }
        verify(frequencyCapSyncCommands, never()).evalsha<String>(
            any(),
            any(),
            anyArray(),
            any(),
            any(),
            any(),
            any()
        )
    }
}