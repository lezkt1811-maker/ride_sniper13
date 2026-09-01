package com.ridesniper.app.calculator

import com.ridesniper.app.model.*
import org.junit.Assert.*
import org.junit.Test

class RideCalculatorTest {

    private val defaultSettings = AppSettings() // PICKY: 2.00/mi, 0.50/min, hard 1.25/mi

    @Test
    fun `basic formulas compute correctly`() {
        val input = RideOfferInput(
            payout = 20.0, pickupMiles = 2.0, tripMiles = 8.0,
            pickupMinutes = 5.0, tripMinutes = 15.0
        )
        val result = RideCalculator.calculate(input, defaultSettings)

        assertEquals(10.0, result.totalMiles, 0.001)
        assertEquals(20.0, result.totalMinutes, 0.001)
        assertEquals(2.0, result.grossPerMile, 0.001)
        assertEquals(1.0, result.grossPerMinute, 0.001)

        val expectedFuelCost = (10.0 / defaultSettings.mpg) * defaultSettings.gasPricePerGallon
        val expectedWearCost = 10.0 * defaultSettings.wearCostPerMile
        assertEquals(expectedFuelCost, result.fuelCost, 0.001)
        assertEquals(expectedWearCost, result.wearCost, 0.001)
        assertEquals(20.0 - expectedFuelCost - expectedWearCost, result.estimatedProfit, 0.001)
    }

    @Test
    fun `great offer is TAKE under picky strategy`() {
        val input = RideOfferInput(payout = 24.0, pickupMiles = 1.0, tripMiles = 9.0, pickupMinutes = 3.0, tripMinutes = 21.0)
        // 24 / 10mi = 2.40/mi (>= 2.00), 24 / 24min = 1.00/min (>= 0.50)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.TAKE, result.recommendation)
    }

    @Test
    fun `offer between minimum and preferred is MAYBE`() {
        val input = RideOfferInput(payout = 16.0, pickupMiles = 2.0, tripMiles = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // 16 / 10mi = 1.60/mi -> between minimumPerMile(1.50) and preferredPerMile(2.00)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.MAYBE, result.recommendation)
    }

    @Test
    fun `offer below minimum is DECLINE`() {
        val input = RideOfferInput(payout = 13.0, pickupMiles = 2.0, tripMiles = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // 13 / 10mi = 1.30/mi -> below minimumPerMile(1.50), above hardDecline(1.25)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.DECLINE, result.recommendation)
    }

    @Test
    fun `offer below hard decline floor is HARD_DECLINE`() {
        val input = RideOfferInput(payout = 11.0, pickupMiles = 2.0, tripMiles = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // 11 / 10mi = 1.10/mi -> below hardDeclinePerMile(1.25)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.HARD_DECLINE, result.recommendation)
    }

    @Test
    fun `long ride raises required per mile threshold`() {
        // 15 total miles > longRideThresholdMiles(10) -> requires >= longRidePreferredPerMile(1.75)
        val input = RideOfferInput(payout = 24.0, pickupMiles = 2.0, tripMiles = 13.0, pickupMinutes = 5.0, tripMinutes = 40.0)
        // 24 / 15mi = 1.60/mi -> below the long-ride-adjusted required (1.75) -> DECLINE despite being above base minimum
        val result = RideCalculator.calculate(input, defaultSettings)
        assertTrue(result.warnings.contains(WarningFlag.LONG_RIDE))
        assertEquals(Recommendation.DECLINE, result.recommendation)
    }

    @Test
    fun `airport destination applies stricter minimum`() {
        val input = RideOfferInput(
            payout = 16.0, pickupMiles = 2.0, tripMiles = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0,
            destinationText = "MCI Terminal A", isAirport = true
        )
        // 16 / 10mi = 1.60/mi -> above base minimum(1.50) but below airportMinimumPerMile(1.75) -> DECLINE
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(DestinationCategory.AIRPORT, result.destinationCategory)
        assertTrue(result.warnings.contains(WarningFlag.AIRPORT_RISK))
        assertEquals(Recommendation.DECLINE, result.recommendation)
    }

    @Test
    fun `airport keyword detected from destination text alone`() {
        val input = RideOfferInput(
            payout = 30.0, pickupMiles = 2.0, tripMiles = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0,
            destinationText = "Kansas City International Airport"
        )
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(DestinationCategory.AIRPORT, result.destinationCategory)
    }

    @Test
    fun `bad return zone increases required per mile`() {
        val input = RideOfferInput(payout = 20.0, pickupMiles = 2.0, tripMiles = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        val neutral = RideCalculator.calculate(input, defaultSettings, ZoneRating.NEUTRAL)
        val badReturn = RideCalculator.calculate(input, defaultSettings, ZoneRating.BAD_RETURN)

        assertTrue(badReturn.effectiveRequiredPerMile > neutral.effectiveRequiredPerMile)
        assertTrue(badReturn.warnings.contains(WarningFlag.DEADHEAD_RISK))
    }

    @Test
    fun `long pickup warning fires above threshold`() {
        val input = RideOfferInput(payout = 20.0, pickupMiles = 4.0, tripMiles = 6.0, pickupMinutes = 10.0, tripMinutes = 15.0)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertTrue(result.warnings.contains(WarningFlag.LONG_PICKUP))
    }

    @Test
    fun `great deal flag requires strong mile, minute, and profit`() {
        val input = RideOfferInput(payout = 30.0, pickupMiles = 1.0, tripMiles = 9.0, pickupMinutes = 2.0, tripMinutes = 18.0)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertTrue(result.warnings.contains(WarningFlag.GREAT_DEAL))
    }

    @Test
    fun `normal strategy preset uses looser thresholds than picky`() {
        val normalSettings = defaultSettings.copy(
            strategy = StrategyPreset.NORMAL,
            preferredPerMile = StrategyPreset.NORMAL.preferredPerMile,
            preferredPerMinute = StrategyPreset.NORMAL.preferredPerMinute
        )
        val input = RideOfferInput(payout = 16.0, pickupMiles = 2.0, tripMiles = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // 1.60/mi meets NORMAL's preferred (1.50) -> TAKE, whereas under PICKY this was MAYBE.
        val result = RideCalculator.calculate(input, normalSettings)
        assertEquals(Recommendation.TAKE, result.recommendation)
    }
}
