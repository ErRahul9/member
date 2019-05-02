package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.lettuce.core.RedisFuture
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.logging.Log
import org.junit.Before
import org.junit.Test


class KafkaConsumerTest {

    var log: Log = mock()

    var redisClientPartner: StatefulRedisClusterConnection<String, String> = mock()

    var redisClientMembership: StatefulRedisClusterConnection<String, String> = mock()

    var partnerCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    @Before
    fun init() {
        whenever(redisClientPartner.async()).thenReturn(partnerCommands)
        whenever(redisClientMembership.async()).thenReturn(membershipCommands)


    }


    @Test
    fun oneMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452}"

        val future: RedisFuture<Map<String, String>> = mock()
        whenever(future.get()).thenReturn(mutableMapOf(Pair("test","test")))
        whenever(partnerCommands.hgetall(any())).thenReturn(future)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(future2)

        val consumer = KafkaConsumer(log, redisClientPartner, redisClientMembership)
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        verify(redisClientPartner.async(), times(1)).hgetall(any())
        verify(redisClientMembership.async(), times(2)).hset(any(), any(), any())
    }

    @Test
    fun twoMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452}"

        val future: RedisFuture<Map<String, String>> = mock()
        whenever(future.get()).thenReturn(mutableMapOf(Pair("test","test"), Pair("test2","test2")))
        whenever(partnerCommands.hgetall(any())).thenReturn(future)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(future2)

        val consumer = KafkaConsumer(log, redisClientPartner, redisClientMembership)
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        verify(redisClientPartner.async(), times(1)).hgetall(any())
        verify(redisClientMembership.async(), times(3)).hset(any(), any(), any())
    }

    @Test
    fun noMatchingPartner() {

        val message = "{\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452}"

        val future: RedisFuture<Map<String, String>> = mock()
        whenever(future.get()).thenReturn(mutableMapOf())
        whenever(partnerCommands.hgetall(any())).thenReturn(future)

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(future2)

        val consumer = KafkaConsumer(log, redisClientPartner, redisClientMembership)
        consumer.consume(message)

        runBlocking {
            delay(100)
        }


        verify(redisClientPartner.async(), times(1)).hgetall(any())
        verify(redisClientMembership.async(), times(1)).hset(any(), any(), any())
    }

}
