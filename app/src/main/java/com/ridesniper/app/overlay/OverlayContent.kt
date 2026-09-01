package com.ridesniper.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.model.RideCalculationResult
import java.util.Locale

private val GreenTake = Color(0xFF00E676)
private val YellowMaybe = Color(0xFFFFD600)
private val RedDecline = Color(0xFFFF3D3D)

fun colorFor(rec: Recommendation): Color = when (rec) {
    Recommendation.TAKE -> GreenTake
    Recommendation.MAYBE -> YellowMaybe
    Recommendation.DECLINE, Recommendation.HARD_DECLINE -> RedDecline
}

fun labelFor(rec: Recommendation): String = when (rec) {
    Recommendation.TAKE -> "✅ TAKE"
    Recommendation.MAYBE -> "⚠️ MAYBE"
    Recommendation.DECLINE -> "❌ DECLINE"
    Recommendation.HARD_DECLINE -> "❌ HARD DECLINE"
}

@Composable
fun BubbleView(sizeDp: Int, isBusy: Boolean, lastRecommendation: Recommendation?) {
    val bg = lastRecommendation?.let { colorFor(it) } ?: Color(0xFF2A2A2A)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isBusy) "…" else "RS",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp / 3.2).sp
        )
    }
}

@Composable
fun ResultCard(result: RideCalculationResult, onDismiss: () -> Unit) {
    val color = colorFor(result.recommendation)
    Box(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF121212))
            .border(BorderStroke(1.5.dp, color), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = labelFor(result.recommendation),
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp
            )
            Text(
                text = "$%.2f/mi   $%.2f/min".format(Locale.US, result.grossPerMile, result.grossPerMinute),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatColumn("Offer", "$%.2f".format(Locale.US, result.input.payout))
                StatColumn("Total mi", "%.1f".format(Locale.US, result.totalMiles))
                StatColumn("Total min", "%.0f".format(Locale.US, result.totalMinutes))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatColumn("Pickup", "%.1f mi / %.0f min".format(Locale.US, result.input.pickupMiles, result.input.pickupMinutes))
                StatColumn("Trip", "%.1f mi / %.0f min".format(Locale.US, result.input.tripMiles, result.input.tripMinutes))
            }
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatColumn("Fuel", "$%.2f".format(Locale.US, result.fuelCost))
                StatColumn("Wear", "$%.2f".format(Locale.US, result.wearCost))
                StatColumn("Net profit", "$%.2f".format(Locale.US, result.estimatedProfit))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatColumn("Net/mi", "$%.2f".format(Locale.US, result.netPerMile))
                StatColumn("Net/hr", "$%.2f".format(Locale.US, result.netPerHour))
            }
            if (result.warnings.isNotEmpty()) {
                Divider()
                Text(
                    text = result.warnings.joinToString("  ·  ") { it.label },
                    color = Color(0xFFBBBBBB),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(text = label, color = Color(0xFF888888), fontSize = 11.sp)
        Text(text = value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF2A2A2A))
    )
}

