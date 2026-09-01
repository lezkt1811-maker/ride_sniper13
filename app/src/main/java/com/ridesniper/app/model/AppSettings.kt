package com.ridesniper.app.model

/**
 * All user-editable settings. Defaults match the spec: 2012 Ford Escape 3.0L V6 AWD,
 * 16.5 default MPG, $0.20/mile wear reserve, PICKY strategy.
 */
data class AppSettings(
    val gasPricePerGallon: Double = 3.50,
    val mpg: Double = 16.5,
    val wearCostPerMile: Double = 0.20,

    val strategy: StrategyPreset = StrategyPreset.DEFAULT,

    // Editable overrides layered on top of the strategy preset.
    val preferredPerMile: Double = StrategyPreset.DEFAULT.preferredPerMile,
    val minimumPerMile: Double = 1.50,
    val hardDeclinePerMile: Double = 1.25,
    val preferredPerMinute: Double = StrategyPreset.DEFAULT.preferredPerMinute,
    val minimumPerMinute: Double = 0.40,

    val longPickupThresholdMiles: Double = 3.0,
    val longRideThresholdMiles: Double = 10.0,
    val longRidePreferredPerMile: Double = 1.75,

    val airportMinimumPerMile: Double = 1.75,
    val airportPreferredPerMile: Double = 2.00,

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
