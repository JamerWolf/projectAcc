# Build, test & release

Windows-first, single-module Gradle project. Debug builds are portable;
release signing is machine-bound. JUnit 4 only; no CI.

## Gradle facts

| Fact | Value | Evidence |
|---|---|---|
| Modules | Single `:app`; `rootProject.name = "project acc"` (space included) | `settings.gradle.kts:25-26` |
| namespace / applicationId | `com.example.projectacc` | `app/build.gradle.kts:7`, `:15` |
| SDKs | compileSdk 36 (minorApiLevel 1), minSdk 24, targetSdk 36 | `app/build.gradle.kts:8-17` |
| Version | `versionCode = 69`, `versionName = "1.0.4"` — **manual** versioning | `app/build.gradle.kts:34-35` |
| Plugins | `com.android.application` (AGP 9.0.0) + `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.2.10). **No separate kotlin-android plugin** — AGP 9 ships built-in Kotlin | `app/build.gradle.kts:1-4`, `gradle/libs.versions.toml:2`, `:9`, `:31-32` |
| Compose BOM | `2026.02.01` | `gradle/libs.versions.toml:10` |
| Build types | Only `release`, no flavors, `isMinifyEnabled = false` | `app/build.gradle.kts:32-41` |
| Java compat | `VERSION_11` | `app/build.gradle.kts:42-45` |
| Gradle wrapper | 9.3.1 | `gradle/wrapper/gradle-wrapper.properties:5` |
| Static analysis | AGP lint only — no detekt/ktlint/.editorconfig | repo layout; `gradle.properties` |
| Key deps (catalog) | junit 4.13.2, espresso 3.7.0, androidx.test junit 1.3.0, coreKtx 1.18.0, play-services-location 21.3.0 | `gradle/libs.versions.toml` |

### Release signing (fact, not a secret)

`app/build.gradle.kts` builds the `release` signing config from four
properties, read from `local.properties` (gitignored) or the environment:

| Property | Value on the reference machine |
|---|---|
| `RELEASE_STORE_FILE` | `~/keystores/projectacc-release.jks` (outside the repo) |
| `RELEASE_STORE_PASSWORD` | random, in `local.properties` only |
| `RELEASE_KEY_ALIAS` | `projectacc` |
| `RELEASE_KEY_PASSWORD` | same as the store password |

The keystore is **not** in git. Back it up off-machine together with
`local.properties`; without both, a machine loss is unrecoverable.

If the properties are missing, AGP fails at `validateSigningRelease` instead
of emitting an unsigned APK.

**History:** releases up to 1.0.3 were signed with a `debug.keystore` living
in a Windows user profile. That keystore was lost when the machine was
formatted, so the signing identity changed at 1.0.4. Existing installs cannot
receive 1.0.4 as an update — they must be uninstalled and reinstalled, losing
SharedPreferences (settings, saved locations, order history). Never store the
signing key only inside a machine profile again.


## Commands (Windows)

| Task | Command | Output / note |
|---|---|---|
| Debug APK | `.\gradlew.bat assembleDebug` | `app\build\outputs\apk\debug\app-debug.apk` |
| Compile only (fast check) | `.\gradlew.bat :app:compileDebugKotlin` | — |
| All unit tests | `.\gradlew.bat :app:testDebugUnitTest` | — |
| One test class | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.projectacc.parser.PicapParserTest"` | — |
| Lint | `.\gradlew.bat :app:lintDebug` | — |
| Instrumented (device needed) | `.\gradlew.bat :app:connectedDebugAndroidTest` | — |
| Clean | `.\gradlew.bat clean` | — |

Both `gradlew` and `gradlew.bat` exist; on Windows use `.\gradlew.bat` (cmd)
or `.\gradlew` (PowerShell).

## JDK notes

- Daemon JVM is pinned to **21** via `gradle/gradle-daemon-jvm.properties:12`
  (`toolchainVersion=21`) with foojay auto-provision URLs — the daemon JDK is
  provisioned **independently of `JAVA_HOME`**.
- There is **no** `jvmToolchain { }` block and **no** `org.gradle.java.home`
  in this repo.
- `JAVA_HOME` is only consulted by `gradlew.bat` to launch the wrapper.

### Troubleshooting: broken `JAVA_HOME`

Observed on this machine: if `JAVA_HOME` points at a broken/incomplete JDK,
Gradle fails before the daemon starts. Temporary per-run fix:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
```

(Any real JDK works.) This is a machine workaround, not project config — do
not commit it.

## Test conventions

- Locations: `app/src/test/java/com/example/projectacc/...` (JVM) and
  `app/src/androidTest/...` (device).
- Framework: **JUnit 4.13.2 only**. No Robolectric, no Mockito/MockK, no Truth.
  `compose-ui-test-junit4` is on the androidTest classpath
  (`app/build.gradle.kts:63-68`).
- Current suite: `ExampleUnitTest.kt` (template) +
  `parser/PicapParserTest.kt` (3 real tests) + 1 instrumented template test.
- Test names: **backticked Spanish sentences** describing behavior, e.g.
  `` fun `parsea entrega clasica con id interpuesto`() ``
  (`PicapParserTest.kt:12`). Backtick names must **not** contain `"` — it
  breaks on Windows tooling.
- Assertions: `org.junit.Assert.assertEquals` (JUnit 4). No mocking framework
  — prefer testing pure functions instead of introducing mocks.
- Unit-testable without refactoring: `eval.AutoAcceptEvaluator` (declared
  pure), `WhatsAppParser.parse`, `PicapParser.parseOrder` /
  `extractKmFromPickup`. Anything touching `AccessibilityNodeInfo`,
  `SharedPreferences`, `WindowManager`, or a `Context` parameter is not.
- A parser/eval change ⇒ add a test in the same commit.

## Git & commit conventions

- Default branch `master`; active development branch
  `refactor/my-accessibility-service`.
- Commit style: **Conventional Commits, English, imperative**. Types seen:
  `feat`, `fix`, `refactor`, `perf`, `chore`; optional scope `feat(picap):`.
  Real examples: `refactor: remove redundant global Picap order-type filter`,
  `feat: identify Traslado orders on card with shared delivery rule`,
  `perf: single-pass scan, off-main pipeline and overlay after click`,
  `chore: release 0.3.33 (versionCode 48)`.
- Branch naming: `<type>/<kebab-description>` or `downgrade/v<version>`.
- No CI (no `.github/`), no active git hooks (only `*.sample`). `.gitignore`
  covers `/build`, `.gradle`, `/local.properties`, selective `.idea`
  (`.gitignore:1-15`). Agent tooling dirs (`.codegraph/`, `.atl/`,
  `.gentle-review/`, `.engram/`) are on disk and **not** ignored — do not
  commit them.

## Release process

In-app updater (`update/UpdateChecker.kt:28-31`) reads
`https://api.github.com/repos/JamerWolf/projectAcc/releases/latest`, strips the
`v` prefix from `tag_name` (`UpdateChecker.kt:55-56`), picks the first asset
ending in `.apk`, and compares it semver-ish to `versionName`.

Checklist:

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts:34-35`.
2. Commit `chore: release X.Y.Z (versionCode N)`.
3. `assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
   Verify with `apksigner verify --print-certs` that the signer is the current
   keystore, not an unsigned or foreign build.
4. Create a GitHub Release with a `vX.Y.Z` tag and attach that `.apk` asset.

If the signer identity changed (a lost keystore), the release notes **must**
say the previous version has to be uninstalled first: existing installs
reject an APK signed with a different key.

## Manifest permissions & services

Permissions (`app/src/main/AndroidManifest.xml:5-11`): `SYSTEM_ALERT_WINDOW`,
`POST_NOTIFICATIONS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
`ACCESS_BACKGROUND_LOCATION`, `INTERNET`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

| Component | Manifest notes |
|---|---|
| `.MyAccessibilityService` | `BIND_ACCESSIBILITY_SERVICE`, config `@xml/accessibility_service_config` (`:41-52`) |
| `.NotificationInterceptorService` | `BIND_NOTIFICATION_LISTENER_SERVICE` (`:54-61`) |
| `MainActivity` | launcher (`:22-32`) |
| `WhatsAppForwardActivity` | translucent theme, excluded from recents (`:34-39`) |
| FileProvider | authority `${applicationId}.fileprovider` (`:63-71`) |

## See also

- [Architecture](architecture.md)
- [Coding style](coding-style.md)
- [UI](ui.md)
- [AGENTS.md](../../AGENTS.md)
