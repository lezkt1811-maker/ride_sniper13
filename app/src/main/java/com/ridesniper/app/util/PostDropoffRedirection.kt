package com.ridesniper.app.util

import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Post-dropoff redirection advice: after completing a ride, suggests the best
 * next zone to head to, avoiding dead return miles and catching demand waves.
 */
object PostDropoffRedirection {

    data class DropoffAnalysis(
        val dropoffZone: ZoneTimingFinder.DemandZone,
        val dropoffDensity: DensityLevel,
        val isDeadheadRisk: Boolean,
        val nextRecommendedZone: ZoneTimingFinder.DemandZone,
        val reason: String,
        val estimatedDeadheadMiles: Double,
        val expectedNextEarnings: Double,
        val urgency: String // "URGENT", "HIGH", "NORMAL"
    )

    enum class DensityLevel {
        HIGH_DENSITY,     // Power & Light, Westport, Plaza, Midtown - reliable pickups
        MEDIUM_DENSITY,   // Established commercial areas
        LOW_DENSITY,      // Residential, suburbs - few pickups nearby
        VERY_LOW_DENSITY  // Far suburbs, rural - very few pickups
    }

    /**
     * Analyze post-dropoff situation and recommend routing.
     */
    fun analyzePostDropoff(
        dropoffLocation: String,
        dropoffZone: ZoneTimingFinder.DemandZone,
        currentTime: LocalDateTime,
        lastPickupZone: ZoneTimingFinder.DemandZone?
    ): DropoffAnalysis {
        val density = classifyZoneDensity(dropoffZone)
        val isDeadhead = density == DensityLevel.LOW_DENSITY || density == DensityLevel.VERY_LOW_DENSITY

        // Get best next zone based on current demand and time
        val suggestions = ZoneTimingFinder.getPostDropoffSuggestions(currentTime, dropoffZone, lastPickupZone)
        val (nextZone, reason) = if (suggestions.isNotEmpty()) {
            suggestions[0]
        } else {
            ZoneTimingFinder.DemandZone.POWER_AND_LIGHT to "Default safe zone"
        }

        val estimatedDeadhead = estimateDeadheadMiles(dropoffZone, nextZone)
        val expectedEarnings = ZoneTimingFinder.getExpectedEarningsPerMile(nextZone, currentTime.hour)
        val urgency = when {
            isDeadhead && currentTime.hour in 7..9 -> "URGENT"
            isDeadhead && currentTime.hour in 16..19 -> "URGENT"
            density == DensityLevel.VERY_LOW_DENSITY -> "HIGH"
            else -> "NORMAL"
        }

        return DropoffAnalysis(
            dropoffZone = dropoffZone,
            dropoffDensity = density,
            isDeadheadRisk = isDeadhead,
            nextRecommendedZone = nextZone,
            reason = reason,
            estimatedDeadheadMiles = estimatedDeadhead,
            expectedNextEarnings = expectedEarnings,
            urgency = urgency
        )
    }

    /**
     * Classify a zone's ride density for pickup likelihood.
     */
    fun classifyZoneDensity(zone: ZoneTimingFinder.DemandZone): DensityLevel {
        return when (zone) {
            ZoneTimingFinder.DemandZone.POWER_AND_LIGHT -> DensityLevel.HIGH_DENSITY
            ZoneTimingFinder.DemandZone.WESTPORT -> DensityLevel.HIGH_DENSITY
            ZoneTimingFinder.DemandZone.PLAZA -> DensityLevel.HIGH_DENSITY
            ZoneTimingFinder.DemandZone.MIDTOWN -> DensityLevel.HIGH_DENSITY
            ZoneTimingFinder.DemandZone.AIRPORT -> DensityLevel.MEDIUM_DENSITY
            ZoneTimingFinder.DemandZone.MIDTOWN_COMMUTE -> DensityLevel.MEDIUM_DENSITY
            ZoneTimingFinder.DemandZone.OLATHE_OVERLAND -> DensityLevel.LOW_DENSITY
            ZoneTimingFinder.DemandZone.UNKNOWN -> DensityLevel.VERY_LOW_DENSITY
        }
    }

    /**
     * Estimate deadhead miles from current zone to recommended next zone.
     * Uses simple distances between KC zone centers.
     */
    fun estimateDeadheadMiles(fromZone: ZoneTimingFinder.DemandZone, toZone: ZoneTimingFinder.DemandZone): Double {
        // Approximate distances between zone centers in KC area
        val distances = mapOf(
            "POWER_AND_LIGHT_to_WESTPORT" to 2.5,
            "POWER_AND_LIGHT_to_PLAZA" to 3.0,
            "POWER_AND_LIGHT_to_MIDTOWN" to 1.5,
            "POWER_AND_LIGHT_to_AIRPORT" to 12.0,
            "WESTPORT_to_MIDTOWN" to 2.0,
            "WESTPORT_to_PLAZA" to 4.0,
            "PLAZA_to_MIDTOWN" to 2.5,
            "MIDTOWN_to_AIRPORT" to 10.0,
            "OLATHE_OVERLAND_to_POWER_AND_LIGHT" to 20.0,
            "OLATHE_OVERLAND_to_AIRPORT" to 15.0,
            "AIRPORT_to_POWER_AND_LIGHT" to 12.0
        )

        // Build key for lookup (check both directions)
        val key1 = "${fromZone.name}_to_${toZone.name}"
        val key2 = "${toZone.name}_to_${fromZone.name}"

        return when {
            distances.containsKey(key1) -> distances[key1]!!
            distances.containsKey(key2) -> distances[key2]!!
            fromZone == toZone -> 0.0
            else -> 5.0 // Default estimate
        }
    }

    /**
     * Get immediate action items after dropoff.
     */
    fun getPostDropoffActions(analysis: DropoffAnalysis): List<String> {
        val actions = mutableListOf<String>()

        if (analysis.isDeadheadRisk) {
            when (analysis.urgency) {
                "URGENT" -> {
                    actions.add("🚨 DEADHEAD ALERT: High-density demand nearby")
                    actions.add("Move NOW to ${analysis.nextRecommendedZone.label} (est. ${analysis.estimatedDeadheadMiles} mi)")
                }
                "HIGH" -> {
                    actions.add("⚠️ Low-density dropoff: Plan next move before sitting")
                    actions.add("Head to ${analysis.nextRecommendedZone.label} - ${analysis.reason}")
                }
                else -> {
                    actions.add("Consider moving to ${analysis.nextRecommendedZone.label} for next ride")
                }
            }
        } else {
            actions.add("✓ Good dropoff density: Wait for nearby pickup or move up")
            actions.add("Optional: Head to ${analysis.nextRecommendedZone.label} for even better demand")
        }

        actions.add("Expected per-mile rate at next zone: \$${String.format("%.2f", analysis.expectedNextEarnings)}")

        return actions
    }

    /**
     * Route optimization score: lower is better (fewer deadhead miles, higher demand).
     */
    fun getRouteQualityScore(
        fromZone: ZoneTimingFinder.DemandZone,
        toZone: ZoneTimingFinder.DemandZone,
        hour: Int
    ): Double {
        val deadheadMiles = estimateDeadheadMiles(fromZone, toZone)
        val demand = ZoneTimingFinder.getExpectedEarningsPerMile(toZone, hour)

        // Penalize deadhead miles, reward good demand
        // Lower score = better route
        return (deadheadMiles * 0.25) - (demand * 2.0)
    }
}
