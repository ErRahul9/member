package com.steelhouse.membership.controller


import com.steelhouse.core.model.gsonmessages.GsonMessageUtil
import com.steelhouse.core.model.segmentation.gson.MembershipUpdateMessage
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.micrometer.core.annotation.Timed
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
import java.util.concurrent.Semaphore
import java.util.stream.Collectors


@Service
class KafkaConsumer constructor(@Qualifier("app") private val log: Log,
                                @Qualifier("redisConnectionPartner") private val redisConnectionPartner: StatefulRedisClusterConnection<String, String>,
                                @Qualifier("redisConnectionMembership") private val redisConnectionMembership: StatefulRedisClusterConnection<String, String>) {

    val context = newFixedThreadPoolContext(1, "write-membership-thread-pool")
    val lock = Semaphore(10000)



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

    @Timed(value = "writeMemberships", histogram = true, longTask = true)
    fun writeMemberships(guid: String, currentSegments: String, aid: String ) {
        val results= redisConnectionMembership.async().hset(guid, aid, currentSegments)
        results.get()
    }

    @Timed(value = "retrievePartnerId", histogram = true, longTask = true)
    fun retrievePartnerId(guid: String): MutableMap<String, String>? {
        val asyncResults  = redisConnectionPartner.async().hgetall(guid)
        return asyncResults.get()
    }

}
