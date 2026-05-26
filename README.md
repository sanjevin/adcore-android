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

The daily sync time is controlled by the SQLite config key `daily_sync_time` in `HH:mm` 24-hour local-device time. The default is `00:30`.

At the configured time:

- The app calls `getMappedResources/{deviceId}` and performs the daily resource comparison.
- If `AdsActivity` is playing a video, it lets the current video finish, persists playback counters, closes playback resources, and then opens `SyncActivity`.
- `SyncActivity` stays in the foreground and displays sync progress rows/progress bars while the sync worker runs.
- After sync completes, the app starts a fresh `AdsActivity` and reloads playback from the refreshed cache.
- If foreground launch fails immediately, the scheduler keeps a background retry fallback.

### Audit And Device Data

Device data is uploaded independently from resource sync by `DeviceDataUploadJobService`.

- Upload interval is controlled by `AppConstants.DEVICE_DATA_UPLOAD_INTERVAL_MINUTES`; default is `60`. Change this constant to `10` for local testing if needed.
- Each run sends pending data up to the current time, not only yesterday's data.
- Payloads are deltas since the last successful upload for that date/resource.
- Local send state is stored only after `sendDeviceData` succeeds, so failed uploads are retried by the next run.
- Playback counts are tracked per `play_date + resource_id` in `playback_counts`, while successfully uploaded counters are tracked in `playback_send_state`.
- Daily sync/app/device uptime upload state is tracked in `device_data_daily_send_state`.
- The request body contains `deviceId`; the endpoint is `POST /deviceManagement/saveDeviceData/{deviceId}`.

### Authentication

The app stores credentials for emergency recovery, but credentials are not used as the normal session path.

- Login stores username/password, access token, refresh token, token type, and access-token expiry in SQLite table `auth_sessions`.
- Current release stores these values in plain SQLite per operating requirement. The DB layer has placeholders for future Android Keystore-backed encryption of username/password/refresh token.
- Before every authenticated API call, the app checks `accessTokenExpiresAt`; if less than 5 minutes remain, it refreshes first using `POST /auth/refresh` with `X-Refresh-Token`.
- If refresh succeeds, the rotated refresh token and new access-token expiry are saved immediately.
- If refresh fails, the app performs one credential-login recovery using the stored username/password, saves the new token pair, and retries the original API.
- Access-token `401` responses are treated as an exceptional fallback: refresh/recover once and retry the authenticated API once. The app does not loop beyond that single recovery pass.
- If refresh and credential recovery both fail, access/refresh tokens are cleared while credentials are retained for manual recovery; locked or rejected credentials are marked so automatic recovery does not loop.
- Logout support calls `POST /auth/logout` with `X-Refresh-Token`, then clears local tokens and stored credentials.
- Login failures containing `locked` or stable code `ACCOUNT_LOCKED` show a clear locked-account message during both startup auto-login and manual login, and are not automatically retried.

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
- `POST /auth/refresh` with `X-Refresh-Token`
- `POST /auth/logout` with `X-Refresh-Token`
- `GET /deviceManagement/getMappedResources/{deviceId}`
- `GET /resources/download/{resourceId}`
- `POST /deviceManagement/saveDeviceLogs`
- `POST /deviceManagement/saveDeviceDB/{deviceId}`
- `POST /deviceManagement/saveDeviceData/{deviceId}`
- `GET /deviceManagement/downloadLatestApp?appVersion={version}`

All authenticated calls attach the current `Authorization: Bearer <accessToken>` header when available. The app refreshes before access-token expiry and only uses `401` handling as an exceptional fallback.

## SQLite Tables

- `auth_sessions`
- `nodes`
- `resources`
- `playback_counts`
- `sync_runs`
- `api_metrics`
- `config`
- `markers`
- `uptime_sessions`
- `sent_error_logs`
- `playback_send_state`
- `device_data_daily_send_state`

Default configurable values:

- `api_retry_delay_sec = 5`
- `api_retry_max = 3`
- `background_retry_delay_sec = 300`
- `background_retry_max = 3`
- `download_threads = 4`
- `daily_sync_time = 00:30`
- `DEVICE_DATA_UPLOAD_INTERVAL_MINUTES = 60` in `AppConstants`

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

## Verification

Completed in this workspace:

- `git diff --check`
- `./gradlew assembleDebug assembleRelease testDebugUnitTest --no-daemon`

## Future Release Hooks

- Android Keystore-backed encryption for stored username/password and refresh tokens.
- Websocket listener for server-triggered DB/log upload commands.
- Backend admin workflow to remap Android IDs if factory reset or bad firmware changes identity.
- Interface boundaries for playback, scheduler, installer, identity, storage, and network clients.
- Checksum validation of downloaded files if the server checksum format is confirmed.
- Foreground-service notification only if deployment target does not run as a managed kiosk app.
