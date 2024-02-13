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
        val impressionTime = 1707255347727544
        val impressionId = "1706220285992216.59847714.9356.steelhouse"
        val message = "{\"DW_AgentParams\": \"{\\\"_geo_ver\\\": \\\"1640995200\\\", \\\"_hh_score\\\": \\\"30\\\", \\\"household_score\\\": \\\"30\\\", " +
                "\\\"geo_version\\\": \\\"1640995200\\\", \\\"device_type_group\\\": \\\"COMPUTER\\\", \\\"campaign_id\\\":\\\"$cid\\\" ,\\\"campaign_group_id\\\":\\\"$cgid\\\"}\"," +
                "\"DW_ImpressionWinPriceMicrosUsd\":\"12000\",\"DW_BidRequestDeviceIp\":\"$remoteIP\",\"DW_ImpressionAuctionId\":\"$impressionId\",\"DW_ImpressionTime\":\"$impressionTime\"}\n"
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
            eq((impressionTime/1000).toString()),
            any(),
            eq(appConfig.frequencyDeviceIDTTLSeconds.toString()),
            eq(impressionId)
        )
        verify(frequencyCapSyncCommands).evalsha<String>(
            eq(appConfig.frequencySha),
            eq(ScriptOutputType.VALUE),
            eq(arrayOf("$remoteIP:${cgid}_cgid")),
            eq((impressionTime/1000).toString()),
            any(),
            eq(appConfig.frequencyDeviceIDTTLSeconds.toString()),
            eq(impressionId)
        )
    }

    @Test
    fun testConsumeWhenMissingCGID() {
        val remoteIP = "172.1.1"
        val cid = 1
        val impressionId = "1706220285992216.59847714.9356.steelhouse"
        val impressionTime = 1707255347727544
        val expectedEpoch = "1707255347727"
        val message = "{\"DW_AgentParams\": \"{\\\"_geo_ver\\\": \\\"1640995200\\\", \\\"_hh_score\\\": \\\"30\\\", \\\"household_score\\\": \\\"30\\\", " +
                "\\\"geo_version\\\": \\\"1640995200\\\", \\\"device_type_group\\\": \\\"COMPUTER\\\",\\\"campaign_id\\\":\\\"$cid\\\"}\"," +
                "\"DW_ImpressionWinPriceMicrosUsd\":\"12000\",\"DW_BidRequestDeviceIp\":\"$remoteIP\",\"DW_ImpressionAuctionId\":\"$impressionId\",\"DW_ImpressionTime\":\"$impressionTime\"}\n"
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
            eq(expectedEpoch),
            any(),
            eq(appConfig.frequencyDeviceIDTTLSeconds.toString()),
            eq(impressionId)
        )
    }

    @Test
    fun testConsumeWhenMissingCID() {
        val remoteIP = "172.1.1"
        val cgid = 99
        val impressionId = "1706220285992216.59847714.9356.steelhouse"
        val impressionTime = 1707255347727544
        val expectedEpoch = "1707255347727"
        val message = "{\"DW_AgentParams\": \"{\\\"_geo_ver\\\": \\\"1640995200\\\", \\\"_hh_score\\\": \\\"30\\\", \\\"household_score\\\": \\\"30\\\", " +
                "\\\"geo_version\\\": \\\"1640995200\\\", \\\"device_type_group\\\": \\\"COMPUTER\\\",\\\"campaign_group_id\\\":\\\"$cgid\\\"}\"," +
                "\"DW_ImpressionWinPriceMicrosUsd\":\"12000\",\"DW_BidRequestDeviceIp\":\"$remoteIP\",\"DW_ImpressionAuctionId\":\"$impressionId\",\"DW_ImpressionTime\":\"$impressionTime\"}\n"
        appConfig.frequencySha = "d0092a4b68842a839daa2cf020983b8c0872f0db"
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
            eq(arrayOf("$remoteIP:${cgid}_cgid")),
            eq(expectedEpoch),
            any(),
            eq(appConfig.frequencyDeviceIDTTLSeconds.toString()),
            eq(impressionId)
        )
    }

    @Test
    fun testConsumeWhenMissingCIDAndCGID() {
        val message = "{\"DW_AgentParams\": \"{\\\"_geo_ver\\\": \\\"1640995200\\\", \\\"_hh_score\\\": \\\"30\\\", \\\"household_score\\\": \\\"30\\\", " +
                "\\\"geo_version\\\": \\\"1640995200\\\", \\\"device_type_group\\\": \\\"COMPUTER\\\"}\", \"DW_ImpressionWinPriceMicrosUsd\": \"2570\", " +
                "\"DW_BidRequestDeviceIp\": \"145.14.135.225\", \"DW_ImpressionAuctionId\": \"1707488594342213.3674259501.781551.steelhouse\", " +
                "\"DW_BidRequestDeviceType\": \"PC\", \"DW_BidRequestId\": \"b46c407d-925e-4f7f-acdf-2a72b9ee9b99\", \"DW_ImpressionAdGroupIdLineItemId\": \"33101\"," +
                " \"DW_BidRequestAppDomain\": null, \"DW_ImpressionAdGroupIdCampaignId\": \"5116\", \"DW_BidRequestImp\": \"[{\\\"id\\\": \\\"1\\\", " +
                "\\\"banner\\\": {\\\"w\\\": 300, \\\"h\\\": 250, \\\"topframe\\\": \\\"YES\\\"}, \\\"bidfloor\\\": 0.4, \\\"pmp\\\": {\\\"private_auction\\\": " +
                "\\\"NO\\\", \\\"deals\\\": [{\\\"id\\\": \\\"IXIVPBEESWAXINTENSIFYDIS\\\", \\\"bidfloor\\\": 0.4, \\\"bidfloorcur\\\": \\\"USD\\\", \\\"at\\\": 1}, " +
                "{\\\"id\\\": \\\"IXIVPBWMKPNEWS\\\", \\\"bidfloor\\\": 0.4, \\\"bidfloorcur\\\": \\\"USD\\\", \\\"at\\\": 1}, {\\\"id\\\": \\\"IXIVPBEESWAXALCDIS\\\", " +
                "\\\"bidfloor\\\": 0.4, \\\"bidfloorcur\\\": \\\"USD\\\", \\\"at\\\": 1}, {\\\"id\\\": \\\"IXIVPBWGSDIS\\\", \\\"bidfloor\\\": 0.4, \\\"bidfloorcur\\\": " +
                "\\\"USD\\\", \\\"wseat\\\": [\\\"325989\\\"], \\\"at\\\": 1}, {\\\"id\\\": \\\"IXIVPBWGSGAMBDIS\\\", \\\"bidfloor\\\": 0.4, \\\"bidfloorcur\\\": \\\"USD\\\", " +
                "\\\"at\\\": 1}, {\\\"id\\\": \\\"IXIVPBWGSENTDIS\\\", \\\"bidfloor\\\": 0.4, \\\"bidfloorcur\\\": \\\"USD\\\", \\\"at\\\": 1}, {\\\"id\\\": \\\"IXIVPMNTNDISPLAYUS\\\"," +
                " \\\"bidfloor\\\": 0.4, \\\"bidfloorcur\\\": \\\"USD\\\", \\\"wseat\\\": [\\\"74283\\\"], \\\"at\\\": 1}, {\\\"id\\\": \\\"IX706241074449169491\\\", " +
                "\\\"bidfloor\\\": 0.5, \\\"bidfloorcur\\\": \\\"USD\\\", \\\"wseat\\\": [\\\"3566\\\"], \\\"at\\\": 1}]}, \\\"secure\\\": 1, \\\"ext\\\": {\\\"placement_type\\\": " +
                "\\\"BANNER\\\", \\\"environment_type\\\": \\\"WEB\\\"}}]\", \"DW_KinesisTime\": \"1707488602\", " +
                "\"DW_BidRequestDeviceUa\": \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36\", " +
                "\"DW_ImpressionTime\": \"1707488600570329\", \"DW_Time\": \"1707488594342213\", \"DW_LambdaTime\": \"1707488603\", \"DW_BidRequestSiteBundle\": null, " +
                "\"DW_InventorySource\": \"INDEX_EXCHANGE\", \"DW_BidRequestSiteDomain\": \"www.foxnews.com\", \"DW_ImpressionAdvertiserId\": \"2588\", \"DW_BidRequestAppBundle\": null}"
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
    fun testConsumeWhenMissingAgentParams() {
        val remoteIP = "172.1.1"
        val impressionId = "1706220285992216.59847714.9356.steelhouse"
        val impressionTime = 1707255347727544
        val message = "{\"DW_ImpressionWinPriceMicrosUsd\":\"12000\",\"DW_BidRequestDeviceIp\":\"$remoteIP\",\"DW_ImpressionAuctionId\":\"$impressionId\",\"DW_ImpressionTime\":\"$impressionTime\"}\n"
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