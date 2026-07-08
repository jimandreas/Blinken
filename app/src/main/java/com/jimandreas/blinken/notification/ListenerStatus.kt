package com.jimandreas.blinken.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat

fun isNotificationAccessGranted(context: Context): Boolean {
    val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
    return context.packageName in enabledPackages
}
