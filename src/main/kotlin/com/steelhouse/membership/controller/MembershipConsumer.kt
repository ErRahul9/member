package com.steelhouse.membership.controller

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateLogMessage
import com.steelhouse.membership.model.MembershipUpdateMessage
import com.steelhouse.membership.service.KafkaProducerService
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Instant

@Service
class MembershipConsumer(
    meterRegistry: MeterRegistry,
    @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
    redisConfig: RedisConfig,
    private val kafkaProducerService: KafkaProducerService
) : BaseConsumer(
    meterRegistry = meterRegistry,
    redisConnectionMembershipTpa = redisConnectionMembershipTpa,
    redisConfig = redisConfig,
) {

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
                if (membership.dataSource in tpaCacheSources) {
                    val overwrite = !(membership?.isDelta ?: true)

                    writeMemberships(
                        membership.ip,
                        membership.currentSegments,
                        "ip",
                        overwrite,
                    )
                    uploadLogMessage(membership)
                }
            } finally {
                lock.release()
            }
        }
    }

    private fun uploadLogMessage(membership: MembershipUpdateMessage) {
        val messageUpdateLog = MembershipUpdateLogMessage(
            guid = membership.guid,
            advertiserId = membership.advertiserId,
            epoch = Instant.now().toEpochMilli(),
            activityEpoch = membership.activityEpoch,
            ip = membership.ip,
            geoVersion = membership.geoVersion,
            segmentVersions = membership.segmentVersions
        )
        val logMessage = gson.toJson(messageUpdateLog)
        kafkaProducerService.sendMessage(MEMBERSHIP_UPDATES_LOG_TOPIC, logMessage)
    }

    companion object {
        const val MEMBERSHIP_UPDATES_LOG_TOPIC = "membership-updates-log"
    }
}
