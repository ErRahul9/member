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
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.Status

class CustomHealthIndicatorTest {

    var log: Log = mock()

    var redisClientPartner: StatefulRedisClusterConnection<String, String> = mock()

    var redisClientMembership: StatefulRedisClusterConnection<String, String> = mock()

    var partnerCommands: RedisAdvancedClusterCommands<String, String> = mock()

    var membershipCommands: RedisAdvancedClusterCommands<String, String> = mock()

    val executions: Executions<String> = mock()

    val executions2: Executions<String> = mock()

    val partnerNodeSelectionCommands: NodeSelectionCommands<String,String> = mock()

    val membershipNodeSelectionCommands: NodeSelectionCommands<String,String> = mock()

    @Before
    fun init() {

        whenever(redisClientPartner.sync()).thenReturn(partnerCommands)
        whenever(redisClientMembership.sync()).thenReturn(membershipCommands)


        val membershipMasters: NodeSelection<String,String> = mock()
        whenever(membershipCommands.masters()).thenReturn(membershipMasters)

        val partnerMasters: NodeSelection<String, String> = mock()
        whenever(partnerCommands.masters()).thenReturn(partnerMasters)


        whenever(membershipMasters.commands()).thenReturn(membershipNodeSelectionCommands)
        whenever(partnerMasters.commands()).thenReturn(partnerNodeSelectionCommands)

    }

    @Test
    fun healthyRedisConnections() {

        val indicator = CustomHealthIndicator(log, redisClientPartner, redisClientMembership)

        whenever(executions.iterator()).thenReturn(mutableListOf("PONG","PONG","PONG").iterator())
        whenever(executions2.iterator()).thenReturn(mutableListOf("PONG","PONG","PONG").iterator())

        whenever(partnerNodeSelectionCommands.ping()).thenReturn(executions)
        whenever(membershipNodeSelectionCommands.ping()).thenReturn(executions2)


        val builder = Health.Builder()
        Assert.assertTrue(indicator.verifyConnections(builder))
        Assert.assertTrue(builder.build().status == Status.UP)
    }

    @Test
    fun unHealthyRedisConnections() {

        val indicator = CustomHealthIndicator(log, redisClientPartner, redisClientMembership)

        whenever(executions.iterator()).thenReturn(mutableListOf("PONG","PONG","PONG").iterator())
        whenever(executions2.iterator()).thenReturn(mutableListOf("PONG","BOOM","PONG").iterator())

        whenever(partnerNodeSelectionCommands.ping()).thenReturn(executions)
        whenever(membershipNodeSelectionCommands.ping()).thenReturn(executions2)

        val builder = Health.Builder()
        Assert.assertFalse(indicator.verifyConnections(builder))
        Assert.assertTrue(builder.build().status == Status.DOWN)
    }

}