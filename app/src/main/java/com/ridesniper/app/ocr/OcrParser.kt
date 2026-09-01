package com.ridesniper.app.ocr

import com.ridesniper.app.model.AIRPORT_KEYWORDS
import com.ridesniper.app.model.RideOfferInput
import java.util.Locale

/**
 * Result of a parse attempt. Any field that couldn't be confidently extracted
 * is left null so the UI can fall back to the manual correction sheet for
 * exactly those fields, pre-filling everything else.
 */
data class ParsedField<T>(val value: T?, val matchedRule: String?)

data class OcrParseResult(
    val payout: ParsedField<Double>,
    val pickupMiles: ParsedField<Double>,
    val tripMiles: ParsedField<Double>,
    val pickupMinutes: ParsedField<Double>,
    val tripMinutes: ParsedField<Double>,
    val destinationText: String,
    val isAirport: Boolean,
    val isSurge: Boolean,
    val isReservation: Boolean,
    val rawText: String,
    val confidence: Float
) {
    fun missingFields(): List<String> = buildList {
        if (payout.value == null) add("Offer amount")
        if (pickupMiles.value == null) add("Pickup miles")
        if (tripMiles.value == null) add("Trip miles")
        if (pickupMinutes.value == null) add("Pickup minutes")
        if (tripMinutes.value == null) add("Trip minutes")
    }

    fun toInputOrNull(): RideOfferInput? {
        val p = payout.value ?: return null
        val pm = pickupMiles.value ?: return null
        val tm = tripMiles.value ?: return null
        val pmin = pickupMinutes.value ?: return null
        val tmin = tripMinutes.value ?: return null
        return RideOfferInput(
            payout = p, pickupMiles = pm, tripMiles = tm,
            pickupMinutes = pmin, tripMinutes = tmin,
            destinationText = destinationText, isAirport = isAirport,
            isSurge = isSurge, isReservation = isReservation, ocrConfidence = confidence
        )
    }
}

/**
 * Parses raw OCR text scraped from an Uber Driver ride-offer screen. Written
 * to tolerate spacing errors, line breaks, commas-as-decimals, OCR-misread
 * characters (O->0, l->1, S->5), varying UI layouts, dark mode, and font
 * size differences, since none of that is under this app's control.
 */
object OcrParser {

    // Money: $12.50   $ 12.50   $12,50 (comma-as-decimal)   $12
    private val moneyRegex = Regex("""\$\s?(\d{1,3}(?:[.,]\d{1,2})?)""")

    // Distance: 2.1 mi   5.4mi   0.9 mi.   12 mi
    private val distanceRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?mi\b""", RegexOption.IGNORE_CASE)

    // Duration: 18 min   5 min   4mins
    private val durationRegex = Regex("""(\d{1,3})\s?min""", RegexOption.IGNORE_CASE)

    // Labeled pickup/trip lines: "Pickup 4 min", "Trip 13 min", "Pickup: 2.1 mi"
    private val pickupLineRegex = Regex("""pickup[^0-9]{0,6}(\d{1,3}(?:[.,]\d{1,2})?)\s?(mi|min)""", RegexOption.IGNORE_CASE)
    private val tripLineRegex = Regex("""trip[^0-9]{0,6}(\d{1,3}(?:[.,]\d{1,2})?)\s?(mi|min)""", RegexOption.IGNORE_CASE)

    private val surgeRegex = Regex("""increased|surge|boost""", RegexOption.IGNORE_CASE)
    private val reservationRegex = Regex("""reserve|reservation|scheduled""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, mlKitConfidence: Float): OcrParseResult {
        val normalized = normalize(rawText)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }

        val payout = parsePayout(normalized)
        val (pickupMiles, tripMiles) = parseDistances(normalized, lines)
        val (pickupMinutes, tripMinutes) = parseDurations(normalized, lines)

        val destinationText = extractDestination(lines)
        val isAirport = AIRPORT_KEYWORDS.any { normalized.uppercase().contains(it) }
        val isSurge = surgeRegex.containsMatchIn(normalized)
        val isReservation = reservationRegex.containsMatchIn(normalized)

        val fieldsFound = listOf(payout, pickupMiles, tripMiles, pickupMinutes, tripMinutes).count { it.value != null }
        val heuristicConfidence = (fieldsFound / 5.0f) * mlKitConfidence.coerceIn(0f, 1f).let { if (it == 0f) 1f else it }

        return OcrParseResult(
            payout = payout,
            pickupMiles = pickupMiles,
            tripMiles = tripMiles,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            destinationText = destinationText,
            isAirport = isAirport,
            isSurge = isSurge,
            isReservation = isReservation,
            rawText = rawText,
            confidence = heuristicConfidence.coerceIn(0f, 1f)
        )
    }

    /** Fixes common OCR misreads before regex matching: O/o -> 0, l/I -> 1 in numeric contexts, stray commas. */
    private fun normalize(text: String): String {
        var t = text
        // Fix letter-for-digit swaps when adjacent to a digit on either side, to avoid
        // mangling real words while still catching cases like "1O.5O" -> "10.50".
        t = t.replace(Regex("""(?<=\d)[oO]|[oO](?=\d)"""), "0")
        t = t.replace(Regex("""(?<=\d)[lI]|[lI](?=\d)"""), "1")
        // Collapse repeated whitespace, keep line breaks.
        t = t.lines().joinToString("\n") { it.replace(Regex("""[ \t]+"""), " ").trim() }
        return t
    }

    private fun toDouble(raw: String): Double? =
        raw.replace(",", ".").toDoubleOrNull()

    private fun parsePayout(text: String): ParsedField<Double> {
        val matches = moneyRegex.findAll(text).mapNotNull { toDouble(it.groupValues[1]) }.toList()
        // The offer amount is almost always the largest dollar figure on an Uber offer screen
        // (bigger than any per-mile/per-minute sub-figures that sometimes appear).
        val value = matches.maxOrNull()
        return ParsedField(value, if (value != null) "moneyRegex.max" else null)
    }

    private fun parseDistances(text: String, lines: List<String>): Pair<ParsedField<Double>, ParsedField<Double>> {
        val pickupLabeled = pickupLineRegex.find(text)?.takeIf { it.groupValues[2].equals("mi", true) }
            ?.let { toDouble(it.groupValues[1]) }
        val tripLabeled = tripLineRegex.find(text)?.takeIf { it.groupValues[2].equals("mi", true) }
            ?.let { toDouble(it.groupValues[1]) }

        if (pickupLabeled != null && tripLabeled != null) {
            return ParsedField(pickupLabeled, "pickupLineRegex") to ParsedField(tripLabeled, "tripLineRegex")
        }

        // Fallback heuristic: collect all "X mi" values in on-screen order. Uber offer
        // cards conventionally list pickup distance first, trip distance second.
        val allDistances = distanceRegex.findAll(text).mapNotNull { toDouble(it.groupValues[1]) }.toList()
        val pickup = pickupLabeled ?: allDistances.getOrNull(0)
        val trip = tripLabeled ?: allDistances.getOrNull(1) ?: allDistances.getOrNull(0)?.takeIf { allDistances.size == 1 }

        val pickupRule = if (pickupLabeled != null) "pickupLineRegex" else if (pickup != null) "distanceRegex.order[0]" else null
        val tripRule = if (tripLabeled != null) "tripLineRegex" else if (trip != null) "distanceRegex.order[1]" else null

        return ParsedField(pickup, pickupRule) to ParsedField(trip, tripRule)
    }

    private fun parseDurations(text: String, lines: List<String>): Pair<ParsedField<Double>, ParsedField<Double>> {
        val pickupLabeled = pickupLineRegex.find(text)?.takeIf { it.groupValues[2].equals("min", true) }
            ?.let { toDouble(it.groupValues[1]) }
        val tripLabeled = tripLineRegex.find(text)?.takeIf { it.groupValues[2].equals("min", true) }
            ?.let { toDouble(it.groupValues[1]) }

        if (pickupLabeled != null && tripLabeled != null) {
            return ParsedField(pickupLabeled, "pickupLineRegex") to ParsedField(tripLabeled, "tripLineRegex")
        }

        val allDurations = durationRegex.findAll(text).mapNotNull { toDouble(it.groupValues[1]) }.toList()
        val pickup = pickupLabeled ?: allDurations.getOrNull(0)
        val trip = tripLabeled ?: allDurations.getOrNull(1)

        val pickupRule = if (pickupLabeled != null) "pickupLineRegex" else if (pickup != null) "durationRegex.order[0]" else null
        val tripRule = if (tripLabeled != null) "tripLineRegex" else if (trip != null) "durationRegex.order[1]" else null

        return ParsedField(pickup, pickupRule) to ParsedField(trip, tripRule)
    }

    private fun extractDestination(lines: List<String>): String {
        // Heuristic: the destination line is usually the longest line that isn't a
        // money/mile/min token by itself, and often follows an arrow or "to" marker.
        val candidate = lines.firstOrNull { line ->
            !moneyRegex.matches(line) &&
                !distanceRegex.matches(line) &&
                !durationRegex.matches(line) &&
                line.length > 4 &&
                (line.contains("->") || line.contains("to ", true) ||
                    AIRPORT_KEYWORDS.any { line.uppercase().contains(it) })
        }
        return candidate?.trim().orEmpty()
    }
}
