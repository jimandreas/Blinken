package com.jimandreas.blinken.settings

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

class InstalledAppsProvider(private val context: Context) {

    fun listInstalledApps(excluding: Set<String> = emptySet()): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName !in excluding }
            .filter { app ->
                pm.getLaunchIntentForPackage(app.packageName) != null &&
                    app.packageName != context.packageName
            }
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    label = app.loadLabel(pm).toString(),
                    icon = app.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
