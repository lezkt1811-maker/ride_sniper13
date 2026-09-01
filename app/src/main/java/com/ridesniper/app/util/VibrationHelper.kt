package com.ridesniper.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ridesniper.app.model.Recommendation

class VibrationHelper(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun vibrateFor(recommendation: Recommendation, enabled: Boolean) {
        if (!enabled || !vibrator.hasVibrator()) return
        val pattern: LongArray = when (recommendation) {
            Recommendation.TAKE -> longArrayOf(0, 60)
            Recommendation.MAYBE -> longArrayOf(0, 50, 80, 50)
            Recommendation.DECLINE, Recommendation.HARD_DECLINE -> longArrayOf(0, 220)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
