# adcore-android

Android TV kiosk application for Adcore resource sync and local video playback.

## Summary

- App name: `adcore`
- Package: `zonely.ams.adcore`
- Language: Java
- Minimum Android version: Android 9 / API 28
- Target SDK: 36
- Primary device type: dedicated Android TV box
- Entry activity: `SplashActivity`
- Main flow: `SplashActivity -> LoginActivity -> SyncActivity -> AdsActivity`

## Current Implementation

### Activity Layouts

Each concrete activity now owns a separate XML layout under `app/src/main/res/layout` and binds views by resource id from Java:

- `activity_splash.xml` -> `SplashActivity`
- `activity_login.xml` -> `LoginActivity`
- `activity_sync.xml` -> `SyncActivity`
- `activity_ads.xml` -> `AdsActivity`
- `item_sync_progress.xml` -> dynamic sync progress rows

User-facing activity text and sync progress labels are maintained in `app/src/main/res/values/strings.xml`. Shared UI dimensions are maintained in `dimens.xml`.

### Device Identity

The project currently uses exactly one active `deviceId` source: `Settings.Secure.ANDROID_ID`.

The app does not use:

- Printed serial number
- `Build.getSerial()`
- `ro.serialno`
- App-generated SQLite UUID

The older serial and app-generated UUID fallback options are kept as commented reference in `DeviceIdProvider` only. They are intentionally inactive. `READ_PHONE_STATE` is not requested.

If `ANDROID_ID` is unavailable or invalid, the app logs the issue and fails identity resolution instead of silently switching to another identifier.

### Mapping Flow

`SyncActivity` calls `getMappedResources/{deviceId}` with `ANDROID_ID`.

If the server returns `Device not configured`:

- The app clears cached videos from the app-private `resources/` folder.
- The app clears local mapped `resources` and `nodes` records.
- If ads are currently playing, `AdsActivity` is interrupted and `SyncActivity` opens.
- `SyncActivity` shows the centered Android ID with the message `please map the device id to node`.
- The refresh button starts a clean sync from the beginning after the Android ID is mapped on the server.

The app also defensively rejects a successful mapped-resource response if the returned node has a non-empty `deviceId` that does not match the local Android ID.

### Playback

`AdsActivity` uses LibVLC through `org.videolan.android:libvlc-all:3.7.0`, not Android `VideoView` / platform `MediaPlayer`.

LibVLC native libraries are packaged with the APK for all ABIs included by the dependency, so playback is not dependent on the Android OS media player or device codec support in the same way `VideoView` is. Hardware decoding is disabled by default to reduce dependence on firmware codecs. Playback still depends on Android surfaces, graphics, audio output, and native ABI loading.

During video playback:

- Short Back is consumed so the kiosk screen does not exit accidentally.
- Long Back pauses playback, displays only the current Android ID, and auto-resumes after 60 seconds.
- No action buttons are shown on the AdsActivity Android ID overlay.
- Repeated Back events are consumed safely.

### Sync And Downloads

Before video downloads begin, `SyncManager` creates and verifies the app-private video cache folder is writable.

Video download failures are logged with full stack trace plus resource metadata:

- `resourceId`
- `fileName`
- `mediaType`
- `fileSizeBytes`
- `checksum`
- `durationSeconds`
- `typeKey`
- `status`
- `displayOrder`
- `fileUri`
- target local file path

Daily sync still compares resources by `id`, updates metadata changes, downloads new/changed videos, and deletes missing resources plus their cached files.

### Audit And Device Data

Previous-day audit payloads are sent with `sendDeviceData`. The request body contains `deviceId`; the endpoint is `POST /deviceManagement/saveDeviceData`.

Credentials remain stored in plain SQLite for this release per requirement. The database code contains placeholders for a future Android Keystore-backed encryption release.

## Device ID Advice

For the current simple rollout, `ANDROID_ID` is the active primary identity.

Recommended production rule:

- The app sends only `ANDROID_ID` as `deviceId`.
- The backend enforces one active node mapping per Android ID.
- The backend rejects duplicate active mappings.
- Admin tooling should show and map the Android ID displayed by `SyncActivity`.

This removes the app clear-data problem because the ID is not stored in SQLite. It still must be validated on the actual cheap rooted boxes because bad/cloned firmware can duplicate Android IDs.

The app can prevent itself from using the wrong platform identity. It cannot globally guarantee uniqueness alone. The backend must enforce uniqueness and active mapping ownership.

## Hardware And OS Dependency Analysis

Complete independence from Android OS is not possible because this is an Android application. The realistic goal is to remove vendor/hardware identity assumptions, reduce codec dependence, and isolate Android-specific APIs behind small boundaries.

Current dependency areas:

| Area | Current dependency | Risk | Recommended direction |
| --- | --- | --- | --- |
| Device identity | `ANDROID_ID` | Stable across app clear-data; can change after factory reset or be duplicated by bad firmware | Use Android ID now; sample-test real boxes; backend must reject duplicate active mappings |
| Video playback | LibVLC native player on Android surfaces | Still depends on Android surface/audio/native ABI loading | Keep LibVLC; test target ABIs; consider a `VideoPlayer` interface if another engine is ever needed |
| Storage | App-private files, SQLite | Android app data clear removes cache/session, but not Android ID | Keep app-private storage; server remains source of truth for node mapping |
| Scheduling | `AlarmManager`, `JobScheduler`, exact alarm permission | OEM power policies can delay jobs | Keep device-owner/default-launcher deployment; add server-side freshness monitoring |
| Boot start | Boot/package-replaced broadcasts | Background activity launch restrictions on newer Android | Deploy as kiosk/default launcher/device-owner |
| Silent update | `DevicePolicyManager`, `PackageInstaller` | True silent install requires device owner | Keep device-owner path; avoid root-specific install for now |
| Network | `HttpURLConnection`, Android TLS/CA store | Old/cheap firmware may have TLS/CA issues | Consider an HTTP client adapter and certificate/CA strategy if boxes show TLS failures |
| Fullscreen/kiosk | System UI flags, fixed landscape, keep-screen-on | OEM remote/navigation behavior varies | Keep immersive mode; test physical remote keys on target box |
| Uptime tracking | App lifecycle and shutdown broadcasts | Shutdown broadcasts may be skipped on hard power loss | Treat uptime as best effort; reconcile with backend heartbeat later |
| Logs/exports | App-private files and ZIP | Storage pressure can remove data only if app data is cleared | Keep rolling logs; preserve export services for next websocket release |

Best path toward stronger independence:

1. Use Android ID as the only active app-side identity for this phase.
2. Keep LibVLC and test actual deployment ABIs/codecs on the cheap box.
3. Add interfaces around playback, identity, scheduler, installer, storage, and network clients.
4. Use device-owner/kiosk provisioning instead of root-specific commands.
5. Let the backend enforce mapping uniqueness, liveness, and stale-device detection.

## API Endpoints

Configured in `ApiConfig`:

- `POST /auth/login`
- `GET /deviceManagement/getMappedResources/{deviceId}`
- `GET /resources/download/{resourceId}`
- `POST /deviceManagement/saveDeviceLogs`
- `POST /deviceManagement/saveDeviceDB/{deviceId}`
- `POST /deviceManagement/saveDeviceData`
- `GET /deviceManagement/downloadLatestApp?appVersion={version}`

All authenticated calls attach the current `Authorization: Bearer <accessToken>` header when available.

## SQLite Tables

- `credentials`
- `nodes`
- `resources`
- `playback_counts`
- `sync_runs`
- `api_metrics`
- `config`
- `markers`
- `uptime_sessions`
- `sent_error_logs`

Default configurable retry values:

- `api_retry_delay_sec = 5`
- `api_retry_max = 3`
- `background_retry_delay_sec = 300`
- `background_retry_max = 3`
- `download_threads = 4`

## App-Private Storage

Under `context.getFilesDir()`:

- `resources/` - downloaded videos
- `logs/` - rolling log files
- `exports/` - prepared DB/log zip files
- `updates/` - downloaded latest APK

## Device Owner Provisioning Notes

Silent install and reliable boot/kiosk behavior require managed-device provisioning.

Recommended deployment:

- Provision Adcore as device owner during factory setup.
- Configure Adcore as the default launcher/kiosk app.
- Grant required runtime permissions through device policy/OEM tooling.
- Keep root out of the app flow unless a later release explicitly requires a root-specific installer adapter.

Without device-owner or OEM privileges:

- Android may block silent APK installation and require installer UI.
- Android may restrict background activity launch after boot.
- Exact daily alarms may be delayed or denied.

## Build

Typical build command:

```bash
./gradlew assembleDebug --no-daemon
```

Unit tests:

```bash
./gradlew testDebugUnitTest --no-daemon
```

Generated APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This workstation currently has no Android SDK configured at `ANDROID_HOME` or `local.properties`, so local APK compilation cannot complete until an SDK with API 36 is installed/configured.

## Verification

Completed in this workspace:

- XML resource validation with `xmllint` - successful
- Gradle wrapper/dependency start - blocked after wrapper download because Android SDK is not configured

Build blocker observed:

```text
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable
or by setting the sdk.dir path in local.properties.
```

## Future Release Hooks

- Android Keystore-backed encryption for stored credentials.
- Websocket listener for server-triggered DB/log upload commands.
- Backend admin workflow to remap Android IDs if factory reset or bad firmware changes identity.
- Interface boundaries for playback, scheduler, installer, identity, storage, and network clients.
- Checksum validation of downloaded files if the server checksum format is confirmed.
- Foreground-service notification only if deployment target does not run as a managed kiosk app.
