package com.bammellab.blinken.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.bammellab.blinken.settings.AllowlistRepository
import com.bammellab.blinken.settings.AppSettings
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

    private var screenOffReceiver: BroadcastReceiver? = null

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
            .onEach {
                Log.d(
                    TAG,
                    "settings updated: ecoMode=${it.ecoModeEnabled} entries=${it.entries.map { e -> "${e.packageName}(enabled=${e.enabled})" }}",
                )
                cachedSettings.value = it
            }
            .launchIn(scope)

        // Reminder alarms don't survive a reboot, and this process/service may have been
        // killed and restarted independent of the notifications it was tracking - reconcile
        // persisted active-notification state against the system's current truth so pending
        // reminders resume (or stop) correctly rather than relying on stale bookkeeping.
        scope.launch {
            reconcileActiveReminders(repository.settings.first())
        }

        registerScreenOffReceiver()
    }

    // setShowWhenLocked only reliably keeps FlashActivity visible when it's launched fresh while
    // already locked (the full-screen-intent path, which is BAL-exempt by design) - manually
    // locking the device via the power button while it's already resumed in the foreground drops
    // it behind the keyguard immediately on at least this device/API level, and it would
    // otherwise only reappear once ContinuousNudgeReceiver's periodic (~60s) alarm happens to
    // fire next. Reacting to ACTION_SCREEN_OFF directly closes that gap by re-posting the
    // trigger the moment the screen actually turns off, instead of waiting on the poll interval.
    // ACTION_SCREEN_OFF is a protected system broadcast and can only be received via a
    // dynamically-registered receiver (manifest-declared receivers are excluded from it since
    // API 26), hence registering/unregistering here rather than in AndroidManifest.xml.
    private fun registerScreenOffReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isDeviceCharging(context)) return
                val settings = cachedSettings.value ?: return
                // Cheap, natural checkpoint to also re-sync ActiveNotificationsStore against
                // system truth - otherwise it only happens on listener (re)connect, so any drift
                // (observed once during testing, cause not fully pinned down) could persist
                // indefinitely while the service stays continuously bound.
                reconcileActiveReminders(settings)
                val visible = resolveVisibleSegments(ActiveNotificationsStore.active.value, settings, System.currentTimeMillis())
                if (visible.isEmpty()) return
                Log.d(TAG, "screen off with ${visible.size} visible notification(s), re-triggering continuous display")
                postContinuousTriggerNotification(context)
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        screenOffReceiver = receiver
    }

    private fun reconcileActiveReminders(settings: AppSettings) {
        val allowedPackages = settings.entries.filter { it.enabled }.map { it.packageName }.toSet()
        val truth = activeNotifications
            .filter { it.packageName in allowedPackages }
            .map { ActiveNotification(it.packageName, it.key, it.postTime) }
        ActiveNotificationsStore.reconcileAll(this, truth)

        // Reminder alarms don't survive a reboot either, and the two mechanisms (per-package
        // legacy alarms vs. the single global continuous-mode nudge) must never both be armed for
        // the same package - see handleNotification/onNotificationRemoved for where they're
        // normally kept mutually exclusive as notifications arrive/clear in real time.
        val charging = isDeviceCharging(this)
        val activePackages = truth.map { it.packageName }.toSet()
        for (packageName in allowedPackages) {
            when {
                packageName !in activePackages -> cancelReminder(this, packageName)
                charging -> cancelReminder(this, packageName)
                else -> scheduleReminder(this, packageName)
            }
        }
        if (charging && truth.isNotEmpty()) {
            ActiveNotificationsStore.scheduleContinuousNudge(this)
        } else {
            ActiveNotificationsStore.cancelContinuousNudge(this)
        }
        Log.d(TAG, "reconciled active notifications for: $activePackages (charging=$charging)")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "onListenerDisconnected")
        cachedSettings.value = null
        serviceJob?.cancel()
        serviceJob = null
        screenOffReceiver?.let { unregisterReceiver(it) }
        screenOffReceiver = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val packageName = sbn.packageName
        Log.d(TAG, "onNotificationPosted: $packageName")

        scope.launch {
            val cached = cachedSettings.value
            val settings = cached ?: repository.settings.first()
            Log.d(TAG, "settings source for $packageName: ${if (cached != null) "cache" else "fallback one-shot read"}")
            handleNotification(sbn, settings)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        super.onNotificationRemoved(sbn, rankingMap)
        val packageName = sbn.packageName
        val nowEmpty = removeActiveNotificationKey(this, packageName, sbn.key)
        Log.d(TAG, "onNotificationRemoved: $packageName key=${sbn.key} nowEmpty=$nowEmpty")
        if (nowEmpty) cancelReminder(this, packageName)
        if (ActiveNotificationsStore.active.value.isEmpty()) ActiveNotificationsStore.cancelContinuousNudge(this)
    }

    private fun handleNotification(sbn: StatusBarNotification, settings: AppSettings) {
        val packageName = sbn.packageName
        val now = SystemClock.elapsedRealtime()
        val lastFlash = ecoDebouncePrefs.getLong(packageName, -1L).takeIf { it >= 0L }
        val elapsedSinceLastFlash = lastFlash?.let { now - it }
        Log.d(TAG, "elapsedSinceLastFlash for $packageName: $elapsedSinceLastFlash ms (ecoMode=${settings.ecoModeEnabled})")

        val entry = settings.entries.firstOrNull { it.packageName == packageName }
        val spec = resolveBlink(
            packageName = packageName,
            settings = settings,
            elapsedSinceLastFlashMs = elapsedSinceLastFlash,
        )
        if (spec == null) {
            val reason = when {
                entry == null -> "no allowlist entry for $packageName"
                !entry.enabled -> "entry for $packageName is disabled"
                else -> "eco-mode debounce for $packageName"
            }
            Log.d(TAG, "resolveBlink returned null: $reason")
            return
        }
        Log.d(TAG, "resolveBlink matched $packageName: color=${spec.colorArgb.toString(16)} durationMs=${spec.durationMs}")

        ecoDebouncePrefs.edit().putLong(packageName, now).apply()

        // Store update runs before posting the trigger, so SnakeScreen's first composed frame
        // (once FlashActivity launches) can never miss this notification in a race.
        val wasEmpty = !hasActiveNotifications(this, packageName)
        addActiveNotificationKey(this, packageName, sbn.key)
        postFlashTriggerNotification(this, packageName, spec, label = entry?.label ?: packageName)

        if (isDeviceCharging(this)) {
            ActiveNotificationsStore.scheduleContinuousNudge(this)
        } else if (wasEmpty) {
            scheduleReminder(this, packageName)
        }
    }
}
