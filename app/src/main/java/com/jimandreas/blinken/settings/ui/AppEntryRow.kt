package com.jimandreas.blinken.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.jimandreas.blinken.settings.AppEntry
import com.jimandreas.blinken.settings.COLOR_PRESETS
import com.jimandreas.blinken.settings.DURATION_PRESETS_MS
import com.jimandreas.blinken.settings.InstalledApp

@Composable
fun AppEntryRow(
    entry: AppEntry,
    icon: InstalledApp?,
    onColorSelected: (Int) -> Unit,
    onDurationSelected: (Long) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var colorMenuExpanded by remember { mutableStateOf(false) }
    var durationMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            val bitmap = remember(icon.packageName) { icon.icon.toBitmap().asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = entry.label,
                modifier = Modifier.size(40.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = entry.label,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(entry.colorArgb)),
                )
                TextButton(onClick = { colorMenuExpanded = true }) { Text("Color") }
                DropdownMenu(expanded = colorMenuExpanded, onDismissRequest = { colorMenuExpanded = false }) {
                    COLOR_PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                onColorSelected(preset.argb)
                                colorMenuExpanded = false
                            },
                        )
                    }
                }

                TextButton(onClick = { durationMenuExpanded = true }) { Text("${entry.durationMs / 1000}s") }
                DropdownMenu(expanded = durationMenuExpanded, onDismissRequest = { durationMenuExpanded = false }) {
                    DURATION_PRESETS_MS.forEach { ms ->
                        DropdownMenuItem(
                            text = { Text("${ms / 1000}s") },
                            onClick = {
                                onDurationSelected(ms)
                                durationMenuExpanded = false
                            },
                        )
                    }
                }

                Switch(checked = entry.enabled, onCheckedChange = onEnabledChanged)

                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}
