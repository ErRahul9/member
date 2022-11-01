package com.steelhouse.membership.controller

import com.nhaarman.mockitokotlin2.*
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.configuration.RedisConfig
import io.lettuce.core.RedisFuture
import io.lettuce.core.ScriptOutputType
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

    var redisClientMembershipTpa: StatefulRedisClusterConnection<String, String> = mock()

    var redisConnectionRecency: StatefulRedisClusterConnection<String, String> = mock()


    var membershipCommands: RedisAdvancedClusterCommands<String, String> = mock()
    var membershipAsyncCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()
    var recencyAsyncCommands: RedisAdvancedClusterCommands<String, String> = mock()

    var segmentMappingCommands: RedisAdvancedClusterAsyncCommands<String, String> = mock()

    val meterRegistry = SimpleMeterRegistry()

    var redisConfig: RedisConfig = mock()

    val appConfig = AppConfig()

    @Before
    fun init() {
        whenever(redisClientMembershipTpa.sync()).thenReturn(membershipCommands)
        whenever(redisClientMembershipTpa.async()).thenReturn(membershipAsyncCommands)
        whenever(redisConnectionRecency.sync()).thenReturn(recencyAsyncCommands)
        whenever(redisConfig.membershipTTL).thenReturn(5)

        appConfig.recencyExpirationWindowSeconds = 100
        appConfig.recencySha = "iuhioy87yg"
        appConfig.recencyDeviceIDTTLSeconds = 100
    }


    @Test
    fun noValidDataSource() {

        val message = "{\"data_source\":\"1\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"

        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(log, meterRegistry, appConfig, redisClientMembershipTpa,
            redisConnectionRecency, redisConfig)
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembershipTpa.sync(), times(0)).sadd(hSetKey.capture(), fieldValue.capture())
        Assert.assertTrue(listOf("006866ac-cfb1-4639-99d3-c7948d7f5111", "154.130.20.55", "beeswaxId", "tradedeskId").containsAll(hSetKey.allValues))
        Assert.assertEquals(emptyList<String>(), fieldValue.allValues)

    }

    @Test
    fun tpaDataSourceWrite() {

        val message = "{\"data_source\":\"3\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":154.130.20.55}"


        val future2: RedisFuture<Boolean> = mock()
        whenever(future2.get()).thenReturn(true)
        whenever(membershipAsyncCommands.expire(any(), same(5))).thenReturn(future2)
        whenever(membershipCommands.hset(any(), any(), any())).thenReturn(true)

        val segmentMappingFuture: RedisFuture<String> = mock()
        whenever(segmentMappingFuture.get()).thenReturn("steelhouse-4")
        whenever(segmentMappingCommands.get(any())).thenReturn(segmentMappingFuture)

        val consumer = MembershipConsumer(log, meterRegistry, appConfig, redisClientMembershipTpa,
            redisConnectionRecency, redisConfig)
        consumer.consume(message)

        runBlocking {
            delay(100)
        }


        val hSetKey = argumentCaptor<String>()
        val fieldValue = argumentCaptor<String>()
        verify(redisClientMembershipTpa.sync(), times(1)).sadd(hSetKey.capture(), fieldValue.capture())
        Assert.assertEquals(listOf("154.130.20.55"), hSetKey.allValues)
        Assert.assertEquals(listOf("27797", "27798", "27801"), fieldValue.allValues)
    }

}
