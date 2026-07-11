package com.jimandreas.blinken.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jimandreas.blinken.settings.AllowlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ContinuousNudgeReceiver"

// Global counterpart to FlashReminderReceiver for continuous (charging-mode) sessions: fired
// periodically (see ActiveNotificationsStore.scheduleContinuousNudge) to re-foreground
// FlashActivity if it got backgrounded or killed while notifications remain unread and the device
// is still charging - SnakeScreen's own tick loop handles staying current once it's actually on
// screen, this only handles bringing it back. Self-terminating, same pattern as
// FlashReminderReceiver: stops rescheduling itself the moment either condition no longer holds.
class ContinuousNudgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handleNudge(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleNudge(context: Context) {
        if (!isDeviceCharging(context)) {
            Log.d(TAG, "no longer charging, stopping")
            ActiveNotificationsStore.cancelContinuousNudge(context)
            return
        }

        ActiveNotificationsStore.init(context)
        val settings = AllowlistRepository(context).settings.first()
        val visible = resolveVisibleSegments(
            ActiveNotificationsStore.active.value,
            settings,
            System.currentTimeMillis(),
        )
        if (visible.isEmpty()) {
            Log.d(TAG, "nothing left to show, stopping")
            ActiveNotificationsStore.cancelContinuousNudge(context)
            return
        }

        Log.d(TAG, "nudging - re-posting continuous trigger for ${visible.size} visible notification(s)")
        postContinuousTriggerNotification(context)
        ActiveNotificationsStore.scheduleContinuousNudge(context)
    }
}
