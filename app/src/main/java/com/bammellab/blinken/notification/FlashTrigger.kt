package com.bammellab.blinken.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bammellab.blinken.R
import com.bammellab.blinken.flash.EXTRA_COLOR_ARGB
import com.bammellab.blinken.flash.EXTRA_DURATION_MS
import com.bammellab.blinken.flash.EXTRA_PACKAGE_NAME
import com.bammellab.blinken.flash.FLASH_NOTIFICATION_ID
import com.bammellab.blinken.flash.FlashActivity

private const val TAG = "FlashTrigger"

// Android blocks a NotificationListenerService from calling startActivity() directly - it's a
// background-activity-start (BAL) call site with no exemption, confirmed via ActivityTaskManager
// logging "allowBackgroundActivityStart: false" even when startActivity() itself reports success.
// The OS-sanctioned way to show full-screen UI triggered by a notification is a full-screen-intent
// notification, which is exempt from BAL and (by design) only auto-launches while the device is
// locked - exactly the behavior Blinken wants. Shared between BlinkenListenerService (first
// arrival) and FlashReminderReceiver (repeat-until-dismissed reminders), so both trigger the
// same way.
private const val FLASH_TRIGGER_CHANNEL_ID = "blinken_flash_trigger"

// The trigger notification's only job is to fire its full-screen intent, which the OS does
// immediately at post time (or not at all) - it never needs to outlive that moment. Without a
// timeout, a copy that didn't get consumed/cancelled (e.g. posted while FlashActivity was
// already in the foreground, so no onNewIntent -> no cancel) lingers invisibly in the shade
// (VISIBILITY_SECRET hides it on the lockscreen) and then pops up as a "Blinken - Unread
// notifications" heads-up the moment the user unlocks, burying the real notification they
// actually want to read - observed on-device. setTimeoutAfter is API 26+; below that the
// existing cancel paths (FlashActivity.applyIntent, unlock detection) still apply.
private const val TRIGGER_NOTIFICATION_TIMEOUT_MS = 3_000L

// Single choke point for "should the display be showing at all right now", shared by both
// trigger paths (first arrival, FlashReminderReceiver, ContinuousNudgeReceiver, the service's
// screen-off/power receiver) and re-used by BlinkenListenerService to decide whether a
// suppressed flash should still consume its eco-mode debounce budget (it must not). Returns a
// human-readable reason for logging, or null when the display may show.
fun flashSuppressedReason(context: Context): String? = when {
    isDeviceInActiveUnlockedUse(context) -> "device unlocked and in use"
    isCallRingingOrActive(context) -> "phone call ringing or in progress"
    else -> null
}

private fun ensureFlashTriggerChannel(context: Context) {
    val channel = NotificationChannelCompat.Builder(FLASH_TRIGGER_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName("Flash trigger")
        .setDescription("Used internally to trigger the lockscreen flash; produces no sound or vibration of its own.")
        .setSound(null, null)
        .setVibrationEnabled(false)
        .setShowBadge(false)
        .build()
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}

fun postFlashTriggerNotification(context: Context, packageName: String, spec: BlinkSpec, label: String) {
    if (!isPostNotificationsGranted(context)) {
        Log.w(TAG, "cannot trigger flash: POST_NOTIFICATIONS not granted")
        return
    }
    flashSuppressedReason(context)?.let { reason ->
        Log.d(TAG, "$reason, suppressing flash trigger for $packageName")
        return
    }

    ensureFlashTriggerChannel(context)

    val intent = Intent(context, FlashActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(EXTRA_COLOR_ARGB, spec.colorArgb)
        putExtra(EXTRA_DURATION_MS, spec.durationMs)
        putExtra(EXTRA_PACKAGE_NAME, packageName)
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, FLASH_TRIGGER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(label)
        .setContentText("Blinken flash triggered")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_EVENT)
        // Internal trigger only - keep it out of the lockscreen notification list (full-screen
        // intent auto-launch is not affected by lockscreen visibility).
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setTimeoutAfter(TRIGGER_NOTIFICATION_TIMEOUT_MS)
        .setOnlyAlertOnce(true)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setAutoCancel(true)
        .setContentIntent(fullScreenPendingIntent)
        .build()
    NotificationManagerCompat.from(context).notify(FLASH_NOTIFICATION_ID, notification)
    Log.d(TAG, "posted full-screen-intent notification for $label ($packageName)")
}

// Continuous-mode counterpart to postFlashTriggerNotification: carries no per-notification
// extras, since FlashActivity's continuous branch (SnakeScreen) re-derives everything it needs
// live from BatteryManager and ActiveNotificationsStore rather than from intent extras. Used both
// for the first trigger while charging and by ContinuousNudgeReceiver's re-foreground nudges.
fun postContinuousTriggerNotification(context: Context) {
    if (!isPostNotificationsGranted(context)) {
        Log.w(TAG, "cannot trigger continuous flash: POST_NOTIFICATIONS not granted")
        return
    }
    flashSuppressedReason(context)?.let { reason ->
        Log.d(TAG, "$reason, suppressing continuous trigger")
        return
    }

    ensureFlashTriggerChannel(context)

    val intent = Intent(context, FlashActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
        context,
        1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, FLASH_TRIGGER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("Blinken")
        .setContentText("Unread notifications")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_EVENT)
        // Internal trigger only - keep it out of the lockscreen notification list (full-screen
        // intent auto-launch is not affected by lockscreen visibility).
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setTimeoutAfter(TRIGGER_NOTIFICATION_TIMEOUT_MS)
        .setOnlyAlertOnce(true)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setAutoCancel(true)
        .setContentIntent(fullScreenPendingIntent)
        .build()
    NotificationManagerCompat.from(context).notify(FLASH_NOTIFICATION_ID, notification)
    Log.d(TAG, "posted continuous full-screen-intent notification")
}
