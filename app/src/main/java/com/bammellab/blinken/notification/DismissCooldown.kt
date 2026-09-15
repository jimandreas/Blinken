package com.bammellab.blinken.notification

import android.content.Context

// FlashActivity shows over the keyguard (setShowWhenLocked) and swallows input, so until this
// existed there was no way for the user to get past it: the display's only exits were "every
// notification cleared", "charging stopped", "a call went live", or a locked->unlocked transition
// - and on the continuous path that last one is unreachable, because unlocking requires touching
// the keyguard the snake is sitting on top of. Pressing power made it worse rather than better:
// the service's ACTION_SCREEN_OFF receiver re-posts the trigger within ~200ms, so the snake was
// back over the keyguard before the user could swipe. User-reported lockout, 2026-09-15: a locked,
// charging phone with three unread notifications (an SMS plus a missed call, which by definition
// can't be read without unlocking) could not be unlocked at all.
//
// The fix is a user-dismissal gesture (see FlashActivity.dismissForUser) plus this cooldown. The
// gesture alone is not enough - without a cooldown the very next screen-off or nudge relaunches
// the display, so a tap would buy only milliseconds, which is the same livelock with extra steps.
//
// Persisted in SharedPreferences rather than held in memory for the same reason the eco-mode
// debounce timestamps are: the dismissal has to outlive the process. FlashActivity is finishing
// at the moment it records this, and the thing that must honor it (FlashTrigger, via the listener
// service or an alarm receiver) may well be in a different process lifetime by then.
private const val PREFS_NAME = "blinken_dismiss_cooldown"
private const val KEY_DISMISSED_AT_MS = "dismissed_at_ms"

// Long enough to unlock at a human pace (find the phone, swipe, type a PIN or wait for face
// unlock) without the display fighting back; short enough that a dismissal made by accident
// doesn't silently disable the notification LED for a meaningful stretch. Once the user actually
// unlocks, isDeviceInActiveUnlockedUse takes over as the reason for staying hidden anyway, so in
// the common case this window is doing its job for only a couple of seconds.
const val DISMISS_COOLDOWN_MS = 30_000L

// Pure so the policy is unit-testable, matching isCallAudioMode / NotificationFilter.resolveBlink.
//
// Note the deliberate `in 0 until` rather than a bare `elapsed < DISMISS_COOLDOWN_MS`: a stored
// timestamp in the *future* (elapsed negative) must not count as "within the cooldown", or a clock
// that has jumped backwards would suppress the display indefinitely instead of for 30 seconds.
// That is not a hypothetical on this project - on-device logs show elapsedSinceLastFlash values
// like -443213197 ms from the eco-debounce store, i.e. timestamps genuinely landing in the future.
fun isWithinDismissCooldown(dismissedAtMs: Long?, nowMs: Long): Boolean {
    if (dismissedAtMs == null) return false
    return (nowMs - dismissedAtMs) in 0 until DISMISS_COOLDOWN_MS
}

private fun dismissPrefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun recordUserDismissal(context: Context, nowMs: Long = System.currentTimeMillis()) {
    dismissPrefs(context).edit().putLong(KEY_DISMISSED_AT_MS, nowMs).apply()
}

fun isDismissCooldownActive(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
    val prefs = dismissPrefs(context)
    val dismissedAtMs = if (prefs.contains(KEY_DISMISSED_AT_MS)) {
        prefs.getLong(KEY_DISMISSED_AT_MS, 0L)
    } else {
        null
    }
    return isWithinDismissCooldown(dismissedAtMs, nowMs)
}
