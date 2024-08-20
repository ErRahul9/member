package com.steelhouse.membership.health

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.sync.Executions
import io.lettuce.core.cluster.api.sync.NodeSelection
import io.lettuce.core.cluster.api.sync.NodeSelectionCommands
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
import org.apache.commons.logging.Log
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.Status

class CustomHealthIndicatorTest {

    val log: Log = mock()

    val redisClientRecency: StatefulRedisClusterConnection<String, String> = mock()

    val redisClientFrequencyCap: StatefulRedisClusterConnection<String, String> = mock()

    val recencyCommands: RedisAdvancedClusterCommands<String, String> = mock()

    val frequencyCapCommands: RedisAdvancedClusterCommands<String, String> = mock()

    val executions: Executions<String> = mock()

    val executions2: Executions<String> = mock()

    val recencyNodeSelectionCommands: NodeSelectionCommands<String, String> = mock()

    val frequencyCapNodeSelectionCommands: NodeSelectionCommands<String, String> = mock()

    @BeforeEach
    fun init() {
        whenever(redisClientRecency.sync()).thenReturn(recencyCommands)
        whenever(redisClientFrequencyCap.sync()).thenReturn(frequencyCapCommands)

        val recencyMasters: NodeSelection<String, String> = mock()
        whenever(recencyCommands.masters()).thenReturn(recencyMasters)
        whenever(recencyMasters.commands()).thenReturn(recencyNodeSelectionCommands)

        val frequencyCapMasters: NodeSelection<String, String> = mock()
        whenever(frequencyCapCommands.masters()).thenReturn(frequencyCapMasters)
        whenever(frequencyCapMasters.commands()).thenReturn(frequencyCapNodeSelectionCommands)
    }

    @Test
    fun healthyRedisConnections() {
        val indicator = CustomHealthIndicator(log, redisClientRecency, redisClientFrequencyCap)

        whenever(executions.iterator()).thenReturn(mutableListOf("PONG", "PONG", "PONG").iterator())
        whenever(executions2.iterator()).thenReturn(mutableListOf("PONG", "PONG", "PONG").iterator())

        whenever(recencyNodeSelectionCommands.ping()).thenReturn(executions)
        whenever(frequencyCapNodeSelectionCommands.ping()).thenReturn(executions2)

        val builder = Health.Builder()
        assertTrue(indicator.verifyConnections(builder, redisClientRecency))
        assertTrue(indicator.verifyConnections(builder, redisClientFrequencyCap))
        assertTrue(builder.build().status == Status.UP)
    }

    @Test
    fun unHealthyRedisConnections() {
        val indicator = CustomHealthIndicator(log, redisClientRecency, redisClientFrequencyCap)

        whenever(executions.iterator()).thenReturn(mutableListOf("PONG", "PONG", "PONG").iterator())
        whenever(executions2.iterator()).thenReturn(mutableListOf("PONG", "BOOM", "PONG").iterator())

        whenever(recencyNodeSelectionCommands.ping()).thenReturn(executions)
        whenever(frequencyCapNodeSelectionCommands.ping()).thenReturn(executions2)

        val builder = Health.Builder()
        assertTrue(indicator.verifyConnections(builder, redisClientRecency))
        assertFalse(indicator.verifyConnections(builder, redisClientFrequencyCap))
        assertTrue(builder.build().status == Status.DOWN)
    }
}
