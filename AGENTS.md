# Repository Guidelines

This workspace holds **two independent Android projects** plus a built APK. There is no
root Gradle build, no root package manifest, and no shared code. Each project is built and
tested from its own directory.

```
Mobile Apps/
├── grade-pulse/winners-whatsapp-app/   # GradePulse — git repo (source of truth)
│   └── GradePulse-local-debug.apk      # built debug APK (18.4 MB, fully offline)
└── brahma-connect-android-app/         # Brahma Connect Android client (copy of monorepo)
```

---

## Project Overview

### GradePulse (`grade-pulse/winners-whatsapp-app/`)
Kotlin + Jetpack Compose app for **Winners Family School**. Teachers manage students, upload
report-card PDFs/images, and dispatch them to parents **pre-filled in WhatsApp** (via
`ACTION_SEND` / `ACTION_SEND_MULTIPLE` intents). Runs **fully local**: no Firebase, no
network, no Google account. Data lives in `SharedPreferences` JSON scoped per device profile.

| | |
|---|---|
| Package / namespace | `com.aistudio.gradepulse.kxmprq` / `com.example` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 (+36.1) |
| AGP / Kotlin | 9.1.1 / 2.2.10 |
| Remote | https://github.com/eminent337/Winners_whatsapp |

### Brahma Connect (`brahma-connect-android-app/`)
Native Android **agent** for the Brahma Connect gateway. Discovers a gateway (mDNS
`_BRAHMA._tcp` / QR / manual IP), pairs over WebSocket, then executes device commands
(battery, flashlight, volume, launch app, open URL, file ops, UI dumps via Accessibility).
**Not** an AI assistant or dashboard — a bridge client.

| | |
|---|---|
| Package / namespace | `com.brahma.connect` |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| AGP / Kotlin | 8.5.2 / 2.0.21 |
| Canonical source | `~/tmp/brahma-study/brahma-connect-android` (monorepo, 41 files — identical count) |

---

## Architecture & Data Flow

### GradePulse — local-first MVVM
Single-module, **no DI framework**. Manual wiring via `viewModelFactory`.

```
MainActivity → AppNavigation() → AuthScreen | GradePulseMainScreen
                     │                        │
              authStateFlow()          GradePulseViewModel.provideFactory(uid)
              (LocalProfile)           ├─ GradePulseRepository   (SharedPreferences JSON)
                                       └─ UploadedFolderRepository (filesDir + folder_index.json)
                                                  │
              WhatsAppDispatcher → WhatsAppDispatchService (non-exported Service)
                                                  │
                                        WhatsApp (external app, via Intent)
```

- **`GradePulseRepository`** — the data layer. Holds `MutableStateFlow`s for students,
  report cards, and chat. Every mutation updates the flow then `persist*()` writes JSON to
  `SharedPreferences` (`gradepulse_local_store`). **Every public method returns
  `Result<T>`** (via `runCatching`) and keeps the exact signature of the original
  Firestore-backed repo, so the ViewModel needed no changes when Firebase was removed.
  Exposes `observe*(): Flow<List<T>>` that mimic Firestore snapshot listeners.
- **`UploadedFolderRepository`** — manages imported report files on disk
  (`filesDir/uploaded_report_cards/<userId>/`), a `folder_index.json` index, student
  auto-matching, and PDF generation. Files live outside `SharedPreferences`.
- **State**: `StateFlow<UiState<T>>` where `UiState = Loading | Success(data) | Error(msg)`,
  built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5s), Loading)`.
- **WhatsApp dispatch**: `WhatsAppDispatcher` (object) builds intents; `WhatsAppDispatchService`
  validates a per-process `SecureRandom` nonce, checks path traversal, and launches WhatsApp.
  Results surface through a singleton `StateFlow<WhatsAppDispatchEvent?>`.

### Brahma Connect — event-driven agent with global state
**No DI, no ViewModel.** A singleton `object AgentStateStore` holds all observable state as
`MutableStateFlow`s (connection, gateway, credential, status, logs, chat). The foreground
service owns the WebSocket client and drives state; Compose screens collect from
`AgentStateStore` directly.

```
MainActivity → BrahmaConnectForegroundService (CoroutineScope(Dispatchers.IO))
                     │        ├─ PairingStorage (EncryptedSharedPreferences)
                     │        ├─ BrahmaGatewayDiscovery (NsdManager)
                     │        └─ BrahmaWebSocketClient (OkHttp WebSocket)
                     │                 ├─ BrahmaProtocol (JSON envelope: type/request_id/payload)
                     │                 └─ DeviceCommandHandler → BrahmaAccessibilityService.instance
                     └─ AgentStateStore (object, StateFlows) ← Compose UI (BrahmaConnectApp NavHost)
```

- **Protocol**: JSON envelopes via `BrahmaProtocol.envelope(type, payload)` →
  `{type, request_id, timestamp, payload}`. Message types: `hello`, `pair_request`,
  `pair_approved`, `authenticate`, `execute`, `result`, `chat_message`, `ping`/`pong`.
- **Pairing**: QR (ML Kit barcode) → `PairingPayloadParser.parse()` → `PairingOffer` JSON →
  stored in `EncryptedSharedPreferences` (`brahma_connect_secure`, AES256).
- **Reconnect**: exponential backoff (`reconnectAttempt`, capped) in `BrahmaSocketListener`.
- **Commands**: `DeviceCommandHandler.handle(action, params)` returns `CommandResult`.
  UI/file commands delegate to the `BrahmaAccessibilityService` singleton (must be enabled
  by the user in system Accessibility settings).

---

## Key Directories

### GradePulse (`grade-pulse/winners-whatsapp-app/`)
```
app/src/main/java/com/example/
├── MainActivity.kt              # entry, AppNavigation, auth gate
├── auth/GoogleAuthManager.kt    # local profile auth (LocalProfile, authStateFlow, AuthScreen)
├── data/
│   ├── model/                   # GradePulseModels.kt, UploadedReportFile.kt (data classes)
│   ├── repository/              # GradePulseRepository.kt, UploadedFolderRepository.kt
│   └── FirestoreErrorHandler.kt # error formatting (kept for API compat)
├── service/                     # WhatsAppDispatchService.kt, WhatsAppIntentPayload.kt
├── ui/
│   ├── screens/GradePulseMainScreen.kt  # main Scaffold, tabs, dialogs (1157 lines)
│   ├── components/              # dialogs, chat views, report visuals
│   ├── viewmodel/GradePulseViewModel.kt # MVVM state + actions
│   └── theme/                   # Color.kt, Theme.kt, Type.kt (emerald/gold/ivory palette)
└── util/WhatsAppDispatcher.kt   # intent builders + FileProvider helpers
```

### Brahma Connect (`brahma-connect-android-app/`)
```
app/src/main/java/com/brahma/connect/
├── MainActivity.kt, BrahmaConnectApplication.kt, BrahmaConnectForegroundService.kt
├── accessibility/BrahmaAccessibilityService.kt   # UI automation (singleton instance)
├── commands/DeviceCommandHandler.kt              # command dispatch + handlers
├── core/AppModels.kt (models + BrahmaProtocol), AgentStateStore.kt (global state)
├── device/AndroidDeviceInfoProvider.kt           # battery/wifi/model snapshot
├── network/BrahmaGatewayDiscovery.kt (mDNS), BrahmaWebSocketClient.kt (OkHttp WS)
├── pairing/PairingPayloadParser.kt, PairingStorage.kt (EncryptedSharedPreferences)
└── ui/BrahmaConnectApp.kt (NavHost), ChatScreen.kt, theme/Theme.kt
app/src/main/assets/web_background/   # Three.js holographic WebView background
```

---

## Development Commands

### Environment (this machine)
- **JDK 17**: `~/jdk17` (Temurin 17.0.21). Set `JAVA_HOME=~/jdk17`.
- **Android SDK**: `~/android-sdk` (build-tools 36.0.0, platforms 36 & 36.1).
- **Gradle 9.3.1**: `~/gradle-9.3.1/bin/gradle` (standalone, no `gradlew` in GradePulse).
- **gh CLI**: authenticated as `eminent337`.

### GradePulse
```bash
cd grade-pulse/winners-whatsapp-app
export JAVA_HOME=~/jdk17

# One-time setup (local.properties is gitignored)
printf 'sdk.dir=%s\n' "$HOME/android-sdk" > local.properties

# Debug keystore — REQUIRED: debugConfig signing config references ${rootDir}/debug.keystore
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

# Build debug APK (no gradlew — use standalone gradle)
gradle :app:assembleDebug --no-daemon
# -> app/build/outputs/apk/debug/app-debug.apk

# Run all unit/Robolectric tests (failFast, 2 min timeout per test)
gradle :app:testDebugUnitTest --no-daemon

# Lint
gradle :app:lintDebug --no-daemon
```
> The repo has **no `gradlew` wrapper** (only `gradle/wrapper/gradle-wrapper.properties`
> pointing at 9.3.1). CI installs Gradle 9.3.1 explicitly. Running
> `gradle wrapper --gradle-version 9.3.1` once would fix this permanently.
> The `secrets` plugin reads `.env` (gitignored); `.env.example` documents keys.

### Brahma Connect
```bash
cd brahma-connect-android-app
export JAVA_HOME=~/jdk17

# local.properties is gitignored — create it (no committed sdk.dir)
printf 'sdk.dir=%s\n' "$HOME/android-sdk" > local.properties

# Has gradlew (wrapper → Gradle 8.7, AGP 8.5.2)
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:testDebugUnitTest
./gradlew :app:lint
```

---

## Code Conventions & Common Patterns

### Both projects
- **Kotlin, official code style** (`kotlin.code.style=official`). Compose for all UI.
- **Coroutines + Flow** for async/state. `kotlinx-coroutines-android` + `StateFlow`.
- **Manual DI**: no Hilt/Dagger. Repositories/clients are constructed directly or via
  `viewModelFactory { initializer { ... } }` / service `onCreate`.
- **Data models**: `data class` with defaults; JSON via `org.json.JSONObject`/`JSONArray`
  (no Moshi/kotlinx.serialization in use).
- **Error handling**: GradePulse uses `Result<T>` + `runCatching` everywhere in the repo
  layer. Brahma returns `CommandResult(success, data, errorCode, error)`.

### GradePulse specifics
- **Repository contract is sacred**: keep `Result<T>` signatures and `observe*(): Flow`
  shape. The ViewModel is written against them; changing signatures breaks it.
- **Timestamps are `Long` epoch millis**, not `Timestamp`. `SimpleDateFormat` for display
  labels (e.g. `"MMM d, yyyy • h:mm a"`).
- **Input sanitization** on every write: `.trim().take(n)` + range coercion
  (e.g. `attendanceDays.coerceIn(0, 366)`). Mirror the bounds in `firestore.rules`.
- **IDs**: `UUID.randomUUID().toString()`.
- **Package `com.example`** but `applicationId` `com.aistudio.gradepulse.kxmprq` —
  namespace ≠ applicationId is intentional; don't "fix" it.
- **Tests are partly stale**: `GradePulseRepositoryRuleTest` and `FirestoreEmulatorTestBase`
  still import `com.google.firebase.*` and call the old Firestore `GradePulseRepository(
  firestore, auth)` constructor — they will **not compile** against the current local
  repository. `ExampleRobolectricTest`, `ExampleUnitTest`, and
  `WhatsAppDispatchServiceTest` are current. Treat the Firebase tests as historical.

### Brahma Connect specifics
- **State lives in `object AgentStateStore`** — never create a second source of truth;
  read/write through it so the service and UI stay in sync.
- **JSON keys are `snake_case`** (`device_id`, `pairing_token`, `request_id`).
- **Accessibility-dependent features** (UI dump/tap/swipe/type) require
  `BrahmaAccessibilityService.instance` to be non-null (user must enable the service).
  Guard with null checks; it is null until `onServiceConnected`.
- **Cleartext traffic is allowed** (`android:usesCleartextTraffic="true"`) for local gateway
  HTTP/WebSocket — intentional, don't disable.

---

## Important Files

### GradePulse
- `app/src/main/java/com/example/MainActivity.kt` — entry + auth gate.
- `app/src/main/java/com/example/data/repository/GradePulseRepository.kt` — core data layer.
- `app/src/main/java/com/example/data/repository/UploadedFolderRepository.kt` — file/folder store.
- `app/src/main/java/com/example/ui/viewmodel/GradePulseViewModel.kt` — all UI state/actions.
- `app/src/main/java/com/example/service/WhatsAppDispatchService.kt` — WhatsApp launch + nonce.
- `app/src/main/java/com/example/data/model/GradePulseModels.kt` — models + `buildWhatsAppReportMessage`.
- `app/build.gradle.kts` — AGP 9.1.1, compileSdk 36.1, signingConfigs (debugConfig/release).
- `gradle/libs.versions.toml` — version catalog (many deps commented out in app build).
- `.github/workflows/deploy-apk.yml` — CI: JDK17 → Gradle 9.3.1 → assembleDebug → GitHub Releases + Pages.
- `firestore.rules`, `firebase.json`, `firestore.test.js`, `firebase-blueprint.json` — **legacy** Firestore artifacts kept for reference; the app no longer uses them.

### Brahma Connect
- `app/src/main/java/com/brahma/connect/core/AppModels.kt` — models + `BrahmaProtocol`.
- `app/src/main/java/com/brahma/connect/core/AgentStateStore.kt` — global state + capabilities.
- `app/src/main/java/com/brahma/connect/network/BrahmaWebSocketClient.kt` — WS lifecycle/reconnect.
- `app/src/main/java/com/brahma/connect/commands/DeviceCommandHandler.kt` — command handlers.
- `app/src/main/java/com/brahma/connect/pairing/PairingStorage.kt` — encrypted credential store.
- `app/build.gradle.kts` — AGP 8.5.2, compileSdk 35, no version catalog (versions inline).
- `app/src/main/AndroidManifest.xml` — permissions, foreground service, accessibility service.

---

## Runtime / Tooling Preferences

- **Runtime**: Android apps; build host needs **JDK 17** for both projects
  (GradePulse `compileOptions` targets Java 11; Brahma targets Java 17).
- **Package manager**: Gradle. GradePulse has **no wrapper** (use `~/gradle-9.3.1`);
  Brahma uses the committed `gradlew` (Gradle 8.7).
- **No build-time code generation** in active use (KSP/room/moshi deps are commented out
  in GradePulse; Brahma has none).
- **No DI framework**, **no serialization library** — `org.json` only.
- **Brahma is a copy**, not authoritative. The canonical source is the `brahma-study`
  monorepo at `~/tmp/brahma-study/brahma-connect-android`. Check there before assuming
  this copy is the source of truth.

---

## Testing & QA

### GradePulse
- **Frameworks**: JUnit4, Robolectric 4.16.1, `kotlinx-coroutines-test`, Compose UI test,
  Espresso (androidTest). Runner `androidx.test.runner.AndroidJUnitRunner`.
- **Config**: `testOptions.unitTests.isIncludeAndroidResources = true`; `Test` tasks are
  `failFast = true` with a 2-minute timeout.
- **Run**: `gradle :app:testDebugUnitTest` (unit/Robolectric),
  `gradle :app:connectedDebugAndroidTest` (instrumented, needs device/emulator).
- **Coverage**: no coverage tooling configured. No minimum threshold.
- ⚠️ Two Firebase-era test files won't compile against the current local repository (see
  Conventions). Run the current tests (`ExampleRobolectricTest`,
  `WhatsAppDispatchServiceTest`, `ExampleUnitTest`) and update/remove the stale ones when
  touching the data layer.

### Brahma Connect
- **Frameworks**: JUnit4 + `androidx.test` (runner `AndroidJUnitRunner`). Test source dirs
  exist but the repo ships **no test files** (0 tests). `testDebugUnitTest` is effectively a no-op.
- **Coverage**: none configured.
- **Manual QA**: requires a running Brahma gateway to pair against (mDNS/QR/manual IP).
  UI-automation commands need the Accessibility service enabled in system settings.

---

## CI / Release

- **GradePulse** (`.github/workflows/deploy-apk.yml`): on push to `main`/`master` or manual
  dispatch — sets up JDK 17, Android SDK, Gradle 9.3.1, generates `debug.keystore`, runs
  `gradle :app:assembleDebug --no-daemon`, publishes the APK to **GitHub Releases** (`latest`
  tag) and a **GitHub Pages** install page. The signed release build uses env vars
  (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) and is not exercised by CI.
- **Brahma Connect**: no CI workflow in this copy.
