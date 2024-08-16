package com.steelhouse.membership.controller

import com.aerospike.client.AerospikeClient
import com.aerospike.client.Bin
import com.aerospike.client.Key
import com.aerospike.client.policy.WritePolicy
import com.google.common.base.Stopwatch
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.steelhouse.membership.configuration.AerospikeConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class ThirdPartyConsumer(
    private val meterRegistry: MeterRegistry,
    private val aerospikeClient: AerospikeClient,
    private val aerospikeConfig: AerospikeConfig,
    private val writePolicy: WritePolicy,
    @Qualifier("app") private val log: Log,
) : BaseConsumer(
    meterRegistry = meterRegistry,
    aerospikeClient = aerospikeClient,
    aerospikeConfig = aerospikeConfig,
    writePolicy = writePolicy,
) {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @KafkaListener(topics = ["sh-dw-generated-audiences"], autoStartup = "\${membership.oracleConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {
        lock.acquire()
        CoroutineScope(context).launch {
            try {
                val oracleMembership = gson.fromJson(message, MembershipUpdateMessage::class.java)
                val results = mutableListOf<Deferred<Any>>()

                if (oracleMembership.currentSegments != null && oracleMembership.dataSource in tpaCacheSources) {
                    val overwrite = !(oracleMembership?.isDelta ?: true)
                    results += async {
                        writeMemberships(
                            oracleMembership.ip,
                            oracleMembership.currentSegments,
                            "ip",
                            overwrite,
                        )
                    }
                }

                // if the currentSegments is empty, we should not write into device metadata and delete the ip
                results += if (oracleMembership.currentSegments.isNotEmpty()) {
                    async {
                        writeDeviceMetadata(oracleMembership)
                    }
                } else {
                    async {
                        deleteIp(oracleMembership.ip)
                    }
                }

                results.awaitAll()
            } catch (ex: JsonSyntaxException) {
                meterRegistry.counter("invalid.audience.records").increment()
            } finally {
                lock.release()
            }
        }
    }

    fun writeDeviceMetadata(message: MembershipUpdateMessage) {
        val stopwatch = Stopwatch.createStarted()

        val key = Key(aerospikeConfig.namespace, aerospikeConfig.setName, message.ip)

        // Insert geo version
        val ipGeoVersionValue = message.geoVersion
        if (!ipGeoVersionValue.isNullOrEmpty()) {
            val geoVersion = Bin("geo_version", ipGeoVersionValue)
            aerospikeClient.put(writePolicy, key, geoVersion)
        }

        // Insert household score
        val ipHouseholdScoreValue = message.metadataInfo?.filterKeys {
            it.startsWith("cs_")
        }?.mapKeys {
            it.key.split("_").last()
        }
        if (!ipHouseholdScoreValue.isNullOrEmpty()) {
            val householdScore = Bin("household_score:campaign", ipHouseholdScoreValue)
            aerospikeClient.put(writePolicy, key, householdScore)
        }

        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.user.score.latency")
            .record(Duration.ofMillis(responseTime))
    }
}
