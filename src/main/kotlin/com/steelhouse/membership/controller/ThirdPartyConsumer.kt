package com.steelhouse.membership.controller


import com.steelhouse.core.model.gsonmessages.GsonMessageUtil
import com.steelhouse.core.model.segmentation.gson.MembershipUpdateMessage
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.configuration.RedisConfig
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.*
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException


@Service
class ThirdPartyConsumer constructor(@Qualifier("app") private val log: Log,
                                     private val meterRegistry: MeterRegistry,
                                     val appConfig: AppConfig,
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
        val results = mutableListOf<Deferred<Any>>()

        lock.acquire()

        CoroutineScope(context).launch {
            try {

                val segments = oracleMembership.currentSegments.map { it.toString() }.toTypedArray()

                results += async {
                    writeMemberships(oracleMembership.ip.orEmpty(), segments, "ip", Audiencetype.oracle.name)
                }

                val oldSegments = oracleMembership.oldSegments.map { it.toString() }.toTypedArray()

                results += async {
                    deleteMemberships(oracleMembership.ip.orEmpty(), oldSegments, "ip", Audiencetype.oracle.name)
                }

                results.forEach{ it.await() }
            } finally {
                lock.release()
            }
        }

    }



}
