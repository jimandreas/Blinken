package com.bammellab.blinken.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bammellab.blinken.settings.AllowlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "FlashReminderReceiver"

// Fired periodically (see ActiveReminders.scheduleReminder) while an allowlisted app's
// notification remains unread, to re-trigger the flash - a lighter-weight stand-in for a real
// LED's continuous blink. Self-terminating: each fire re-checks whether there's still something
// to remind about before triggering and rescheduling; if not, the chain simply stops here rather
// than needing an explicit cancellation from wherever the notification got cleared.
class FlashReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_REMINDER_PACKAGE_NAME) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handleReminder(context, packageName)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(context: Context, packageName: String) {
        if (!hasActiveNotifications(context, packageName)) {
            Log.d(TAG, "reminder for $packageName: no longer active, stopping")
            return
        }

        val settings = AllowlistRepository(context).settings.first()
        val entry = settings.entries.firstOrNull { it.packageName == packageName && it.enabled }
        if (entry == null) {
            Log.d(TAG, "reminder for $packageName: no longer an enabled allowlist entry, stopping")
            replaceActiveNotificationKeys(context, packageName, emptySet())
            return
        }

        Log.d(TAG, "reminder firing for $packageName")
        postFlashTriggerNotification(
            context,
            packageName,
            BlinkSpec(colorArgb = entry.colorArgb, durationMs = entry.durationMs),
            label = entry.label,
        )
        scheduleReminder(context, packageName)
    }
}
