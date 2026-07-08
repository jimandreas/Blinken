package com.jimandreas.blinken.settings.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable
fun PermissionBanner(
    notificationAccessGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val batteryUnrestricted = isIgnoringBatteryOptimizations(context)

    if (notificationAccessGranted && batteryUnrestricted) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!notificationAccessGranted) {
                Text("Blinken needs notification access to see when apps post notifications.")
                Button(onClick = { context.startActivity(notificationAccessSettingsIntent()) }) {
                    Text("Grant notification access")
                }
            }
            if (!batteryUnrestricted) {
                Text("Exempt Blinken from battery optimization so it keeps working while the screen is off.")
                Button(onClick = { context.startActivity(ignoreBatteryOptimizationsIntent(context)) }) {
                    Text("Ignore battery optimizations")
                }
            }
        }
    }
}

private fun notificationAccessSettingsIntent(): Intent =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

private fun ignoreBatteryOptimizationsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${context.packageName}".toUri()
    }

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
