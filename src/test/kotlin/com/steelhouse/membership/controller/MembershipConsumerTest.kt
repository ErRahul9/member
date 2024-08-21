package com.steelhouse.membership.controller

import com.aerospike.client.AerospikeClient
import com.aerospike.client.Bin
import com.aerospike.client.Key
import com.aerospike.client.policy.WritePolicy
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.configuration.AerospikeConfig
import com.steelhouse.membership.service.KafkaProducerService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val NAMESPACE = "rtb"
private const val SET_NAME = "household-profile"

class MembershipConsumerTest {

    private val aerospikeClient: AerospikeClient = mock<AerospikeClient>()

    private val meterRegistry = SimpleMeterRegistry()

    private val aerospikeConfig = mock<AerospikeConfig>()

    private var writePolicy = mock<WritePolicy>()

    private val kafkaProducerService: KafkaProducerService = mock()

    @BeforeEach
    fun init() {
        whenever(aerospikeConfig.namespace).thenReturn(NAMESPACE)
        whenever(aerospikeConfig.setName).thenReturn(SET_NAME)
    }

    @Test
    fun noValidDataSource() {
        val message =
            "{\"data_source\":\"1\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":\"154.130.20.55\"}"

        val consumer = MembershipConsumer(meterRegistry, aerospikeClient, aerospikeConfig, writePolicy, kafkaProducerService)
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val aerospikeKey = Key(NAMESPACE, SET_NAME, "154.130.20.55")
        val aerospikeValues = argumentCaptor<Bin>()

        verify(aerospikeClient, times(0)).put(
            eq(writePolicy),
            eq(aerospikeKey),
            aerospikeValues.capture()
        )

        assertThat(aerospikeValues.allValues).isEmpty()
        verify(kafkaProducerService, times(0)).sendMessage(eq("membership-updates-log"), any())
    }

    @Test
    fun tpaDataSourceWrite() {
        val message =
            "{\"data_source\":\"3\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":\"154.130.20.55\",\"is_delta\":false}"

        val consumer = MembershipConsumer(meterRegistry, aerospikeClient, aerospikeConfig, writePolicy, kafkaProducerService)
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val aerospikeKey = Key(NAMESPACE, SET_NAME, "154.130.20.55")
        val aerospikeValues = argumentCaptor<Bin>()

        val delKey = argumentCaptor<Key>()

        verify(aerospikeClient, times(1)).put(
            eq(writePolicy),
            eq(aerospikeKey),
            aerospikeValues.capture()
        )

        verify(aerospikeClient, times(1)).delete(
            eq(writePolicy),
            delKey.capture(),
        )

        assertThat(aerospikeValues.allValues).containsExactlyInAnyOrder(
            Bin("segments", listOf(27797, 27798, 27801).joinToString(",")),
        )
        assertEquals("154.130.20.55", delKey.firstValue.userKey.toString())
        verify(kafkaProducerService, times(1)).sendMessage(eq("membership-updates-log"), any())
    }

    /**
     * When the is_delta flag has been set to True do not delete all segments associated with an IP address.
     */
    @Test
    fun deltaIsTrue() {
        val message =
            "{\"data_source\":\"3\",\"guid\":\"006866ac-cfb1-4639-99d3-c7948d7f5111\",\"advertiser_id\":20460,\"current_segments\":[27797,27798,27801],\"old_segments\":[28579,29060,32357,42631,43527,42825,43508,27702,27799,27800,27992,28571,29595,28572,44061],\"epoch\":1556195886916784,\"activity_epoch\":1556195801515452,\"ip\":\"154.130.20.55\",\"is_delta\":true}"

        val consumer = MembershipConsumer(meterRegistry, aerospikeClient, aerospikeConfig, writePolicy, kafkaProducerService)
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val aerospikeKey = Key(NAMESPACE, SET_NAME, "154.130.20.55")
        val aerospikeValues = argumentCaptor<Bin>()

        verify(aerospikeClient, times(1)).put(
            eq(writePolicy),
            eq(aerospikeKey),
            aerospikeValues.capture()
        )

        verify(aerospikeClient, times(0)).delete(
            anyOrNull(),
            any()
        )

        assertThat(aerospikeValues.allValues).containsExactlyInAnyOrder(
            Bin("segments", listOf(27797, 27798, 27801).joinToString(",")),
        )

        verify(kafkaProducerService, times(1)).sendMessage(eq("membership-updates-log"), any())
    }
}
