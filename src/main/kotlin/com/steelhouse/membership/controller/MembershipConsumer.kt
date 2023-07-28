package com.steelhouse.membership.controller

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class MembershipConsumer(
    meterRegistry: MeterRegistry,
    @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
    redisConfig: RedisConfig,
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
                val segments = membership.currentSegments.map { it.toString() }.toTypedArray()

                if (membership.dataSource in tpaCacheSources) {
                    val overwrite = membership?.isDelta ?: true

                    writeMemberships(
                        membership.ip.orEmpty(),
                        segments,
                        "ip",
                        overwrite,
                    )
                }
            } finally {
                lock.release()
            }
        }
    }
}
