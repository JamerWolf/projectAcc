# project acc — Agent Guide

Single-module Android app (`:app`, namespace `com.example.projectacc`,
`app/build.gradle.kts:7`) that automates Picap courier order acceptance. It
listens to the Picap app via an `AccessibilityService`, intercepts WhatsApp
notifications via a `NotificationListenerService`, auto-accepts orders that
match configurable ganancia/km rules, auto-replies with the vehicle plate, and
shows an on-screen km overlay. UI is Jetpack Compose + Material3
(`app/build.gradle.kts:46-48`); current version `0.3.49`
(`app/build.gradle.kts:18-19`).

This file is the entry point. Detail lives in `docs/conventions/`.

## Quick path

Windows-first commands (wrapper `gradlew.bat`, Gradle 9.3.1
`gradle/wrapper/gradle-wrapper.properties:5`):

1. **Compile check:** `.\gradlew.bat :app:compileDebugKotlin`
2. **Unit tests:** `.\gradlew.bat :app:testDebugUnitTest`
3. **Debug APK:** `.\gradlew.bat assembleDebug` →
   `app\build\outputs\apk\debug\app-debug.apk`
4. **Lint:** `.\gradlew.bat :app:lintDebug`

Full command table, JDK notes, and release steps:
[build-test-release](docs/conventions/build-test-release.md).

## Global non-negotiables

| Rule | Why |
|---|---|
| `OrderStateManager.init(context)` must run in `MainActivity.onCreate` | `MainActivity.kt:84`. If skipped, `prefs` stays null and every setting write silently no-ops (`OrderStateManager.kt:42-47`) |
| No global Picap order-type pre-filter | `picapAutoAcceptFilter` was removed on purpose; per-condition selectors `picapAutoAcceptTypeCond1/2/3` (`OrderStateManager.kt:36-38`) are the single source of truth (commit `5a978b4`) |
| Order type is binary; card title = Traslado > OMS > Mostrador | `PicapParser.isOmsService` (`PicapParser.kt:92-95`), `OrderCard.kt:103-108` |
| Decision rules stay pure, one definition, reused everywhere | `AutoAcceptEvaluator.kt:46-47`; shared `isTrasladoDelivery`/`isOmsService` used by policy AND card |
| km sentinel `999.0` = "km desconocido"; `maxKm >= 5.0` = limit disabled | `AutoAcceptEvaluator.kt:50`, `:66`; `PicapParser.kt:100-108` |
| Product-UI anchors are parse contracts — never "clean them up" | `"ID: "`, `"Tu ganancia final"`, `"Aceptar"`, `"📏 …"` (`PicapParser.kt:26-31`, `KmOverlay.kt:70`) |
| UI copy stays hardcoded Spanish; identifiers stay English | Zero `stringResource` in Kotlin; `res/values/strings.xml:2-3` is app name + a11y description only |
| A parser/eval change ships with a unit test in the same commit | `PicapParserTest.kt` is the template |
| Release signing is machine-bound | `app/build.gradle.kts:24-31` points at a local debug keystore with hardcoded credentials → `assembleRelease` fails elsewhere. Never copy the password values anywhere |
| Comment language = language already used in that file | Mixed Spanish/English by area; see [coding-style](docs/conventions/coding-style.md#comment-language) |
| Errors: swallow + log (`catch (e: Exception)` → `Log.e`), never rethrow | House pattern across services |

## Domain glossary

| Term | Meaning |
|---|---|
| Picap | Delivery/courier app whose UI this app automates via accessibility |
| Order | A Picap offer (`model/PicapOrder.kt`) parsed from the accessibility tree |
| Ganancia | Payout in COP, parsed from the `"Tu ganancia final"` line (`PicapParser.kt:31`) |
| OMS | Order type: servicio contains "cruz verde" AND "integracion"/"integración" (`PicapParser.kt:92-95`) |
| Mostrador | Everything that is not OMS (binary classification, commit `6d0ceb8`) |
| Traslado | Delivery whose address contains "cruz verde" or matches a saved location (`PicapParser.kt:84-86`) |
| Non-CV | Order without a Cruz Verde node → auto-closed after `NON_CV_CLOSE_DELAY_MS = 200L` (`PicapOfferCloser.kt:15`) |
| Auto-accept | Rule engine (`eval/AutoAcceptEvaluator.kt`) that decides whether to click "Aceptar" |
| Km overlay | `TYPE_APPLICATION_OVERLAY` TextView showing total km next to the price node (`picap/KmOverlay.kt`) |
| Auto-placa | Auto-reply of the vehicle plate in WhatsApp on a phrase match (`NotificationInterceptorService.kt:99`) |
| Umbral al máximo | `maxKm >= 5.0` disables the km limit (`AutoAcceptEvaluator.kt:66`) |
| `serviceScope` | The single coroutine scope owned by `MyAccessibilityService`, injected into collaborators |

## Docs index

| File | Purpose | Lines |
|---|---|---|
| [docs/conventions/architecture.md](docs/conventions/architecture.md) | Package map, 5 runtime pipelines, parsers & decision rules, state/persistence, threading contracts | 141 |
| [docs/conventions/coding-style.md](docs/conventions/coding-style.md) | Kotlin style, comment language, logging, error handling, sentinels, patterns, known debt | 124 |
| [docs/conventions/ui.md](docs/conventions/ui.md) | Compose Material3 screens, Spanish UI strings, scroll rules, navigation flags, non-Compose overlays | 109 |
| [docs/conventions/build-test-release.md](docs/conventions/build-test-release.md) | Gradle facts, commands, JDK notes, tests, git/commits, release, manifest | 138 |

## Known debt (read before large changes)

Summarized from [architecture](docs/conventions/architecture.md#known-debt--do-not-make-it-worse)
and [coding-style](docs/conventions/coding-style.md#known-debt--do-not-make-it-worse):

- Two full WhatsApp pipelines (`WhatsAppResponder` vs
  `NotificationInterceptorService`) — behavioral changes usually land in both.
- `OrderStateManager` is a ~405-line god object with a silent `init` failure.
- Duplicated `kmOrigenRegex`; `AutoAcceptConfig(...)` assembled 3×.
- Hardcoded personal data (allowed plate senders, one group name) in source.
- Possible `AccessibilityNodeInfo` recycle leaks in accept-click / send-button paths.
- No MVVM/NavHost/DI; the WhatsApp popup is a classic View, everything else Compose.

Do not "fix" these as drive-bys; do not make them worse.

## See also

- [Architecture](docs/conventions/architecture.md)
- [Coding style](docs/conventions/coding-style.md)
- [UI](docs/conventions/ui.md)
- [Build, test & release](docs/conventions/build-test-release.md)
