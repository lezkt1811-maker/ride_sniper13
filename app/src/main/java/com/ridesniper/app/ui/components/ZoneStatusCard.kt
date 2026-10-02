package com.ridesniper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesniper.app.util.ZoneTimingFinder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ZoneStatusCard() {
    val now = remember { LocalDateTime.now() }
    val hour = now.hour
    val dayOfWeek = now.dayOfWeek.value

    // Get current demand level for major zones
    val powerAndLight = ZoneTimingFinder.getDemandLevel(now, ZoneTimingFinder.DemandZone.POWER_AND_LIGHT)
    val westport = ZoneTimingFinder.getDemandLevel(now, ZoneTimingFinder.DemandZone.WESTPORT)
    val plaza = ZoneTimingFinder.getDemandLevel(now, ZoneTimingFinder.DemandZone.PLAZA)
    val airport = ZoneTimingFinder.getDemandLevel(now, ZoneTimingFinder.DemandZone.AIRPORT)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = Color(0xFF64B5F6))
                Text(
                    "Zone Demand Right Now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Zone grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneDemandRow("Power & Light", powerAndLight)
                ZoneDemandRow("Westport", westport)
                ZoneDemandRow("Plaza", plaza)
                ZoneDemandRow("Airport (MCI)", airport)
            }

            Divider(color = Color(0xFF444444), thickness = 1.dp)

            // Next peak window
            val nextPeaks = getTodayNextPeak(hour)
            if (nextPeaks != null) {
                Text(
                    "📈 Next Peak: ${nextPeaks.first} in ${nextPeaks.second} (${nextPeaks.third})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF81C784),
                    fontSize = 12.sp
                )
            }

            // Time warning
            if (hour in 14..15) {
                Text(
                    "💤 Slow window: Wait for evening commute (5-7 PM)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp
                )
            } else if (hour in 7..9 || hour in 17..19) {
                Text(
                    "🔥 PEAK COMMUTE: High acceptance, short waits",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ZoneDemandRow(zoneName: String, level: ZoneTimingFinder.DemandLevel) {
    val levelColor = when (level) {
        ZoneTimingFinder.DemandLevel.PEAK -> Color(0xFFEF5350)
        ZoneTimingFinder.DemandLevel.HIGH -> Color(0xFFFFB74D)
        ZoneTimingFinder.DemandLevel.NORMAL -> Color(0xFF64B5F6)
        ZoneTimingFinder.DemandLevel.LOW -> Color(0xFF90A4AE)
        ZoneTimingFinder.DemandLevel.DEAD -> Color(0xFF616161)
    }
    val levelLabel = when (level) {
        ZoneTimingFinder.DemandLevel.PEAK -> "🔥 PEAK"
        ZoneTimingFinder.DemandLevel.HIGH -> "⬆️ HIGH"
        ZoneTimingFinder.DemandLevel.NORMAL -> "➡️ NORMAL"
        ZoneTimingFinder.DemandLevel.LOW -> "⬇️ LOW"
        ZoneTimingFinder.DemandLevel.DEAD -> "☠️ DEAD"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(zoneName, color = Color.White, fontSize = 13.sp)
        Text(
            levelLabel,
            color = levelColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

private fun getTodayNextPeak(currentHour: Int): Triple<String, String, String>? {
    return when {
        currentHour < 7 -> Triple("Morning Commute", "7 AM", "Midtown-Downtown corridor")
        currentHour < 11 -> Triple("Lunch Rush", "11 AM", "Plaza & Downtown")
        currentHour < 14 -> Triple("Afternoon Slump", "→", "Wait for 5 PM surge")
        currentHour < 17 -> Triple("Evening Commute", "5 PM", "All zones peak")
        currentHour < 21 -> Triple("Night Crowd", "9 PM", "Westport & Power & Light")
        else -> Triple("Late Night", "12 AM", "Westport closing surge")
    }
}
