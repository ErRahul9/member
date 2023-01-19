package com.steelhouse.membership.controller


import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
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
                                     val appConfig: AppConfig,
                                     @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
                                     private val redisConfig: RedisConfig): BaseConsumer(log = log,
        meterRegistry = meterRegistry,
        redisConnectionMembershipTpa = redisConnectionMembershipTpa,
        redisConfig = redisConfig,
        appConfig = appConfig) {

    val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

    val opmCacheSources = setOf(2,6,7) //OPM datasources
    val tpaCacheSources = setOf(3) // TPA datasources

    @KafkaListener(topics = ["membership-updates"], autoStartup = "\${membership.membershipConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {

        val membership = gson.fromJson(message, MembershipUpdateMessage::class.java)

        lock.acquire()

        CoroutineScope(context).launch {
            try {

                val segments = membership.currentSegments.map { it.toString() }.toTypedArray()

                val membershipResult = async {

                    if(membership.dataSource in opmCacheSources) {
                        writeMemberships(membership.ip.orEmpty(), segments, "ip",
                            Audiencetype.steelhouse.name)

                    } else if (membership.dataSource in tpaCacheSources) {
                        writeMemberships(membership.ip.orEmpty(), segments, "ip",
                            Audiencetype.steelhouse.name)
                    }
                    if (membership.oldSegments != null) {
                        val oldSegments = membership.oldSegments.map { it.toString() }.toTypedArray()
                        deleteMemberships(membership.ip.orEmpty(), oldSegments, "ip",
                            Audiencetype.oracle.name)
                    }
                }

                membershipResult.await()

            } finally {
                lock.release()
            }
        }

    }

}
