# Blinken

A software replacement for a hardware notification LED, for Android phones that don't have one (originally built for a Sony Xperia 5 V).

Blinken listens for notifications from a user-configurable allowlist of apps and briefly flashes a transparent, glowing overlay on the lock screen instead of turning on the whole display — a lighter-weight, less battery/OLED-wearing alternative to leaving the always-on clock display enabled.

The name is short for "Blinkenlights," a nod to the German-English computer-room folklore sign warning not to touch the blinking lights on old mainframes.

## How it works

1. Grant Blinken notification access (Settings → Apps → Special access → Notification access) — there's no runtime permission dialog for this.
2. Add apps to the allowlist in Blinken's settings screen, and pick a color and flash duration for each.
3. When an allowlisted app posts a notification, Blinken briefly shows a pulsing glow over the lock screen, then auto-dismisses without unlocking the device.

An "eco mode" toggle limits how often repeat notifications from the same app can re-trigger a flash.

## Status

v1: notification listening, per-app allowlist with color/duration, and the lockscreen flash are implemented. Not yet built: a foreground service to improve reliability against aggressive OEM background-process killing (e.g. Sony's Stamina mode) — for now, the app requests a battery-optimization exemption instead. See `CLAUDE.md` for architecture notes and `app-suggestions.txt` for the original design discussion.

## Building

```
./gradlew assembleDebug
```

Requires JDK 11+ and the Android SDK (targetSdk 36, minSdk 24). See `CLAUDE.md` for test commands and architecture details.
