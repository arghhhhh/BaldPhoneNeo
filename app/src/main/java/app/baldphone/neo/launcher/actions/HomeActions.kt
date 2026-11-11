// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.launcher.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

import app.baldphone.neo.R
import app.baldphone.neo.services.DeviceLock
import app.baldphone.neo.services.DeviceLockResult
import app.baldphone.neo.utils.launchAssistant

/*
 * Invisible "action" activities for built-in home screen functions that aren't real apps
 * (Assistant, Lock Screen). They are registered in PredefinedApps as choose-only entries, so
 * they can be picked for any home screen button in the home screen editor but never appear
 * in the normal Apps list. Each one performs its action and finishes immediately.
 *
 * Manifest: translucent, no history, excluded from recents, and their own taskAffinity -
 * custom home buttons are launched with FLAG_ACTIVITY_CLEAR_TASK, which must not clear the
 * launcher's own task.
 */

/** Opens the assistant (Gemini Live when possible) - same as the default Assistant button. */
class AssistantActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchAssistant()
        finish()
    }
}

/** Locks the screen - same as the default Lock Screen button. */
class LockScreenActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            finish()
            return
        }
        DeviceLock.requestLock(this) { result ->
            if (result != DeviceLockResult.SUCCESS) {
                // The accessibility service is off or misbehaving: send the user to turn it on.
                Toast.makeText(this, R.string.accessibility_permission_check_message, Toast.LENGTH_LONG).show()
                try {
                    startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: ActivityNotFoundException) {
                    // Nothing else to do
                }
            }
            finish()
        }
    }
}
