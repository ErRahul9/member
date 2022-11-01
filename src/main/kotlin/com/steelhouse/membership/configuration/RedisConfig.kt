package com.steelhouse.membership.configuration

import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.resource.DirContextDnsResolver
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.validation.constraints.NotNull

@Configuration
@ConfigurationProperties(prefix = "redis")
open class RedisConfig  constructor(@Qualifier("app") private val log: Log){

    @NotNull
    var membershipConnectionTpa: String? = null

    @NotNull
    var householdConnection: String? = null

    @NotNull
    open var membershipTTL: Long? = null

    @NotNull
    var requestTimeoutSeconds: Long? = null

    @NotNull
    var topologyRefreshHours: Long? = null

    @NotNull
    var clientShutdownSeconds: Long? = null

    @NotNull
    var recencyConnection: String? = null

    @NotNull
    var frequencyCapConnection: String? = null

    @Bean
    open fun redisClientRecency(clusterClientOptions: ClusterClientOptions) : RedisClusterClient? {

        var clientResources = DefaultClientResources.builder() //
                .dnsResolver(DirContextDnsResolver()) // Does not cache DNS lookups
                .build()

        var redisClient = RedisClusterClient.create(clientResources, recencyConnection)

        redisClient.setOptions(clusterClientOptions)
        redisClient.setDefaultTimeout(Duration.ofSeconds(requestTimeoutSeconds!!))

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                redisClient.shutdown(clientShutdownSeconds!!, clientShutdownSeconds!!, TimeUnit.SECONDS)
            }
        })

        return redisClient
    }

    @Bean
    open fun redisConnectionRecency(redisClientRecency: RedisClusterClient) : StatefulRedisClusterConnection<String, String>? {

        return redisClientRecency.connect()

    }

    @Bean
    open fun clusterClientOptions(): ClusterClientOptions {
        val duration = Duration.ofHours(topologyRefreshHours!!)

        val clusterTopologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(duration)
                .enableAdaptiveRefreshTrigger()
                .build()

        return ClusterClientOptions.builder()
                .topologyRefreshOptions(clusterTopologyRefreshOptions)
                .build()
    }

    @Bean
    open fun redisClientMembershipTpa(clusterClientOptions: ClusterClientOptions) : RedisClusterClient? {

        val clientResources = DefaultClientResources.builder() //
                .dnsResolver(DirContextDnsResolver()) // Does not cache DNS lookups
                .build()

        val redisClient = RedisClusterClient.create(clientResources, membershipConnectionTpa)

        redisClient.setOptions(clusterClientOptions)
        redisClient.setDefaultTimeout(Duration.ofSeconds(requestTimeoutSeconds!!))

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                redisClient.shutdown(clientShutdownSeconds!!, clientShutdownSeconds!!, TimeUnit.SECONDS)
            }
        })

        return redisClient
    }


    @Bean
    open fun redisClientHousehold(clusterClientOptions: ClusterClientOptions) : RedisClusterClient? {

        val clientResources = DefaultClientResources.builder() //
                .dnsResolver(DirContextDnsResolver()) // Does not cache DNS lookups
                .build()

        val redisClient = RedisClusterClient.create(clientResources, householdConnection)

        redisClient.setOptions(clusterClientOptions)
        redisClient.setDefaultTimeout(Duration.ofSeconds(requestTimeoutSeconds!!))

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                redisClient.shutdown(clientShutdownSeconds!!, clientShutdownSeconds!!, TimeUnit.SECONDS)
            }
        })

        return redisClient
    }

    @Bean
    open fun redisClientFrequencyCap(clusterClientOptions: ClusterClientOptions) : RedisClusterClient? {

        var clientResources = DefaultClientResources.builder() //
            .dnsResolver(DirContextDnsResolver()) // Does not cache DNS lookups
            .build()

        var redisClient = RedisClusterClient.create(clientResources, frequencyCapConnection)

        redisClient.setOptions(clusterClientOptions)
        redisClient.setDefaultTimeout(Duration.ofSeconds(requestTimeoutSeconds!!))

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                redisClient.shutdown(clientShutdownSeconds!!, clientShutdownSeconds!!, TimeUnit.SECONDS)
            }
        })

        return redisClient
    }

    @Bean
    open fun redisConnectionMembershipTpa(redisClientMembershipTpa: RedisClusterClient) : StatefulRedisClusterConnection<String, String>? {

        return redisClientMembershipTpa.connect()

    }

    @Bean
    open fun redisConnectionUserScore(redisClientHousehold: RedisClusterClient) : StatefulRedisClusterConnection<String, String>? {

        return redisClientHousehold.connect()

    }

    @Bean
    open fun redisConnectionFrequencyCap(redisClientFrequencyCap: RedisClusterClient) : StatefulRedisClusterConnection<String, String>? {

        return redisClientFrequencyCap.connect()

    }

}
