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
    var membershipConnection: String? = null

    @NotNull
    var partnerConnection: String? = null

    @NotNull
    open var membershipTTL: Long? = null

    @NotNull
    open var partnerTTL: Long? = null

    @NotNull
    var requestTimeoutSeconds: Long? = null

    @NotNull
    var topologyRefreshHours: Long? = null

    @NotNull
    var clientShutdownSeconds: Long? = null

    @Bean
    open fun redisClientPartner(clusterClientOptions: ClusterClientOptions) : RedisClusterClient? {

        var clientResources = DefaultClientResources.builder() //
                .dnsResolver(DirContextDnsResolver()) // Does not cache DNS lookups
                .build()

        var redisClient = RedisClusterClient.create(clientResources, partnerConnection)

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
    open fun redisConnectionPartner(redisClientPartner: RedisClusterClient) : StatefulRedisClusterConnection<String, String>? {

        return redisClientPartner.connect()

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
    open fun redisClientMembership(clusterClientOptions: ClusterClientOptions) : RedisClusterClient? {

        val clientResources = DefaultClientResources.builder() //
                .dnsResolver(DirContextDnsResolver()) // Does not cache DNS lookups
                .build()

        val redisClient = RedisClusterClient.create(clientResources, membershipConnection)

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
    open fun redisConnectionMembership(redisClientMembership: RedisClusterClient) : StatefulRedisClusterConnection<String, String>? {

        return redisClientMembership.connect()

    }

}
