package com.jimandreas.blinken.flash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

private val BADGE_SIZE = 120.dp
private val ICON_SIZE = 72.dp

@Composable
fun FlashScreen(color: Color, packageName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        packageName?.let {
            runCatching { context.packageManager.getApplicationIcon(it).toBitmap().asImageBitmap() }.getOrNull()
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(BADGE_SIZE)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            iconBitmap?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            }
        }
    }
}
