package com.bammellab.blinken.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round trips through json`() {
        val settings = AppSettings(
            entries = listOf(
                AppEntry(
                    packageName = "com.example.mail",
                    label = "Mail",
                    colorArgb = 0xFF00FF00.toInt(),
                    durationMs = 2500L,
                    enabled = true,
                )
            ),
            globalDefaultColorArgb = 0xFF4DD8FF.toInt(),
            globalDefaultDurationMs = 3000L,
            ecoModeEnabled = true,
        )

        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)

        assertEquals(settings, decoded)
    }

    @Test
    fun `defaults are used when nothing stored`() {
        assertEquals(emptyList<AppEntry>(), AppSettings.DEFAULT.entries)
        assertEquals(false, AppSettings.DEFAULT.ecoModeEnabled)
    }
}
