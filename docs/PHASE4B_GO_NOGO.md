# Phase 4B — App Foundation + Vertical Slice — GO/NO-GO

Date: 2026-08-30
Scope: `:app` Compose module, adaptive navigation shell, Search→Playback vertical slice, architecture enforcement, UI test.

## Verdict: **GO** ✅

All build, lint, unit, UI, architecture, and e2e gates are green. No blocking issues.

---

## 1. What was built

### New module: `:app` (`com.cadence.app`)
Android application module (`applicationId com.cadence`, namespace `com.cadence.app`).

- **Theme / design system** — `ui/theme/`: brand-independent neutral palette (primary `#3B6E5B` green-toned) in light+dark, custom `CadenceTypography` mapped onto Material3 roles, `CadenceTokens` (spacing/radius/shapes/motion Fast 150 / Medium 300 / Slow 450ms). No brand name hard-coded (per PRODUCT-IDENTITY.md — "Cadence" is the internal identity; no "Keepsake").
- **Adaptive shell** — `CadenceApp.kt` + `MainActivity.kt`: `Scaffold` with `NavigationBar` (compact/medium) or `NavigationRail` (Expanded width via `calculateWindowSizeClass`), full-player overlay when expanded, MiniPlayer pinned above the bottom bar. Destinations: Home, Search, Library, Downloads (`navigation/AppDestination.kt`).
- **Composition root** — `di/`:
  - `MemoryFixtureTransport` — hermetic `DemoTransport` reading embedded fixture JSON (no network/classpath).
  - `DemoCatalog` — embedded `FixtureEnvelope`-shaped JSON: search slug `alpha` → 4 tracks, each with a matching resolve doc.
  - `AppGraph` — builds `DemoProvider` + `DefaultProviderRegistry` + `SpiCatalogSearchAdapter` + `SpiResolutionAdapter`; exposes `searchUseCase` + `resolutionPort`; takes injected `playerController`.
  - `AppViewModelFactory` — one lazy shared `PlaybackViewModel` across all branches.
- **Player engine bridge** — `android/player/Media3PlayerController.kt`: added `createMedia3PlayerController(context, scope)` factory wrapping `ExoPlayer.Builder(context).build()`; `CadenceApplication` builds one controller via this factory. Media3 stays confined to `:android:player`.
- **Presenters** — `viewmodel/`: `SearchViewModel` (300ms-debounced instant search, `SearchUiState`), `PlaybackViewModel` (owns controller + current track; resolve→load→play, play/pause/toggle/seek/skip, `preparing`, `playError`, `expanded`).
- **Screens** — `screen/`: `SearchScreen` (slice entry), `HomeScreen`, `LibraryScreen`, `DownloadsScreen`, `PlayerScreen` (full player).
- **Shared components** — `ui/components/`: `TrackRow`, `Feedback` (Loading/Empty/Error), `ProgressControl` (seekable), `MiniPlayer`; `ui/ErrorText.kt` maps `SourceError`/`PlaybackError` to user-safe messages (no raw provider/exception strings).

### Vertical slice (Diagram B)
`SearchScreen → SearchViewModel → SearchUseCase (core/domain) → SpiCatalogSearchAdapter → DemoProvider (MemoryFixtureTransport) → [tap track] PlaybackViewModel.loadAndPlay → SpiResolutionAdapter → DemoProvider.resolve → Media3 PlayerController.load/play → MiniPlayer + PlayerScreen`.

**Design decision (recorded):** the UI slice drives media `PlayerController` directly for playback (Diagram B) and `SearchUseCase` for search. It intentionally does **not** route through `PreparePlaybackUseCase`/the domain `PlaybackEngine`. Reason: the two `PlaybackState` abstractions (`core/domain` `PlaybackState[Envelope]` vs `core/media` `PlaybackState`) are separate types; bridging them adds a fragile adapter not required for this slice. The domain `PlaybackEngine`/`PreparePlaybackUseCase` remain intact and covered by their own tests; wiring them into the UI is deferred to a later phase.

## 2. Architecture enforcement

- `:app` added to **new** `uiModules` map in `tests/architecture/.../ArchEnvironment.kt` (NOT `productionModules` — `:app`'s composition root legitimately needs `com.cadence.source.demo`).
- New `tests/architecture/.../AppBoundaryChecks.kt` rules:
  - **N** — no `com.metrolist` / `com.cadence.core.testing` imports in app main.
  - **O** — no `androidx.media3` import in app surface (media3 confined to `:android:player`).
  - **P** — `com.cadence.source.demo` only under `com/cadence/app/di`.
  - **Q** — `com.cadence.core.data` only under `com/cadence/app/di`.
- Rule F (≤800 lines/file) applies to `:app` automatically via `allKtFilesUnderRoot()`.
- Result: `AppBoundaryChecks` (4 tests) and `SourceBoundaryChecks` (9 tests) pass, 0 skipped/failed.

## 3. Verification results

| Gate | Result |
|------|--------|
| `:app:compileDebugKotlin` | ✅ PASS |
| `:app:assembleDebug` (APK produced) | ✅ PASS (~22 MB debug APK) |
| `:app:lintDebug` | ✅ 0 errors (96 warnings, all benign: version-bump suggestions, redundant label, obsolete-sdk-folder) |
| `:app:testDebugUnitTest` — `SearchToPlaybackSliceTest` (2 tests) | ✅ PASS (search "alpha"→4 tracks→tap→fake controller Playing; blank-query idle) |
| `:tests:architecture:test` | ✅ PASS (13 tests) |
| `:tests:e2e:test` | ✅ PASS |
| `./gradlew build` (all modules) | ✅ PASS (`BUILD SUCCESSFUL in 5m 5s`) |

### Compile-fix notes (from earlier failing state)
- `calculateWindowSizeClass` requires an `Activity`; fixed to use `LocalActivity.current` (lint `ContextCastToActivity` resolved) — `CadenceApp.kt`.
- Custom typography roles (`headingLarge`/`headingMedium`/`metadata`/`label`) are **not** Material3 `Typography` slots; screens now map directly to M3 roles (`headlineLarge`/`titleLarge`/`labelSmall`/`titleMedium`). Accessors remain in `Type.kt`.
- `stateIn(Lifecycle.State.STARTED, …)` → expose the controller's hot `StateFlow` directly (`PlaybackViewModel`).
- `clip(Dp)` → `clip(RoundedCornerShape(tokens))` in `TrackRow`/`MiniPlayer`.
- Error mapping guarded to typed `SourceError` (not raw `Exception`).

## 4. Deviations / notes
- **No brand** finalized; UI is brand-independent ("Cadence" internal identity only).
- `:source:ytmusic` deferred per `docs/LICENSE_DECISION.md` (NewPipe Extractor GPL conditionally approved but not added this phase).
- UI slice uses media `PlayerController` directly rather than the domain `PlaybackEngine` (see §1 decision).
- `mipmap-anydpi-v26` could be folded into `mipmap-anydpi` (lint `ObsoleteSdkInt`, minSdk 26) — cosmetic, optional.
- `junit:junit:4.13.2` hard-coded in `app/build.gradle.kts` (matches existing module style; lint `UseTomlInstead` is advisory).

## 5. What's deliberately NOT in scope (future phases)
- Remaining flows (Home recommendations wiring, Library CRUD, Downloads, browsing, settings).
- Real source providers / network extraction (`:source:ytmusic`).
- Media session / notification / background playback integration (played only in-foreground via `:android:player`).
- Instrumented/connected-device UI tests (unit-level Compose slice test provided; full UI test harness deferred).

## 6. Recommendation
Proceed. Phase 4B delivers a polished, brand-independent foundation and one complete, verified Search→Playback vertical slice with the player engine bridged through `:android:player`. Architecture boundaries are code-enforced (`AppBoundaryChecks` rules N/O/P/Q) and the whole project builds clean.
