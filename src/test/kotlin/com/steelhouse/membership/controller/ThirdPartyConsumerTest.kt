package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
                "\"ip\":154.130.20.55,\"household_score\":80,\"data_source\":3,\"c_data\":{\"34343\":{\"household_score\":60}}}"

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
            mock()
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
        verify(redisMetadataScore.sync(), times(2)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )
        assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])
        assertEquals("154.130.20.55-34343", hSetKeyScore.firstValue)
        assertEquals(1, metadataValueMap.firstValue.size)
        assertEquals("60", metadataValueMap.firstValue["household_score"])
        assertEquals("{\"household_score\":\"80\"}", metadataValueMap.secondValue["metadata_info"])
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
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(1)).set(hSetKey.capture(), fieldValue.capture())
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
                "\"ip\":154.130.20.55,\"household_score\":80,\"data_source\":1,\"c_data\":{\"34343\":" +
                "{\"household_score\": 60}}}"

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
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKeyScore = argumentCaptor<String>()
        val metadataValueMap = argumentCaptor<Map<String, String>>()

        verify(redisClientMembershipTpa.sync(), times(0)).sadd(any(), any())
        verify(redisMetadataScore.sync(), times(2)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )

        assertEquals("154.130.20.55-34343", hSetKeyScore.firstValue)
        assertEquals(1, metadataValueMap.firstValue.size)
        assertEquals("60", metadataValueMap.firstValue["household_score"])
        assertEquals("{\"household_score\":\"80\"}", metadataValueMap.secondValue["metadata_info"])
    }

    @Test
    fun nullDataSource() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\"" +
                ":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800," +
                "27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452," +
                "\"ip\":154.130.20.55,\"household_score\":80,\"data_source\":1,\"c_data\":{\"34343\":" +
                "{\"household_score\": 60}}}"

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
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKeyScore = argumentCaptor<String>()
        val metadataValueMap = argumentCaptor<Map<String, String>>()

        verify(redisClientMembershipTpa.sync(), times(0)).sadd(any(), any())
        verify(redisMetadataScore.sync(), times(2)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )

        assertEquals("154.130.20.55-34343", hSetKeyScore.firstValue)
        assertEquals(1, metadataValueMap.firstValue.size)
        assertEquals("60", metadataValueMap.firstValue["household_score"])
        assertEquals("{\"household_score\":\"80\"}", metadataValueMap.secondValue["metadata_info"])
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
            mock(),
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
    fun testWriteDeviceMetadata() {
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
            metadataInfo = mapOf("_hh_score" to "55", "_geo_ver" to "76543543543"),
            cData = mapOf(
                "34343" to mapOf(
                    "household_score" to 55
                )
            ),
        )

        ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisMetadataScore,
            redisConfig,
            mock(),
        ).writeDeviceMetadata(testMsg)

        val valueMap = argumentCaptor<Map<String, String>>()
        val key = argumentCaptor<String>()
        verify(redisMetadataScore.sync(), times(2)).hset(key.capture(), valueMap.capture())

        assertEquals("154.130.20.55-34343", key.firstValue)
        assertEquals(1, valueMap.firstValue.size)
        assertEquals("55", valueMap.firstValue["household_score"])

        assertEquals(1, valueMap.secondValue.size)
        assertEquals("{\"_hh_score\":\"55\",\"_geo_ver\":\"76543543543\",\"household_score\":\"33\",\"geo_version\":\"43543543543\"}", valueMap.secondValue["metadata_info"])
        assertEquals("154.130.20.55", key.secondValue)
    }

    @Test
    fun hasHouseHoldScoreAndGeoVersionAndMetadataInfo() {
        val message =
            "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\"" +
                ":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800," +
                "27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452," +
                "\"ip\":154.130.20.55,\"household_score\":80,\"geo_version\":55555,\"data_source\":3," +
                "\"metadata_info\":{\"_hh_score\":50,\"_geo_ver\":77777},\"c_data\":{\"34343\":{\"household_score\": 60}}}"

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
            mock(),
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
        verify(redisMetadataScore.sync(), times(2)).hset(
            hSetKeyScore.capture(),
            metadataValueMap.capture(),
        )
        assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])

        assertEquals("154.130.20.55", hSetKeyScore.secondValue)
        assertEquals(1, metadataValueMap.secondValue.size)
        assertEquals(
            "{\"_hh_score\":\"50\",\"_geo_ver\":\"77777\",\"household_score\":\"80\",\"geo_version\":\"55555\"}",
            metadataValueMap.secondValue["metadata_info"],
        )

        assertEquals("154.130.20.55-34343", hSetKeyScore.firstValue)
        assertEquals(1, metadataValueMap.firstValue.size)
        assertEquals(
            "60", metadataValueMap.firstValue["household_score"],
        )
    }
}
