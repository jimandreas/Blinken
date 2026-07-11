package com.bammellab.blinken.notification

import com.bammellab.blinken.settings.AppSettings

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

data class VisibleSegment(val packageName: String, val key: String, val colorArgb: Int)

// Deliberately NOT entry.durationMs: that field means "brief flash length" for the legacy
// unplugged path (defaults to a few seconds) and reusing it here would make a freshly-added
// entry's continuous-mode segment vanish within seconds of appearing. The safety cap's job is
// only to bound a pathologically long-unread notification, not to mirror flash duration, so it's
// a fixed constant rather than per-app configurable.
private const val CONTINUOUS_SAFETY_CAP_MS = 30 * 60 * 1000L

/**
 * Pure filter for the continuous "snake" display: which of the currently-[active] notifications
 * should still render as a segment, given [settings] (allowlist + enabled/colorArgb) and [nowMs].
 * A notification older than CONTINUOUS_SAFETY_CAP_MS is dropped as a safety cap against an
 * indefinitely-growing/stuck snake - this is a display-only filter; it never mutates
 * ActiveNotificationsStore or cancels anything.
 */
fun resolveVisibleSegments(
    active: List<ActiveNotification>,
    settings: AppSettings,
    nowMs: Long,
): List<VisibleSegment> = active.mapNotNull { notification ->
    val entry = settings.entries.firstOrNull { it.packageName == notification.packageName && it.enabled }
        ?: return@mapNotNull null
    if (nowMs - notification.postedAtMs >= CONTINUOUS_SAFETY_CAP_MS) return@mapNotNull null
    VisibleSegment(notification.packageName, notification.key, entry.colorArgb)
}
