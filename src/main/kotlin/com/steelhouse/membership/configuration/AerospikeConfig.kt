package com.steelhouse.membership.configuration

import com.aerospike.client.AerospikeClient
import com.aerospike.client.Host
import com.aerospike.client.policy.ClientPolicy
import com.aerospike.client.policy.WritePolicy
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.validation.constraints.NotNull

@Configuration
@ConfigurationProperties(prefix = "aerospike")
class AerospikeConfig(@Qualifier("app") private val log: Log) {

    @NotNull
    var hostnames: List<String> = emptyList()

    @NotNull
    var port: Int? = null

    @NotNull
    var namespace: String? = null

    @NotNull
    var setName: String? = null

    @NotNull
    var ttlSeconds: Int? = null

    @Bean
    fun clientPolicy(): ClientPolicy {
        // if necessary, we can customize authentication, timeouts, connections, etc.
        return ClientPolicy()
    }

    @Bean
    fun aerospikeClient(clientPolicy: ClientPolicy): AerospikeClient {
        val hosts: List<Host> = hostnames.map { Host(it, port!!) }
        try {
            return AerospikeClient(clientPolicy, *hosts.toTypedArray())
        } catch (e: Exception) {
            log.error("Failed to create Aerospike client (hosts: $hosts)", e)
            throw e
        }
    }

    @Bean
    fun writePolicy(): WritePolicy {
        val policy = WritePolicy()
        policy.expiration = ttlSeconds!!
        return policy
    }
}
