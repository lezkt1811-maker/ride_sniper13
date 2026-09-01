package com.ridesniper.app.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrParserTest {

    @Test
    fun `parses a clean well-formatted offer`() {
        val raw = """
            $12.50
            Pickup 4 min
            2.1 mi
            Trip 13 min
            5.4 mi
            123 Main St -> Downtown Plaza
        """.trimIndent()

        val result = OcrParser.parse(raw, mlKitConfidence = 0.9f)

        assertEquals(12.50, result.payout.value!!, 0.001)
        assertEquals(2.1, result.pickupMiles.value!!, 0.001)
        assertEquals(5.4, result.tripMiles.value!!, 0.001)
        assertEquals(4.0, result.pickupMinutes.value!!, 0.001)
        assertEquals(13.0, result.tripMinutes.value!!, 0.001)
        assertTrue(result.missingFields().isEmpty())
    }

    @Test
    fun `tolerates comma as decimal separator`() {
        val raw = "$12,50\n2,1 mi\n18 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.8f)
        assertEquals(12.50, result.payout.value!!, 0.001)
    }

    @Test
    fun `tolerates misread zero as letter O between digits`() {
        val raw = "$1O.5O\n3.2 mi\n12 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.7f)
        assertEquals(10.50, result.payout.value!!, 0.001)
    }

    @Test
    fun `tolerates misread one as letter l or I when adjacent to a real digit`() {
        // "l1.00" -> the leading 'l' sits immediately next to the real digit '1' and
        // gets normalized to "11.00"; "1l mi" similarly normalizes to "11 mi".
        val raw = "$l1.00\n1l mi\n12 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.7f)
        assertEquals(11.00, result.payout.value!!, 0.001)
        assertEquals(11.0, result.pickupMiles.value!!, 0.001)
    }

    @Test
    fun `detects airport keyword MCI`() {
        val raw = "$28.00\nPickup 6 min\n1.8 mi\nTrip 22 min\n14.2 mi\nMCI Terminal A"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.85f)
        assertTrue(result.isAirport)
    }

    @Test
    fun `detects surge and reservation keywords`() {
        val surgeRaw = "$18.20\nIncreased\n2.0 mi\n10 min"
        val reservationRaw = "$40.00\nReservation\n3.0 mi\n25 min"

        assertTrue(OcrParser.parse(surgeRaw, 0.8f).isSurge)
        assertTrue(OcrParser.parse(reservationRaw, 0.8f).isReservation)
    }

    @Test
    fun `missing fields are reported for manual correction`() {
        val raw = "$15.00\nsome garbled text with no distances"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.5f)
        assertTrue(result.missingFields().isNotEmpty())
        assertNull(result.toInputOrNull())
    }

    @Test
    fun `falls back to positional order when labels are absent`() {
        // No "Pickup"/"Trip" labels — just two mi values and two min values in order.
        val raw = "$9.75\n1.2 mi\n6.5 mi\n5 min\n17 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.6f)
        assertEquals(1.2, result.pickupMiles.value!!, 0.001)
        assertEquals(6.5, result.tripMiles.value!!, 0.001)
        assertEquals(5.0, result.pickupMinutes.value!!, 0.001)
        assertEquals(17.0, result.tripMinutes.value!!, 0.001)
    }

    @Test
    fun `payout picks the largest dollar figure on screen`() {
        // Uber sometimes shows a small per-mile or surge sub-figure alongside the main offer.
        val raw = "$22.40\n$2.10/mi\n3.5 mi\n20 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.8f)
        assertEquals(22.40, result.payout.value!!, 0.001)
    }
}
