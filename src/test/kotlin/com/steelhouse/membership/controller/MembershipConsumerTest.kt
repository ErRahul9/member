package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.service.KafkaProducerService
import io.lettuce.core.RedisFuture
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MembershipConsumerTest {

    var redisClientMembershipTpa: StatefulRedisClusterConnection<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterCommands<String, String> = mock()
    var membershipAsyncCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    var segmentMappingCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    val meterRegistry = SimpleMeterRegistry()

    var redisConfig: RedisConfig = mock()

    val kafkaProducerService: KafkaProducerService = mock()

    @BeforeEach
    fun init() {
        whenever(redisClientMembershipTpa.sync()).thenReturn(membershipCommands)
        whenever(redisClientMembershipTpa.async()).thenReturn(membershipAsyncCommands)
        whenever(redisConfig.membershipTTL).thenReturn(5)
    }

    @Test
    fun noValidDataSource() {
        val message =
            "{\"data_source\":\"1\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(meterRegistry, redisClientMembershipTpa, redisConfig, kafkaProducerService)
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembershipTpa.sync(), times(0)).sadd(hSetKey.capture(), fieldValue.capture())
        assertTrue(
            listOf(
                "006866ac-cfb1-4639-99d3-c7948d7f5111",
                "154.130.20.55",
                "beeswaxId",
                "tradedeskId",
            ).containsAll(hSetKey.allValues),
        )
        assertEquals(emptyList<String>(), fieldValue.allValues)
        verify(kafkaProducerService, times(0)).sendMessage(eq("membership-updates-log"), any())
    }

    @Test
    fun tpaDataSourceWrite() {
        val message =
            "{\"data_source\":\"3\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55,\"is_delta\":false}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(meterRegistry, redisClientMembershipTpa, redisConfig, kafkaProducerService)
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val hKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        val hKeyDelete = argumentCaptor<String>()
        verify(redisClientMembershipTpa.sync(), times(1)).set(hKey.capture(), fieldValue.capture())
        verify(redisClientMembershipTpa.sync(), times(1)).del(hKeyDelete.capture())
        assertEquals(listOf("154.130.20.55"), hKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])
        verify(kafkaProducerService, times(1)).sendMessage(eq("membership-updates-log"), any())
    }

    /**
     * When the is_delta flag has been set to True do not delete all segments associated with an IP address.
     */
    @Test
    fun deltaIsTrue() {
        val message =
            "{\"data_source\":\"3\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55,\"is_delta\":true}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(meterRegistry, redisClientMembershipTpa, redisConfig, kafkaProducerService)
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val hKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        val hKeyDelete = argumentCaptor<String>()
        verify(redisClientMembershipTpa.sync(), times(1)).set(hKey.capture(), fieldValue.capture())
        verify(redisClientMembershipTpa.sync(), times(0)).del(hKeyDelete.capture())
        assertEquals(listOf("154.130.20.55"), hKey.allValues)
        assertEquals(listOf(27797, 27798, 27801).joinToString(",") { it.toString() }, fieldValue.allValues[0])
        verify(kafkaProducerService, times(1)).sendMessage(eq("membership-updates-log"), any())
    }
}
