package com.jimandreas.blinken.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun isNotificationAccessGranted(context: Context): Boolean {
    val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
    return context.packageName in enabledPackages
}

fun isPostNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

// The flash is triggered via a full-screen-intent notification (see BlinkenListenerService),
// since Android blocks a NotificationListenerService from starting an Activity directly. Below
// API 34 this permission is granted automatically; API 34+ lets the user revoke it separately
// from POST_NOTIFICATIONS.
fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    return notificationManager.canUseFullScreenIntent()
}
