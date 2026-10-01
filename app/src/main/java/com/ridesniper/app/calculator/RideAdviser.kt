package com.ridesniper.app.calculator

import com.ridesniper.app.model.*
import com.ridesniper.app.util.PostDropoffRedirection
import com.ridesniper.app.util.ZoneTimingFinder
import java.time.LocalDateTime

/**
 * Enhanced ride advisor that combines profitability analysis with zone timing
 * and post-dropoff routing suggestions. This is the unified interface for
 * helping a driver decide: (1) take this ride?, (2) where to go next after dropoff?
 */
object RideAdviser {

    data class RideAdvice(
        val calculation: RideCalculationResult,
        val timeContext: String,                          // "PEAK", "NORMAL", "SLOW"
        val zoneContext: ZoneTimingFinder.DemandZone,     // Where ride originated
        val demandLevel: ZoneTimingFinder.DemandLevel,
        val relatedWarnings: List<String> = emptyList(),
        val postDropoffAdvice: PostDropoffRedirection.DropoffAnalysis? = null,
        val reasoning: String                             // Human-readable advice
    )

    /**
     * Analyze a ride offer in full context: is it good RIGHT NOW in THIS ZONE?
     */
    fun adviseOnRide(
        input: RideOfferInput,
        settings: AppSettings,
        pickupZone: ZoneTimingFinder.DemandZone = ZoneTimingFinder.DemandZone.UNKNOWN,
        dropoffZone: ZoneTimingFinder.DemandZone = ZoneTimingFinder.DemandZone.UNKNOWN
    ): RideAdvice {
        val now = LocalDateTime.now()
        val demandLevel = ZoneTimingFinder.getDemandLevel(now, pickupZone)
        val timeContext = when (demandLevel) {
            ZoneTimingFinder.DemandLevel.PEAK -> "PEAK"
            ZoneTimingFinder.DemandLevel.HIGH -> "HIGH"
            ZoneTimingFinder.DemandLevel.NORMAL -> "NORMAL"
            ZoneTimingFinder.DemandLevel.LOW -> "SLOW"
            ZoneTimingFinder.DemandLevel.DEAD -> "DEAD"
        }

        // Classify zone for deadhead risk
        val zoneRating = if (dropoffZone == ZoneTimingFinder.DemandZone.OLATHE_OVERLAND ||
            dropoffZone == ZoneTimingFinder.DemandZone.UNKNOWN
        ) {
            ZoneRating.BAD_RETURN
        } else {
            ZoneRating.GOOD_RETURN
        }

        // Core profitability calculation
        val calculation = RideCalculator.calculate(input, settings, zoneRating)

        // Post-dropoff analysis
        val postDropoffAdvice = if (dropoffZone != ZoneTimingFinder.DemandZone.UNKNOWN) {
            PostDropoffRedirection.analyzePostDropoff(
                dropoffLocation = input.destinationText,
                dropoffZone = dropoffZone,
                currentTime = now,
                lastPickupZone = pickupZone
            )
        } else {
            null
        }

        // Build contextual warnings
        val warnings = buildContextualWarnings(
            calculation = calculation,
            demandLevel = demandLevel,
            pickupZone = pickupZone,
            postDropoffAdvice = postDropoffAdvice,
            now = now
        )

        // Final reasoning
        val reasoning = buildReasoning(
            calculation = calculation,
            demandLevel = demandLevel,
            timeContext = timeContext,
            pickupZone = pickupZone,
            postDropoffAdvice = postDropoffAdvice,
            warnings = warnings
        )

        return RideAdvice(
            calculation = calculation,
            timeContext = timeContext,
            zoneContext = pickupZone,
            demandLevel = demandLevel,
            relatedWarnings = warnings,
            postDropoffAdvice = postDropoffAdvice,
            reasoning = reasoning
        )
    }

    private fun buildContextualWarnings(
        calculation: RideCalculationResult,
        demandLevel: ZoneTimingFinder.DemandLevel,
        pickupZone: ZoneTimingFinder.DemandZone,
        postDropoffAdvice: PostDropoffRedirection.DropoffAnalysis?,
        now: LocalDateTime
    ): List<String> {
        val warnings = mutableListOf<String>()

        // Time-based warnings
        if (demandLevel == ZoneTimingFinder.DemandLevel.PEAK && now.hour in 7..9) {
            warnings.add("📈 PEAK COMMUTE: High acceptance rate, short wait times expected")
        }

        // Zone-based warnings
        when (pickupZone) {
            ZoneTimingFinder.DemandZone.AIRPORT -> {
                warnings.add("✈️ Airport ride: Account for wait times, distance, and limited quick pickups after")
            }
            ZoneTimingFinder.DemandZone.WESTPORT -> {
                if (now.hour >= 20) warnings.add("🍻 Late night bar zone: Count on short hops, high tips")
            }
            ZoneTimingFinder.DemandZone.OLATHE_OVERLAND -> {
                warnings.add("🏘️ Suburb pickup: Deadhead risk returning to city center")
            }
            else -> {}
        }

        // Post-dropoff warnings
        postDropoffAdvice?.let {
            if (it.isDeadheadRisk) {
                warnings.add("🚗 Dropoff is low-density: Plan next zone movement to avoid long deadhead return")
            }
        }

        // Profit margin warning
        if (calculation.estimatedProfit < 5.0 && calculation.recommendation == Recommendation.MAYBE) {
            warnings.add("⚠️ Low profit margin: Consider waiting for better-paying rides")
        }

        return warnings
    }

    private fun buildReasoning(
        calculation: RideCalculationResult,
        demandLevel: ZoneTimingFinder.DemandLevel,
        timeContext: String,
        pickupZone: ZoneTimingFinder.DemandZone,
        postDropoffAdvice: PostDropoffRedirection.DropoffAnalysis?,
        warnings: List<String>
    ): String {
        return when (calculation.recommendation) {
            Recommendation.TAKE -> {
                val profit = String.format("%.2f", calculation.estimatedProfit)
                when {
                    demandLevel == ZoneTimingFinder.DemandLevel.PEAK -> {
                        "✅ TAKE: $profit net profit during PEAK demand. ${if (postDropoffAdvice?.reason != null) "After: ${postDropoffAdvice.reason}" else ""}"
                    }
                    calculation.netPerMile >= 1.0 -> {
                        "✅ TAKE: $profit net, strong per-mile ($${String.format("%.2f", calculation.netPerMile)}/mi)"
                    }
                    else -> {
                        "✅ TAKE: $profit net profit. Good rate for $timeContext demand."
                    }
                }
            }
            Recommendation.MAYBE -> {
                val profit = String.format("%.2f", calculation.estimatedProfit)
                when {
                    demandLevel == ZoneTimingFinder.DemandLevel.PEAK -> {
                        "🤔 MAYBE: $profit profit during PEAK - worth taking if no better offers coming"
                    }
                    calculation.netPerMile >= 0.90 -> {
                        "🤔 MAYBE: $profit net, but acceptable per-mile. High-density zone?"
                    }
                    else -> {
                        "🤔 MAYBE: $profit profit, borderline. Market dependent."
                    }
                }
            }
            Recommendation.DECLINE -> {
                "❌ DECLINE: Below minimum thresholds. Wait for better."
            }
            Recommendation.HARD_DECLINE -> {
                "🛑 HARD DECLINE: Below hard floor (${String.format("%.2f", calculation.effectiveRequiredPerMile)}/mi). Not viable."
            }
        }
    }

    /**
     * After dropoff: what's the plan for the next ride?
     */
    fun advisePostDropoff(
        dropoffZone: ZoneTimingFinder.DemandZone,
        rideData: RideCalculationResult,
        lastPickupZone: ZoneTimingFinder.DemandZone? = null
    ): List<String> {
        val analysis = PostDropoffRedirection.analyzePostDropoff(
            currentTime = LocalDateTime.now(),
            dropoffZone = dropoffZone,
            dropoffLocation = rideData.input.destinationText,
            lastPickupZone = lastPickupZone
        )
        return PostDropoffRedirection.getPostDropoffActions(analysis)
    }
}
