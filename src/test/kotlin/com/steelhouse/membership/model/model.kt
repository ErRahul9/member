package com.steelhouse.membership.model

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelTest {

    @Test
    fun convertJsonToMessage() {
        val message = "{\n" +
            "  \"guid\": \"7501cd62-7e55-3d27-9f08-135aa4370fa0\",\n" +
            "  \"advertiser_id\": 21951,\n" +
            "  \"current_segments\": [\n" +
            "    44577,\n" +
            "    44971,\n" +
            "    55291,\n" +
            "    55293\n" +
            "  ],\n" +
            "  \"old_segments\": [\n" +
            "    42511,\n" +
            "    42622,\n" +
            "    43053\n" +
            "  ],\n" +
            "  \"activity_epoch\": 1626992631738255,\n" +
            "  \"epoch\": 1626992698000397,\n" +
            "  \"ip\": \"68.74.197.31\",\n" +
            "  \"household_score\": \"80\"\n" +
            "}"
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val membershipUpdateMessage = gson.fromJson(message, MembershipUpdateMessage::class.java)

        assertEquals(membershipUpdateMessage.guid, "7501cd62-7e55-3d27-9f08-135aa4370fa0")
        assertEquals(membershipUpdateMessage.advertiserId, 21951)
        assertEquals(membershipUpdateMessage.currentSegments, listOf(44577, 44971, 55291, 55293))
        assertEquals(membershipUpdateMessage.oldSegments, listOf(42511, 42622, 43053))
        assertEquals(membershipUpdateMessage.activityEpoch, 1626992631738255)
        assertEquals(membershipUpdateMessage.epoch, 1626992698000397)
        assertEquals(membershipUpdateMessage.ip, "68.74.197.31")
        assertEquals(membershipUpdateMessage.householdScore, 80)
    }

    @Test
    fun convertJsonToMessageWithoutHouseholdScore() {
        val message = "{\n" +
            "  \"guid\": \"7501cd62-7e55-3d27-9f08-135aa4370fa0\",\n" +
            "  \"advertiser_id\": 21951,\n" +
            "  \"current_segments\": [\n" +
            "    44577,\n" +
            "    44971,\n" +
            "    55291,\n" +
            "    55293\n" +
            "  ],\n" +
            "  \"old_segments\": [\n" +
            "    42511,\n" +
            "    42622,\n" +
            "    43053\n" +
            "  ],\n" +
            "  \"activity_epoch\": 1626992631738255,\n" +
            "  \"epoch\": 1626992698000397,\n" +
            "  \"ip\": \"68.74.197.31\"\n" +
            "}"
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val membershipUpdateMessage = gson.fromJson(message, MembershipUpdateMessage::class.java)

        assertEquals(membershipUpdateMessage.guid, "7501cd62-7e55-3d27-9f08-135aa4370fa0")
        assertEquals(membershipUpdateMessage.advertiserId, 21951)
        assertEquals(membershipUpdateMessage.currentSegments, listOf(44577, 44971, 55291, 55293))
        assertEquals(membershipUpdateMessage.oldSegments, listOf(42511, 42622, 43053))
        assertEquals(membershipUpdateMessage.activityEpoch, 1626992631738255)
        assertEquals(membershipUpdateMessage.epoch, 1626992698000397)
        assertEquals(membershipUpdateMessage.ip, "68.74.197.31")
        assertEquals(membershipUpdateMessage.householdScore, null)
    }
}
