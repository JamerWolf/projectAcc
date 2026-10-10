# UI conventions

Jetpack Compose + Material3, single-activity app. UI strings are hardcoded
Spanish literals. Navigation is boolean flags (no NavHost). Two non-Compose
surfaces exist on purpose — do not migrate them as a drive-by.

## Compose Material3 screens

- Single-activity: `MainActivity` (`MainActivity.kt:73`) hosts everything via
  `setContent` wrapped in `ProjectAccTheme` (`MainActivity.kt:113`,
  `ui/theme/Theme.kt`).
- Screens: `SettingsScreen`, `WhatsAppScreen`, `SavedLocationsScreen`,
  `PlateAutoSendScreen`, `WhatsAppPopup`, plus the Picap content and
  `ActivationScreen` that live inside `MainActivity.kt`.

### Scroll rule (mandatory for overflowing screens)

Root screens scroll with `.verticalScroll(rememberScrollState())` on the root
Column — `SettingsScreen.kt:89`, `WhatsAppScreen.kt:46`,
`PlateAutoSendScreen.kt:51`, ActivationScreen `MainActivity.kt:293`, Picap
content `MainActivity.kt:470`.

A screen whose content can exceed the viewport **MUST** be scrollable.
`verticalArrangement = Arrangement.Center` on a non-scrollable Column clips
BOTH ends when content overflows.

### Modifier order on full-screen roots

`fillMaxSize()` → background / `windowInsetsPadding` → `verticalScroll` →
`padding(…)`, so padding scrolls with content. Canonical example
(`SettingsScreen.kt:85-90`):

```kotlin
modifier = modifier
    .fillMaxSize()
    .background(MaterialTheme.colorScheme.background)
    .windowInsetsPadding(WindowInsets.systemBars)
    .verticalScroll(rememberScrollState())
    .padding(16.dp)
```

## UI string language

UI strings are **hardcoded Spanish literals in composables**. There is zero
`stringResource`/`R.string` usage in Kotlin (verified by grep over
`app/src/main`); `res/values/strings.xml` only has `app_name` and the
accessibility service description (`strings.xml:2-3`).

Verbatim examples:

| String | Location |
|---|---|
| "Permisos Requeridos" | `MainActivity.kt:298` |
| "Ganancia mínima (aceptar siempre)" | `ui/SettingsScreen.kt:240` |
| "Auto-Responder en grupo" | `ui/WhatsAppScreen.kt:63` |
| "Numeros permitidos" | `ui/PlateAutoSendScreen.kt:85` |
| "Nuevo servicio WhatsApp" | `ui/WhatsAppPopup.kt:35` |

**Convention:** new user-facing copy stays **Spanish** (the app's user-facing
language). Identifiers, package names, class names, and technical terms stay
English. Do not introduce `stringResource` for new copy without an explicit
i18n decision.

## Navigation

Boolean state flags inside the `setContent` `when`
(`MainActivity.kt:114-131`): `showSettings`, `showSavedLocations`,
`showPlateAutoSend`. Back = set the flag to false (`onBack = { showX = false }`).

There is **no NavHost / navigation library** — follow the flag pattern when
adding a screen; do not introduce a navigation framework as a drive-by.

### Permission (Activation) screen

`ActivationScreen` gates the main UI
(`MainActivity.kt:149-169`):

```
allPermissionsGranted =
    isServiceEnabled && isOverlayEnabled &&
    isNotificationListenerEnabled && isBatteryOptimizationExempt
```

- False → `ActivationScreen` (`MainActivity.kt:158`) with deep links into the
  system settings for each missing grant.
- True → `MainScreen` (`MainActivity.kt:152`).
- The four state fields are refreshed in `MainActivity.onResume`
  (`MainActivity.kt:177`).

## Non-Compose overlays & popups (on purpose)

| Surface | Tech | Where |
|---|---|---|
| Km overlay | `WindowManager` `TYPE_APPLICATION_OVERLAY` `TextView` | `picap/KmOverlay.kt` — **MAIN THREAD ONLY** (`KmOverlay.kt:27-31`) |
| WhatsApp popup | Classic View inflation from XML | `floating/FloatingPopupManager.kt:73-74` + `res/layout/floating_whatsapp_popup.xml` |

Do **not** migrate them to Compose as a drive-by change:

- An application overlay is a separate window; it is not part of the activity's
  Compose hierarchy (`KmOverlay.kt:24-25`).
- The popup is reached from the notification pipeline
  (`NotificationInterceptorService`) and predates the Compose screens.

## See also

- [Architecture](architecture.md)
- [Coding style](coding-style.md)
- [Build, test & release](build-test-release.md)
- [AGENTS.md](../../AGENTS.md)
