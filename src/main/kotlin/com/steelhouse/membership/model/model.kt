package com.steelhouse.membership.model



data class MembershipUpdateMessage(var guid: String, val advertiserId: Int, val currentSegments: List<Int> = emptyList(),
                              val oldSegments: List<Int> = emptyList(), val epoch: Long, val activityEpoch: Long,
                              val ip: String, val source: String, val householdScore: Int?)


