package com.steelhouse.membership.controller

import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThirdPartyConsumerTest {

    private val meterRegistry = SimpleMeterRegistry()

    private val redisClientMembershipTpa = mock<StatefulRedisClusterConnection<String, String>>()
    private val membershipCommands = mock<RedisAdvancedClusterCommands<String, String>>()

    private val redisClientDeviceInfo = mock<StatefulRedisClusterConnection<String, String>>()
    private val deviceCommands = mock<RedisAdvancedClusterCommands<String, String>>()

    private val redisConfig = mock<RedisConfig>()

    private val gson = Gson()

    @BeforeEach
    fun init() {
        whenever(redisClientMembershipTpa.sync()).thenReturn(membershipCommands)
        whenever(redisClientDeviceInfo.sync()).thenReturn(deviceCommands)
        whenever(redisConfig.membershipTTL).thenReturn(5)
    }

    @Test
    fun `all fields valid and present`() {
        val message = gson.toJson(
            mapOf(
                "guid" to "006866ac-cfb1-4639-99d3-c7948d7f5111",
                "advertiser_id" to 20460,
                "current_segments" to listOf(27797, 27798, 27801),
                "old_segments" to listOf(28579, 29060, 32357, 42631, 43527, 42825, 43508),
                "epoch" to 1556195886916784,
                "activity_epoch" to 1556195801515452,
                "ip" to "154.130.20.55",
                "household_score" to 80,
                "geo_version" to "1556195600",
                "data_source" to 3,
                "is_delta" to true,
                "metadata_info" to mapOf(
                    "cs_123" to 10,
                    "cs_321" to 20,
                )
            )
        )

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisClientDeviceInfo,
            redisConfig,
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val membershipSetKey = argumentCaptor<String>()
        val membershipSetValue = argumentCaptor<String>()

        val deviceSetKey = argumentCaptor<String>()
        val deviceSetValue = argumentCaptor<String>()

        val deviceHsetKey = argumentCaptor<String>()
        val deviceHsetValue = argumentCaptor<Map<String, String>>()

        verify(redisClientMembershipTpa.sync(), times(0)).del(
            any(),
        )

        verify(redisClientMembershipTpa.sync(), times(1)).set(
            membershipSetKey.capture(),
            membershipSetValue.capture(),
        )

        verify(redisClientDeviceInfo.sync(), times(1)).set(
            deviceSetKey.capture(),
            deviceSetValue.capture(),
        )

        verify(redisClientDeviceInfo.sync(), times(1)).hset(
            deviceHsetKey.capture(),
            deviceHsetValue.capture(),
        )

        assertEquals("154.130.20.55", membershipSetKey.firstValue)
        assertEquals(listOf(27797, 27798, 27801).joinToString(","), membershipSetValue.firstValue)

        assertEquals("154.130.20.55:geo_version", deviceSetKey.firstValue)
        assertEquals("1556195600", deviceSetValue.firstValue)

        assertEquals("154.130.20.55:household_score:campaign", deviceHsetKey.firstValue)
        assertEquals(mapOf("123" to "10", "321" to "20"), deviceHsetValue.firstValue)
    }

    @Test
    fun `missing metadata_info field`() {
        val message = gson.toJson(
            mapOf(
                "guid" to "006866ac-cfb1-4639-99d3-c7948d7f5111",
                "advertiser_id" to 20460,
                "current_segments" to listOf(27797, 27798, 27801),
                "old_segments" to listOf(28579, 29060, 32357, 42631, 43527, 42825, 43508),
                "epoch" to 1556195886916784,
                "activity_epoch" to 1556195801515452,
                "ip" to "154.130.20.55",
                "household_score" to 80,
                "geo_version" to "1556195600",
                "data_source" to 3,
                "is_delta" to false,
            )
        )

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisClientDeviceInfo,
            redisConfig,
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val membershipDelKey = argumentCaptor<String>()

        val membershipSetKey = argumentCaptor<String>()
        val membershipSetValue = argumentCaptor<String>()

        val deviceSetKey = argumentCaptor<String>()
        val deviceSetValue = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(1)).del(
            membershipDelKey.capture(),
        )

        verify(redisClientMembershipTpa.sync(), times(1)).set(
            membershipSetKey.capture(),
            membershipSetValue.capture(),
        )

        verify(redisClientDeviceInfo.sync(), times(1)).set(
            deviceSetKey.capture(),
            deviceSetValue.capture(),
        )

        verify(redisClientDeviceInfo.sync(), times(0)).hset(
            any(),
            any(),
        )

        assertEquals("154.130.20.55", membershipSetKey.firstValue)
        assertEquals(listOf(27797, 27798, 27801).joinToString(","), membershipSetValue.firstValue)

        assertEquals("154.130.20.55:geo_version", deviceSetKey.firstValue)
        assertEquals("1556195600", deviceSetValue.firstValue)
    }

    @Test
    fun `invalid data_source field`() {
        val message = gson.toJson(
            mapOf(
                "guid" to "006866ac-cfb1-4639-99d3-c7948d7f5111",
                "advertiser_id" to 20460,
                "current_segments" to listOf(27797, 27798, 27801),
                "old_segments" to listOf(28579, 29060, 32357, 42631, 43527, 42825, 43508),
                "epoch" to 1556195886916784,
                "activity_epoch" to 1556195801515452,
                "ip" to "154.130.20.55",
                "household_score" to 80,
                "geo_version" to "1556195600",
                "data_source" to 1,
                "is_delta" to true,
                "metadata_info" to mapOf(
                    "cs_123" to 10,
                    "cs_321" to 20,
                )
            )
        )

        val consumer = ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisClientDeviceInfo,
            redisConfig,
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val deviceHsetKey = argumentCaptor<String>()
        val deviceHsetValue = argumentCaptor<Map<String, String>>()

        val deviceSetKey = argumentCaptor<String>()
        val deviceSetValue = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(0)).set(
            any(),
            any(),
        )

        verify(redisClientDeviceInfo.sync(), times(1)).set(
            deviceSetKey.capture(),
            deviceSetValue.capture(),
        )

        verify(redisClientDeviceInfo.sync(), times(1)).hset(
            deviceHsetKey.capture(),
            deviceHsetValue.capture(),
        )

        assertEquals("154.130.20.55:geo_version", deviceSetKey.firstValue)
        assertEquals("1556195600", deviceSetValue.firstValue)

        assertEquals("154.130.20.55:household_score:campaign", deviceHsetKey.firstValue)
        assertEquals(mapOf("123" to "10", "321" to "20"), deviceHsetValue.firstValue)
    }

    @Test
    fun `test writeDeviceMetadata function`() {
        val message = MembershipUpdateMessage(
            guid = "006866ac-cfb1-4639-99d3-c7948d7f5111",
            advertiserId = 20460,
            epoch = 1556195886916784L,
            activityEpoch = 1556195801515452L,
            ip = "154.130.20.55",
            householdScore = 33,
            geoVersion = "43543543543",
            dataSource = 8,
            isDelta = false,
            metadataInfo = mapOf(
                "cs_123" to "10",
                "cs_321" to "20",
            ),
        )

        ThirdPartyConsumer(
            meterRegistry,
            redisClientMembershipTpa,
            redisClientDeviceInfo,
            redisConfig,
            mock(),
        ).writeDeviceMetadata(message)

        val deviceSetKey = argumentCaptor<String>()
        val deviceSetValue = argumentCaptor<String>()

        val deviceHsetKey = argumentCaptor<String>()
        val deviceHsetValue = argumentCaptor<Map<String, String>>()

        verify(redisClientDeviceInfo.sync(), times(1)).set(
            deviceSetKey.capture(),
            deviceSetValue.capture(),
        )

        verify(redisClientDeviceInfo.sync(), times(1)).hset(
            deviceHsetKey.capture(),
            deviceHsetValue.capture(),
        )

        assertEquals("154.130.20.55:geo_version", deviceSetKey.firstValue)
        assertEquals("43543543543", deviceSetValue.firstValue)

        assertEquals("154.130.20.55:household_score:campaign", deviceHsetKey.firstValue)
        assertEquals(mapOf("123" to "10", "321" to "20"), deviceHsetValue.firstValue)
    }
}
