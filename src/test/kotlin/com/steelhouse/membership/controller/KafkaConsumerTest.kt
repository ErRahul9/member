package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.lettuce.core.RedisFuture
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.logging.Log
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class KafkaConsumerTest {

    var log: Log = mock()

    var redisClientPartner: StatefulRedisClusterConnection<String, String> = mock()

    var redisClientMembership: StatefulRedisClusterConnection<String, String> = mock()

    var partnerCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    val meterRegistry: MeterRegistry = mock()

    @Before
    fun init() {
        whenever(redisClientPartner.async()).thenReturn(partnerCommands)
        whenever(redisClientMembership.async()).thenReturn(membershipCommands)


    }


    @Test
    fun oneMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        val future: RedisFuture<Map<String, String>> = mock()
        whenever(future.get()).thenReturn(mutableMapOf(Pair("beeswax","beeswaxId")))
        whenever(partnerCommands.hgetall(any())).thenReturn(future)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(future2)

        val consumer = KafkaConsumer(log, meterRegistry, redisClientPartner, redisClientMembership)
        consumer.partnerCounter = mock()
        consumer.partnerTimer = mock()
        consumer.membershipCounter = mock()
        consumer.membershipTimer = mock()
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val getAllKey = argumentCaptor<String>()

        verify(redisClientPartner.async(), times(1)).hgetall(getAllKey.capture())
        Assert.assertEquals("006866ac-cfb1-4639-99d3-c7948d7f5111", getAllKey.firstValue)

        val hSetKey = argumentCaptor<String>()
        val fieldKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembership.async(), times(3)).hset(hSetKey.capture(), fieldKey.capture(), fieldValue.capture())
        Assert.assertEquals(listOf("006866ac-cfb1-4639-99d3-c7948d7f5111", "154.130.20.55", "beeswaxId"), hSetKey.allValues)
        Assert.assertEquals(listOf("20460", "20460", "20460" ), fieldKey.allValues)
        Assert.assertEquals(listOf("27797,27798,27801", "27797,27798,27801", "27797,27798,27801"), fieldValue.allValues)


    }

    @Test
    fun twoMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        val future: RedisFuture<Map<String, String>> = mock()
        whenever(future.get()).thenReturn(mutableMapOf(Pair("beeswax","beeswaxId"), Pair("tradedesk","tradedeskId")))
        whenever(partnerCommands.hgetall(any())).thenReturn(future)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(future2)

        val consumer = KafkaConsumer(log, meterRegistry, redisClientPartner, redisClientMembership)
        consumer.partnerCounter = mock()
        consumer.partnerTimer = mock()
        consumer.membershipCounter = mock()
        consumer.membershipTimer = mock()
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val getAllKey = argumentCaptor<String>()
        verify(redisClientPartner.async(), times(1)).hgetall(getAllKey.capture())
        Assert.assertEquals("006866ac-cfb1-4639-99d3-c7948d7f5111", getAllKey.firstValue)

        val hSetKey = argumentCaptor<String>()
        val fieldKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembership.async(), times(4)).hset(hSetKey.capture(), fieldKey.capture(), fieldValue.capture())
        Assert.assertEquals(listOf("006866ac-cfb1-4639-99d3-c7948d7f5111", "154.130.20.55", "beeswaxId", "tradedeskId"), hSetKey.allValues)
        Assert.assertEquals(listOf("20460", "20460", "20460", "20460"), fieldKey.allValues)
        Assert.assertEquals(listOf("27797,27798,27801", "27797,27798,27801", "27797,27798,27801", "27797,27798,27801"), fieldValue.allValues)
    }

    @Test
    fun noMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        val future: RedisFuture<Map<String, String>> = mock()
        whenever(future.get()).thenReturn(mutableMapOf())
        whenever(partnerCommands.hgetall(any())).thenReturn(future)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(future2)

        val consumer = KafkaConsumer(log, meterRegistry, redisClientPartner, redisClientMembership)
        consumer.partnerCounter = mock()
        consumer.partnerTimer = mock()
        consumer.membershipCounter = mock()
        consumer.membershipTimer = mock()
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val getAllKey = argumentCaptor<String>()
        verify(redisClientPartner.async(), times(1)).hgetall(getAllKey.capture())
        Assert.assertEquals("006866ac-cfb1-4639-99d3-c7948d7f5111", getAllKey.firstValue)

        val hSetKey = argumentCaptor<String>()
        val fieldKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembership.async(), times(2)).hset(hSetKey.capture(), fieldKey.capture(), fieldValue.capture())
        Assert.assertEquals(listOf("006866ac-cfb1-4639-99d3-c7948d7f5111", "154.130.20.55"), hSetKey.allValues)
        Assert.assertEquals(listOf("20460", "20460"), fieldKey.allValues)
        Assert.assertEquals(listOf("27797,27798,27801", "27797,27798,27801"), fieldValue.allValues)
    }

}
