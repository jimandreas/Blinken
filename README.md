# Blinken

A software replacement for a hardware notification LED, for Android phones that don't have one (originally built for a Sony Xperia 5 V).

Blinken listens for notifications from a user-configurable allowlist of apps and shows something over the lock screen instead of turning on the whole display. Unplugged, it briefly shows a small icon — patterned after the notifying app — re-appearing every minute or so for as long as the notification stays unread, similar to a real notification LED's persistent blink, then stops the instant it's read or dismissed. While charging, every currently-unread notification instead renders continuously as a small icon that joins a moving "snake" sweeping the screen, so a glance shows everything unread at once without a static image risking OLED burn-in — a lighter-weight, less battery/OLED-wearing alternative to leaving the always-on clock display enabled either way.

![Continuous charging-mode display: three unread notifications (two WhatsApp, one Messages) rendered as a moving snake over the lock screen](docs/AppScreenshot.png)

The name is short for "Blinkenlights," a nod to the German-English computer-room folklore sign warning not to touch the blinking lights on old mainframes.

## How it works

1. Grant Blinken notification access (Settings → Apps → Special access → Notification access) — there's no runtime permission dialog for this. On Android 13+/14+ it also needs the notification and full-screen-notification permissions; Blinken's settings screen prompts for whichever of these are missing.
2. Add apps to the allowlist in Blinken's settings screen, and pick a color and flash duration for each.
3. When an allowlisted app posts a notification, Blinken shows something over the lock screen without unlocking the device or lighting up the whole display:
   - **Unplugged:** a brief colored badge with that app's icon, auto-dismissing after a few seconds. If the notification is still unread a minute later, it shows again — repeating until you read or dismiss it.
   - **Charging:** every currently-unread allowlisted notification renders as a small badge, and all of them move together as a snake, continuously sweeping the whole screen until everything is read or dismissed.
4. **Touch the screen to dismiss the display** and get straight to your lock screen. It stays out of the way for 30 seconds so you can unlock in peace, then resumes if anything is still unread.

Blinken keeps itself out of the way automatically, too: it shows nothing while you're actively using the unlocked phone, and nothing while a call is ringing or in progress — an incoming call owns the lock screen, and the "you missed a call" badge appears afterwards like any other notification.

An "eco mode" toggle limits how often repeat notifications from the same app can re-trigger the *first* flash (the repeat-until-dismissed reminders are a separate, slower cadence and aren't affected by eco mode).

## Status

v1.6: notification listening, per-app allowlist with color/duration, the lockscreen flash, repeat-until-dismissed reminders, the continuous charging-mode snake display, and an in-app About screen (source link, version) are implemented. The snake display hides the status and navigation bars for a true full-screen sweep (swipe from an edge to reveal them briefly).

New since v1.4:

- **Touch-to-dismiss (v1.6).** Fixes a serious bug: because the display draws over the lock screen and had no dismiss gesture, a locked, charging phone whose notifications couldn't be cleared without unlocking (a missed call, for instance) could become impossible to unlock — the power button only made the display reappear. Any touch now dismisses it for 30 seconds.
- **Call suppression (v1.5).** The display no longer appears over an incoming-call screen ([#1](https://github.com/jimandreas/Blinken/issues/1)).
- **Lock-screen-only gating (v1.5).** Nothing is shown while the unlocked phone is in active use, and the internal trigger no longer surfaces as a stray heads-up notification after unlocking.

Signed release builds are published automatically to [GitHub Releases](https://github.com/jimandreas/Blinken/releases) whenever a `vX.Y` tag is pushed (see `.github/workflows/release.yml`). Not yet built: a foreground service to improve reliability against aggressive OEM background-process killing (e.g. Sony's Stamina mode) — for now, the app requests a battery-optimization exemption instead, and reminders use `AlarmManager` rather than a foreground service. See `BUILDING.md` for build/test commands and release steps, and `app-suggestions.txt` for the original design discussion.

## Building

```
./gradlew assembleDebug
```

Requires JDK 11+ and the Android SDK (targetSdk 36, minSdk 24). See `BUILDING.md` for test commands and release details.
