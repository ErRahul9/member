package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.*
import com.steelhouse.membership.configuration.RedisConfig
import io.lettuce.core.RedisFuture
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ThirdPartyConsumerTest {

    var redisClientMembershipTpa: StatefulRedisClusterConnection<String, String> = mock()

    var redisClientUserScore: StatefulRedisClusterConnection<String, String> = mock()
    var userScoreCommands: RedisAdvancedClusterCommands<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterCommands<String, String> = mock()
    var membershipAsyncCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    var segmentMappingCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    val meterRegistry = SimpleMeterRegistry()

    var redisConfig: RedisConfig = mock()

    @Before
    fun init() {
        whenever(redisClientMembershipTpa.sync()).thenReturn(membershipCommands)
        whenever(redisClientMembershipTpa.async()).thenReturn(membershipAsyncCommands)
        whenever(redisClientUserScore.sync()).thenReturn(userScoreCommands)
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
            redisClientUserScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        val hSetKeyScore = argumentCaptor<String>()
        val fieldKeyScore = argumentCaptor<String>()
        val fieldValueScore = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(1)).sadd(hSetKey.capture(), fieldValue.capture())
        verify(redisClientUserScore.sync(), times(1)).hset(
            hSetKeyScore.capture(),
            fieldKeyScore.capture(),
            fieldValueScore.capture(),
        )
        Assert.assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        Assert.assertEquals(listOf("27797", "27798", "27801"), fieldValue.allValues)
        Assert.assertEquals("household_score", fieldKeyScore.firstValue)
        Assert.assertEquals("80", fieldValueScore.firstValue)
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
            redisClientUserScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKey = argumentCaptor<String>()
        val hSetKeyDelete = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(1)).sadd(hSetKey.capture(), fieldValue.capture())
        verify(redisClientMembershipTpa.sync(), times(1)).del(hSetKeyDelete.capture())
        verify(redisClientUserScore.sync(), times(0)).hset(any(), any(), any())
        Assert.assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        Assert.assertEquals(listOf("27797", "27798", "27801"), fieldValue.allValues)
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
            redisClientUserScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKeyScore = argumentCaptor<String>()
        val fieldKeyScore = argumentCaptor<String>()
        val fieldValueScore = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(0)).sadd(any(), any())
        verify(redisClientUserScore.sync(), times(1)).hset(
            hSetKeyScore.capture(),
            fieldKeyScore.capture(),
            fieldValueScore.capture(),
        )

        Assert.assertEquals("household_score", fieldKeyScore.firstValue)
        Assert.assertEquals("80", fieldValueScore.firstValue)
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
            redisClientUserScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKeyScore = argumentCaptor<String>()
        val fieldKeyScore = argumentCaptor<String>()
        val fieldValueScore = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(0)).sadd(any(), any())
        verify(redisClientUserScore.sync(), times(1)).hset(
            hSetKeyScore.capture(),
            fieldKeyScore.capture(),
            fieldValueScore.capture(),
        )

        Assert.assertEquals("household_score", fieldKeyScore.firstValue)
        Assert.assertEquals("80", fieldValueScore.firstValue)
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
            redisClientUserScore,
            redisConfig,
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val hSetKey = argumentCaptor<String>()
        val hSetKeyDelete = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()

        verify(redisClientMembershipTpa.sync(), times(1)).sadd(hSetKey.capture(), fieldValue.capture())
        verify(redisClientMembershipTpa.sync(), times(0)).del(hSetKeyDelete.capture())
        verify(redisClientUserScore.sync(), times(0)).hset(any(), any(), any())
        Assert.assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        Assert.assertEquals(listOf("27797", "27798", "27801"), fieldValue.allValues)
    }
}
