package com.steelhouse.membership.model

import com.google.gson.annotations.SerializedName

data class MembershipUpdateMessage(
    var guid: String,
    val advertiserId: Int,
    val currentSegments: List<Int> = emptyList(),
    val oldSegments: List<Int> = emptyList(),
    val epoch: Long,
    val activityEpoch: Long,
    val ip: String,
    val dataSource: Int?,
    val householdScore: Int?,
    val geoVersion: String?,
    val isDelta: Boolean?,
    val metadataInfo: Map<String, String>? = emptyMap(),
    val cData: Map<String, Map<String, Int>>? = emptyMap(),
)

data class ImpressionMessage(
    @SerializedName("DW_AgentParams") val agentParams: AgentParams,
    @SerializedName("DW_ImpressionTime") val impressionTime: Long?,
    @SerializedName("DW_ImpressionAuctionId") var impressionId: String?,
    @SerializedName("DW_BidRequestDeviceIp") var deviceIp: String?,
)

data class AgentParams(
    @SerializedName("campaign_id") val campaignId: Long?,
    @SerializedName("campaign_group_id") val campaignGroupId: Long?,
)

data class RecencyMessage(val ip: String, val advertiserID: Int?, val epoch: Long?)
