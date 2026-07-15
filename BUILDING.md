# Building and Testing

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
./gradlew testDebugUnitTest --tests "com.bammellab.blinken.notification.NotificationFilterTest"
```

Instrumented tests require a connected device/emulator:
```
./gradlew connectedDebugAndroidTest
```

## Cutting a release

Push a tag matching `v*` (e.g. `v1.2`) to trigger `.github/workflows/release.yml`: it runs `assembleRelease`, signs the APK, and publishes it as a GitHub Release. The workflow does **not** bump the version itself - update `versionCode`/`versionName` in `app/build.gradle.kts` before tagging, and keep the tag's numeric part matching `versionName` (e.g. `versionName = "1.2"` -> tag `v1.2`).
