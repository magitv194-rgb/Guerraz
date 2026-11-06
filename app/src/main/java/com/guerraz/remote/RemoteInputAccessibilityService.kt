package com.guerraz.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Optional, user-enabled accessibility bridge for remote taps and swipes.
 * Android requires the device owner to enable this service in Settings.
 */
class RemoteInputAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun dispatch(kind: String, values: List<Float>) {
        val metrics = resources.displayMetrics
        val path = Path()
        val x1 = values[0].coerceIn(0f, 1f) * metrics.widthPixels
        val y1 = values[1].coerceIn(0f, 1f) * metrics.heightPixels
        path.moveTo(x1, y1)
        val duration: Long
        if (kind == "SWIPE" && values.size >= 4) {
            val x2 = values[2].coerceIn(0f, 1f) * metrics.widthPixels
            val y2 = values[3].coerceIn(0f, 1f) * metrics.heightPixels
            path.lineTo(x2, y2)
            duration = 350L
        } else {
            duration = 70L
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile private var instance: RemoteInputAccessibilityService? = null

        fun handleRemoteCommand(command: String) {
            val parts = command.split('|')
            if (parts.size < 3) return
            val kind = parts[0]
            if (kind != "TAP" && kind != "SWIPE") return
            val numbers = parts.drop(1).mapNotNull { it.toFloatOrNull() }
            if (numbers.size < 2 || (kind == "SWIPE" && numbers.size < 4)) return
            Handler(Looper.getMainLooper()).post { instance?.dispatch(kind, numbers) }
        }
    }
}
