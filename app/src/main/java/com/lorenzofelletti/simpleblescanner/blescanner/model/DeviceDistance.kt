package com.lorenzofelletti.simpleblescanner.blescanner.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

class DeviceDistance {
    val n = 2.0
    fun calculateDistance(txPower: Int, rssi: Int): String{
        val distance = BigDecimal(10.0.pow((txPower - rssi) / (10 * n))).setScale(1, RoundingMode.HALF_EVEN)
        return "Estimated distance: $distance meter"
    }
}