package com.steelhouse.membership.controller


import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.steelhouse.core.model.gsonmessages.GsonMessageUtil
import com.steelhouse.core.model.segmentation.gson.MembershipUpdateMessage
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.Membership
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.stream.Collectors


@Service
class ThirdPartyConsumer constructor(@Qualifier("app") private val log: Log,
                                     private val meterRegistry: MeterRegistry,
                                     val appConfig: AppConfig,
                                     @Qualifier("redisConnectionPartner") private val redisConnectionPartner: StatefulRedisClusterConnection<String, String>,
                                     @Qualifier("redisConnectionMembership") private val redisConnectionMembership: StatefulRedisClusterConnection<String, String>,
                                     private val amazonDynamoDB: AmazonDynamoDB,
                                     private val redisConfig: RedisConfig): BaseConsumer(log = log,
        meterRegistry = meterRegistry, redisConnectionPartner = redisConnectionPartner,
        redisConnectionMembership = redisConnectionMembership,
        redisConfig = redisConfig) {

    private val mapper = DynamoDBMapper(amazonDynamoDB)

    @KafkaListener(topics = ["sh-dw-generated-audiences"], autoStartup = "\${membership.oracleConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {

        val oracleMembership = GsonMessageUtil.deserialize(message, MembershipUpdateMessage::class.java)

        lock.acquire()

        CoroutineScope(context).launch {
            try {

                val membershipEvent = async {
                    processMembershipEvents(oracleMembership)
                }

                val membershipResult = async {

                    val segments = oracleMembership.currentSegments.stream().map { it.toString() }
                            .collect(Collectors.joining(","))

                    writeMemberships(oracleMembership.ip.orEmpty(), segments,
                            oracleMembership.aid.toString(), "ip", Audiencetype.oracle.name)
                }

                membershipResult.await()
                membershipEvent.await()
            } finally {
                lock.release()
            }
        }
        }

    private suspend fun processMembershipEvents(oracleMembership: MembershipUpdateMessage) {

        withContext(context) {

           val currentEvents =  async {
                val events = generatePayload(oracleMembership, oracleMembership.currentSegments)
                save(events)
            }

            val oldEvents = async {
                val oldEvents = generatePayload(oracleMembership, oracleMembership.oldSegments)
                delete(oldEvents)
            }

            currentEvents.await()
            oldEvents.await()

        }


    }

    private fun generatePayload(oracleMembership: MembershipUpdateMessage, segments: List<Int>): Set<Membership> {
        val events = mutableSetOf<Membership>()

        for (segment in segments) {
            val membership = Membership()

            membership.ip = oracleMembership.ip
            membership.segment = segment.toString()
            membership.source = oracleMembership.source
            membership.epoch = oracleMembership.epoch / 1000 + appConfig.membershipDataExpirationWindowSeconds!!
            membership.aid = oracleMembership.aid

            events.add(membership)
        }

        return events
    }

    private fun delete(data: Set<Membership>) {

        val results = mapper.batchDelete(data)

        for (x in results) {
            log.error(x.exception.toString())
            log.error(x.unprocessedItems.size.toString() + " events failed to process")
        }
    }

    private fun save(data: Set<Membership>) {

            val results = mapper.batchSave(data)

            for (x in results) {
                log.error(x.exception.toString())
                log.error(x.unprocessedItems.size.toString() + " events failed to process")
            }
    }


}
