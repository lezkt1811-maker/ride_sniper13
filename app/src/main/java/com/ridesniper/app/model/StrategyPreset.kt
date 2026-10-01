package com.ridesniper.app.model

/**
 * The three built-in strategy presets for Kansas City. Values are $/mile and $/minute
 * preferred minimums used as the baseline before per-ride adjustments (long ride,
 * airport, bad-return-zone) are applied. Tuned for realistic KC earnings.
 */
enum class StrategyPreset(
    val label: String,
    val preferredPerMile: Double,
    val preferredPerMinute: Double
) {
    NORMAL("Normal", 1.10, 0.30),          // Accept most short hops in high-density zones
    PICKY("Picky", 1.25, 0.35),             // Wait for better per-mile rates
    EXTREME("Extreme", 1.50, 0.40);         // Only premium rides

    companion object {
        val DEFAULT = NORMAL  // Changed from PICKY to NORMAL for KC market

        fun fromLabel(label: String): StrategyPreset =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: DEFAULT
    }
}

enum class Recommendation { TAKE, MAYBE, DECLINE, HARD_DECLINE }

enum class DestinationCategory { AIRPORT, SUBURBAN, DOWNTOWN, UNKNOWN }

enum class ZoneRating { GOOD_RETURN, BAD_RETURN, NEUTRAL }

enum class WarningFlag(val label: String) {
    LONG_PICKUP("LONG PICKUP"),
    LONG_RIDE("LONG RIDE"),
    LOW_PAY_PER_MILE("LOW PAY PER MILE"),
    LOW_PAY_PER_MINUTE("LOW PAY PER MINUTE"),
    AIRPORT_RISK("AIRPORT RISK"),
    DEADHEAD_RISK("DEADHEAD RISK"),
    FUEL_HEAVY("FUEL HEAVY"),
    HIGH_WEAR("HIGH WEAR"),
    BAD_DEAL("BAD DEAL"),
    GREAT_DEAL("GREAT DEAL")
}
