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
    val segmentVersions: List<SegmentVersion>?
)

data class MembershipUpdateLogMessage(
    var guid: String,
    val advertiserId: Int,
    val epoch: Long,
    val activityEpoch: Long,
    val ip: String,
    val geoVersion: String?,
    val segmentVersions: List<SegmentVersion>?
)
data class SegmentVersion(
    val segmentId: Int,
    val version: Long
)

data class ImpressionMessage(
    @SerializedName("DW_AgentParams") val agentParams: String?,
    @SerializedName("DW_ImpressionTime") val impressionTime: Long?,
    @SerializedName("DW_ImpressionAuctionId") var impressionId: String?,
    @SerializedName("DW_BidRequestDeviceIp") var deviceIp: String?,
)

data class AgentParams(
    @SerializedName("campaign_id") val campaignId: Long?,
    @SerializedName("campaign_group_id") val campaignGroupId: Long?,
)

data class RecencyMessage(
    val ip: String,
    val advertiserID: Int?,
    val epoch: Long?,
)
