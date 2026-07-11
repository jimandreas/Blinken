package com.jimandreas.blinken.flash

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.jimandreas.blinken.notification.isDeviceCharging
import com.jimandreas.blinken.settings.AllowlistRepository
import com.jimandreas.blinken.settings.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val EXTRA_COLOR_ARGB = "color_argb"
const val EXTRA_DURATION_MS = "duration_ms"
const val EXTRA_PACKAGE_NAME = "package_name"

// Shared with BlinkenListenerService, which posts the full-screen-intent notification that
// launches this activity; cancelled here so it doesn't linger in the shade once shown.
const val FLASH_NOTIFICATION_ID = 4242

private const val TAG = "FlashActivity"

class FlashActivity : ComponentActivity() {

    private val colorArgbState = mutableIntStateOf(AppSettings.DEFAULT_COLOR_ARGB)
    private val packageNameState = mutableStateOf<String?>(null)
    private val continuousModeState = mutableStateOf(false)
    private var dismissJob: Job? = null
    private val repository by lazy { AllowlistRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        showOverLockScreen()

        setContent {
            if (continuousModeState.value) {
                SnakeScreen(repository = repository, onFinished = { finish() })
            } else {
                FlashScreen(color = Color(colorArgbState.intValue), packageName = packageNameState.value)
            }
        }

        applyIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent")
        setIntent(intent)
        applyIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    private fun applyIntent(intent: Intent) {
        val continuous = isDeviceCharging(this)
        continuousModeState.value = continuous
        Log.d(TAG, "applyIntent: continuous=$continuous")

        NotificationManagerCompat.from(this).cancel(FLASH_NOTIFICATION_ID)

        if (continuous) {
            // No self-dismiss timer here - SnakeScreen calls finish() itself once nothing is
            // left to show or charging stops. FLAG_KEEP_SCREEN_ON is defense-in-depth beyond the
            // user's own "stay awake while charging" setting, since this mode's whole point is
            // staying on screen continuously.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            dismissJob?.cancel()
            dismissJob = null
        } else {
            // singleTask reuses this Activity instance across triggers - clear the flag a prior
            // continuous session may have set, or it would silently leak into this legacy flash's
            // screen-timeout behavior.
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            colorArgbState.intValue = intent.getIntExtra(EXTRA_COLOR_ARGB, AppSettings.DEFAULT_COLOR_ARGB)
            packageNameState.value = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, AppSettings.DEFAULT_DURATION_MS)
            Log.d(TAG, "applyIntent: color=${colorArgbState.intValue.toString(16)} packageName=${packageNameState.value} durationMs=$durationMs")

            dismissJob?.cancel()
            dismissJob = lifecycleScope.launch {
                delay(durationMs)
                Log.d(TAG, "self-dismiss timer elapsed, finishing")
                finish()
            }
        }
    }

    private fun showOverLockScreen() {
        Log.d(TAG, "showOverLockScreen: SDK_INT=${Build.VERSION.SDK_INT}")
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
