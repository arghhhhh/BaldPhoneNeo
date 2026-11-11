// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.utils

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import java.lang.ref.WeakReference

import app.baldphone.neo.services.DeviceLock

/**
 * Opens Gemini straight into Gemini Live (hands-free voice chat), so after pressing the
 * Assistant button the user can simply start talking.
 *
 * Google publishes no intent or launcher shortcut for Gemini Live, so this opens the Gemini
 * app and then lets BaldPhone's accessibility service ([DeviceLock.LockService]) press the
 * "Gemini Live" button. The service only sees the Google app (see
 * res/xml-v24/accessibility_service_config.xml) and this only acts for [ARM_WINDOW_MS] after
 * the Assistant button was pressed.
 *
 * Without the accessibility service enabled, this still opens Gemini; the user then taps
 * the Live button themselves.
 */
object GeminiLive {
    private const val TAG = "GeminiLive"

    /** The Gemini app (a thin shell that opens Gemini inside the Google app). */
    const val GEMINI_PACKAGE = "com.google.android.apps.bard"

    /** Package whose windows host the Gemini UI. */
    private const val HOST_PACKAGE = "com.google.android.googlequicksearchbox"

    /** Text of the Live button's accessibility label, e.g. "Open Gemini Live". Product name, not translated. */
    private const val LIVE_LABEL = "Gemini Live"

    /** How long after pressing Assistant we may press the Live button. */
    private const val ARM_WINDOW_MS = 8_000L

    /** How often to look for the Live button while armed. */
    private const val POLL_INTERVAL_MS = 300L

    /** Set by the accessibility service while it is connected. */
    @Volatile
    var service: WeakReference<AccessibilityService>? = null

    @Volatile
    private var armedUntil = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val poll =
        object : Runnable {
            override fun run() {
                if (!isArmed()) return
                val svc = service?.get()
                if (svc == null) Log.d(TAG, "poll: accessibility service not connected")
                if (svc != null && tryPressLive(svc)) return
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }

    /**
     * Opens Gemini and, when possible, presses its Live button automatically.
     * Returns false if the Gemini app isn't installed or couldn't be opened.
     */
    fun start(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(GEMINI_PACKAGE) ?: return false

        val canAutoStart =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && DeviceLock.isServiceEnabled(context)
        Log.i(TAG, "Opening Gemini (auto Live: $canAutoStart)")

        try {
            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Gemini app could not be opened", e)
            return false
        }

        handler.removeCallbacks(poll)
        if (canAutoStart) {
            armedUntil = SystemClock.elapsedRealtime() + ARM_WINDOW_MS
            handler.postDelayed(poll, POLL_INTERVAL_MS)
        } else {
            armedUntil = 0L
        }
        return true
    }

    /** Called by the accessibility service for window events from the Google app. */
    fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent?) {
        if (event != null && isArmed()) tryPressLive(service)
    }

    private fun isArmed() = SystemClock.elapsedRealtime() <= armedUntil

    /** Returns true once the Live button was pressed (and disarms). */
    private fun tryPressLive(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: run {
            Log.d(TAG, "no active window content")
            return false
        }
        if (root.packageName?.toString() != HOST_PACKAGE) {
            Log.d(TAG, "active window is ${root.packageName}")
            return false
        }

        val button = findLiveButton(root) ?: run {
            Log.d(TAG, "Live button not found yet")
            return false
        }

        val clickable = generateSequence(button) { it.parent }.firstOrNull { it.isClickable } ?: return false
        if (!clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return false

        armedUntil = 0L
        handler.removeCallbacks(poll)
        Log.i(TAG, "Pressed '${button.contentDescription ?: button.text}'")
        return true
    }

    /**
     * Depth-first search for the node labelled "... Gemini Live" (content description or text).
     * Done by hand because findAccessibilityNodeInfosByText() doesn't match content
     * descriptions in Compose UIs such as Gemini's.
     */
    private fun findLiveButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (stack.isNotEmpty() && visited++ < MAX_NODES) {
            val node = stack.removeLast()
            val label = (node.contentDescription ?: node.text)?.toString().orEmpty()
            // Skip e.g. "Share screen with Live"
            if (label.contains(LIVE_LABEL, ignoreCase = true) && !label.contains("share", ignoreCase = true)) {
                return node
            }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
        }
        return null
    }

    /** Safety cap on how many nodes we inspect per check. */
    private const val MAX_NODES = 2_000
}
