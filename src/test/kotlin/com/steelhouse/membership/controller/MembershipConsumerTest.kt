package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.configuration.RedisConfig
import io.lettuce.core.RedisFuture
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.logging.Log
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class MembershipConsumerTest {

    var log: Log = mock()

    var redisClientPartner: StatefulRedisClusterConnection<String, String> = mock()

    var redisClientMembership: StatefulRedisClusterConnection<String, String> = mock()

    var partnerCommands: RedisAdvancedClusterCommands<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterCommands<String, String> = mock()
    var membershipAsyncCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    var segmentMappingCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    val meterRegistry = SimpleMeterRegistry()

    var redisConfig: RedisConfig = mock()

    @Before
    fun init() {
        whenever(redisClientPartner.sync()).thenReturn(partnerCommands)
        whenever(redisClientMembership.sync()).thenReturn(membershipCommands)
        whenever(redisClientMembership.async()).thenReturn(membershipAsyncCommands)
    }


    @Test
    fun oneMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        whenever(partnerCommands.hgetall(any())).thenReturn(mutableMapOf(Pair("beeswax","beeswaxId")))

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), any())).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(log, meterRegistry, redisClientPartner, redisClientMembership, redisConfig)
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val getAllKey = argumentCaptor<String>()

        verify(redisClientPartner.sync(), times(1)).hgetall(getAllKey.capture())
        Assert.assertEquals("006866ac-cfb1-4639-99d3-c7948d7f5111", getAllKey.firstValue)

        val hSetKey = argumentCaptor<String>()
        val fieldKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembership.sync(), times(3)).sadd(hSetKey.capture(), fieldValue.capture())
        Assert.assertEquals(listOf("006866ac-cfb1-4639-99d3-c7948d7f5111", "154.130.20.55", "beeswaxId"), hSetKey.allValues)
        Assert.assertEquals(listOf("27797", "27798", "27801", "27797", "27798", "27801", "27797", "27798", "27801"), fieldValue.allValues)

    }

    @Test
    fun twoMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        whenever(partnerCommands.hgetall(any())).thenReturn(mutableMapOf(Pair("beeswax","beeswaxId"), Pair("tradedesk","tradedeskId")))

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), any())).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(log, meterRegistry, redisClientPartner, redisClientMembership, redisConfig)
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val getAllKey = argumentCaptor<String>()
        verify(redisClientPartner.sync(), times(1)).hgetall(getAllKey.capture())
        Assert.assertEquals("006866ac-cfb1-4639-99d3-c7948d7f5111", getAllKey.firstValue)

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembership.sync(), times(4)).sadd(hSetKey.capture(), fieldValue.capture())
        Assert.assertEquals(listOf("006866ac-cfb1-4639-99d3-c7948d7f5111", "154.130.20.55", "beeswaxId", "tradedeskId"), hSetKey.allValues)
        Assert.assertEquals(listOf("27797", "27798", "27801", "27797", "27798", "27801", "27797", "27798", "27801", "27797", "27798" ,"27801"), fieldValue.allValues)

    }

    @Test
    fun noMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        whenever(partnerCommands.hgetall(any())).thenReturn(mutableMapOf())

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), any())).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(log, meterRegistry, redisClientPartner, redisClientMembership, redisConfig)
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val getAllKey = argumentCaptor<String>()
        verify(redisClientPartner.sync(), times(1)).hgetall(getAllKey.capture())
        Assert.assertEquals("006866ac-cfb1-4639-99d3-c7948d7f5111", getAllKey.firstValue)

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembership.sync(), times(2)).sadd(hSetKey.capture(), fieldValue.capture())
        Assert.assertEquals(listOf("006866ac-cfb1-4639-99d3-c7948d7f5111", "154.130.20.55"), hSetKey.allValues)
        Assert.assertEquals(listOf("27797", "27798", "27801", "27797", "27798", "27801"), fieldValue.allValues)
    }

}
