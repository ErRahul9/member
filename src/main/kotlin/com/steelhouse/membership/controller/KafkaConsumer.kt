package com.steelhouse.membership.controller


import com.google.common.base.Stopwatch
import com.steelhouse.core.model.gsonmessages.GsonMessageUtil
import com.steelhouse.core.model.segmentation.gson.MembershipUpdateMessage
import com.steelhouse.membership.configuration.RedisConfig
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors


@Service
class KafkaConsumer constructor(@Qualifier("app") private val log: Log,
                                private val meterRegistry: MeterRegistry,
                                @Qualifier("redisConnectionPartner") private val redisConnectionPartner: StatefulRedisClusterConnection<String, String>,
                                @Qualifier("redisConnectionMembership") private val redisConnectionMembership: StatefulRedisClusterConnection<String, String>,
                                @Qualifier("redisConnectionSegmentMapping") private val redisConnectionSegmentMapping: StatefulRedisClusterConnection<String, String>,
                                private val redisConfig: RedisConfig) {

    val context = newFixedThreadPoolContext(1, "write-membership-thread-pool")
    val lock = Semaphore(7000)

    @KafkaListener(topics = ["membership-updates"])
    @Throws(IOException::class)
    open fun consume(message: String) {

        val membership = GsonMessageUtil.deserialize(message, MembershipUpdateMessage::class.java)

        lock.acquire()

        CoroutineScope(context).launch {
            try {

                val segments = membership.currentSegments.stream().map { it.toString() }.collect(Collectors.joining(","))

                val partnerResult = async {
                    retrievePartnerId(membership.guid)
                }

                val membershipResult = async {
                    writeMemberships(membership.guid.orEmpty(), segments, membership.aid.toString(), "steelhouse")
                    writeMemberships(membership.ip.orEmpty(), segments, membership.aid.toString(), "ip")
                }

                val partnerResults = mutableListOf<Deferred<Any>>()

                val partners = partnerResult.await()
                for(key in partners.orEmpty().keys) {

                    partnerResults += async {
                        val partnerGuid = partners.orEmpty()[key]
                        writeMemberships(partnerGuid.orEmpty(), segments, membership.aid.toString(), key)
                    }
                }

                membershipResult.await()
                partnerResults.forEach{it.await()}
            } finally {
                lock.release()
            }
        }

    }


    fun writeMemberships(guid: String, currentSegments: String, aid: String, cookieType: String ) {
        val stopwatch = Stopwatch.createStarted()
        val results= redisConnectionMembership.async().hset(guid, aid, currentSegments)
        redisConnectionMembership.async().expire(guid, redisConfig.membershipTTL!!)
        results.get()
        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.membership.match.latency", "cookieType", cookieType).record(Duration.ofMillis(responseTime))
    }

    fun retrievePartnerId(guid: String): MutableMap<String, String>? {
        val stopwatch = Stopwatch.createStarted()
        val asyncResults  = redisConnectionPartner.async().hgetall(guid)
        val results = asyncResults.get()
        val responseTime = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
        meterRegistry.timer("write.partner.match.latency").record(Duration.ofMillis(responseTime))
        return results
    }

}
