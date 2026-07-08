package com.jimandreas.blinken.notification

import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jimandreas.blinken.flash.EXTRA_COLOR_ARGB
import com.jimandreas.blinken.flash.EXTRA_DURATION_MS
import com.jimandreas.blinken.flash.FlashActivity
import com.jimandreas.blinken.settings.AllowlistRepository
import com.jimandreas.blinken.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "BlinkenListenerService"
private const val ECO_DEBOUNCE_PREFS = "blinken_eco_debounce"

class BlinkenListenerService : NotificationListenerService() {

    private var serviceJob: Job? = null
    private lateinit var scope: CoroutineScope
    private lateinit var repository: AllowlistRepository
    private lateinit var ecoDebouncePrefs: SharedPreferences

    // Null until the DataStore Flow's first emission lands; onNotificationPosted falls back
    // to a one-shot suspend read rather than treating "not loaded yet" as "empty allowlist".
    private val cachedSettings = MutableStateFlow<AppSettings?>(null)

    override fun onCreate() {
        super.onCreate()
        repository = AllowlistRepository(applicationContext)
        ecoDebouncePrefs = getSharedPreferences(ECO_DEBOUNCE_PREFS, MODE_PRIVATE)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "onListenerConnected")
        val job = SupervisorJob()
        serviceJob = job
        scope = CoroutineScope(job + Dispatchers.Main.immediate)
        repository.settings
            .onEach { cachedSettings.value = it }
            .launchIn(scope)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "onListenerDisconnected")
        cachedSettings.value = null
        serviceJob?.cancel()
        serviceJob = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val packageName = sbn.packageName
        Log.d(TAG, "onNotificationPosted: $packageName")

        scope.launch {
            val settings = cachedSettings.value ?: repository.settings.first()

            val now = SystemClock.elapsedRealtime()
            val lastFlash = ecoDebouncePrefs.getLong(packageName, -1L).takeIf { it >= 0L }
            val elapsedSinceLastFlash = lastFlash?.let { now - it }

            val spec = resolveBlink(
                packageName = packageName,
                settings = settings,
                elapsedSinceLastFlashMs = elapsedSinceLastFlash,
            ) ?: return@launch

            ecoDebouncePrefs.edit().putLong(packageName, now).apply()
            launchFlash(spec)
        }
    }

    private fun launchFlash(spec: BlinkSpec) {
        val intent = Intent(this, FlashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_COLOR_ARGB, spec.colorArgb)
            putExtra(EXTRA_DURATION_MS, spec.durationMs)
        }
        startActivity(intent)
    }
}
