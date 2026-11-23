# Add Google Account

A tiny Android app that launches the system **Add Google Account** flow, optionally pre-filling the account name from a value pushed by an MDM/UEM server through Android managed app configuration (application restrictions).

Intended for managed-device rollouts where users should land directly in Google sign-in with their username already filled in.

## How it works

1. On launch, the app shows a splash screen and a "Loading configuration from server…" progress indicator.
2. It reads the `username` application restriction via `RestrictionsManager`.
3. If the value isn't present yet, it polls every **500 ms** for up to **10 seconds** and also listens for `android.intent.action.APPLICATION_RESTRICTIONS_CHANGED`, so late-arriving UEM configuration is still picked up.
4. It then calls `AccountManager.addAccount("com.google", …)`, passing the username as `KEY_ACCOUNT_NAME` (an **unverified hint**). If no username arrives within the timeout, the flow starts without a hint.
5. When the account flow completes (or fails), the app finishes itself.

## Managed configuration

| Key        | Type   | Description                                                   |
|------------|--------|---------------------------------------------------------------|
| `username` | string | Google account email/username to prefill during account setup |

Defined in [`app/src/main/res/xml/app_restrictions.xml`](app/src/main/res/xml/app_restrictions.xml). ### Workspace ONE UEM

1. Add the app to Workspace ONE UEM as an internal Android app (upload the APK) or as a public app, using package `com.ffglobal.addgoogleaccount`.
2. In the app's **Assignment** settings, enable **Send Configuration** (Application Configuration) and add a key:
   - **Configuration Key:** `username`
   - **Value Type:** String
   - **Configuration Value:** the user's Google account, e.g. a lookup value such as `{EmailAddress}` or `{EmailUserName}` so each device gets its own user.
3. Assign the app to the smart group and push it to enrolled Android Enterprise devices.

The value is delivered to the device as an application restriction, which the app reads at launch. Console menu names vary by UEM version, so check them against your console.

## Requirements

- Android 8.0+ (minSdk 26); targets SDK 36
- Kotlin, Gradle (Kotlin DSL), JDK 17
- Android Studio (current stable) for development

## Build

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK (needs your signing config)
```

Output: `app/build/outputs/apk/`. A prebuilt release APK is also checked in under `app/release/`.

## Project layout

```
app/src/main/
├── java/com/ffglobal/addgoogleaccount/MainActivity.kt   # polling + addAccount logic
├── res/xml/app_restrictions.xml                         # managed config schema
├── res/layout/activity_main.xml                         # loading screen
└── AndroidManifest.xml
```

## Notes

- The username is only a hint; Google sign-in still requires the user to authenticate.
- Failures from `addAccount` are swallowed and the app simply closes. The username is intentionally not logged.
- The receiver is registered as exported so restriction-change broadcasts are delivered on Android 13+.

