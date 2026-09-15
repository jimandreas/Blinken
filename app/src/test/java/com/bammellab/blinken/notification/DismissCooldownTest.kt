package com.bammellab.blinken.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissCooldownTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `no recorded dismissal means no cooldown`() {
        assertFalse(isWithinDismissCooldown(null, now))
    }

    @Test
    fun `a dismissal just now is within the cooldown`() {
        assertTrue(isWithinDismissCooldown(now, now))
        assertTrue(isWithinDismissCooldown(now - 1, now))
    }

    @Test
    fun `a dismissal just inside the window is still within the cooldown`() {
        assertTrue(isWithinDismissCooldown(now - (DISMISS_COOLDOWN_MS - 1), now))
    }

    @Test
    fun `the cooldown expires exactly at the window length`() {
        assertFalse(isWithinDismissCooldown(now - DISMISS_COOLDOWN_MS, now))
        assertFalse(isWithinDismissCooldown(now - (DISMISS_COOLDOWN_MS + 1), now))
    }

    @Test
    fun `an old dismissal does not suppress the display`() {
        assertFalse(isWithinDismissCooldown(now - 86_400_000L, now))
    }

    // A timestamp in the future means the clock moved backwards (or a stale value survived from a
    // different device time) - real on this project, where on-device logs have shown the eco-mode
    // debounce store yielding elapsed values around -443213197 ms. Treating that as "within the
    // cooldown" would suppress the display until the clock caught up, which for a large skew means
    // effectively forever: the exact failure mode this whole file exists to prevent.
    @Test
    fun `a future dismissal timestamp does not suppress the display`() {
        assertFalse(isWithinDismissCooldown(now + 1, now))
        assertFalse(isWithinDismissCooldown(now + 443_213_197L, now))
    }
}
