package com.jimandreas.blinken.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

private const val ACTIVE_REMINDERS_PREFS = "blinken_active_reminders"
private const val KEY_PREFIX = "keys_"

const val REMINDER_INTERVAL_MS = 60_000L
const val EXTRA_REMINDER_PACKAGE_NAME = "reminder_package_name"

// Tracks, per allowlisted package, which of its StatusBarNotification keys are still active
// (unread/undismissed) - backed by SharedPreferences so it survives process death, same as the
// existing eco-mode debounce timestamps. Used to decide when to stop the repeat-until-dismissed
// reminder: once a package's set is empty, its notification(s) have all been cleared.

private fun prefs(context: Context) =
    context.getSharedPreferences(ACTIVE_REMINDERS_PREFS, Context.MODE_PRIVATE)

private fun prefsKey(packageName: String) = KEY_PREFIX + packageName

fun hasActiveNotifications(context: Context, packageName: String): Boolean =
    !prefs(context).getStringSet(prefsKey(packageName), emptySet()).isNullOrEmpty()

fun addActiveNotificationKey(context: Context, packageName: String, notificationKey: String) {
    val current = prefs(context).getStringSet(prefsKey(packageName), emptySet()) ?: emptySet()
    prefs(context).edit().putStringSet(prefsKey(packageName), current + notificationKey).apply()
}

// Returns true if the package's active set is now empty (nothing left to remind about).
fun removeActiveNotificationKey(context: Context, packageName: String, notificationKey: String): Boolean {
    val current = prefs(context).getStringSet(prefsKey(packageName), emptySet()) ?: emptySet()
    val updated = current - notificationKey
    val editor = prefs(context).edit()
    if (updated.isEmpty()) editor.remove(prefsKey(packageName)) else editor.putStringSet(prefsKey(packageName), updated)
    editor.apply()
    return updated.isEmpty()
}

fun replaceActiveNotificationKeys(context: Context, packageName: String, keys: Set<String>) {
    val editor = prefs(context).edit()
    if (keys.isEmpty()) editor.remove(prefsKey(packageName)) else editor.putStringSet(prefsKey(packageName), keys)
    editor.apply()
}

private fun reminderPendingIntent(context: Context, packageName: String): PendingIntent {
    val intent = Intent(context, FlashReminderReceiver::class.java)
        .putExtra(EXTRA_REMINDER_PACKAGE_NAME, packageName)
    return PendingIntent.getBroadcast(
        context,
        packageName.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

fun scheduleReminder(context: Context, packageName: String) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + REMINDER_INTERVAL_MS,
        reminderPendingIntent(context, packageName),
    )
}

fun cancelReminder(context: Context, packageName: String) {
    context.getSystemService(AlarmManager::class.java).cancel(reminderPendingIntent(context, packageName))
}
