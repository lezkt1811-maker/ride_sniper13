package com.ridesniper.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.ridesniper.app.R
import com.ridesniper.app.RideSniperApp
import com.ridesniper.app.calculator.RideCalculator
import com.ridesniper.app.capture.ScreenCaptureManager
import com.ridesniper.app.model.AppSettings
import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.model.ZoneRating
import com.ridesniper.app.ocr.OcrEngine
import com.ridesniper.app.ocr.OcrParseResult
import com.ridesniper.app.ocr.OcrParser
import com.ridesniper.app.overlay.OverlayController
import com.ridesniper.app.util.VibrationHelper
import kotlinx.coroutines.*

/**
 * Foreground service that keeps the floating bubble alive while Uber Driver is
 * open, owns the MediaProjection session, runs the on-device OCR + calculation
 * pipeline on each tap, and shows the result. It never touches Uber's UI; it
 * only reads the screen and displays a recommendation.
 */
class RideSniperForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ride_sniper_active"
        const val NOTIF_ID = 1001

        const val ACTION_START = "com.ridesniper.app.action.START"
        const val ACTION_ANALYZE = "com.ridesniper.app.action.ANALYZE"
        const val ACTION_PAUSE = "com.ridesniper.app.action.PAUSE"
        const val ACTION_RESUME = "com.ridesniper.app.action.RESUME"
        const val ACTION_STOP = "com.ridesniper.app.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        var onNeedsManualCorrection: ((OcrParseResult) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var overlayController: OverlayController
    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var ocrEngine: OcrEngine
    private lateinit var vibrationHelper: VibrationHelper
    private var isPaused = false
    private var currentSettings = AppSettings()

    override fun onCreate() {
        super.onCreate()
        overlayController = OverlayController(this)
        captureManager = ScreenCaptureManager(this)
        ocrEngine = OcrEngine()
        vibrationHelper = VibrationHelper(this)

        val app = application as RideSniperApp
        serviceScope.launch {
            app.settingsDataStore.settingsFlow.collect { currentSettings = it }
        }

        overlayController.onBubbleTapped = { runAnalysis() }

        captureManager.onProjectionStopped = {
            // The user revoked capture via the system "Stop sharing" chip, or the OS
            // otherwise ended the session. Further analyze taps would be meaningless
            // without a live projection, so stop the whole service; the Home screen's
            // "Start Ride Sniper" button re-requests consent from a foreground Activity.
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                startForeground(NOTIF_ID, buildNotification())
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    captureManager.start(resultCode, resultData, projectionManager)
                }
                overlayController.showBubble(currentSettings.overlaySizeDp, currentSettings.overlayPositionX, currentSettings.overlayPositionY)
            }
            ACTION_ANALYZE -> runAnalysis()
            ACTION_PAUSE -> {
                isPaused = true
                updateNotification()
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun runAnalysis() {
        if (isPaused) return
        overlayController.setBusy(true)
        serviceScope.launch {
            try {
                val bitmap = captureManager.captureFrame()
                if (bitmap == null) {
                    overlayController.setBusy(false)
                    return@launch
                }
                val (text, mlKitConfidence) = ocrEngine.recognize(bitmap)
                if (currentSettings.autoDeleteScreenshots) {
                    bitmap.recycle()
                }

                val parsed = OcrParser.parse(text, mlKitConfidence)
                OverlayResultHolder.lastParseResult = parsed

                val input = parsed.toInputOrNull()
                if (input == null || parsed.confidence < currentSettings.ocrConfidenceThreshold) {
                    withContext(Dispatchers.Main) {
                        onNeedsManualCorrection?.invoke(parsed)
                    }
                } else {
                    val result = RideCalculator.calculate(input, currentSettings, ZoneRating.NEUTRAL)
                    OverlayResultHolder.lastCalculationResult = result
                    withContext(Dispatchers.Main) {
                        overlayController.showResult(result)
                        vibrationHelper.vibrateFor(result.recommendation, currentSettings.vibrationEnabled)
                    }
                    val app = application as RideSniperApp
                    app.rideRepository.saveResult(result)
                }
            } finally {
                overlayController.setBusy(false)
            }
        }
    }

    private fun buildNotification(): Notification {
        createChannelIfNeeded()

        val analyzeIntent = actionPendingIntent(ACTION_ANALYZE)
        val pauseResumeIntent = actionPendingIntent(if (isPaused) ACTION_RESUME else ACTION_PAUSE)
        val stopIntent = actionPendingIntent(ACTION_STOP)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.ridesniper.app.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_analyze), analyzeIntent)
            .addAction(0, getString(if (isPaused) R.string.action_resume else R.string.action_pause), pauseResumeIntent)
            .addAction(0, getString(R.string.action_open), openIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification())
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RideSniperForegroundService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW
                ).apply { description = getString(R.string.notif_channel_desc) }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayController.hideBubble()
        captureManager.stop()
        ocrEngine.close()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
