package com.jimandreas.blinken.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppEntry(
    val packageName: String,
    val label: String,
    val colorArgb: Int,
    val durationMs: Long,
    val enabled: Boolean = true,
)

@Serializable
data class AppSettings(
    val schemaVersion: Int = 1,
    val entries: List<AppEntry> = emptyList(),
    val globalDefaultColorArgb: Int = DEFAULT_COLOR_ARGB,
    val globalDefaultDurationMs: Long = DEFAULT_DURATION_MS,
    val ecoModeEnabled: Boolean = false,
) {
    companion object {
        const val DEFAULT_COLOR_ARGB: Int = 0xFF4DD8FF.toInt()
        const val DEFAULT_DURATION_MS: Long = 3000L
        val DEFAULT = AppSettings()
    }
}
