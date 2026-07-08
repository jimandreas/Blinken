package com.jimandreas.blinken.settings

data class ColorPreset(val argb: Int, val label: String)

val COLOR_PRESETS = listOf(
    ColorPreset(0xFF4DD8FF.toInt(), "Cyan"),
    ColorPreset(0xFFFFB300.toInt(), "Amber"),
    ColorPreset(0xFF69F0AE.toInt(), "Green"),
    ColorPreset(0xFFFF5252.toInt(), "Red"),
    ColorPreset(0xFFE040FB.toInt(), "Magenta"),
    ColorPreset(0xFFFFFFFF.toInt(), "White"),
)

val DURATION_PRESETS_MS = listOf(1000L, 2000L, 3000L, 5000L, 8000L)
