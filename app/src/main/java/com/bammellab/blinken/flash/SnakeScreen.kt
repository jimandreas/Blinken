package com.bammellab.blinken.flash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bammellab.blinken.notification.ActiveNotificationsStore
import com.bammellab.blinken.notification.isDeviceCharging
import com.bammellab.blinken.notification.resolveVisibleSegments
import com.bammellab.blinken.settings.AllowlistRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val GRID_CELL_SIZE = 56.dp
private val SEGMENT_SIZE = 44.dp
private val SEGMENT_ICON_SIZE = 26.dp
private const val TICK_INTERVAL_MS = 450L

// Continuous (charging-mode) counterpart to FlashScreen: instead of one static centered badge,
// every currently-visible notification (see resolveVisibleSegments) renders as a small badge and
// all of them move together as a snake, sweeping the whole grid (see SnakeEngine.stepSnake) so no
// pixel stays lit continuously. Has no self-dismiss timer - [onFinished] is invoked instead, once
// per the reasons a continuous session should end: nothing left to show, or power was unplugged.
@Composable
fun SnakeScreen(repository: AllowlistRepository, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    ActiveNotificationsStore.init(context)
    // Null - not AppSettings.DEFAULT's empty entry list - until the DataStore Flow's first real
    // emission lands. Treating "not loaded yet" as "empty allowlist" would make the emptiness
    // check below fire onFinished() and close this Activity before settings ever arrive - the
    // same trap BlinkenListenerService.cachedSettings is deliberately structured to avoid.
    val settings by repository.settings.collectAsState(initial = null)
    val currentSettings = settings ?: return
    val active by ActiveNotificationsStore.active.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val gridWidth = remember(maxWidth) { (maxWidth / GRID_CELL_SIZE).toInt().coerceAtLeast(2) }
        val gridHeight = remember(maxHeight) { (maxHeight / GRID_CELL_SIZE).toInt().coerceAtLeast(2) }
        val cellPx = with(density) { GRID_CELL_SIZE.roundToPx() }

        var snakeState by remember(gridWidth, gridHeight) {
            mutableStateOf(initialSnakeState(gridWidth, gridHeight))
        }

        LaunchedEffect(gridWidth, gridHeight) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(TICK_INTERVAL_MS)
                    if (!isDeviceCharging(context)) {
                        onFinished()
                        break
                    }
                    snakeState = stepSnake(snakeState, gridWidth, gridHeight)
                }
            }
        }

        // Deliberately not remember()'d: the safety cap depends on wall-clock elapsed time, not
        // just on active/settings changing, so this needs to re-evaluate every recomposition
        // (i.e. every tick, since snakeState changes each tick) for a capped-out notification to
        // actually drop out of the snake on its own.
        val visible = resolveVisibleSegments(active, currentSettings, System.currentTimeMillis())

        LaunchedEffect(visible) {
            if (visible.isEmpty()) onFinished()
        }

        mapSegments(snakeState, visible).forEach { seg ->
            key(seg.segment.key) {
                val icon = remember(seg.segment.packageName) {
                    runCatching {
                        context.packageManager.getApplicationIcon(seg.segment.packageName).toBitmap().asImageBitmap()
                    }.getOrNull()
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(seg.position.x * cellPx, seg.position.y * cellPx) }
                        .size(SEGMENT_SIZE)
                        .background(Color(seg.segment.colorArgb), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    icon?.let {
                        Image(bitmap = it, contentDescription = null, modifier = Modifier.size(SEGMENT_ICON_SIZE))
                    }
                }
            }
        }
    }
}
