package com.ridesniper.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.ui.MainActivity
import kotlin.math.abs

/**
 * Owns the two overlay windows: the small draggable bubble, and the result
 * card shown after an analysis. Tap = analyze, long-press = open full app,
 * drag = reposition. Neither window ever intercepts input meant for Uber;
 * the result card auto-dismisses and both windows use FLAG_NOT_TOUCH_MODAL
 * so touches outside them pass through to whatever's underneath.
 */
class OverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleOwner = OverlayLifecycleOwner().apply { performRestore() }

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cardView: ComposeView? = null

    var onBubbleTapped: (() -> Unit)? = null

    private val busyState = mutableStateOf(false)
    private val lastRecommendationState = mutableStateOf<Recommendation?>(null)

    fun showBubble(sizeDp: Int, startX: Int, startY: Int) {
        if (bubbleView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
        bubbleParams = params

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                BubbleView(sizeDp = sizeDp, isBusy = busyState.value, lastRecommendation = lastRecommendationState.value)
            }
            setOnTouchListener(dragAndClickListener(params))
        }
        bubbleView = view

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager.addView(view, params)
    }

    fun setBusy(busy: Boolean) {
        busyState.value = busy
    }

    fun showResult(result: RideCalculationResult, autoDismissMillis: Long = 9000) {
        hideResult()
        lastRecommendationState.value = result.recommendation

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 220
        }

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                ResultCard(result = result, onDismiss = { hideResult() })
            }
        }
        cardView = view
        windowManager.addView(view, params)

        view.postDelayed({ hideResult() }, autoDismissMillis)
    }

    fun hideResult() {
        cardView?.let { runCatching { windowManager.removeView(it) } }
        cardView = null
    }

    fun hideBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        hideResult()
        lifecycleOwner.destroy()
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dragAndClickListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var downTimeMillis = 0L
        var moved = false
        val longPressThresholdMillis = 500L
        val dragTouchSlopPx = 12f

        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    downTimeMillis = System.currentTimeMillis()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (abs(dx) > dragTouchSlopPx || abs(dy) > dragTouchSlopPx) {
                        moved = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val heldMillis = System.currentTimeMillis() - downTimeMillis
                    if (!moved) {
                        if (heldMillis >= longPressThresholdMillis) {
                            openFullApp()
                        } else {
                            onBubbleTapped?.invoke()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openFullApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
