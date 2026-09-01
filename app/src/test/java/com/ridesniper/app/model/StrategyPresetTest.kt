package com.ridesniper.app.model

import org.junit.Assert.*
import org.junit.Test

class StrategyPresetTest {

    @Test
    fun `default strategy is PICKY`() {
        assertEquals(StrategyPreset.PICKY, StrategyPreset.DEFAULT)
    }

    @Test
    fun `preset thresholds match spec`() {
        assertEquals(1.50, StrategyPreset.NORMAL.preferredPerMile, 0.001)
        assertEquals(0.40, StrategyPreset.NORMAL.preferredPerMinute, 0.001)

        assertEquals(2.00, StrategyPreset.PICKY.preferredPerMile, 0.001)
        assertEquals(0.50, StrategyPreset.PICKY.preferredPerMinute, 0.001)

        assertEquals(2.50, StrategyPreset.EXTREME.preferredPerMile, 0.001)
        assertEquals(0.60, StrategyPreset.EXTREME.preferredPerMinute, 0.001)
    }

    @Test
    fun `fromLabel resolves case-insensitively and falls back to default`() {
        assertEquals(StrategyPreset.EXTREME, StrategyPreset.fromLabel("extreme"))
        assertEquals(StrategyPreset.NORMAL, StrategyPreset.fromLabel("Normal"))
        assertEquals(StrategyPreset.DEFAULT, StrategyPreset.fromLabel("nonsense"))
    }

    @Test
    fun `default AppSettings match vehicle and reserve spec`() {
        val settings = AppSettings()
        assertEquals(16.5, settings.mpg, 0.001)
        assertEquals(0.20, settings.wearCostPerMile, 0.001)
        assertEquals(StrategyPreset.PICKY, settings.strategy)
        assertEquals(1.25, settings.hardDeclinePerMile, 0.001)
        assertEquals(1.75, settings.airportMinimumPerMile, 0.001)
        assertEquals(2.00, settings.airportPreferredPerMile, 0.001)
    }

    @Test
    fun `airport keyword list matches spec exactly`() {
        assertTrue(AIRPORT_KEYWORDS.contains("MCI"))
        assertTrue(AIRPORT_KEYWORDS.contains("KANSAS CITY INTERNATIONAL AIRPORT"))
        assertTrue(AIRPORT_KEYWORDS.contains("TERMINAL A"))
        assertTrue(AIRPORT_KEYWORDS.contains("KANSAS CITY INTERNATIONAL"))
    }
}
