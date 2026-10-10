# Coding style

Kotlin-only codebase built on `object` singletons, mixed-language comments
(Spanish + English), and swallow-and-log error handling. Match the language of
the file you edit; do not "modernize" style as a drive-by.

## Kotlin style

| Rule | Evidence |
|---|---|
| Singletons: Kotlin `object` for managers/parsers | `OrderStateManager.kt:16`, `PicapParser.kt:7`, `WhatsAppParser.kt:6`, `AccessibilityTree.kt:14`, `UpdateChecker.kt:25` |
| Nullability: elvis defaults everywhere (`?: ""`, `?: 0`, `?: return`) | `OrderStateManager.kt:51`, `PicapParser.kt:73-74` |
| Nullable parse returns | `WhatsAppParser.parse(text: String): WhatsAppService?` (`WhatsAppParser.kt:22`) |
| Java compat = `VERSION_11`; official Kotlin style | `app/build.gradle.kts:42-45`, `gradle.properties:15` |
| `@Suppress("DEPRECATION")` on every class touching `AccessibilityNodeInfo.recycle()` | `PicapOfferProcessor.kt:44`, `:83`, `WhatsAppResponder.kt:30` |
| No static analysis beyond AGP lint — no detekt/ktlint/.editorconfig | repo layout; `gradle.properties` |

## Comment language

Comment language is **mixed by design**:

- Spanish: picap/state/parser files — `PicapOfferProcessor.kt:23-27`,
  `OrderStateManager.kt:11-15`, `PicapParser.kt:25-44`.
- English: a11y/eval/update/location — `AccessibilityTree.kt:8-12`,
  `AutoAcceptEvaluator.kt:3-14`, `UpdateChecker.kt:33-36`.
- Both in one file: `MyAccessibilityService.kt:21-34`.

**Convention:** follow the language already used in the file you are editing.
Do not mix languages inside one comment block. Do not translate existing
comments. Product jargon inside comments stays as written.

## Logging

- Shared top-level tag `SERVICE_TAG = "MyAccessibilityService"`
  (`MyAccessibilityService.kt:25`) so a11y collaborators produce
  byte-identical logcat markers (`MyAccessibilityService.kt:22-23`). It is
  imported by `PicapOfferProcessor`, `PicapOfferCloser`, `KmOverlay`,
  `WhatsAppResponder`, `PicapAutoAcceptPolicy`, and `AccessibilityTree`.
- Other classes use private literal tags (`UpdateChecker.kt:27`,
  `OrderHistoryManager.kt:23`, `SavedLocationManager.kt:47`).
- Message prefix = uppercase subsystem marker as the first token:

| Prefix | Area (example) |
|---|---|
| `PICAP:` | Picap event pipeline (`PicapOfferProcessor.kt:159`) |
| `NO-CV:` | Non-Cruz-Verde close loop (`PicapOfferCloser.kt:58`) |
| `AUTO-ACCEPT:` | Accept decision + click (`PicapAutoAcceptPolicy.kt:73`) |
| `AUTOCLICK:` | List autoclick / notification click (`PicapOfferProcessor.kt:169`, `NotificationInterceptorService.kt:63`) |
| `PICAP-THROTTLE:` | Event throttle (`MyAccessibilityService.kt:23`) |
| `WHATSAPP:` | WhatsApp pipeline (`WhatsAppResponder.kt:77`) |
| `KM_OVERLAY:` | Overlay (`KmOverlay.kt:67`) |
| `AUTO-PLACA:` | Auto plate reply (`NotificationInterceptorService.kt:99`) |
| `AUTO-SERVICIO:` | Notification-parsed service accept (`NotificationInterceptorService.kt:133`) |
| `MEMORY:` | Dedupe/ID memory cache (`PicapListAutoclicker.kt:38`) |

## Error handling

Pattern everywhere: `try { … } catch (e: Exception) { Log.e(TAG, "...: ${e.message}") }` —
swallow + log, never rethrow. Rare `catch (t: Throwable)` on import paths
(`SavedLocationManager.kt:148`). Do not introduce Result types or exception
hierarchies in service code; match the surrounding style.

## Sentinels & magic values (policy, not accidents)

| Value | Meaning | Evidence |
|---|---|---|
| `999.0` | "km desconocido" sentinel | `AutoAcceptEvaluator.kt:50`, `PicapParser.kt:72-74`, `:103`, `:107`; consumers `?: 999.0` (`WhatsAppResponder.kt:138`, `NotificationInterceptorService.kt:164`) |
| `maxKm >= 5.0` | "umbral al máximo" — disables the km limit | `AutoAcceptEvaluator.kt:12-13`, `:66` |
| `NON_CV_CLOSE_DELAY_MS = 200L` | First non-CV close delay | `PicapOfferCloser.kt:15` |
| `CLICK_COOLDOWN_MS = 2000L` | Auto-accept click cooldown | `PicapOfferProcessor.kt:103` |

Product jargon/emoji hardcoded as **parse contracts** with the Picap UI:
`"ID: "` (`PicapParser.kt:26`), `"Tu ganancia final"` (`PicapParser.kt:31`),
`"Aceptar"` (`PicapOfferCloser.kt:153`), `"📏 …"` overlay text
(`KmOverlay.kt:70`). Do not "clean them up" — see
[architecture](architecture.md#parsers--decision-rules).

## Patterns to follow

- **Pure decision rules.** Decision logic lives in pure functions with no
  Android imports and no logging; each call site keeps its own log strings
  (`AutoAcceptEvaluator.kt:46-47`).
- **One definition per rule.** `PicapParser.isTrasladoDelivery` and
  `PicapParser.isOmsService` are shared by the policy AND the card
  (`PicapAutoAcceptPolicy.kt:100-101`, `OrderCard.kt:105-106`). Never fork the
  logic into a second copy.
- **KDoc threading contracts.** When a class has thread confinement, write it
  in the class KDoc (see
  [architecture](architecture.md#threading-contracts)).
- **Tests with parser/eval changes.** A parser or eval change ships with a
  unit test in the same commit.

## Domain rules that must NOT be duplicated or reintroduced

- Order type is **binary**: OMS = `isOmsService`, everything else = Mostrador.
  Card title precedence is **Traslado > OMS > Mostrador**
  (`OrderCard.kt:103-108`).
- Do **not** reintroduce a global Picap order-type pre-filter
  (`picapAutoAcceptFilter` was removed on purpose; per-condition selectors
  `picapAutoAcceptTypeCond1/2/3` at `OrderStateManager.kt:36-38` are the single
  source of truth — commit `5a978b4`).

## Known debt — do not make it worse

- Log tag fragmentation: 7+ independent string tags besides `SERVICE_TAG` —
  prefer `SERVICE_TAG` for anything accessibility-adjacent.
- Duplicated `kmOrigenRegex` (`WhatsAppResponder.kt:55` ==
  `NotificationInterceptorService.kt:19`) — if you must touch it, extract; do
  not create a third copy.
- Hardcoded personal data (default allowed plate senders
  `OrderStateManager.kt:73`, group name `NotificationInterceptorService.kt:131`)
  — do not add more.
- Possible `AccessibilityNodeInfo` recycle leaks in
  `PicapOfferProcessor.clickAcceptCandidates` and `WhatsAppResponder.findSendButton`
  — if you touch those paths, be careful; do not spread node retention.
- `OrderStateManager` god object — add fields only if unavoidable; prefer a
  focused manager for new concerns.

## See also

- [Architecture](architecture.md)
- [UI](ui.md)
- [Build, test & release](build-test-release.md)
- [AGENTS.md](../../AGENTS.md)
