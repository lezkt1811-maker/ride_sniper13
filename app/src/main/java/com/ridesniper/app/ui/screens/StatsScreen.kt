package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridesniper.app.data.repository.RideStats
import com.ridesniper.app.data.repository.StatsFilter
import com.ridesniper.app.ui.RideSniperViewModel
import java.util.Locale

@Composable
fun StatsScreen(viewModel: RideSniperViewModel) {
    val stats by viewModel.stats.collectAsState()
    val filter by viewModel.currentStatsFilter.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Statistics", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { viewModel.setStatsFilter(f) },
                    label = { Text(f.label) }
                )
            }
        }

        if (stats.offersAnalyzed == 0) {
            Text("No data in this range yet.", color = MaterialTheme.colorScheme.secondary)
            return@Column
        }

        StatSection("Volume") {
            StatLine("Offers analyzed", "${stats.offersAnalyzed}")
            StatLine("Offers accepted", "${stats.offersAccepted}")
            StatLine("Offers declined", "${stats.offersDeclined}")
            StatLine("Acceptance rate", "%.0f%%".format(Locale.US, stats.acceptanceRate * 100))
        }

        StatSection("Averages") {
            StatLine("Avg gross $/mile", "$%.2f".format(Locale.US, stats.avgGrossPerMile))
            StatLine("Avg gross $/minute", "$%.2f".format(Locale.US, stats.avgGrossPerMinute))
            StatLine("Avg net $/mile", "$%.2f".format(Locale.US, stats.avgNetPerMile))
            StatLine("Avg net $/hour", "$%.2f".format(Locale.US, stats.avgNetPerHour))
            StatLine("Avg payout", "$%.2f".format(Locale.US, stats.avgPayout))
            StatLine("Avg pickup distance", "%.1f mi".format(Locale.US, stats.avgPickupMiles))
            StatLine("Avg trip distance", "%.1f mi".format(Locale.US, stats.avgTripMiles))
            StatLine("Avg total distance", "%.1f mi".format(Locale.US, stats.avgTotalMiles))
        }

        StatSection("Declines saved you") {
            StatLine("Miles avoided", "%.1f mi".format(Locale.US, stats.milesAvoidedByDeclining))
            StatLine("Fuel avoided", "$%.2f".format(Locale.US, stats.fuelAvoided))
            StatLine("Wear avoided", "$%.2f".format(Locale.US, stats.wearAvoided))
            StatLine("Money preserved", "$%.2f".format(Locale.US, stats.moneyPreservedByDeclining))
        }

        StatSection("Extremes") {
            stats.bestRide?.let { StatLine("Best ride profit", "$%.2f".format(Locale.US, it.estimatedProfit)) }
            stats.worstRide?.let { StatLine("Worst ride profit", "$%.2f".format(Locale.US, it.estimatedProfit)) }
            stats.mostProfitableHour?.let { StatLine("Most profitable hour", formatHour(it)) }
            stats.leastProfitableHour?.let { StatLine("Least profitable hour", formatHour(it)) }
        }
    }
}

private fun formatHour(hour24: Int): String {
    val period = if (hour24 < 12) "AM" else "PM"
    val h = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "$h:00 $period"
}

@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
