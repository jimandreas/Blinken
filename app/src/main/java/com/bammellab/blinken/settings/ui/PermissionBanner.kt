package com.bammellab.blinken.settings.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    postNotificationsGranted: Boolean,
    fullScreenIntentAllowed: Boolean,
    batteryUnrestricted: Boolean,
    onPostNotificationsResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onPostNotificationsResult,
    )

    if (notificationAccessGranted && batteryUnrestricted && postNotificationsGranted && fullScreenIntentAllowed) return

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
            if (!postNotificationsGranted) {
                Text("Blinken needs notification permission to trigger the lockscreen flash.")
                Button(onClick = { postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Grant notification permission")
                }
            }
            if (!fullScreenIntentAllowed) {
                Text("Blinken needs the full-screen notification permission to show the flash over the lock screen.")
                Button(onClick = { context.startActivity(fullScreenIntentSettingsIntent(context)) }) {
                    Text("Grant full-screen notifications")
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

private fun fullScreenIntentSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = "package:${context.packageName}".toUri()
    }
