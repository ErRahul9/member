package com.steelhouse.membership.controller


import com.steelhouse.core.model.gsonmessages.GsonMessageUtil
import com.steelhouse.core.model.segmentation.gson.MembershipUpdateMessage
import com.steelhouse.membership.configuration.RedisConfig
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
import java.util.stream.Collectors


@Service
class OracleConsumer constructor(@Qualifier("app") private val log: Log,
                                private val meterRegistry: MeterRegistry,
                                @Qualifier("redisConnectionPartner") private val redisConnectionPartner: StatefulRedisClusterConnection<String, String>,
                                @Qualifier("redisConnectionMembership") private val redisConnectionMembership: StatefulRedisClusterConnection<String, String>,
                                private val redisConfig: RedisConfig): BaseConsumer(log = log,
        meterRegistry = meterRegistry, redisConnectionPartner = redisConnectionPartner,
        redisConnectionMembership = redisConnectionMembership,
        redisConfig = redisConfig) {

    @KafkaListener(topics = ["sh-dw-generated-audiences"], autoStartup = "\${membership.oracleConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {

        val oracleMembership = GsonMessageUtil.deserialize(message, MembershipUpdateMessage::class.java)

        lock.acquire()

        CoroutineScope(context).launch {
            try {

                val segments = oracleMembership.currentSegments.stream().map { it.toString() }.collect(Collectors.joining(","))

                val partnerResult = async {
                    retrievePartnerId(oracleMembership.guid, Audiencetype.oracle.name)
                }

                val membershipResult = async {
                    writeMemberships(oracleMembership.guid.orEmpty(), segments, oracleMembership.aid.toString(), "steelhouse", Audiencetype.oracle.name)
                    writeMemberships(oracleMembership.ip.orEmpty(), segments, oracleMembership.aid.toString(), "ip", Audiencetype.oracle.name)
                }

                val partnerResults = mutableListOf<Deferred<Any>>()

                val partners = partnerResult.await()
                for(key in partners.orEmpty().keys) {

                    partnerResults += async {
                        val partnerGuid = partners.orEmpty()[key]
                        writeMemberships(partnerGuid.orEmpty(), segments, oracleMembership.aid.toString(), key, Audiencetype.oracle.name)
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
