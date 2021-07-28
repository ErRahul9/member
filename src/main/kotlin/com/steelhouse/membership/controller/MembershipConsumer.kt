package com.steelhouse.membership.controller


import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException


@Service
class MembershipConsumer constructor(@Qualifier("app") private val log: Log,
                                     private val meterRegistry: MeterRegistry,
                                     @Qualifier("redisConnectionPartner") private val redisConnectionPartner: StatefulRedisClusterConnection<String, String>,
                                     @Qualifier("redisConnectionMembership") private val redisConnectionMembership: StatefulRedisClusterConnection<String, String>,
                                     private val redisConfig: RedisConfig): BaseConsumer(log = log,
        meterRegistry = meterRegistry, redisConnectionPartner = redisConnectionPartner,
        redisConnectionMembership = redisConnectionMembership,
        redisConfig = redisConfig) {

    val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()


    @KafkaListener(topics = ["membership-updates"], autoStartup = "\${membership.membershipConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {

        val membership = gson.fromJson(message, MembershipUpdateMessage::class.java)

        lock.acquire()

        CoroutineScope(context).launch {
            try {

                val segments = membership.currentSegments.map { it.toString() }.toTypedArray()

                val partnerResult = async {
                    retrievePartnerId(membership.guid, Audiencetype.steelhouse.name)
                }

                val membershipResult = async {
                    writeMemberships(membership.guid.orEmpty(), segments, "steelhouse", Audiencetype.steelhouse.name)
                    writeMemberships(membership.ip.orEmpty(), segments, "ip", Audiencetype.steelhouse.name)
                }

                val partnerResults = mutableListOf<Deferred<Any>>()

                val partners = partnerResult.await()
                for(key in partners.orEmpty().keys) {

                    partnerResults += async {
                        val partnerGuid = partners.orEmpty()[key]
                        writeMemberships(partnerGuid.orEmpty(), segments, key, Audiencetype.steelhouse.name)
                    }
                }

                membershipResult.await()
                partnerResults.forEach{it.await()}
            } finally {
                lock.release()
            }
        }

    }

}
