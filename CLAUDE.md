# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build (from repo root, uses the Gradle wrapper):
```
./gradlew assembleDebug
```

Compile only (fast feedback loop while iterating):
```
./gradlew compileDebugKotlin
```

Run all unit tests:
```
./gradlew testDebugUnitTest
```

Run a single unit test class:
```
./gradlew testDebugUnitTest --tests "com.jimandreas.blinken.notification.NotificationFilterTest"
```

Instrumented tests require a connected device/emulator:
```
./gradlew connectedDebugAndroidTest
```

`adb` is not on `PATH` in this dev environment, but it exists at `/c/a/sdk/platform-tools/adb.exe` (invoke it by that path). A physical device has been used for on-device verification in this repo (notification triggering, lockscreen flash behavior, `dumpsys alarm`/`dumpsys power` inspection). Two gotchas hit repeatedly during that testing, worth checking first if a live device behaves unexpectedly:
- Reinstalling the APK (`installDebug`) does **not** rebind `BlinkenListenerService` with the system — notifications silently stop reaching `onNotificationPosted` until you force a rebind: `adb shell cmd notification disallow_listener <pkg>/<service>` then `allow_listener` (same component name). Confirm binding via `adb shell dumpsys activity services <pkg>`.
- Developer option "Stay awake while charging" (`adb shell settings get global stay_on_while_plugged_in`) overrides the screen-timeout setting entirely while USB-connected for `adb` — this can look exactly like a Blinken bug (screen never dims) when it's unrelated.

## Architecture

Blinken is a single-module Android app (`:app`, package `com.jimandreas.blinken`) that acts as a software replacement for a hardware notification LED. It listens for notifications from a user-configured allowlist of apps and, instead of lighting an LED, briefly shows a small icon over the lock screen — repeating periodically for as long as the underlying notification stays unread, then stopping the moment it's read or dismissed.

Package-by-feature layout:

- **`notification/`** — system integration. `BlinkenListenerService` is the `NotificationListenerService`; it caches the allowlist from `AllowlistRepository` into a `StateFlow` (falling back to a one-shot suspend read if a notification arrives before the first Flow emission, to avoid treating "not loaded yet" as "empty allowlist"), and persists eco-mode debounce timestamps in `SharedPreferences` (not in-memory) so they survive the process being killed and restarted mid-burst. `NotificationFilter.resolveBlink` is the pure matching/eco-mode-debounce decision function — unit tested in isolation — and governs only the *first* flash for a given arrival; the repeat-until-dismissed reminder is a separate mechanism (below) that bypasses it entirely.
  - **The flash cannot be triggered by calling `startActivity()` directly.** A `NotificationListenerService` has no background-activity-start (BAL) exemption on modern Android — confirmed on-device via `ActivityTaskManager` logging `allowBackgroundActivityStart: false` even though `startActivity()` itself reports success with no exception (the activity just silently never appears). The workaround used throughout this package is a **full-screen-intent notification**: `FlashTrigger.postFlashTriggerNotification` posts a silent, `IMPORTANCE_HIGH` notification whose `setFullScreenIntent` targets `FlashActivity`; that mechanism is BAL-exempt and, by design, only auto-launches the target while the device is locked. This needs the `POST_NOTIFICATIONS` (API 33+) and `USE_FULL_SCREEN_INTENT` (API 34+, separately revocable) permissions — checked in `ListenerStatus.kt`, surfaced via `PermissionBanner`.
  - **Repeat-until-dismissed reminders.** `ActiveReminders.kt` tracks, per allowlisted package, which `StatusBarNotification` keys are still active (SharedPreferences-backed, so it survives process death) and schedules/cancels a reminder roughly every minute via `AlarmManager.setAndAllowWhileIdle` (inexact — deliberately avoids needing the `SCHEDULE_EXACT_ALARM` permission; expect actual delivery to drift/batch a bit past the requested time). `onNotificationRemoved` cancels a package's reminder once its active set empties; `onListenerConnected` reconciles persisted state against `getActiveNotifications()` on every (re)connect, which also transparently recovers scheduling after a reboot (`AlarmManager` alarms don't survive one, but this reconciliation re-creates them). `FlashReminderReceiver` is the alarm target and is self-terminating: each fire re-checks the package is still unread *and* still an enabled allowlist entry before re-triggering and rescheduling — either check failing just stops the chain.
  - There is no public API for a third-party app to force the screen back to sleep early or shorten its timeout (`WindowManager.LayoutParams.userActivityTimeout` exists in the platform but is not part of the public SDK — verified against the `android.jar` stub). Waking a fully-off screen via `FlashActivity` is indistinguishable to the OS from a real power-button wake, so afterward the screen follows whatever timeout the user has configured. This is the reason reminders are minutes apart rather than a true continuous LED-speed blink, and it's also why "Stay awake while charging" can look exactly like a Blinken bug during testing (see Commands section above).
- **`flash/`** — the visual trigger. `FlashActivity` is a real Android Activity (not a Compose nav destination) because it needs independent lockscreen window flags: `setShowWhenLocked`/`setTurnScreenOn` on API 27+, with a `WindowManager.LayoutParams` flag fallback below that. It's declared `singleTask` in the manifest and **must** override `onNewIntent` (it does) to update color/icon/duration and restart its self-dismiss timer when a second notification arrives while a flash is already showing — without that override the new intent's extras are silently dropped by the default no-op `onNewIntent`. It also cancels the triggering full-screen-intent notification (`FLASH_NOTIFICATION_ID`) on every `applyIntent` so it doesn't linger in the shade. `FlashScreen` renders a small (~120dp) colored badge — the per-app `colorArgb` as background, with the source app's own launcher icon on top (`PackageManager.getApplicationIcon`, looked up via the `EXTRA_PACKAGE_NAME` extra, falling back to no icon if the source app was uninstalled) — **not** a full-screen glow; this was a deliberate downsize from the original v1 design so the source app is identifiable at a glance instead of just a color, and so each wake is briefer.
- **`settings/`** — persistence and data. `AllowlistRepository` wraps a Preferences DataStore, serializing the whole `AppSettings` object (allowlist entries + global defaults + eco mode flag) to **one JSON string under a single key** via kotlinx.serialization, rather than one Preferences key per package — the allowlist is always read/written as a cohesive unit, so this keeps writes atomic and avoids key-prefix enumeration. `InstalledAppsProvider` queries `PackageManager` for the "add app" picker; this requires the `<queries>` element in `AndroidManifest.xml` (declared for `MAIN`/`LAUNCHER` intents) because of Android 11+ package-visibility filtering — without it, `getInstalledApplications()` silently returns almost nothing.
  - **`settings/ui/`** — Compose UI. No `NavHost`: `MainActivity` hosts `SettingsScreen` directly as the only destination, with `AddAppDialog`/color/duration pickers as dialogs and dropdowns over hoisted state, not separate nav routes. `SettingsScreen` loads the installed-apps list once via `produceState` + `Dispatchers.IO` (not inline in `remember{}`) and derives all per-row icon lookups and the "add app" candidate list from that single cached list, to avoid repeated main-thread `PackageManager` scans on every allowlist edit. `PermissionBanner` tracks notification access, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, and battery-optimization-exemption state; all but the runtime `POST_NOTIFICATIONS` request (via `rememberLauncherForActivityResult`) are rechecked through a `DisposableEffect`/`LifecycleEventObserver` on `ON_RESUME`, since granting them happens in a separate system Settings screen that doesn't otherwise trigger recomposition.

### Key architectural decisions

- **No ViewModel layer.** `SettingsScreen` talks to `AllowlistRepository` directly via `collectAsState`/`rememberCoroutineScope`. This is intentional for the current single-screen scope — introduce a ViewModel only if navigation/state complexity grows.
- **No foreground service (yet).** Reliability against OEM background-kill behavior (e.g. Sony Stamina mode) is handled today only via a battery-optimization-exemption request from the UI, plus `AlarmManager.setAndAllowWhileIdle` for the repeat-until-dismissed reminders (which is Doze-aware but still just an inexact alarm, not a guarantee). A foreground service (trading a persistent notification for reliability) is a deliberately deferred v1.1 item — see `app-suggestions.txt` for the original design discussion.
- **minSdk 24, targetSdk 36.** API-level branches exist specifically for the lockscreen-show behavior (`FlashActivity`) — check `Build.VERSION.SDK_INT` guards before removing them. Note `onNotificationRemoved(sbn, rankingMap)` (the 2-arg overload) is used deliberately over the 3-arg `reason`-including one, since the latter is API 26+ only and wouldn't be invoked by the OS below that on a `minSdk 24` build.
