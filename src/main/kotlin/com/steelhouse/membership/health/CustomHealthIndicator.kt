package com.steelhouse.membership.health

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import org.apache.commons.logging.Log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.stereotype.Component

@Component
open class CustomHealthIndicator constructor(
    @Qualifier("app") private val log: Log,
    @Qualifier("redisConnectionRecency") private val redisConnectionRecency: StatefulRedisClusterConnection<String, String>,
    @Qualifier("redisConnectionFrequencyCap") private val redisConnectionFrequencyCap: StatefulRedisClusterConnection<String, String>,
) : AbstractHealthIndicator() {

    @Throws(Exception::class)
    override fun doHealthCheck(builder: Health.Builder) {
        verifyConnections(builder, redisConnectionRecency)
        verifyConnections(builder, redisConnectionFrequencyCap)
    }

    fun verifyConnections(
        builder: Health.Builder,
        redisConnection: StatefulRedisClusterConnection<String, String>,
    ): Boolean {
        var connected = true

        var executions = redisConnection.sync().masters().commands().ping()
        for (execution in executions) {
            connected = connected && execution == ("PONG")
        }

        if (connected) {
            builder.up()
                .withDetail("lettuce", "Alive and Kicking")
        } else {
            builder.down()
                .withDetail("lettuce", "Connection failure")
            log.error("Lettuce connection is unavailable")
        }

        return connected
    }
}
