package app.baldphone.neo.features.notifications.data

import android.app.Notification
import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log

/**
 * Logic for classifying and filtering [StatusBarNotification] objects.
 */
object NotificationClassifier {
    private val KNOWN_DIALERS =
        setOf(
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.miui.dialer",
            "com.android.incallui",
            "com.huawei.contacts",
            "com.android.contacts"
        )

    private val KNOWN_MISSED_CHANNELS =
        setOf(
            "phone_missed_call",
            "missed_call",
            "call_missed",
            "TelecomMissedCalls" // Xiaomi
        )

    /**
     * Checks if the given package name belongs to a known dialer application.
     */
    fun isKnownDialer(packageName: String): Boolean =
        KNOWN_DIALERS.any { packageName.startsWith(it) }

    /**
     * Determines whether a given notification represents a missed call.
     */
    fun isMissedCall(context: Context, sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val pkg = sbn.packageName

        if (n == null) return false
        val fromDialer = isFromDialer(context, pkg)

        val extras = n.extras
        val callType = extras.getInt("android.callType", -1)
        val isMissedType = callType == CallLog.Calls.MISSED_TYPE
        val missedCallCount = extras.getInt("android.telecom.extra.MISSED_CALL_COUNT", 0)
        val hasMissedCount = missedCallCount > 0

        val isMissedCategory =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                n.category == Notification.CATEGORY_MISSED_CALL
            } else {
                n.category == "missed_call"
            }

        // Notification Channels
        val isKnownMissedChannel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                KNOWN_MISSED_CHANNELS.contains(n.channelId)
            } else {
                false
            }

        val isMissedIndicator = isMissedType || hasMissedCount || isMissedCategory || isKnownMissedChannel
        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0

        val isMissed = fromDialer && isMissedIndicator && !isOngoing

        Log.d(
            "NotificationClassifier",
            "pkg=$pkg, " +
                "fromDialer=$fromDialer, " +
                "channelId=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId else "N/A"}, " +
                "ongoing=$isOngoing, " +
                "category=${n.category}, " +
                "callType=$callType, " +
                "missedCallCount=$missedCallCount, " +
                "isMissedIndicator=$isMissedIndicator, " +
                "isMissed=$isMissed"
        )

        return isMissed
    }

    /**
     * Determines whether a notification should be included in the repository.
     */
    /** Categories that describe something ongoing rather than something to look at. */
    private val NON_ALERT_CATEGORIES =
        setOf(
            Notification.CATEGORY_TRANSPORT, // media player controls (e.g. Poweramp, Spotify)
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_LOCATION_SHARING,
            Notification.CATEGORY_WORKOUT
        )

    /**
     * Whether a notification should put a red dot on its app's home screen button.
     * Only dismissable, alert-type notifications count: a media player's controls or a
     * running service would otherwise keep the dot on forever.
     */
    fun countsForBadge(sbn: StatusBarNotification): Boolean {
        if (!sbn.isClearable) return false
        val n = sbn.notification
        if ((n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0) return false
        return n.category !in NON_ALERT_CATEGORIES
    }

    fun shouldShow(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false

        return notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
    }

    /**
     * Checks if the given package name belongs to a dialer application.
     */
    private fun isFromDialer(context: Context, packageName: String): Boolean {
        if (isKnownDialer(packageName)) return true

        val defaultDialer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.defaultDialerPackage
            } else {
                null
            }

        return packageName == defaultDialer
    }
}
