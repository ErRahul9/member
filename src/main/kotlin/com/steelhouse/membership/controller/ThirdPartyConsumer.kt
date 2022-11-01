package com.steelhouse.membership.controller


import com.google.common.base.Stopwatch
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.steelhouse.membership.configuration.AppConfig
import com.steelhouse.membership.configuration.RedisConfig
import com.steelhouse.membership.model.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.*
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit


@Service
class ThirdPartyConsumer constructor(@Qualifier("app") private val log: Log,
                                     private val meterRegistry: MeterRegistry,
                                     val appConfig: AppConfig,
                                     @Qualifier("redisConnectionMembershipTpa") private val redisConnectionMembershipTpa: StatefulRedisClusterConnection<String, String>,
                                     @Qualifier("redisConnectionUserScore") private val redisConnectionUserScore: StatefulRedisClusterConnection<String, String>,
                                     @Qualifier("redisConnectionRecency") private val redisConnectionRecency: StatefulRedisClusterConnection<String, String>,
                                     private val redisConfig: RedisConfig): BaseConsumer(log = log,
        meterRegistry = meterRegistry,
        redisConnectionMembershipTpa = redisConnectionMembershipTpa, redisConfig = redisConfig,
        redisConnectionRecency = redisConnectionRecency, appConfig = appConfig) {

    val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

    @KafkaListener(topics = ["sh-dw-generated-audiences"], autoStartup = "\${membership.oracleConsumer:false}")
    @Throws(IOException::class)
    override fun consume(message: String) {

        val oracleMembership = gson.fromJson(message, MembershipUpdateMessage::class.java)
        val results = mutableListOf<Deferred<Any>>()

        lock.acquire()

        CoroutineScope(context).launch {
            try {

                if(oracleMembership.currentSegments != null) {
                    val segments = oracleMembership.currentSegments.map { it.toString() }.toTypedArray()

                    results += async {
                        writeMemberships(oracleMembership.ip.orEmpty(), segments, "ip",
                            Audiencetype.oracle.name)
                    }
                }

                if(oracleMembership.oldSegments != null) {
                    val oldSegments = oracleMembership.oldSegments.map { it.toString() }.toTypedArray()

                    results += async {
                        deleteMemberships(oracleMembership.ip.orEmpty(), oldSegments, "ip",
                            Audiencetype.oracle.name)
                    }
                }

                results += async{
                    writeHouseHoldScore(oracleMembership.ip.orEmpty(), oracleMembership.householdScore)
                }

                results.forEach{ it.await() }
            } finally {
                lock.release()
            }
        }

    }

    fun writeHouseHoldScore(id: String, houseHoldScore: Int?) {

        val stopwatch = Stopwatch.createStarted()

        if(houseHoldScore != null) {
            redisConnectionUserScore.sync().hset(id, "household_score", houseHoldScore.toString())

            val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
            meterRegistry.timer("write.user.score.latency", "score", "houeshold")
                    .record(Duration.ofMillis(responseTime))
        }
    }


}
