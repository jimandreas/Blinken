package com.jimandreas.blinken.notification

import com.jimandreas.blinken.settings.AppSettings

data class BlinkSpec(val colorArgb: Int, val durationMs: Long)

private const val ECO_MODE_MIN_INTERVAL_MS = 5_000L

/**
 * Pure matching logic: resolves what (if anything) to flash for [packageName], given the
 * current [settings] and, for eco mode, how long it's been since the last flash for that
 * package ([elapsedSinceLastFlashMs], null if there hasn't been one yet).
 */
fun resolveBlink(
    packageName: String,
    settings: AppSettings,
    elapsedSinceLastFlashMs: Long?,
): BlinkSpec? {
    val entry = settings.entries.firstOrNull { it.packageName == packageName && it.enabled }
        ?: return null

    if (settings.ecoModeEnabled &&
        elapsedSinceLastFlashMs != null &&
        elapsedSinceLastFlashMs < ECO_MODE_MIN_INTERVAL_MS
    ) {
        return null
    }

    return BlinkSpec(colorArgb = entry.colorArgb, durationMs = entry.durationMs)
}
