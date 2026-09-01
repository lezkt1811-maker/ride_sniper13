package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.model.StrategyPreset
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.ui.theme.DeclineRed
import com.ridesniper.app.ui.theme.MaybeYellow
import com.ridesniper.app.ui.theme.TakeGreen
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: RideSniperViewModel,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartAnalyzer: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    var gasPriceText by remember(settings.gasPricePerGallon) { mutableStateOf("%.2f".format(Locale.US, settings.gasPricePerGallon)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("RIDE SNIPER", style = MaterialTheme.typography.headlineLarge)

        if (!hasOverlayPermission) {
            PermissionCard(
                title = "Overlay permission needed",
                description = "Allow Ride Sniper to display over other apps so the bubble can appear on top of Uber Driver.",
                buttonLabel = "Grant overlay permission",
                onClick = onRequestOverlayPermission
            )
        } else {
            PermissionCard(
                title = "Start the analyzer",
                description = "Starts the floating bubble and grants one-time screen-capture access. You'll be asked again each time Android restarts capture (this is an OS restriction, not a bug).",
                buttonLabel = "Start Ride Sniper",
                onClick = onStartAnalyzer
            )
        }

        // Core Feature 11: quick gas price entry, no internet required.
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null)
                    Text("Gas today", style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = gasPriceText,
                        onValueChange = { gasPriceText = it },
                        modifier = Modifier.width(120.dp),
                        singleLine = true
                    )
                    Button(onClick = {
                        gasPriceText.toDoubleOrNull()?.let { viewModel.setGasPrice(it) }
                    }) { Text("Save") }
                }
            }
        }

        // Strategy preset quick switch.
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Strategy", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.strategy == preset,
                            onClick = { viewModel.applyStrategyPreset(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }
                Text(
                    "Preferred: $%.2f/mi · $%.2f/min".format(Locale.US, settings.preferredPerMile, settings.preferredPerMinute),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Text("Last analyzed offer", style = MaterialTheme.typography.titleLarge)
        if (latestResult == null) {
            Text(
                "No offer analyzed yet. Tap the floating bubble over Uber Driver to analyze one.",
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            ResultHud(latestResult!!)
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, buttonLabel: String, onClick: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Button(onClick = onClick) { Text(buttonLabel) }
        }
    }
}

@Composable
fun ResultHud(result: RideCalculationResult) {
    val color = when (result.recommendation) {
        Recommendation.TAKE -> TakeGreen
        Recommendation.MAYBE -> MaybeYellow
        Recommendation.DECLINE, Recommendation.HARD_DECLINE -> DeclineRed
    }
    val label = when (result.recommendation) {
        Recommendation.TAKE -> "TAKE"
        Recommendation.MAYBE -> "MAYBE"
        Recommendation.DECLINE -> "DECLINE"
        Recommendation.HARD_DECLINE -> "HARD DECLINE"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 64.sp
        )
        Text(
            "$%.2f / mile   ·   $%.2f / minute".format(Locale.US, result.grossPerMile, result.grossPerMinute),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "$%.2f estimated profit   ·   $%.2f/hr net".format(Locale.US, result.estimatedProfit, result.netPerHour),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        if (result.warnings.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                result.warnings.take(6).forEach { w ->
                    AssistChip(onClick = {}, label = { Text(w.label) })
                }
            }
        }

        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide details" else "Show details")
        }
        if (expanded) {
            DetailGrid(result)
        }
    }
}

@Composable
private fun DetailGrid(result: RideCalculationResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow("Offer amount", "$%.2f".format(Locale.US, result.input.payout))
        DetailRow("Pickup miles", "%.2f mi".format(Locale.US, result.input.pickupMiles))
        DetailRow("Trip miles", "%.2f mi".format(Locale.US, result.input.tripMiles))
        DetailRow("Total miles", "%.2f mi".format(Locale.US, result.totalMiles))
        DetailRow("Pickup minutes", "%.1f min".format(Locale.US, result.input.pickupMinutes))
        DetailRow("Trip minutes", "%.1f min".format(Locale.US, result.input.tripMinutes))
        DetailRow("Total minutes", "%.1f min".format(Locale.US, result.totalMinutes))
        DetailRow("Estimated fuel cost", "$%.2f".format(Locale.US, result.fuelCost))
        DetailRow("Estimated wear cost", "$%.2f".format(Locale.US, result.wearCost))
        DetailRow("Estimated net profit", "$%.2f".format(Locale.US, result.estimatedProfit))
        DetailRow("Estimated net/mile", "$%.2f".format(Locale.US, result.netPerMile))
        DetailRow("Estimated net/hour", "$%.2f".format(Locale.US, result.netPerHour))
        DetailRow("Destination", result.input.destinationText.ifBlank { "—" })
        DetailRow("Strategy used", result.strategyUsed.label)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
