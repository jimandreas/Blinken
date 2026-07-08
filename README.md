# Blinken

A software replacement for a hardware notification LED, for Android phones that don't have one (originally built for a Sony Xperia 5 V).

Blinken listens for notifications from a user-configurable allowlist of apps and briefly shows a small icon — patterned after the notifying app — over the lock screen instead of turning on the whole display. Unlike a one-off flash, it keeps re-appearing every minute or so for as long as the notification stays unread, similar to a real notification LED's persistent blink, then stops the instant it's read or dismissed — a lighter-weight, less battery/OLED-wearing alternative to leaving the always-on clock display enabled.

The name is short for "Blinkenlights," a nod to the German-English computer-room folklore sign warning not to touch the blinking lights on old mainframes.

## How it works

1. Grant Blinken notification access (Settings → Apps → Special access → Notification access) — there's no runtime permission dialog for this. On Android 13+/14+ it also needs the notification and full-screen-notification permissions; Blinken's settings screen prompts for whichever of these are missing.
2. Add apps to the allowlist in Blinken's settings screen, and pick a color and flash duration for each.
3. When an allowlisted app posts a notification, Blinken briefly shows a small colored badge with that app's icon over the lock screen, then auto-dismisses without unlocking the device. If the notification is still unread a minute later, it shows again — repeating until you read or dismiss it.

An "eco mode" toggle limits how often repeat notifications from the same app can re-trigger the *first* flash (the repeat-until-dismissed reminders are a separate, slower cadence and aren't affected by eco mode).

## Status

v1: notification listening, per-app allowlist with color/duration, the lockscreen flash, and repeat-until-dismissed reminders are implemented. Not yet built: a foreground service to improve reliability against aggressive OEM background-process killing (e.g. Sony's Stamina mode) — for now, the app requests a battery-optimization exemption instead, and reminders use `AlarmManager` rather than a foreground service. See `CLAUDE.md` for architecture notes and `app-suggestions.txt` for the original design discussion.

## Building

```
./gradlew assembleDebug
```

Requires JDK 11+ and the Android SDK (targetSdk 36, minSdk 24). See `CLAUDE.md` for test commands and architecture details.
