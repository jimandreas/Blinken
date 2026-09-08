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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bammellab.blinken.flash.FLASH_NOTIFICATION_ID
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
    // ACTION_USER_PRESENT (unlock) shares the receiver: the display is a lockscreen-only
    // feature, so unlocking cancels any trigger notification still pending/lingering in the
    // shade rather than letting it sit there as a visible "Unread notifications" entry.
    // ACTION_POWER_CONNECTED/DISCONNECTED also share it: the legacy per-package reminder alarms
    // and the single continuous nudge are mutually exclusive per charging state, and the ONLY
    // thing that swaps one set for the other is reconcileActiveReminders - without reacting to
    // the power transition itself, unplugging mid-continuous-session left still-unread
    // notifications with NO reminder armed at all (reminders were cancelled when charging began,
    // and nothing re-armed them), so the unplugged momentary flash never fired again
    // (user-reported).
    private fun registerScreenOffReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) {
                    Log.d(TAG, "device unlocked, cancelling any pending flash trigger notification")
                    NotificationManagerCompat.from(context).cancel(FLASH_NOTIFICATION_ID)
                    return
                }
                val settings = cachedSettings.value ?: return
                // Cheap, natural checkpoint to re-sync ActiveNotificationsStore against system
                // truth (drift observed once during testing, cause not fully pinned down) AND to
                // arm whichever reminder mechanism matches the current charging state - this is
                // what re-arms the legacy per-package alarms after unplugging, and what swaps
                // them back out for the continuous nudge after plugging in.
                Log.d(TAG, "reconciling on ${intent.action}")
                reconcileActiveReminders(settings)
                if (!isDeviceCharging(context)) return
                val visible = resolveVisibleSegments(ActiveNotificationsStore.active.value, settings, System.currentTimeMillis())
                if (visible.isEmpty()) return
                Log.d(TAG, "${intent.action} while charging with ${visible.size} visible notification(s), re-triggering continuous display")
                postContinuousTriggerNotification(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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

        // Only record an eco-debounce timestamp for a flash that will actually show - the
        // trigger is suppressed while the device is in active unlocked use or while a call is
        // ringing/in progress (see FlashTrigger.flashSuppressedReason), and a never-shown flash
        // must not debounce away the next real one. Active-notification tracking and
        // reminder/nudge scheduling below still run regardless, so the display picks the
        // notification up as soon as the device locks again / the call ends.
        if (flashSuppressedReason(this) == null) {
            ecoDebouncePrefs.edit().putLong(packageName, now).apply()
        }

        // Store update runs before posting the trigger, so SnakeScreen's first composed frame
        // (once FlashActivity launches) can never miss this notification in a race.
        addActiveNotificationKey(this, packageName, sbn.key)
        postFlashTriggerNotification(this, packageName, spec, label = entry?.label ?: packageName)

        if (isDeviceCharging(this)) {
            ActiveNotificationsStore.scheduleContinuousNudge(this)
        } else {
            // Unconditionally, not just when the package's active set was previously empty: the
            // reminder chain may have been cancelled by a charging session (the two mechanisms
            // are mutually exclusive) while older keys stayed tracked, and a wasEmpty guard then
            // kept every later arrival from ever restarting it. Re-arming is idempotent anyway -
            // FLAG_UPDATE_CURRENT just resets the same pending alarm to a fresh 60s.
            scheduleReminder(this, packageName)
        }
    }
}
