package com.markvoronin.reelsonthego.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.markvoronin.reelsonthego.data.PreferencesRepository
import com.markvoronin.reelsonthego.util.Logger
import com.markvoronin.reelsonthego.util.ShizukuManager
import java.lang.ref.WeakReference

class ReelsAccessibilityService : AccessibilityService() {

    private lateinit var prefsRepository: PreferencesRepository
    private var currentPackageName: String = ""

    override fun onCreate() {
        super.onCreate()
        prefsRepository = PreferencesRepository(this)
        instance = WeakReference(this)
        Logger.log("ReelsAccessibilityService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        Logger.log("ReelsAccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance?.get() == this) {
            instance = null
        }
        Logger.log("ReelsAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrEmpty() && pkg != "android" && pkg != "com.android.systemui") {
                    if (currentPackageName != pkg) {
                        currentPackageName = pkg
                        Logger.log("Foreground App: $currentPackageName")
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Logger.log("Accessibility Service interrupted", isError = true)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        Logger.log("Raw Key Received: code=${event.keyCode}, action=${event.action}, pkg=$currentPackageName")

        if (!prefsRepository.isServiceEnabled) {
            return super.onKeyEvent(event)
        }

        // Only handle key down events to avoid double triggering
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }

        val isTargetAppActive = prefsRepository.isPackageEnabled(currentPackageName)

        if (!isTargetAppActive) {
            return super.onKeyEvent(event)
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_NAVIGATE_NEXT,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                Logger.log("Intercepted NEXT KeyCode ${event.keyCode} -> Swiping Up")
                swipeUp(force = true)
                true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (prefsRepository.isPrevButtonDoubleTap) {
                    Logger.log("Intercepted PREVIOUS KeyCode ${event.keyCode} -> Double Tapping (Like)")
                    doubleTap(force = true)
                } else {
                    Logger.log("Intercepted PREVIOUS KeyCode ${event.keyCode} -> Swiping Down")
                    swipeDown(force = true)
                }
                true
            }

            else -> super.onKeyEvent(event)
        }
    }

    fun swipeUp(force: Boolean = false) {
        if (!force && !prefsRepository.isPackageEnabled(currentPackageName)) {
            Logger.log("swipeUp ignored: package $currentPackageName not enabled")
            return
        }

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val duration = prefsRepository.swipeDurationMs

        // If Shizuku is available and authorized, execute via Shizuku ADB command
        if (ShizukuManager.isGranted) {
            ShizukuManager.swipeUp(width, height, duration)
        }

        val startX = width / 2f
        val startY = height * 0.75f
        val endX = width / 2f
        val endY = height * 0.25f

        dispatchSwipeGesture(startX, startY, endX, endY, duration)
    }

    fun swipeDown(force: Boolean = false) {
        if (!force && !prefsRepository.isPackageEnabled(currentPackageName)) {
            Logger.log("swipeDown ignored: package $currentPackageName not enabled")
            return
        }

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val duration = prefsRepository.swipeDurationMs

        // If Shizuku is available and authorized, execute via Shizuku ADB command
        if (ShizukuManager.isGranted) {
            ShizukuManager.swipeDown(width, height, duration)
        }

        val startX = width / 2f
        val startY = height * 0.25f
        val endX = width / 2f
        val endY = height * 0.75f

        dispatchSwipeGesture(startX, startY, endX, endY, duration)
    }

    fun doubleTap(force: Boolean = false) {
        if (!force && !prefsRepository.isPackageEnabled(currentPackageName)) {
            Logger.log("doubleTap ignored: package $currentPackageName not enabled")
            return
        }

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        // If Shizuku is available and authorized, execute via Shizuku ADB command
        if (ShizukuManager.isGranted) {
            ShizukuManager.doubleTap(width, height)
        }

        val centerX = width / 2f
        val centerY = height / 2f

        val tapPath = Path().apply {
            moveTo(centerX, centerY)
        }

        val tap1 = GestureDescription.StrokeDescription(tapPath, 0L, 50L)
        val tap2 = GestureDescription.StrokeDescription(tapPath, 100L, 50L)

        val gesture = GestureDescription.Builder()
            .addStroke(tap1)
            .addStroke(tap2)
            .build()

        val success = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Logger.log("Accessibility Double Tap (Like) completed successfully")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Logger.log("Accessibility Double Tap cancelled", isError = true)
            }
        }, null)

        Logger.log("Dispatched Accessibility Double Tap: success=$success")
    }

    private fun dispatchSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val success = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Logger.log("Accessibility Swipe completed successfully (${durationMs}ms)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Logger.log("Accessibility Swipe cancelled", isError = true)
            }
        }, null)

        Logger.log("Dispatched Accessibility Swipe (${durationMs}ms): success=$success")
    }

    companion object {
        @Volatile
        private var instance: WeakReference<ReelsAccessibilityService>? = null

        val isServiceRunning: Boolean
            get() = instance?.get() != null

        fun getInstance(): ReelsAccessibilityService? {
            return instance?.get()
        }
    }
}
