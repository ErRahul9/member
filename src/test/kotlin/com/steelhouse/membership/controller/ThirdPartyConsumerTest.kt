package com.steelhouse.membership.controller

import com.aerospike.client.AerospikeClient
import com.aerospike.client.Bin
import com.aerospike.client.Key
import com.aerospike.client.policy.WritePolicy
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.configuration.AerospikeConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val NAMESPACE = "rtb"
private const val SET_NAME = "household-profile"

class ThirdPartyConsumerTest {

    private val meterRegistry = SimpleMeterRegistry()

    private val aerospikeClient: AerospikeClient = mock<AerospikeClient>()

    private val aerospikeConfig = mock<AerospikeConfig>()

    private val writePolicy = mock<WritePolicy>()

    private val gson = Gson()

    @BeforeEach
    fun init() {
        whenever(aerospikeConfig.namespace).thenReturn(NAMESPACE)
        whenever(aerospikeConfig.setName).thenReturn(SET_NAME)
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
            aerospikeClient,
            aerospikeConfig,
            writePolicy,
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val aerospikeKey = Key(NAMESPACE, SET_NAME, "154.130.20.55")
        val aerospikeValues = argumentCaptor<Bin>()

        verify(aerospikeClient, times(0)).delete(
            anyOrNull(), any()
        )

        verify(aerospikeClient, times(3)).put(
            eq(writePolicy),
            eq(aerospikeKey),
            aerospikeValues.capture(),
        )

        assertThat(aerospikeValues.allValues).containsExactlyInAnyOrder(
            Bin("segments", listOf(27797, 27798, 27801).joinToString(",")),
            Bin("geo_version", "1556195600"),
            Bin("hhs:campaign", mapOf("123" to "10", "321" to "20")),
        )
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
            aerospikeClient,
            aerospikeConfig,
            writePolicy,
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(1000)
        }

        val aerospikeKey = Key(NAMESPACE, SET_NAME, "154.130.20.55")
        val aerospikeValues = argumentCaptor<Bin>()

        val membershipDelKey = argumentCaptor<Key>()

        verify(aerospikeClient, times(1)).delete(
            eq(writePolicy), membershipDelKey.capture()
        )

        verify(aerospikeClient, times(2)).put(
            eq(writePolicy),
            eq(aerospikeKey),
            aerospikeValues.capture(),
        )

        assertEquals("154.130.20.55", membershipDelKey.firstValue.userKey.toString())

        assertThat(aerospikeValues.allValues).containsExactlyInAnyOrder(
            Bin("segments", listOf(27797, 27798, 27801).joinToString(",")),
            Bin("geo_version", "1556195600"),
        )
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
            aerospikeClient,
            aerospikeConfig,
            writePolicy,
            mock(),
        )
        consumer.consume(message)

        runBlocking {
            delay(100)
        }

        val aerospikeKey = Key(NAMESPACE, SET_NAME, "154.130.20.55")
        val aerospikeValues = argumentCaptor<Bin>()

        verify(aerospikeClient, times(2)).put(
            eq(writePolicy),
            eq(aerospikeKey),
            aerospikeValues.capture(),
        )

        assertThat(aerospikeValues.allValues).containsExactlyInAnyOrder(
            Bin("geo_version", "1556195600"),
            Bin("hhs:campaign", mapOf("123" to "10", "321" to "20")),
        )
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
            segmentVersions = emptyList()
        )

        ThirdPartyConsumer(
            meterRegistry,
            aerospikeClient,
            aerospikeConfig,
            writePolicy,
            mock(),
        ).writeDeviceMetadata(message)

        val aerospikeKey = Key(NAMESPACE, SET_NAME, "154.130.20.55")
        val aerospikeValues = argumentCaptor<Bin>()

        verify(aerospikeClient, times(2)).put(
            eq(writePolicy),
            eq(aerospikeKey),
            aerospikeValues.capture(),
        )

        assertThat(aerospikeValues.allValues).containsExactlyInAnyOrder(
            Bin("geo_version", "43543543543"),
            Bin("hhs:campaign", mapOf("123" to "10", "321" to "20")),
        )
    }
}
