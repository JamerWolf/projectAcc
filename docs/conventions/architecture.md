# Architecture

Single-module Android app (`:app`) that automates Picap courier order
acceptance via an AccessibilityService, a NotificationListenerService, and a
Compose UI. This page maps packages, pipelines, state, and threading.

## Package map

Source root: `app/src/main/java/com/example/projectacc/` (namespace
`com.example.projectacc`, `app/build.gradle.kts:7`).

| Package | Files | Role |
|---|---|---|
| `(root)` | `MyAccessibilityService.kt`, `NotificationInterceptorService.kt`, `OrderStateManager.kt`, `OrderHistoryManager.kt`, `OrderCard.kt`, `MainActivity.kt`, `WhatsAppForwardActivity.kt`, `WhatsAppIntentHolder.kt` | Service entry points, state singletons, order card, activity shell |
| `a11y` | `AccessibilityTree.kt` | Stateless `AccessibilityNodeInfo` read/act helpers |
| `picap` | `PicapOfferProcessor.kt`, `PicapEventThrottle.kt`, `PicapOfferCloser.kt`, `PicapListAutoclicker.kt`, `PicapAutoAcceptPolicy.kt`, `KmOverlay.kt` | Picap pipeline, auto-click, non-CV close, km overlay |
| `parser` | `PicapParser.kt`, `WhatsAppParser.kt` | Raw text → `model` extraction |
| `eval` | `AutoAcceptEvaluator.kt` | Pure auto-accept rules (no Android imports) |
| `model` | `PicapOrder.kt`, `WhatsAppService.kt` (+`Cobro`), `ServiceSource.kt` | Data classes |
| `location` | `LocationHelper.kt`, `SavedLocationManager.kt`, `SavedLocation.kt` | GPS + saved-address matching |
| `update` | `UpdateChecker.kt` | GitHub-release update check |
| `whatsapp` | `WhatsAppResponder.kt` | WhatsApp pipeline via accessibility tree |
| `floating` | `FloatingPopupManager.kt` | Classic-View popup (`res/layout/floating_whatsapp_popup.xml`) |
| `ui` (+`ui.theme`) | `SettingsScreen.kt`, `WhatsAppScreen.kt`, `SavedLocationsScreen.kt`, `PlateAutoSendScreen.kt`, `WhatsAppPopup.kt`, `theme/Theme.kt` | Compose Material3 screens |

## Runtime pipelines

### 1. Picap auto-accept

Entry: `MyAccessibilityService.onAccessibilityEvent`.

1. `PicapEventThrottle.picapEventAllowed` — max 1 event/s (`MyAccessibilityService.kt:133`).
2. `PicapOfferProcessor.handlePicapEvent` — runs on MAIN, flips single-flight flags `scanInFlight`/`scanPending` (`PicapOfferProcessor.kt:105-107`).
3. Heavy work runs in the injected `scope` (Dispatchers.Default): `launchScan` → `runPicapPipeline`:
   - `PicapListAutoclicker.findAndClickEstimatedPrice` (if auto-click enabled).
   - `scanWindow` — single pre-order DFS producing `PicapScanResult` (`PicapOfferProcessor.kt:44-69`).
   - `PicapParser.parseOrder`.
   - Non-CV branch → `PicapOfferCloser.scheduleNonCvClose`; otherwise `PicapAutoAcceptPolicy.shouldAutoAccept` → `evaluateAutoAccept` → `clickAcceptCandidates`.
   - `KmOverlay.updateKmOverlay` posted to Main.
   - `OrderStateManager.setOrder` + `OrderHistoryManager.addOrder`.

### 2. Non-CV close

`PicapOfferProcessor.processPicapWindow` → `PicapOfferCloser.scheduleNonCvClose`
uses a coroutine `delay` (not a `Handler`) → `attemptNonCvClose` (re-read, click
X, verify) with a retry loop. First delay is `NON_CV_CLOSE_DELAY_MS = 200L`
(`PicapOfferCloser.kt:15`).

### 3. WhatsApp via accessibility

`MyAccessibilityService` → `WhatsAppResponder.handleWhatsAppEvent` →
`processWhatsAppText` → `WhatsAppParser.parse` → dedupe
`scannedWhatsAppServiceIds` → `evaluateAutoAccept` → `Handler.postDelayed` →
`pasteAndSend` (clipboard + `ACTION_PASTE` + send-button retry) OR
`FloatingPopupManager.show`.

### 4. WhatsApp via notification

`NotificationInterceptorService.onNotificationReceived` →
`handleWhatsAppNotification` → auto-placa (`WhatsAppIntentHolder` +
`WhatsAppForwardActivity`) OR `WhatsAppParser.parse` → `evaluateAutoAccept` →
`WhatsAppForwardActivity` PendingIntent + poll.

### 5. Km overlay

`scanWindow` captures the price-node `Rect`, posted to Main →
`KmOverlay.updateKmOverlay(anchor, order)` → `showKmOverlay`
(`TYPE_APPLICATION_OVERLAY` `TextView`). Skipped when text + anchor are
unchanged (`KmOverlay.kt:71`). Shows total km = `kmRecogida + kmEntrega`
(`KmOverlay.kt:50-51`).

## Parsers & decision rules

| Rule | Definition | Notes |
|---|---|---|
| OMS type | `PicapParser.isOmsService` — servicio contains `"cruz verde"` AND (`"integracion"` \| `"integración"`) (`PicapParser.kt:92-95`) | Binary: anything not OMS is Mostrador |
| Traslado | `PicapParser.isTrasladoDelivery` — delivery address contains `"cruz verde"` OR matches a saved location (`PicapParser.kt:84-86`) | Shared by policy + card |
| Card title precedence | **Traslado > OMS > Mostrador** (`OrderCard.kt:104-108`) | Do not reorder |
| km sentinel | `extractKmFromPickup` returns `999.0` = "km desconocido" (`PicapParser.kt:100-108`); consumers `takeIf { it < 999.0 } ?: 0.0` or `?: 999.0` | See [coding-style](coding-style.md#sentinels--magic-values) |
| Parse anchors | Product jargon/emoji hardcoded as Picap-UI contracts: `"ID: "`, `"Tu ganancia final"`, `"Aceptar"`, `"📏 …"` | Do NOT "clean these up" |

**Short delivery form (parse gotcha).** The delivery line may be
`A menos de un minuto (890m)`. `parseOrder` routes the FIRST `A …min` line to
pickup and a SECOND such line that carries parenthesized km to delivery
(`PicapParser.kt:35-45`). The parentheses are required so address lines
starting with `"A "` are never mistaken for route lines.

**Domain rule — do not reintroduce a global pre-gate.** The former global
Picap order-type filter (`picapAutoAcceptFilter`) was removed on purpose
(commit `5a978b4`). The per-condition type selectors
`picapAutoAcceptTypeCond1/2/3` (`OrderStateManager.kt:36-38`) are the single
source of truth for type gating.

## State & persistence

| Store | Prefs | Mechanics |
|---|---|---|
| `OrderStateManager` | `projectacc_prefs` (`:17`) | Kotlin `object` (`:16`); ~24 `KEY_*` (`:18-40`); `loadAll()` (`:49-74`); one `MutableStateFlow` + `asStateFlow()` per setting; string lists joined with `\|` (`:79`, `:83`). **`init(context)` MUST run in `MainActivity.onCreate`** (`MainActivity.kt:84`) — otherwise `prefs` stays null and every write silently no-ops. ~405-line god object |
| `OrderHistoryManager` | `picap_order_history` (`:24-25`) | Single `orders` key holding a full JSONArray; `@Volatile` + double-checked `ensureInit` (`:30-48`); mutation under `synchronized(historyLock)` (`:57`); persistence on single-thread executor `order-history-persist` (`:35-37`); dedupe by `order.id` (`:59`); `StateFlow<List<PicapOrder>>` oldest-first (`:60`) |
| `SavedLocationManager` | `saved_locations` | `locations` JSON array; `@Volatile` cache invalidated on save; `findMatch` scores +2 starts-with / +1 contains (`SavedLocationManager.kt:31-41`); `exportJson`/`importJson` merge-by-id |

Transient caches (cleared via `clearAllCache`): `scannedServiceIds`,
`scannedWhatsAppServiceIds`, `platedServiceIds` StateFlow Sets.

## Threading contracts

Documented in KDoc — treat them as binding API:

| Component | Contract |
|---|---|
| `PicapOfferProcessor` | `handlePicapEvent` on MAIN; scan/parse/decide on `Dispatchers.Default`; overlay/WindowManager/View posted to Main via `withContext(Dispatchers.Main)`; single-flight state MAIN-confined (`PicapOfferProcessor.kt:75-82`) |
| `KmOverlay` | **MAIN THREAD ONLY** — WindowManager usage (`KmOverlay.kt:27-31`, `:48`) |
| `WhatsAppResponder` | Use the injected service scope; **never create another scope** (`WhatsAppResponder.kt:26`) |
| `OrderHistoryManager` | Mutations under `historyLock`; disk writes on the `order-history-persist` executor thread |
| Shared scope | One `serviceScope` injected into collaborators; UI work via `withContext(Dispatchers.Main)` |

## Known debt — do not make it worse

Not TODOs to fix now. Read before touching the area.

- Two full WhatsApp pipelines (`WhatsAppResponder` vs
  `NotificationInterceptorService`) with divergent side effects — a behavioral
  change usually must land in BOTH.
- Duplicated `kmOrigenRegex` (`WhatsAppResponder.kt:55` ==
  `NotificationInterceptorService.kt:19`) and `AutoAcceptConfig(...)` assembled 3×.
- `OrderStateManager` god object; `init(context)` failure is silent.
- Hardcoded personal data: default allowed plate senders
  (`OrderStateManager.kt:73`) and a group name
  (`NotificationInterceptorService.kt:131`).
- Possible `AccessibilityNodeInfo` recycle leaks in
  `PicapOfferProcessor.clickAcceptCandidates` and `WhatsAppResponder.findSendButton`.
- No MVVM/NavHost/DI: navigation is boolean flags in a `when`
  (`MainActivity.kt:114-172`); the WhatsApp popup is a classic View while the
  rest is Compose.

## See also

- [Coding style](coding-style.md)
- [UI](ui.md)
- [Build, test & release](build-test-release.md)
- [AGENTS.md](../../AGENTS.md)
