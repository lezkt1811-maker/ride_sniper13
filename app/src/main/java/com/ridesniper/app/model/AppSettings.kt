package com.ridesniper.app.model

/**
 * All user-editable settings. Kansas City Market Spec: Ford Escape 18 MPG,
 * $0.20/mile wear reserve, NORMAL strategy with adjusted thresholds for KC real data.
 * Uber baseline: $1.10-$1.15/mile gross + $2-3 per-trip fees.
 */
data class AppSettings(
    val gasPricePerGallon: Double = 3.50,
    val mpg: Double = 18.0,
    val wearCostPerMile: Double = 0.20,

    // Per-trip Uber fees to deduct from payout (insurance, admin, etc.)
    val platformFeesPerTrip: Double = 2.50,

    val strategy: StrategyPreset = StrategyPreset.DEFAULT,

    // Editable overrides layered on top of the strategy preset.
    // KC thresholds: accept $0.90-1.10/mile short hops, min $0.80/mile viable runs
    val preferredPerMile: Double = 1.10,
    val minimumPerMile: Double = 0.90,
    val hardDeclinePerMile: Double = 0.80,
    val preferredPerMinute: Double = 0.30,
    val minimumPerMinute: Double = 0.25,

    val longPickupThresholdMiles: Double = 3.0,
    val longRideThresholdMiles: Double = 10.0,
    val longRidePreferredPerMile: Double = 0.95,

    val airportMinimumPerMile: Double = 1.15,
    val airportPreferredPerMile: Double = 1.30,

    val vibrationEnabled: Boolean = true,
    val overlaySizeDp: Int = 64,
    val overlayPositionX: Int = 0,
    val overlayPositionY: Int = 300,

    val ocrConfidenceThreshold: Float = 0.55f,
    val autoDeleteScreenshots: Boolean = true,

    val lastGasPriceEntry: Double = 3.50
)

val AIRPORT_KEYWORDS = listOf(
    "MCI",
    "KANSAS CITY INTERNATIONAL AIRPORT",
    "TERMINAL A",
    "KANSAS CITY INTERNATIONAL"
)
