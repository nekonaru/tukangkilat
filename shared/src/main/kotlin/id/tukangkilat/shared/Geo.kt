package id.tukangkilat.shared

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0
    const val AVERAGE_SPEED_KMH = 20.0

    /** Great-circle distance in kilometres. */
    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /** Straight-line travel estimate at [AVERAGE_SPEED_KMH] km/h. */
    fun estimateMinutes(distanceKm: Double): Int =
        Math.ceil(distanceKm / AVERAGE_SPEED_KMH * 60.0).toInt().coerceAtLeast(1)
}

object Pricing {
    /** Platform commission taken from the service fee (15%). */
    const val COMMISSION_RATE = 0.15

    /** Technician payout: service fee minus commission, plus full visit fee. */
    fun technicianPayout(serviceFee: Long, visitFee: Long): Long {
        val net = serviceFee - Math.round(serviceFee * COMMISSION_RATE)
        return net + visitFee
    }

    /** Platform commission in absolute amount. */
    fun commission(serviceFee: Long): Long = Math.round(serviceFee * COMMISSION_RATE)
}

object Format {
    /** Format like 150000 -> "Rp150.000". */
    fun rupiah(amount: Long): String {
        val s = amount.toString()
        val grouped = s.reversed().chunked(3).joinToString(".").reversed()
        return "Rp$grouped"
    }
}
