package com.ridesniper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ridesniper.app.util.PostDropoffRedirection

@Composable
fun PostDropoffAlertDialog(
    analysis: PostDropoffRedirection.DropoffAnalysis,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Urgency indicator
                val urgencyColor = when (analysis.urgency) {
                    "URGENT" -> Color(0xFFEF5350)
                    "HIGH" -> Color(0xFFFFB74D)
                    else -> Color(0xFF81C784)
                }
                val urgencyEmoji = when (analysis.urgency) {
                    "URGENT" -> "🚨"
                    "HIGH" -> "⚠️"
                    else -> "✓"
                }

                Text(
                    "$urgencyEmoji ${analysis.urgency}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = urgencyColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp
                )

                // Current location info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Dropped off at: ${analysis.dropoffZone.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Density: ${analysis.dropoffDensity.name.replace("_", " ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }

                // Deadhead warning
                if (analysis.isDeadheadRisk) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723))
                    ) {
                        Text(
                            "⚠️ Low-density dropoff: Expect few nearby pickups. Plan next move now!",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB74D)
                        )
                    }
                }

                Divider(color = Color(0xFF444444), thickness = 1.dp)

                // Next zone recommendation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B5E20), shape = RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Navigation,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Head to: ${analysis.nextRecommendedZone.label}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            analysis.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC8E6C9)
                        )
                    }
                }

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Est. Deadhead",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90A4AE),
                            fontSize = 10.sp
                        )
                        Text(
                            "${String.format("%.1f", analysis.estimatedDeadheadMiles)} mi",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Expected Rate",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90A4AE),
                            fontSize = 10.sp
                        )
                        Text(
                            "$${String.format("%.2f", analysis.expectedNextEarnings)}/mi",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF81C784),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                    ) {
                        Text("Got it", fontSize = 13.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                    ) {
                        Text("Moving now →", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
