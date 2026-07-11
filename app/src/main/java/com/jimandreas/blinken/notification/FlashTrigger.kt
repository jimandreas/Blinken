package com.jimandreas.blinken.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jimandreas.blinken.R
import com.jimandreas.blinken.flash.EXTRA_COLOR_ARGB
import com.jimandreas.blinken.flash.EXTRA_DURATION_MS
import com.jimandreas.blinken.flash.EXTRA_PACKAGE_NAME
import com.jimandreas.blinken.flash.FLASH_NOTIFICATION_ID
import com.jimandreas.blinken.flash.FlashActivity

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
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setAutoCancel(true)
        .setContentIntent(fullScreenPendingIntent)
        .build()
    NotificationManagerCompat.from(context).notify(FLASH_NOTIFICATION_ID, notification)
    Log.d(TAG, "posted continuous full-screen-intent notification")
}
