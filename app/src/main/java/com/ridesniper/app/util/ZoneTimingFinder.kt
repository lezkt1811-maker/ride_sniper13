package com.ridesniper.app.util

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Tracks peak demand zones and times in Kansas City. Helps driver find best earning
 * windows and hotspots to avoid deadhead return miles.
 */
object ZoneTimingFinder {

    enum class DemandZone(val label: String, val description: String) {
        POWER_AND_LIGHT("Power & Light", "Downtown hotspot - evening/weekend peak"),
        WESTPORT("Westport", "Entertainment district - late night peak"),
        PLAZA("Plaza", "Upscale shopping/dining - afternoon peak"),
        MIDTOWN("Midtown", "Arts/dining district - evening peak"),
        AIRPORT("MCI Airport", "Consistent demand 5-11 AM, 3-7 PM"),
        MIDTOWN_COMMUTE("Midtown Commute", "Midtown to Downtown/Plaza corridor"),
        OLATHE_OVERLAND("Olathe/Overland Park", "Suburb commute zone - morning/evening"),
        UNKNOWN("Unknown", "No zone assigned")
    }

    enum class DemandLevel(val label: String, val multiplier: Double) {
        PEAK("Peak", 1.3),
        HIGH("High", 1.15),
        NORMAL("Normal", 1.0),
        LOW("Low", 0.7),
        DEAD("Dead", 0.4)
    }

    data class DemandWindow(
        val zone: DemandZone,
        val startHour: Int,
        val endHour: Int,
        val level: DemandLevel,
        val note: String = ""
    )

    /**
     * Return demand forecast for a specific hour and location.
     */
    fun getDemandLevel(currentTime: LocalDateTime, zone: DemandZone): DemandLevel {
        val hour = currentTime.hour
        val dayOfWeek = currentTime.dayOfWeek.value // 1=Monday, 7=Sunday
        val isWeekend = dayOfWeek >= 6

        return when (zone) {
            DemandZone.POWER_AND_LIGHT -> {
                when {
                    !isWeekend && hour in 5..9 -> DemandLevel.HIGH  // Breakfast crowd
                    !isWeekend && hour in 11..13 -> DemandLevel.HIGH // Lunch
                    isWeekend && hour in 18..23 -> DemandLevel.PEAK  // Friday-Saturday night
                    isWeekend && hour in 11..16 -> DemandLevel.HIGH  // Weekend daytime
                    hour in 17..19 -> DemandLevel.HIGH               // After-work crowd
                    else -> DemandLevel.NORMAL
                }
            }
            DemandZone.WESTPORT -> {
                when {
                    isWeekend && hour in 21..2 -> DemandLevel.PEAK   // Late night (Fri-Sat)
                    !isWeekend && hour in 20..23 -> DemandLevel.HIGH  // Weekday evening
                    !isWeekend && hour in 17..19 -> DemandLevel.HIGH  // After-work drinks
                    else -> DemandLevel.NORMAL
                }
            }
            DemandZone.PLAZA -> {
                when {
                    !isWeekend && hour in 10..16 -> DemandLevel.HIGH   // Lunch/shopping
                    isWeekend && hour in 10..20 -> DemandLevel.PEAK    // Weekend shopping
                    hour in 17..19 -> DemandLevel.HIGH                 // Evening dining
                    else -> DemandLevel.NORMAL
                }
            }
            DemandZone.MIDTOWN -> {
                when {
                    !isWeekend && hour in 11..14 -> DemandLevel.HIGH   // Lunch
                    !isWeekend && hour in 17..20 -> DemandLevel.HIGH   // Dinner/drinks
                    isWeekend && hour in 11..22 -> DemandLevel.HIGH    // All day weekend
                    else -> DemandLevel.NORMAL
                }
            }
            DemandZone.AIRPORT -> {
                when {
                    hour in 5..11 -> DemandLevel.PEAK                  // Morning departures
                    hour in 14..19 -> DemandLevel.PEAK                 // Evening arrivals/departures
                    hour in 11..14 -> DemandLevel.HIGH                 // Midday
                    else -> DemandLevel.NORMAL
                }
            }
            DemandZone.MIDTOWN_COMMUTE -> {
                when {
                    !isWeekend && hour in 7..9 -> DemandLevel.PEAK    // Morning commute
                    !isWeekend && hour in 16..18 -> DemandLevel.PEAK   // Evening commute
                    else -> DemandLevel.NORMAL
                }
            }
            DemandZone.OLATHE_OVERLAND -> {
                when {
                    !isWeekend && hour in 6..8 -> DemandLevel.HIGH    // Morning commute
                    !isWeekend && hour in 16..19 -> DemandLevel.HIGH   // Evening commute
                    else -> DemandLevel.NORMAL
                }
            }
            DemandZone.UNKNOWN -> DemandLevel.LOW
        }
    }

    /**
     * Get top recommendations for next zone after ride completion.
     */
    fun getPostDropoffSuggestions(
        currentTime: LocalDateTime,
        currentZone: DemandZone,
        priorZone: DemandZone? = null
    ): List<Pair<DemandZone, String>> {
        val hour = currentTime.hour
        val dayOfWeek = currentTime.dayOfWeek.value
        val isWeekend = dayOfWeek >= 6

        val suggestions = mutableListOf<Pair<DemandZone, String>>()

        // Smart routing based on current time and location
        when {
            !isWeekend && hour in 7..9 -> {
                // Morning commute peak
                suggestions.add(DemandZone.MIDTOWN_COMMUTE to "Head to Midtown-Downtown corridor for commute surge")
                suggestions.add(DemandZone.AIRPORT to "Early morning airport demand starting")
                suggestions.add(DemandZone.OLATHE_OVERLAND to "Suburb commute peak ongoing")
            }
            !isWeekend && hour in 11..14 -> {
                // Lunch peak
                suggestions.add(DemandZone.PLAZA to "Lunch crowd at Plaza - high demand")
                suggestions.add(DemandZone.POWER_AND_LIGHT to "Downtown lunch rush")
                suggestions.add(DemandZone.MIDTOWN to "Midtown lunch scene")
            }
            !isWeekend && hour in 16..19 -> {
                // Evening commute + after-work
                suggestions.add(DemandZone.MIDTOWN_COMMUTE to "Evening commute peak - busy corridor")
                suggestions.add(DemandZone.AIRPORT to "Evening departure peak")
                suggestions.add(DemandZone.POWER_AND_LIGHT to "After-work crowd gathering")
            }
            isWeekend && hour in 11..16 -> {
                // Weekend daytime
                suggestions.add(DemandZone.PLAZA to "Peak weekend shopping - sustained demand")
                suggestions.add(DemandZone.POWER_AND_LIGHT to "Weekend entertainment crowd")
                suggestions.add(DemandZone.MIDTOWN to "Weekend dining/shopping")
            }
            isWeekend && hour in 18..23 -> {
                // Weekend evening
                suggestions.add(DemandZone.POWER_AND_LIGHT to "Evening entertainment peak")
                suggestions.add(DemandZone.WESTPORT to "Evening bar/music scene")
                suggestions.add(DemandZone.MIDTOWN to "Weekend night entertainment")
            }
            hour in 21..2 && isWeekend -> {
                // Late night (Fri-Sat)
                suggestions.add(DemandZone.WESTPORT to "PEAK: Late night bars/clubs closing surges")
                suggestions.add(DemandZone.POWER_AND_LIGHT to "Late night hotspot")
            }
            else -> {
                // Default: go to highest current demand
                suggestions.add(DemandZone.POWER_AND_LIGHT to "Steady downtown demand")
                suggestions.add(DemandZone.MIDTOWN to "Reliable midtown traffic")
            }
        }

        // Filter out the current zone to avoid just staying put
        return suggestions.filter { it.first != currentZone }.take(3)
    }

    /**
     * Get all peak windows for today.
     */
    fun getTodaysPeakWindows(): List<DemandWindow> {
        return listOf(
            DemandWindow(DemandZone.AIRPORT, 5, 11, DemandLevel.PEAK, "Morning departure wave"),
            DemandWindow(DemandZone.OLATHE_OVERLAND, 6, 9, DemandLevel.HIGH, "Suburb morning commute"),
            DemandWindow(DemandZone.MIDTOWN_COMMUTE, 7, 9, DemandLevel.PEAK, "Downtown commute peak"),
            DemandWindow(DemandZone.PLAZA, 10, 16, DemandLevel.HIGH, "Shopping/lunch daytime"),
            DemandWindow(DemandZone.POWER_AND_LIGHT, 11, 14, DemandLevel.HIGH, "Downtown lunch rush"),
            DemandWindow(DemandZone.MIDTOWN_COMMUTE, 16, 18, DemandLevel.PEAK, "Evening commute surge"),
            DemandWindow(DemandZone.AIRPORT, 14, 19, DemandLevel.PEAK, "Evening departure/arrival wave"),
            DemandWindow(DemandZone.POWER_AND_LIGHT, 17, 23, DemandLevel.PEAK, "Evening entertainment peak"),
            DemandWindow(DemandZone.WESTPORT, 20, 2, DemandLevel.PEAK, "Night entertainment (Fri-Sat)")
        )
    }

    /**
     * Zone heat map: expected gross per mile by zone and time.
     */
    fun getExpectedEarningsPerMile(zone: DemandZone, hour: Int): Double {
        val baseRate = 1.10 // KC baseline
        val demandLevel = getDemandLevel(LocalDateTime.now().withHour(hour), zone)
        return baseRate * demandLevel.multiplier
    }
}
