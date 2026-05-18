# adcore-android

Android TV kiosk application for Adcore resource sync and video playback.

## Summary

- App name: `adcore`
- Package: `zonely.ams.adcore`
- Language: Java
- Minimum Android version: Android 9 / API 28
- Target SDK: 36
- Primary device type: dedicated Android TV box
- Entry activity: `SplashActivity`
- Main flow: `SplashActivity -> LoginActivity -> SyncActivity -> AdsActivity`

The implementation intentionally uses Android platform APIs for the core app: SQLite, `HttpURLConnection`, `AlarmManager`, `JobScheduler`, `Service`, `BroadcastReceiver`, `VideoView`, and `PackageInstaller`. This keeps the runtime predictable on locked-down TV devices.

## Implemented Requirements

### Activities

- `BaseActivity`
  - Implements common Android event interfaces.
  - Each framework callback delegates to a non-static empty `handle...` method, for example `onClick` delegates to `handleOnClick`.
  - Enforces immersive full-screen mode and keep-screen-on behavior.

- `SplashActivity`
  - Shows the dummy app logo for exactly 5 seconds.
  - Starts stored-credential login on a background thread.
  - Skips `LoginActivity` when the access token is ready before the splash completes.
  - Opens `LoginActivity` in indefinite "Logging in" mode when implicit login is still in progress.

- `LoginActivity`
  - Username/password inputs with required validations.
  - Hides the login button and shows an indefinite spinner during login.
  - Disables both inputs during login.
  - Persists credentials in plain SQLite for this release.
  - Contains code comments/placeholders for Android Keystore-backed encryption in a future release.

- `SyncActivity`
  - Calls `getMappedResources`.
  - Stores node/resource metadata in SQLite.
  - Downloads resources concurrently into the app-private `resources` folder.
  - Shows definite progress rows for mapped-resource fetch, DB write/diff, and every dynamic resource download.
  - Retries API calls according to DB-configured retry values.
  - If cached videos already exist, immediately starts `AdsActivity` and lets sync continue in the background.

- `AdsActivity`
  - Reads cached resources from SQLite in DB query order.
  - Plays local cached video files in an infinite loop.
  - Tracks daily play count and total play seconds per resource.

### Boot And Kiosk Behavior

- `BootReceiver` listens for boot/package replacement events and launches `SplashActivity`.
- The app includes Leanback launcher support for Android TV.
- The app is designed for kiosk/default-launcher/device-owner provisioning. Newer Android versions can restrict background activity launch unless the device is managed or configured as a kiosk/default launcher.

### Device Id

`DeviceIdProvider` uses the TV box serial as the primary `deviceId`:

1. `Build.getSerial()` when permitted.
2. `ro.serialno` system property fallback.
3. `Settings.Secure.ANDROID_ID` fallback.
4. Generated UUID fallback stored in SQLite.

For production TV boxes, OEM firmware or device-owner provisioning may be required for the printed serial number to be readable without user prompts.

### Sync And Daily Pull

- First sync and daily pull share the same sync engine.
- Daily pull compares resources by `id`.
- New resources are inserted and downloaded.
- Missing resources are removed from SQLite and their local video file is deleted.
- Matching resources are checked for checksum and property changes.
- Checksum changes clear the stale local cache path and force a fresh download.
- Last successful daily sync timestamp is stored in SQLite.
- If the device missed 00:30 while powered off, `SyncActivity` detects the missed daily sync and refreshes resources as part of its run.

### Background Jobs

- Daily resource pull:
  - Exact 00:30 alarm schedules an immediate `JobScheduler` sync job.
  - The next exact alarm is rescheduled after every alarm fire.

- Hourly log scan:
  - Scans logs newest to oldest for `ERROR` entries.
  - Zips relevant log files and posts them to `sendErrorLogs`/`saveDeviceLogs`.
  - Uses a SHA-256 signature to avoid sending the same error archive repeatedly.

- Daily app update check:
  - Calls `downloadLatestApp` with `BuildConfig.VERSION_NAME`.
  - Treats HTTP 404 as "already latest".
  - Downloads the APK in the background.
  - Uses `PackageInstaller` for silent install when the app is device owner.
  - Falls back to installer UI if the app is not device owner.

- Previous-day audit:
  - Runs once after successful sync.
  - Sends data to `saveDeviceData`.
  - Includes user/device/app metadata, uptime totals, sync totals, play time, and per-resource play counts.

### Logging

- `AdcoreLogger` writes industry-style timestamped logs:
  - UTC timestamp
  - level
  - process id
  - thread name
  - tag
  - message
  - full stacktrace when present
- Rolling policy:
  - Maximum 10 log files.
  - Maximum 10 MB per log file.
  - No intentional truncation of message/stacktrace data.

### Export Services

- `DatabaseExportService`
  - Prepares a zipped SQLite DB export.
  - Keeps at most one DB export file.
  - Upload method is already wired for future websocket-triggered server pull.

- `LogArchiveExportService`
  - Prepares a zipped archive of all app log files.
  - Keeps at most one full-log archive file.
  - Upload method is already wired for future websocket-triggered server pull.

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

Silent install and reliable boot/kiosk behavior require managed-device provisioning. Typical deployment options:

- Provision Adcore as device owner during factory setup.
- Configure Adcore as the default launcher/kiosk app.
- Grant required runtime permissions through device policy/OEM tooling.
- Ensure the TV firmware exposes the printed serial number to device-owner apps.

Without device-owner or OEM privileges:

- Android may block silent APK installation and require installer UI.
- Android may restrict background activity launch after boot.
- The printed serial number may be unavailable, causing fallback to `ANDROID_ID`.

## Build

This workstation was missing an Android SDK and was using GraalVM, whose `jlink` failed during the Android Gradle Plugin JDK-image transform. A local SDK and standard Temurin JDK 21 were installed under the user home directory.

The project currently builds with:

```bash
JAVA_HOME=/Users/sannaidu/.jdks/jdk-21.0.11+10/Contents/Home \
PATH=/Users/sannaidu/.jdks/jdk-21.0.11+10/Contents/Home/bin:$PATH \
./gradlew assembleDebug --no-daemon
```

Unit tests:

```bash
JAVA_HOME=/Users/sannaidu/.jdks/jdk-21.0.11+10/Contents/Home \
PATH=/Users/sannaidu/.jdks/jdk-21.0.11+10/Contents/Home/bin:$PATH \
./gradlew testDebugUnitTest --no-daemon
```

Generated APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Verification

Completed locally:

- `assembleDebug` - successful
- `testDebugUnitTest` - successful
- `lintDebug` - successful

## Future Release Hooks

- Replace plain credential storage with Android Keystore-backed encryption.
- Add websocket listener for server-triggered DB/log upload commands.
- Add OEM/device-policy integration scripts for device-owner provisioning.
- Add checksum validation of downloaded files if the server checksum format is confirmed.
- Add foreground-service notification only if deployment target does not run as a managed kiosk app.
