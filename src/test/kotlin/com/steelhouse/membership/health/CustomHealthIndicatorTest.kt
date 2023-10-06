package com.steelhouse.augmentor.health

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.steelhouse.membership.health.CustomHealthIndicator
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

    var log: Log = mock()

    var redisClientMembership: StatefulRedisClusterConnection<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterCommands<String, String> = mock()

    val executions: Executions<String> = mock()

    val executions2: Executions<String> = mock()

    val partnerNodeSelectionCommands: NodeSelectionCommands<String, String> = mock()

    val membershipNodeSelectionCommands: NodeSelectionCommands<String, String> = mock()

    @BeforeEach
    fun init() {
        whenever(redisClientMembership.sync()).thenReturn(membershipCommands)

        val membershipMasters: NodeSelection<String, String> = mock()
        whenever(membershipCommands.masters()).thenReturn(membershipMasters)

        whenever(membershipMasters.commands()).thenReturn(membershipNodeSelectionCommands)
    }

    @Test
    fun healthyRedisConnections() {
        val indicator = CustomHealthIndicator(log, redisClientMembership)

        whenever(executions.iterator()).thenReturn(mutableListOf("PONG", "PONG", "PONG").iterator())
        whenever(executions2.iterator()).thenReturn(mutableListOf("PONG", "PONG", "PONG").iterator())

        whenever(partnerNodeSelectionCommands.ping()).thenReturn(executions)
        whenever(membershipNodeSelectionCommands.ping()).thenReturn(executions2)

        val builder = Health.Builder()
        assertTrue(indicator.verifyConnections(builder, redisClientMembership))
        assertTrue(builder.build().status == Status.UP)
    }

    @Test
    fun unHealthyRedisConnections() {
        val indicator = CustomHealthIndicator(log, redisClientMembership)

        whenever(executions.iterator()).thenReturn(mutableListOf("PONG", "PONG", "PONG").iterator())
        whenever(executions2.iterator()).thenReturn(mutableListOf("PONG", "BOOM", "PONG").iterator())

        whenever(partnerNodeSelectionCommands.ping()).thenReturn(executions)
        whenever(membershipNodeSelectionCommands.ping()).thenReturn(executions2)

        val builder = Health.Builder()
        assertFalse(indicator.verifyConnections(builder, redisClientMembership))
        assertTrue(builder.build().status == Status.DOWN)
    }
}
