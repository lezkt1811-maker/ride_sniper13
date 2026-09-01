package com.ridesniper.app.calculator

import com.ridesniper.app.model.*

/**
 * Pure, deterministic profitability calculator. No Android dependencies so it is
 * trivially unit-testable in isolation from OCR/overlay/service code.
 */
object RideCalculator {

    fun calculate(input: RideOfferInput, settings: AppSettings, zoneRating: ZoneRating = ZoneRating.NEUTRAL): RideCalculationResult {
        val totalMiles = (input.pickupMiles + input.tripMiles).coerceAtLeast(0.01)
        val totalMinutes = (input.pickupMinutes + input.tripMinutes).coerceAtLeast(0.01)

        val grossPerMile = input.payout / totalMiles
        val grossPerMinute = input.payout / totalMinutes

        val fuelCostPerMile = settings.gasPricePerGallon / settings.mpg
        val fuelCost = fuelCostPerMile * totalMiles
        val wearCost = settings.wearCostPerMile * totalMiles
        val totalVehicleCostPerMile = fuelCostPerMile + settings.wearCostPerMile

        val estimatedProfit = input.payout - fuelCost - wearCost
        val netPerMile = estimatedProfit / totalMiles
        val netPerHour = (estimatedProfit / totalMinutes) * 60.0

        val destinationCategory = classifyDestination(input.destinationText, input.isAirport)
        val isAirportRide = destinationCategory == DestinationCategory.AIRPORT

        // Determine the effective required thresholds for THIS ride, layering
        // long-ride, airport, and bad-return-zone adjustments on top of the
        // strategy baseline. The strictest applicable requirement wins.
        var requiredPerMile = settings.minimumPerMile
        var preferredPerMile = settings.preferredPerMile

        if (totalMiles > settings.longRideThresholdMiles) {
            requiredPerMile = maxOf(requiredPerMile, settings.longRidePreferredPerMile)
        }
        if (isAirportRide) {
            requiredPerMile = maxOf(requiredPerMile, settings.airportMinimumPerMile)
            preferredPerMile = maxOf(preferredPerMile, settings.airportPreferredPerMile)
        }
        if (zoneRating == ZoneRating.BAD_RETURN) {
            // Bad return zones raise the bar since the drive back is likely unpaid.
            requiredPerMile += 0.25
            preferredPerMile += 0.25
        }

        val requiredPerMinute = settings.minimumPerMinute
        val preferredPerMinute = settings.preferredPerMinute

        val recommendation = classify(
            grossPerMile = grossPerMile,
            grossPerMinute = grossPerMinute,
            requiredPerMile = requiredPerMile,
            preferredPerMile = preferredPerMile,
            requiredPerMinute = requiredPerMinute,
            preferredPerMinute = preferredPerMinute,
            hardDeclinePerMile = settings.hardDeclinePerMile
        )

        val warnings = buildWarnings(
            input = input,
            settings = settings,
            totalMiles = totalMiles,
            grossPerMile = grossPerMile,
            grossPerMinute = grossPerMinute,
            requiredPerMile = requiredPerMile,
            requiredPerMinute = requiredPerMinute,
            preferredPerMile = preferredPerMile,
            preferredPerMinute = preferredPerMinute,
            estimatedProfit = estimatedProfit,
            fuelCost = fuelCost,
            isAirportRide = isAirportRide,
            zoneRating = zoneRating
        )

        return RideCalculationResult(
            input = input,
            totalMiles = totalMiles,
            totalMinutes = totalMinutes,
            grossPerMile = grossPerMile,
            grossPerMinute = grossPerMinute,
            fuelCost = fuelCost,
            wearCost = wearCost,
            fuelCostPerMile = fuelCostPerMile,
            wearCostPerMile = settings.wearCostPerMile,
            totalVehicleCostPerMile = totalVehicleCostPerMile,
            estimatedProfit = estimatedProfit,
            netPerMile = netPerMile,
            netPerHour = netPerHour,
            recommendation = recommendation,
            warnings = warnings,
            strategyUsed = settings.strategy,
            destinationCategory = destinationCategory,
            effectiveRequiredPerMile = requiredPerMile,
            effectiveRequiredPerMinute = requiredPerMinute
        )
    }

    private fun classify(
        grossPerMile: Double,
        grossPerMinute: Double,
        requiredPerMile: Double,
        preferredPerMile: Double,
        requiredPerMinute: Double,
        preferredPerMinute: Double,
        hardDeclinePerMile: Double
    ): Recommendation {
        if (grossPerMile < hardDeclinePerMile) return Recommendation.HARD_DECLINE

        val meetsGreen = grossPerMile >= preferredPerMile && grossPerMinute >= preferredPerMinute
        if (meetsGreen) return Recommendation.TAKE

        val belowRed = grossPerMile < requiredPerMile || grossPerMinute < requiredPerMinute
        if (belowRed) return Recommendation.DECLINE

        // Falls between the required minimum and the preferred/green bar -> MAYBE.
        return Recommendation.MAYBE
    }

    private fun buildWarnings(
        input: RideOfferInput,
        settings: AppSettings,
        totalMiles: Double,
        grossPerMile: Double,
        grossPerMinute: Double,
        requiredPerMile: Double,
        requiredPerMinute: Double,
        preferredPerMile: Double,
        preferredPerMinute: Double,
        estimatedProfit: Double,
        fuelCost: Double,
        isAirportRide: Boolean,
        zoneRating: ZoneRating
    ): List<WarningFlag> {
        val flags = mutableListOf<WarningFlag>()

        if (input.pickupMiles > settings.longPickupThresholdMiles) flags += WarningFlag.LONG_PICKUP
        if (input.tripMiles > settings.longRideThresholdMiles) flags += WarningFlag.LONG_RIDE
        if (grossPerMile < requiredPerMile) flags += WarningFlag.LOW_PAY_PER_MILE
        if (grossPerMinute < requiredPerMinute) flags += WarningFlag.LOW_PAY_PER_MINUTE
        if (isAirportRide) flags += WarningFlag.AIRPORT_RISK
        if (zoneRating == ZoneRating.BAD_RETURN) flags += WarningFlag.DEADHEAD_RISK

        // Fuel heavy: fuel cost eats an outsized share of the payout.
        if (input.payout > 0 && fuelCost / input.payout > 0.25) flags += WarningFlag.FUEL_HEAVY

        // High wear: long total mileage combined with weak net-per-mile.
        val netPerMile = estimatedProfit / totalMiles
        if (totalMiles > settings.longRideThresholdMiles && netPerMile < 0.75) flags += WarningFlag.HIGH_WEAR

        if (estimatedProfit < 4.0 || netPerMile < 0.50) flags += WarningFlag.BAD_DEAL

        if (grossPerMile >= preferredPerMile && grossPerMinute >= preferredPerMinute && estimatedProfit > 8.0) {
            flags += WarningFlag.GREAT_DEAL
        }

        return flags
    }

    private fun classifyDestination(destinationText: String, explicitAirportFlag: Boolean): DestinationCategory {
        val upper = destinationText.uppercase()
        val airportMatch = explicitAirportFlag || AIRPORT_KEYWORDS.any { upper.contains(it) }
        return when {
            airportMatch -> DestinationCategory.AIRPORT
            upper.contains("DOWNTOWN") || upper.contains("PLAZA") -> DestinationCategory.DOWNTOWN
            destinationText.isBlank() -> DestinationCategory.UNKNOWN
            else -> DestinationCategory.SUBURBAN
        }
    }
}
