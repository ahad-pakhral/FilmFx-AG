package com.filmfx.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

class HapticManager(private val context: Context, private val hapticFeedback: HapticFeedback? = null) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tick() {
        performHaptic(HapticFeedbackType.TextHandleMove, 10, 50)
    }

    fun heavyTick() {
        performHaptic(HapticFeedbackType.LongPress, 20, 100)
    }

    private fun performHaptic(fallbackType: HapticFeedbackType, duration: Long, amplitude: Int) {
        // Try Compose HapticFeedback first
        try {
            hapticFeedback?.performHapticFeedback(fallbackType)
        } catch (e: Exception) {
            // Fallback to Vibrator if Compose fails or isn't available
            vibrateFallback(duration, amplitude)
        }
    }

    private fun vibrateFallback(duration: Long, amplitude: Int) {
        vibrator?.let { v ->
            if (v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(duration)
                }
            }
        }
    }
}
