package com.ridesniper.app.model

import com.ridesniper.app.util.ZoneTimingFinder
import java.time.LocalDateTime

/**
 * Tracks statistics and performance metrics for each KC zone over time.
 */
data class ZoneStatistics(
    val zone: ZoneTimingFinder.DemandZone,
    val totalRidesPickedUp: Int = 0,
    val totalEarningsGross: Double = 0.0,
    val totalEarningsNet: Double = 0.0,
    val averagePerMile: Double = 0.0,
    val averageNetPerMile: Double = 0.0,
    val declinedRidesCount: Int = 0,
    val lastActive: LocalDateTime? = null,
    val peakHours: List<Int> = emptyList()
) {
    fun withNewRide(grossEarnings: Double, netEarnings: Double, perMile: Double, netPerMile: Double): ZoneStatistics {
        return this.copy(
            totalRidesPickedUp = totalRidesPickedUp + 1,
            totalEarningsGross = totalEarningsGross + grossEarnings,
            totalEarningsNet = totalEarningsNet + netEarnings,
            averagePerMile = (totalEarningsGross + grossEarnings) / (totalRidesPickedUp + 1),
            averageNetPerMile = (totalEarningsNet + netEarnings) / (totalRidesPickedUp + 1),
            lastActive = LocalDateTime.now()
        )
    }

    fun withDecline(): ZoneStatistics {
        return this.copy(declinedRidesCount = declinedRidesCount + 1)
    }
}

/**
 * Post-dropoff event: captures context and suggestion for next ride.
 */
data class PostDropoffEvent(
    val timestamp: LocalDateTime,
    val dropoffZone: ZoneTimingFinder.DemandZone,
    val rideGrossAmount: Double,
    val rideNetAmount: Double,
    val ridePerMile: Double,
    val suggestedNextZone: ZoneTimingFinder.DemandZone,
    val estimatedDeadheadMiles: Double,
    val driverAction: String = ""  // "MOVED_TO_ZONE", "WAITED_FOR_LOCAL", "IGNORED"
)

/**
 * Zone preference: how well a driver performs in each zone.
 */
data class ZonePreference(
    val zone: ZoneTimingFinder.DemandZone,
    val performanceScore: Double = 0.0, // Higher = better earnings/acceptance
    val comfortLevel: Int = 5           // 1-10 scale: how much driver likes zone
)
