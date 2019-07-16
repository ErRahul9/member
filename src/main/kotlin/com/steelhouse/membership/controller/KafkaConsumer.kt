package com.steelhouse.membership.controller


import com.steelhouse.core.model.gsonmessages.GsonMessageUtil
import com.steelhouse.core.model.segmentation.gson.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors


@Service
class KafkaConsumer constructor(@Qualifier("app") private val log: Log,
                                private val meterRegistry: MeterRegistry,
                                @Qualifier("redisConnectionPartner") private val redisConnectionPartner: StatefulRedisClusterConnection<String, String>,
                                @Qualifier("redisConnectionMembership") private val redisConnectionMembership: StatefulRedisClusterConnection<String, String>) {

    val context = newFixedThreadPoolContext(1, "write-membership-thread-pool")
    val lock = Semaphore(7000)

    lateinit var partnerTimer: Timer
    lateinit var membershipTimer: Timer
    lateinit var partnerCounter: Counter
    lateinit var membershipCounter: Counter

    @Autowired
    fun metrics() {
        partnerTimer =  meterRegistry.timer("write.partner.match.latency")
        membershipTimer =  meterRegistry.timer("write.membership.match.latency")
        partnerCounter =  meterRegistry.counter("write.partner.match.counter")
        membershipCounter =  meterRegistry.counter("write.membership.match.counter")
    }

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
                    writeMemberships(membership.guid, segments, membership.aid.toString())
                }

                val partnerResults = mutableListOf<Deferred<Any>>()

                val partners = partnerResult.await()
                for(guid in partners.orEmpty().values) {

                    partnerResults += async {
                        writeMemberships(guid, segments, membership.aid.toString())
                    }
                }

                membershipResult.await()
                partnerResults.forEach{it.await()}
            } finally {
                lock.release()
            }
        }

    }

    fun writeMemberships(guid: String, currentSegments: String, aid: String ) {
        val startTime = System.currentTimeMillis()
        val results= redisConnectionMembership.async().hset(guid, aid, currentSegments)
        results.get()
        membershipTimer.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
        membershipCounter.increment()
    }

    fun retrievePartnerId(guid: String): MutableMap<String, String>? {
        val startTime = System.currentTimeMillis()
        val asyncResults  = redisConnectionPartner.async().hgetall(guid)
        val results = asyncResults.get()
        partnerTimer.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
        partnerCounter.increment()
        return results
    }

}
