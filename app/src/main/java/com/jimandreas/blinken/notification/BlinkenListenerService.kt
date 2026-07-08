package com.jimandreas.blinken.notification

import android.content.SharedPreferences
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
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
    }

    private fun reconcileActiveReminders(settings: AppSettings) {
        val allowedPackages = settings.entries.filter { it.enabled }.map { it.packageName }.toSet()
        val activeKeysByPackage = activeNotifications
            .filter { it.packageName in allowedPackages }
            .groupBy({ it.packageName }, { it.key })
            .mapValues { (_, keys) -> keys.toSet() }

        for (packageName in allowedPackages) {
            val keys = activeKeysByPackage[packageName] ?: emptySet()
            replaceActiveNotificationKeys(this, packageName, keys)
            if (keys.isEmpty()) cancelReminder(this, packageName) else scheduleReminder(this, packageName)
        }
        Log.d(TAG, "reconciled active reminders for: ${activeKeysByPackage.keys}")
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
        postFlashTriggerNotification(this, packageName, spec, label = entry?.label ?: packageName)

        val wasEmpty = !hasActiveNotifications(this, packageName)
        addActiveNotificationKey(this, packageName, sbn.key)
        if (wasEmpty) scheduleReminder(this, packageName)
    }
}
