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
}
