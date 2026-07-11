package com.bammellab.blinken.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ACTIVE_NOTIFICATIONS_PREFS = "blinken_active_notifications"
private const val KEY_ACTIVE_NOTIFICATIONS_JSON = "active_notifications_json"

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class ActiveNotification(
    val packageName: String,
    val key: String,
    val postedAtMs: Long,
)

// Aggregate, cross-package view of every currently-active (unread/undismissed) notification,
// oldest-first. Backs the continuous "snake" display, which needs to enumerate ALL active
// notifications at once - something the per-package bookkeeping ActiveReminders exposed (and
// now delegates to this store for) never supported. Exposed as an in-process StateFlow rather
// than polled from SharedPreferences because BlinkenListenerService and FlashActivity always
// share one process (no android:process override in the manifest), and persisted to
// SharedPreferences write-through on every mutation so it survives process death exactly like
// the data it replaces.
object ActiveNotificationsStore {

    private val _active = MutableStateFlow<List<ActiveNotification>>(emptyList())
    val active: StateFlow<List<ActiveNotification>> = _active.asStateFlow()

    @Volatile
    private var hydrated = false

    private fun prefs(context: Context) =
        context.getSharedPreferences(ACTIVE_NOTIFICATIONS_PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (hydrated) return
        synchronized(this) {
            if (hydrated) return
            _active.value = readFromPrefs(context)
            hydrated = true
        }
    }

    private fun readFromPrefs(context: Context): List<ActiveNotification> {
        val raw = prefs(context).getString(KEY_ACTIVE_NOTIFICATIONS_JSON, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ActiveNotification>>(raw) }.getOrNull() ?: emptyList()
    }

    private fun persist(context: Context, notifications: List<ActiveNotification>) {
        prefs(context).edit()
            .putString(KEY_ACTIVE_NOTIFICATIONS_JSON, json.encodeToString(notifications))
            .apply()
    }

    // Refreshes postedAtMs even when the key already exists: onNotificationPosted firing at all
    // means the app just (re-)posted this notification right now, which is a distinct signal from
    // mergeReconciled's listener-reconnect case (below) where the notification itself hasn't
    // necessarily changed - only our bookkeeping might have been lost. Not refreshing here let a
    // key reused across genuinely new messages (e.g. WhatsApp's per-conversation summary
    // notification) keep an arbitrarily old postedAtMs, which could make the continuous-mode
    // safety cap wrongly evict a brand-new message once enough time had passed since the key was
    // first seen - observed on-device after a reinstall restored old ActiveNotificationsStore data
    // (see backup_rules.xml/data_extraction_rules.xml) whose ancient timestamp then got kept.
    fun add(context: Context, packageName: String, key: String, postedAtMs: Long = System.currentTimeMillis()) {
        init(context)
        synchronized(this) {
            val current = _active.value
            val updated = (current.filterNot { it.key == key } + ActiveNotification(packageName, key, postedAtMs))
                .sortedBy { it.postedAtMs }
            _active.value = updated
            persist(context, updated)
        }
    }

    fun remove(context: Context, packageName: String, key: String) {
        init(context)
        synchronized(this) {
            val updated = _active.value.filterNot { it.packageName == packageName && it.key == key }
            _active.value = updated
            persist(context, updated)
        }
    }

    fun activeForPackage(context: Context, packageName: String): List<ActiveNotification> {
        init(context)
        return _active.value.filter { it.packageName == packageName }
    }

    // Rebuilds the store from system truth (e.g. NotificationListenerService.getActiveNotifications()
    // on reconnect), preserving each already-tracked key's originally-recorded postedAtMs rather
    // than resetting it - see mergeReconciled.
    fun reconcileAll(context: Context, truth: List<ActiveNotification>) {
        init(context)
        synchronized(this) {
            val updated = mergeReconciled(_active.value, truth)
            _active.value = updated
            persist(context, updated)
        }
    }

    private fun nudgePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ContinuousNudgeReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val CONTINUOUS_NUDGE_INTERVAL_MS = 60_000L

    fun scheduleContinuousNudge(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + CONTINUOUS_NUDGE_INTERVAL_MS,
            nudgePendingIntent(context),
        )
    }

    fun cancelContinuousNudge(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(nudgePendingIntent(context))
    }
}

// Pure, unit-testable: preserves each already-tracked key's original postedAtMs across
// reconciliation instead of resetting it to "now", so the continuous-mode safety cap (duration
// since first seen) survives a listener reconnect. Entries absent from truth are dropped.
internal fun mergeReconciled(
    existing: List<ActiveNotification>,
    truth: List<ActiveNotification>,
): List<ActiveNotification> {
    val existingByKey = existing.associateBy { it.key }
    return truth.map { candidate -> existingByKey[candidate.key] ?: candidate }
        .sortedBy { it.postedAtMs }
}
