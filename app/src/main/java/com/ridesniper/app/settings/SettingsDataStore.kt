package com.ridesniper.app.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ridesniper.app.model.AppSettings
import com.ridesniper.app.model.StrategyPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ride_sniper_settings")

/**
 * Persists every editable setting (Core Feature 10) via Jetpack DataStore.
 * Applying a StrategyPreset updates the preferred $/mile and $/minute fields
 * to that preset's baseline; the user can still hand-edit them afterward.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val GAS_PRICE = doublePreferencesKey("gas_price")
        val MPG = doublePreferencesKey("mpg")
        val WEAR_COST = doublePreferencesKey("wear_cost_per_mile")
        val STRATEGY = stringPreferencesKey("strategy")
        val PREFERRED_PER_MILE = doublePreferencesKey("preferred_per_mile")
        val MINIMUM_PER_MILE = doublePreferencesKey("minimum_per_mile")
        val HARD_DECLINE_PER_MILE = doublePreferencesKey("hard_decline_per_mile")
        val PREFERRED_PER_MINUTE = doublePreferencesKey("preferred_per_minute")
        val MINIMUM_PER_MINUTE = doublePreferencesKey("minimum_per_minute")
        val LONG_PICKUP_THRESHOLD = doublePreferencesKey("long_pickup_threshold")
        val LONG_RIDE_THRESHOLD = doublePreferencesKey("long_ride_threshold")
        val LONG_RIDE_PREFERRED_PER_MILE = doublePreferencesKey("long_ride_preferred_per_mile")
        val AIRPORT_MIN_PER_MILE = doublePreferencesKey("airport_min_per_mile")
        val AIRPORT_PREFERRED_PER_MILE = doublePreferencesKey("airport_preferred_per_mile")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val OVERLAY_SIZE = intPreferencesKey("overlay_size_dp")
        val OVERLAY_POS_X = intPreferencesKey("overlay_position_x")
        val OVERLAY_POS_Y = intPreferencesKey("overlay_position_y")
        val OCR_CONFIDENCE_THRESHOLD = floatPreferencesKey("ocr_confidence_threshold")
        val AUTO_DELETE_SCREENSHOTS = booleanPreferencesKey("auto_delete_screenshots")
        val LAST_GAS_PRICE = doublePreferencesKey("last_gas_price_entry")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val default = AppSettings()
        AppSettings(
            gasPricePerGallon = p[Keys.GAS_PRICE] ?: default.gasPricePerGallon,
            mpg = p[Keys.MPG] ?: default.mpg,
            wearCostPerMile = p[Keys.WEAR_COST] ?: default.wearCostPerMile,
            strategy = StrategyPreset.fromLabel(p[Keys.STRATEGY] ?: default.strategy.label),
            preferredPerMile = p[Keys.PREFERRED_PER_MILE] ?: default.preferredPerMile,
            minimumPerMile = p[Keys.MINIMUM_PER_MILE] ?: default.minimumPerMile,
            hardDeclinePerMile = p[Keys.HARD_DECLINE_PER_MILE] ?: default.hardDeclinePerMile,
            preferredPerMinute = p[Keys.PREFERRED_PER_MINUTE] ?: default.preferredPerMinute,
            minimumPerMinute = p[Keys.MINIMUM_PER_MINUTE] ?: default.minimumPerMinute,
            longPickupThresholdMiles = p[Keys.LONG_PICKUP_THRESHOLD] ?: default.longPickupThresholdMiles,
            longRideThresholdMiles = p[Keys.LONG_RIDE_THRESHOLD] ?: default.longRideThresholdMiles,
            longRidePreferredPerMile = p[Keys.LONG_RIDE_PREFERRED_PER_MILE] ?: default.longRidePreferredPerMile,
            airportMinimumPerMile = p[Keys.AIRPORT_MIN_PER_MILE] ?: default.airportMinimumPerMile,
            airportPreferredPerMile = p[Keys.AIRPORT_PREFERRED_PER_MILE] ?: default.airportPreferredPerMile,
            vibrationEnabled = p[Keys.VIBRATION_ENABLED] ?: default.vibrationEnabled,
            overlaySizeDp = p[Keys.OVERLAY_SIZE] ?: default.overlaySizeDp,
            overlayPositionX = p[Keys.OVERLAY_POS_X] ?: default.overlayPositionX,
            overlayPositionY = p[Keys.OVERLAY_POS_Y] ?: default.overlayPositionY,
            ocrConfidenceThreshold = p[Keys.OCR_CONFIDENCE_THRESHOLD] ?: default.ocrConfidenceThreshold,
            autoDeleteScreenshots = p[Keys.AUTO_DELETE_SCREENSHOTS] ?: default.autoDeleteScreenshots,
            lastGasPriceEntry = p[Keys.LAST_GAS_PRICE] ?: default.lastGasPriceEntry
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val current = toSettings(p)
            val updated = transform(current)
            writeAll(p, updated)
        }
    }

    suspend fun applyStrategyPreset(preset: StrategyPreset) {
        update { it.copy(strategy = preset, preferredPerMile = preset.preferredPerMile, preferredPerMinute = preset.preferredPerMinute) }
    }

    suspend fun setGasPrice(price: Double) {
        update { it.copy(gasPricePerGallon = price, lastGasPriceEntry = price) }
    }

    private fun toSettings(p: Preferences): AppSettings {
        val default = AppSettings()
        return AppSettings(
            gasPricePerGallon = p[Keys.GAS_PRICE] ?: default.gasPricePerGallon,
            mpg = p[Keys.MPG] ?: default.mpg,
            wearCostPerMile = p[Keys.WEAR_COST] ?: default.wearCostPerMile,
            strategy = StrategyPreset.fromLabel(p[Keys.STRATEGY] ?: default.strategy.label),
            preferredPerMile = p[Keys.PREFERRED_PER_MILE] ?: default.preferredPerMile,
            minimumPerMile = p[Keys.MINIMUM_PER_MILE] ?: default.minimumPerMile,
            hardDeclinePerMile = p[Keys.HARD_DECLINE_PER_MILE] ?: default.hardDeclinePerMile,
            preferredPerMinute = p[Keys.PREFERRED_PER_MINUTE] ?: default.preferredPerMinute,
            minimumPerMinute = p[Keys.MINIMUM_PER_MINUTE] ?: default.minimumPerMinute,
            longPickupThresholdMiles = p[Keys.LONG_PICKUP_THRESHOLD] ?: default.longPickupThresholdMiles,
            longRideThresholdMiles = p[Keys.LONG_RIDE_THRESHOLD] ?: default.longRideThresholdMiles,
            longRidePreferredPerMile = p[Keys.LONG_RIDE_PREFERRED_PER_MILE] ?: default.longRidePreferredPerMile,
            airportMinimumPerMile = p[Keys.AIRPORT_MIN_PER_MILE] ?: default.airportMinimumPerMile,
            airportPreferredPerMile = p[Keys.AIRPORT_PREFERRED_PER_MILE] ?: default.airportPreferredPerMile,
            vibrationEnabled = p[Keys.VIBRATION_ENABLED] ?: default.vibrationEnabled,
            overlaySizeDp = p[Keys.OVERLAY_SIZE] ?: default.overlaySizeDp,
            overlayPositionX = p[Keys.OVERLAY_POS_X] ?: default.overlayPositionX,
            overlayPositionY = p[Keys.OVERLAY_POS_Y] ?: default.overlayPositionY,
            ocrConfidenceThreshold = p[Keys.OCR_CONFIDENCE_THRESHOLD] ?: default.ocrConfidenceThreshold,
            autoDeleteScreenshots = p[Keys.AUTO_DELETE_SCREENSHOTS] ?: default.autoDeleteScreenshots,
            lastGasPriceEntry = p[Keys.LAST_GAS_PRICE] ?: default.lastGasPriceEntry
        )
    }

    private fun writeAll(p: MutablePreferences, s: AppSettings) {
        p[Keys.GAS_PRICE] = s.gasPricePerGallon
        p[Keys.MPG] = s.mpg
        p[Keys.WEAR_COST] = s.wearCostPerMile
        p[Keys.STRATEGY] = s.strategy.label
        p[Keys.PREFERRED_PER_MILE] = s.preferredPerMile
        p[Keys.MINIMUM_PER_MILE] = s.minimumPerMile
        p[Keys.HARD_DECLINE_PER_MILE] = s.hardDeclinePerMile
        p[Keys.PREFERRED_PER_MINUTE] = s.preferredPerMinute
        p[Keys.MINIMUM_PER_MINUTE] = s.minimumPerMinute
        p[Keys.LONG_PICKUP_THRESHOLD] = s.longPickupThresholdMiles
        p[Keys.LONG_RIDE_THRESHOLD] = s.longRideThresholdMiles
        p[Keys.LONG_RIDE_PREFERRED_PER_MILE] = s.longRidePreferredPerMile
        p[Keys.AIRPORT_MIN_PER_MILE] = s.airportMinimumPerMile
        p[Keys.AIRPORT_PREFERRED_PER_MILE] = s.airportPreferredPerMile
        p[Keys.VIBRATION_ENABLED] = s.vibrationEnabled
        p[Keys.OVERLAY_SIZE] = s.overlaySizeDp
        p[Keys.OVERLAY_POS_X] = s.overlayPositionX
        p[Keys.OVERLAY_POS_Y] = s.overlayPositionY
        p[Keys.OCR_CONFIDENCE_THRESHOLD] = s.ocrConfidenceThreshold
        p[Keys.AUTO_DELETE_SCREENSHOTS] = s.autoDeleteScreenshots
        p[Keys.LAST_GAS_PRICE] = s.lastGasPriceEntry
    }
}
