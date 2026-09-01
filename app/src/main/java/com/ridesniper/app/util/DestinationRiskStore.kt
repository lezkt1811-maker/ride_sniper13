package com.ridesniper.app.util

import android.content.Context
import com.ridesniper.app.model.ZoneRating

/**
 * Simple local key-value store mapping a destination text/key to a manually
 * assigned zone rating (GOOD_RETURN / BAD_RETURN / NEUTRAL). Backed by
 * SharedPreferences since it's a small, flat map that doesn't need Room.
 */
class DestinationRiskStore(context: Context) {

    private val prefs = context.getSharedPreferences("destination_zones", Context.MODE_PRIVATE)

    fun getRating(destinationKey: String): ZoneRating {
        val key = normalizeKey(destinationKey)
        val stored = prefs.getString(key, ZoneRating.NEUTRAL.name)
        return runCatching { ZoneRating.valueOf(stored ?: ZoneRating.NEUTRAL.name) }.getOrDefault(ZoneRating.NEUTRAL)
    }

    fun setRating(destinationKey: String, rating: ZoneRating) {
        val key = normalizeKey(destinationKey)
        prefs.edit().putString(key, rating.name).apply()
    }

    fun allRatings(): Map<String, ZoneRating> =
        prefs.all.mapNotNull { (k, v) ->
            val rating = runCatching { ZoneRating.valueOf(v as? String ?: return@mapNotNull null) }.getOrNull()
            if (rating != null) k to rating else null
        }.toMap()

    private fun normalizeKey(destinationText: String): String =
        destinationText.trim().uppercase().ifBlank { "UNKNOWN" }
}
