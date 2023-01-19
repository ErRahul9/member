package com.steelhouse.membership.util

import org.springframework.stereotype.Component

@Component
class Util {

    fun getEpochInMillis(): Long {
        return System.currentTimeMillis()
    }
 }