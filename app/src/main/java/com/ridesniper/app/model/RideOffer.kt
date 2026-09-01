package com.ridesniper.app.model

/**
 * Raw values extracted (or manually entered) for a single ride offer, before
 * any calculation is applied.
 */
data class RideOfferInput(
    val payout: Double,
    val pickupMiles: Double,
    val tripMiles: Double,
    val pickupMinutes: Double,
    val tripMinutes: Double,
    val destinationText: String = "",
    val isAirport: Boolean = false,
    val isSurge: Boolean = false,
    val isReservation: Boolean = false,
    val ocrConfidence: Float = 1.0f
)

/**
 * Fully computed result for an offer: every derived number plus the final
 * recommendation and warning flags. This is what the overlay card, the
 * result screen, and ride history all render from.
 */
data class RideCalculationResult(
    val input: RideOfferInput,
    val totalMiles: Double,
    val totalMinutes: Double,
    val grossPerMile: Double,
    val grossPerMinute: Double,
    val fuelCost: Double,
    val wearCost: Double,
    val fuelCostPerMile: Double,
    val wearCostPerMile: Double,
    val totalVehicleCostPerMile: Double,
    val estimatedProfit: Double,
    val netPerMile: Double,
    val netPerHour: Double,
    val recommendation: Recommendation,
    val warnings: List<WarningFlag>,
    val strategyUsed: StrategyPreset,
    val destinationCategory: DestinationCategory,
    val effectiveRequiredPerMile: Double,
    val effectiveRequiredPerMinute: Double
)
