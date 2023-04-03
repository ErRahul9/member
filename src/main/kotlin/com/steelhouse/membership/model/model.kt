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
)

data class ImpressionMessage(
    @SerializedName("GUID") val guid: String,
    @SerializedName("EPOCH") val epoch: Long,
    @SerializedName("CID") val cid: Long,
    @SerializedName("AID") val aid: Long,
    @SerializedName("REMOTE_IP") val remoteIp: String,
    @SerializedName("TTD_IMPRESSION_ID") val tdImpressionId: String?,
)

data class RecencyMessage(val ip: String, val advertiserID: Int?, val epoch: Long?)
