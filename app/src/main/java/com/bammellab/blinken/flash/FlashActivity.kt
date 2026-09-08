package com.bammellab.blinken.flash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bammellab.blinken.notification.isCallRingingOrActive
import com.bammellab.blinken.notification.isDeviceCharging
import com.bammellab.blinken.notification.isKeyguardShowingOrDeviceLocked
import com.bammellab.blinken.settings.AllowlistRepository
import com.bammellab.blinken.settings.AppSettings
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
private const val UNLOCK_POLL_INTERVAL_MS = 250L

class FlashActivity : ComponentActivity() {

    private val colorArgbState = mutableIntStateOf(AppSettings.DEFAULT_COLOR_ARGB)
    private val packageNameState = mutableStateOf<String?>(null)
    private val continuousModeState = mutableStateOf(false)
    private var dismissJob: Job? = null
    private var unlockReceiver: BroadcastReceiver? = null
    private var sawKeyguardLocked = false
    private val repository by lazy { AllowlistRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        showOverLockScreen()
        registerUnlockReceiver()
        watchForUnlockTransition()

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

    override fun onDestroy() {
        super.onDestroy()
        unlockReceiver?.let { unregisterReceiver(it) }
        unlockReceiver = null
    }

    // The flash/snake is a lockscreen-only display: if the user unlocks the device while it's
    // showing (setShowWhenLocked keeps this activity on top even after keyguard dismissal),
    // it should get out of the way immediately rather than sweeping over the unlocked phone
    // until its notifications happen to clear. Detected two independent ways, because the
    // ACTION_USER_PRESENT broadcast has proven unreliable on-device while this activity is
    // occluding the keyguard (user-reported: unlocking left the snake running):
    // - watchForUnlockTransition polls the keyguard state and finishes on the locked->unlocked
    //   transition. Transition, not absolute state: launching while the screen is off but the
    //   keyguard hasn't engaged yet (lock-after-timeout delay, Smart Lock) must NOT self-dismiss,
    //   since that's still the nightstand scenario this display exists for - in that case the
    //   locked state is never observed and the poll never fires.
    // - registerUnlockReceiver keeps the broadcast path as the instant reaction when it does fire.
    private fun watchForUnlockTransition() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    // A ringing/in-progress call owns the lockscreen (see CallState.kt): get out
                    // of the way of the incoming-call UI rather than flashing or sweeping over
                    // it. New triggers stay suppressed for the duration at FlashTrigger's choke
                    // point, and the reminder/nudge chains (still armed, and self-rescheduling
                    // across a suppressed post) bring the display back once the call ends - as
                    // does the missed-call notification itself, if the dialer is allowlisted.
                    if (isCallRingingOrActive(this@FlashActivity)) {
                        Log.d(TAG, "call ringing or in progress, finishing")
                        NotificationManagerCompat.from(this@FlashActivity).cancel(FLASH_NOTIFICATION_ID)
                        finish()
                        break
                    }
                    if (isKeyguardShowingOrDeviceLocked(this@FlashActivity)) {
                        // Activity field, not loop-local: the unlock can happen while this
                        // activity is stopped behind the keyguard (manual power-button lock
                        // drops it back there on this device), and the transition must still
                        // be recognized when the loop restarts on the post-unlock re-STARTED.
                        sawKeyguardLocked = true
                    } else if (sawKeyguardLocked) {
                        Log.d(TAG, "keyguard transitioned locked -> unlocked, finishing")
                        // Also sweep up any trigger notification still sitting in the shade
                        // (posted while locked but never consumed by a full-screen launch) so
                        // it can't surface as a heads-up over the freshly-unlocked phone. This
                        // activity is the one component reliably alive at this exact moment -
                        // the service's ACTION_USER_PRESENT path doesn't fire dependably here.
                        NotificationManagerCompat.from(this@FlashActivity).cancel(FLASH_NOTIFICATION_ID)
                        finish()
                        break
                    }
                    delay(UNLOCK_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun registerUnlockReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "device unlocked, finishing")
                NotificationManagerCompat.from(context).cancel(FLASH_NOTIFICATION_ID)
                finish()
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        unlockReceiver = receiver
    }

    private fun applyIntent(intent: Intent) {
        val continuous = isDeviceCharging(this)
        continuousModeState.value = continuous
        Log.d(TAG, "applyIntent: continuous=$continuous")

        NotificationManagerCompat.from(this).cancel(FLASH_NOTIFICATION_ID)
        setSystemBarsHidden(continuous)

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

    // Only applied to the continuous snake (status/nav bars stay put for the legacy flash badge,
    // which is brief and not meant to be a full-screen takeover). singleTask reuses this Activity
    // instance across triggers, so this must also restore the bars if a later trigger switches
    // back to legacy (e.g. unplugging mid-continuous-session).
    private fun setSystemBarsHidden(enabled: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
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
