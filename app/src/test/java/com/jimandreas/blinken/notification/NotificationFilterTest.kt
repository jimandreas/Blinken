package com.jimandreas.blinken.notification

import com.jimandreas.blinken.settings.AppEntry
import com.jimandreas.blinken.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFilterTest {

    private val entry = AppEntry(
        packageName = "com.example.mail",
        label = "Mail",
        colorArgb = 0xFF00FF00.toInt(),
        durationMs = 2000L,
        enabled = true,
    )

    @Test
    fun `returns null for package not in allowlist`() {
        val settings = AppSettings(entries = listOf(entry))
        assertNull(resolveBlink("com.other.app", settings, elapsedSinceLastFlashMs = null))
    }

    @Test
    fun `returns null for disabled entry`() {
        val settings = AppSettings(entries = listOf(entry.copy(enabled = false)))
        assertNull(resolveBlink(entry.packageName, settings, elapsedSinceLastFlashMs = null))
    }

    @Test
    fun `returns spec for matching enabled entry`() {
        val settings = AppSettings(entries = listOf(entry))
        val spec = resolveBlink(entry.packageName, settings, elapsedSinceLastFlashMs = null)
        assertEquals(BlinkSpec(entry.colorArgb, entry.durationMs), spec)
    }

    @Test
    fun `eco mode suppresses flash within debounce window`() {
        val settings = AppSettings(entries = listOf(entry), ecoModeEnabled = true)
        assertNull(resolveBlink(entry.packageName, settings, elapsedSinceLastFlashMs = 1000L))
    }

    @Test
    fun `eco mode allows flash after debounce window`() {
        val settings = AppSettings(entries = listOf(entry), ecoModeEnabled = true)
        val spec = resolveBlink(entry.packageName, settings, elapsedSinceLastFlashMs = 10_000L)
        assertEquals(BlinkSpec(entry.colorArgb, entry.durationMs), spec)
    }

    @Test
    fun `non-eco mode ignores debounce window`() {
        val settings = AppSettings(entries = listOf(entry), ecoModeEnabled = false)
        val spec = resolveBlink(entry.packageName, settings, elapsedSinceLastFlashMs = 0L)
        assertEquals(BlinkSpec(entry.colorArgb, entry.durationMs), spec)
    }

    @Test
    fun `resolveVisibleSegments excludes a notification from a package not in the allowlist`() {
        val settings = AppSettings(entries = listOf(entry))
        val active = listOf(ActiveNotification("com.other.app", "key1", postedAtMs = 0L))
        assertEquals(emptyList<VisibleSegment>(), resolveVisibleSegments(active, settings, nowMs = 0L))
    }

    @Test
    fun `resolveVisibleSegments excludes a notification from a disabled entry`() {
        val settings = AppSettings(entries = listOf(entry.copy(enabled = false)))
        val active = listOf(ActiveNotification(entry.packageName, "key1", postedAtMs = 0L))
        assertEquals(emptyList<VisibleSegment>(), resolveVisibleSegments(active, settings, nowMs = 0L))
    }

    // The safety cap (30 minutes) is a fixed constant independent of entry.durationMs (which is
    // 2000L here) - see NotificationFilter.CONTINUOUS_SAFETY_CAP_MS.
    private val thirtyMinutesMs = 30 * 60 * 1000L

    @Test
    fun `resolveVisibleSegments keeps a notification younger than the safety cap`() {
        val settings = AppSettings(entries = listOf(entry))
        val active = listOf(ActiveNotification(entry.packageName, "key1", postedAtMs = 1000L))
        val result = resolveVisibleSegments(active, settings, nowMs = 1000L + thirtyMinutesMs - 1L)
        assertEquals(listOf(VisibleSegment(entry.packageName, "key1", entry.colorArgb)), result)
    }

    @Test
    fun `resolveVisibleSegments drops a notification exactly at the safety cap boundary`() {
        val settings = AppSettings(entries = listOf(entry))
        val active = listOf(ActiveNotification(entry.packageName, "key1", postedAtMs = 1000L))
        val result = resolveVisibleSegments(active, settings, nowMs = 1000L + thirtyMinutesMs)
        assertEquals(emptyList<VisibleSegment>(), result)
    }

    @Test
    fun `resolveVisibleSegments preserves input ordering`() {
        val other = entry.copy(packageName = "com.example.chat", durationMs = 5000L)
        val settings = AppSettings(entries = listOf(entry, other))
        val active = listOf(
            ActiveNotification(other.packageName, "key2", postedAtMs = 0L),
            ActiveNotification(entry.packageName, "key1", postedAtMs = 500L),
        )
        val result = resolveVisibleSegments(active, settings, nowMs = 500L)
        assertEquals(listOf("key2", "key1"), result.map { it.key })
    }
}
