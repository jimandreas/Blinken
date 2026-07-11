package com.bammellab.blinken.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

const val REMINDER_INTERVAL_MS = 60_000L
const val EXTRA_REMINDER_PACKAGE_NAME = "reminder_package_name"

// Facade over ActiveNotificationsStore preserving the original per-package API: these four
// functions used to own SharedPreferences-backed bookkeeping directly, but that aggregate state
// (now needed cross-package by the continuous "snake" display, which must enumerate every active
// notification at once) moved to ActiveNotificationsStore. Kept here, signature-for-signature, so
// BlinkenListenerService and FlashReminderReceiver need no call-site changes. The AlarmManager
// machinery below (scheduleReminder/cancelReminder) is unrelated bookkeeping and untouched.

fun hasActiveNotifications(context: Context, packageName: String): Boolean =
    ActiveNotificationsStore.activeForPackage(context, packageName).isNotEmpty()

fun addActiveNotificationKey(context: Context, packageName: String, notificationKey: String) {
    ActiveNotificationsStore.add(context, packageName, notificationKey)
}

// Returns true if the package's active set is now empty (nothing left to remind about).
fun removeActiveNotificationKey(context: Context, packageName: String, notificationKey: String): Boolean {
    ActiveNotificationsStore.remove(context, packageName, notificationKey)
    return ActiveNotificationsStore.activeForPackage(context, packageName).isEmpty()
}

fun replaceActiveNotificationKeys(context: Context, packageName: String, keys: Set<String>) {
    val now = System.currentTimeMillis()
    val existingByKey = ActiveNotificationsStore.activeForPackage(context, packageName).associateBy { it.key }
    val newForPackage = keys.map { key -> existingByKey[key] ?: ActiveNotification(packageName, key, now) }
    val otherPackages = ActiveNotificationsStore.active.value.filterNot { it.packageName == packageName }
    ActiveNotificationsStore.reconcileAll(context, otherPackages + newForPackage)
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
