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

Instrumented tests require a connected device/emulator and are not runnable from this environment (no `adb` on PATH here):
```
./gradlew connectedDebugAndroidTest
```

There is no `adb` available in this dev environment — on-device verification (notification triggering, lockscreen flash behavior, listener reliability under Doze/Stamina mode) must be done by the user on real hardware.

## Architecture

Blinken is a single-module Android app (`:app`, package `com.jimandreas.blinken`) that acts as a software replacement for a hardware notification LED. It listens for notifications from a user-configured allowlist of apps and briefly flashes a transparent activity over the lock screen instead.

Package-by-feature layout:

- **`notification/`** — system integration. `BlinkenListenerService` is the `NotificationListenerService`; it caches the allowlist from `AllowlistRepository` into a `StateFlow` (falling back to a one-shot suspend read if a notification arrives before the first Flow emission, to avoid treating "not loaded yet" as "empty allowlist"), and persists eco-mode debounce timestamps in `SharedPreferences` (not in-memory) so they survive the process being killed and restarted mid-burst. `NotificationFilter.resolveBlink` is the pure matching/eco-mode-debounce decision function — unit tested in isolation.
- **`flash/`** — the visual trigger. `FlashActivity` is a real Android Activity (not a Compose nav destination) because it needs independent lockscreen window flags: `setShowWhenLocked`/`setTurnScreenOn` on API 27+, with a `WindowManager.LayoutParams` flag fallback below that. It's declared `singleTask` in the manifest and **must** override `onNewIntent` (it does) to update color/duration and restart its self-dismiss timer when a second notification arrives while a flash is already showing — without that override the new intent's extras are silently dropped by the default no-op `onNewIntent`. `FlashScreen` is the Compose Canvas glow animation.
- **`settings/`** — persistence and data. `AllowlistRepository` wraps a Preferences DataStore, serializing the whole `AppSettings` object (allowlist entries + global defaults + eco mode flag) to **one JSON string under a single key** via kotlinx.serialization, rather than one Preferences key per package — the allowlist is always read/written as a cohesive unit, so this keeps writes atomic and avoids key-prefix enumeration. `InstalledAppsProvider` queries `PackageManager` for the "add app" picker; this requires the `<queries>` element in `AndroidManifest.xml` (declared for `MAIN`/`LAUNCHER` intents) because of Android 11+ package-visibility filtering — without it, `getInstalledApplications()` silently returns almost nothing.
  - **`settings/ui/`** — Compose UI. No `NavHost`: `MainActivity` hosts `SettingsScreen` directly as the only destination, with `AddAppDialog`/color/duration pickers as dialogs and dropdowns over hoisted state, not separate nav routes. `SettingsScreen` loads the installed-apps list once via `produceState` + `Dispatchers.IO` (not inline in `remember{}`) and derives all per-row icon lookups and the "add app" candidate list from that single cached list, to avoid repeated main-thread `PackageManager` scans on every allowlist edit. The "notification access granted" banner state is rechecked via a `DisposableEffect`/`LifecycleEventObserver` on `ON_RESUME`, since granting access happens in a separate system Settings screen that doesn't otherwise trigger recomposition.

### Key architectural decisions

- **No ViewModel layer.** `SettingsScreen` talks to `AllowlistRepository` directly via `collectAsState`/`rememberCoroutineScope`. This is intentional for the current single-screen scope — introduce a ViewModel only if navigation/state complexity grows.
- **No foreground service (yet).** Reliability against OEM background-kill behavior (e.g. Sony Stamina mode) is handled today only via a battery-optimization-exemption request from the UI. A foreground service (trading a persistent notification for reliability) is a deliberately deferred v1.1 item — see `app-suggestions.txt` for the original design discussion.
- **minSdk 24, targetSdk 36.** API-level branches exist specifically for the lockscreen-show behavior (`FlashActivity`) — check `Build.VERSION.SDK_INT` guards before removing them.
