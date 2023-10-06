package com.steelhouse.membership.controller

import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.*
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.RedisFuture
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThirdPartyConsumerTest {

    var redisClientMembershipTpa: StatefulRedisClusterConnection<String, String> = mock()

    var redisMetadataScore: StatefulRedisClusterConnection<String, String> = mock()
    var userScoreCommands: RedisAdvancedClusterCommands<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterCommands<String, String> = mock()
    var membershipAsyncCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    var segmentMappingCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    val meterRegistry = SimpleMeterRegistry()

    var redisConfig: RedisConfig = mock()

    @BeforeEach
    fun init() {
        whenever(redisClientMembershipTpa.sync()).thenReturn(membershipCommands)
        whenever(redisClientMembershipTpa.async()).thenReturn(membershipAsyncCommands)
        whenever(redisMetadataScore.sync()).thenReturn(userScoreCommands)
        whenever(redisConfig.membershipTTL).thenReturn(5)
    }

    @Test
    fun hasHouseHoldScore() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\"" +
                ":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800," +
                "27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452," +
                "\"ip\":154.130.20.55,\"household_score\":80,\"data_source\":3}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        val hSetKeyScore = argumentCaptor<String>()
        val metadataValueMap = argumentCaptor<Map<String, String>>()

        verify(redisClientMembershipTpa.sync(), times(1)).set(hSetKey.capture(), fieldValue.capture())
        verify(redisMetadataScore.sync(), times(1)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )
        assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])
        assertEquals(1, metadataValueMap.firstValue.size)
        assertEquals("80", metadataValueMap.firstValue["household_score"])
    }

    @Test
    fun noMatchingPartner() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55,\"data_source\":3,\"is_delta\":false}"

        whenever(userScoreCommands.hset(any(), any(), any())).thenReturn(true)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val hSetKey = argumentCaptor<String>()
        val hSetKeyDelete = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(1)).set(hSetKey.capture(), fieldValue.capture())
        verify(redisClientMembershipTpa.sync(), times(1)).del(hSetKeyDelete.capture())
        verify(redisMetadataScore.sync(), times(0)).hset(any(), any())
        assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])
    }

    @Test
    fun nonDataSourceThree() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\"" +
                ":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800," +
                "27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452," +
                "\"ip\":154.130.20.55,\"household_score\":80,\"data_source\":1}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKeyScore = argumentCaptor<String>()
        val metadataValueMap = argumentCaptor<Map<String, String>>()

        verify(redisClientMembershipTpa.sync(), times(0)).sadd(any(), any())
        verify(redisMetadataScore.sync(), times(1)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )

        assertEquals(1, metadataValueMap.firstValue.size)
        assertEquals("80", metadataValueMap.firstValue["household_score"])
    }

    @Test
    fun nullDataSource() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\"" +
                ":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800," +
                "27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452," +
                "\"ip\":154.130.20.55,\"household_score\":80,\"data_source\":1}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKeyScore = argumentCaptor<String>()
        val metadataValueMap = argumentCaptor<Map<String, String>>()

        verify(redisClientMembershipTpa.sync(), times(0)).sadd(any(), any())
        verify(redisMetadataScore.sync(), times(1)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )

        assertEquals(1, metadataValueMap.firstValue.size)
        assertEquals("80", metadataValueMap.firstValue["household_score"])
    }

    @Test
    fun deltaIsTrue() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55,\"data_source\":3,\"is_delta\":true}"

        whenever(userScoreCommands.hset(any(), any(), any())).thenReturn(true)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKey = argumentCaptor<String>()
        val hSetKeyDelete = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(1)).set(hSetKey.capture(), fieldValue.capture())
        verify(redisClientMembershipTpa.sync(), times(0)).del(hSetKeyDelete.capture())
        verify(redisMetadataScore.sync(), times(0)).hset(any(), any())
        assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])
    }

    @Test
    fun writeDeviceMetadataToCache() {
        val testMsg = MembershipUpdateMessage(
            guid = "006866ac-cfb1-4639-99d3-c7948d7f5111",
            advertiserId = 20460,
            epoch = 1556195886916784L,
            ip = "154.130.20.55",
            householdScore = 33,
            geoVersion = "43543543543",
            activityEpoch = 1556195801515452L,
            isDelta = false,
            dataSource = 8,
            metadataInfo = mapOf("household_score" to "55", "geo_version" to "76543543543"),
        )

        ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
        ).writeDeviceMetadata(testMsg)

        val valueMap = argumentCaptor<Map<String, String>>()
        verify(redisMetadataScore.sync(), times(1)).hset(any(), valueMap.capture())
        assertEquals(3, valueMap.firstValue.size)
        assertEquals(testMsg.householdScore.toString(), valueMap.firstValue["household_score"])
        assertEquals(testMsg.geoVersion, valueMap.firstValue["geo_version"])
        assertEquals(Gson().toJson(testMsg.metadataInfo), valueMap.firstValue["metadata_info"])
    }

    @Test
    fun hasHouseHoldScoreAndGeoVersionAndMetadataInfo() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\"" +
                ":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800," +
                "27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452," +
                "\"ip\":154.130.20.55,\"household_score\":80,\"geo_version\":55555,\"data_source\":3," +
                "\"metadata_info\":{\"household_score\":50,\"geo_version\":77777}}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        val hSetKeyScore = argumentCaptor<String>()
        val metadataValueMap = argumentCaptor<Map<String, String>>()

        verify(redisClientMembershipTpa.sync(), times(1)).set(hSetKey.capture(), fieldValue.capture())
        verify(redisMetadataScore.sync(), times(1)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )
        assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])
        assertEquals(3, metadataValueMap.firstValue.size)
        assertEquals("80", metadataValueMap.firstValue["household_score"])
        assertEquals("55555", metadataValueMap.firstValue["geo_version"])
        assertEquals(
            "{\"household_score\":\"50\",\"geo_version\":\"77777\"}",
            metadataValueMap.firstValue["metadata_info"],
        )
    }
}
