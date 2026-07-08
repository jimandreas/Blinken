package com.jimandreas.blinken.flash

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.jimandreas.blinken.settings.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val EXTRA_COLOR_ARGB = "color_argb"
const val EXTRA_DURATION_MS = "duration_ms"

class FlashActivity : ComponentActivity() {

    private val colorArgbState = mutableIntStateOf(AppSettings.DEFAULT_COLOR_ARGB)
    private var dismissJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            FlashScreen(color = Color(colorArgbState.intValue))
        }

        applyIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent) {
        colorArgbState.intValue = intent.getIntExtra(EXTRA_COLOR_ARGB, AppSettings.DEFAULT_COLOR_ARGB)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, AppSettings.DEFAULT_DURATION_MS)

        dismissJob?.cancel()
        dismissJob = lifecycleScope.launch {
            delay(durationMs)
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }
}
