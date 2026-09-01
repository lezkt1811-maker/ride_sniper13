package com.ridesniper.app.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.delay

/**
 * Wraps MediaProjection + ImageReader to grab a single frame of the current
 * screen on demand. Frames are decoded straight into a Bitmap in memory and
 * never touch disk unless the caller explicitly persists them.
 *
 * Android 14+ (API 34) requires a [MediaProjection.Callback] to be registered
 * *before* [MediaProjection.createVirtualDisplay] is called, or the call
 * throws IllegalStateException. The callback also fires if the user revokes
 * capture from the system "Stop sharing" chip, which we surface via
 * [onProjectionStopped] so the service can react instead of silently failing
 * on the next analyze tap.
 */
class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var registeredCallback: MediaProjection.Callback? = null

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Invoked when the OS or the user ends the capture session out from under us. */
    var onProjectionStopped: (() -> Unit)? = null

    /** Must be called once, with the Activity Result from the system consent dialog. */
    fun start(resultCode: Int, data: Intent, projectionManager: MediaProjectionManager) {
        // getMediaProjection() is @Nullable in the Android SDK (it can return null if
        // consent was denied or the OS refused the request), so bind it to a guaranteed
        // non-null local before calling anything on it. Every call below uses this same
        // `projection` reference, so the callback is registered and the virtual display
        // is created against the exact same non-null instance.
        val projection = projectionManager.getMediaProjection(resultCode, data) ?: return
        mediaProjection = projection

        // Required on Android 14+ before createVirtualDisplay is called at all;
        // harmless (and still correct practice) on older versions too.
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                tearDownDisplayAndReader()
                mediaProjection = null
                onProjectionStopped?.invoke()
            }
        }
        registeredCallback = callback
        projection.registerCallback(callback, mainHandler)

        val size = screenSize()

        val reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "RideSniperCapture",
            size.width, size.height, size.density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, mainHandler
        )
    }

    /**
     * Captures exactly one frame and returns it as a Bitmap, or null if no
     * frame was available after a short retry window (the virtual display
     * needs a moment to produce its first frame right after start()).
     */
    suspend fun captureFrame(): Bitmap? {
        val reader = imageReader ?: return null
        repeat(5) { attempt ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                return try {
                    imageToBitmap(image)
                } finally {
                    image.close()
                }
            }
            if (attempt < 4) delay(60)
        }
        return null
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (bitmap.width != image.width) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }

    private data class ScreenSize(val width: Int, val height: Int, val density: Int)

    private fun screenSize(): ScreenSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            val density = context.resources.displayMetrics.densityDpi
            ScreenSize(bounds.width(), bounds.height(), density)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            ScreenSize(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }
    }

    /** Releases the virtual display and image reader without stopping the projection itself. */
    private fun tearDownDisplayAndReader() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    /** Full teardown: display, reader, callback, then the projection session itself, in that order. */
    fun stop() {
        tearDownDisplayAndReader()
        mediaProjection?.let { projection ->
            registeredCallback?.let { projection.unregisterCallback(it) }
            projection.stop()
        }
        registeredCallback = null
        mediaProjection = null
    }

    fun isActive(): Boolean = mediaProjection != null
}
