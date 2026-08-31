# Phase 5A — Real YT Music Provider — GO/NO-GO

Date: 2026-08-30
Scope: real YT Music `SourceProvider` implemented over NewPipe Extractor v0.26.5 behind the proven SPI; hermetic fixture-first tests; registry + composition-root wiring (single-source default); architecture enforcement; honest states.

## Verdict: **GO** ✅

All build, lint, unit, architecture, and e2e gates are green. No blocking issues.

---

## 1. What was built

### New module: `:source:ytmusic` (`com.cadence.source.ytmusic`)
Adapter-isolated real provider over **NewPipe Extractor v0.26.5** (GPL-3.0, approved with conditions — `LICENSE_DECISION.md`). Only `com.cadence.core.model` values cross the SPI boundary; NewPipe + HTTP types never leave this module.

- `YtMusicProvider` — `SourceProvider` impl; runs the extractor over a transport seam; cancellation via `runInterruptible(IO)`; `CancellationException` never wrapped; anonymous-first (`authState` stays null).
- `YtMusicTransport` — fun-interface seam supplying the NewPipe `Downloader` (production `okHttp()`, tests fixture).
- `YtMusicDownloader` — OkHttp-backed `Downloader`.
- `YtMusicMapper` — wire → domain `Track`/`ResolvedSource` (prefers direct non-HLS rendition).
- `YtMusicErrorMapping` — NewPipe exception taxonomy → typed `SourceError` (specific-first; premium content matched structurally by class name).

**Real flow (live-verified):** USER SEARCH → `YoutubeMusicSearchExtractor` (`getSearchQHFactory().fromQuery(q, ["music_songs"], "")`) → extension → `searchItem` mapping → `Track`s; RESOLVE → `StreamInfo.getInfo(music.youtube.com/watch?v=…)` → direct audio stream → `ResolvedSource`.

**Two root-cause bugs fixed (live-verified):** `extractor.fetchPage()` must precede `getInitialPage()` (else `initialData` NPE); music search requires the `music_songs` content-filter handler (a `music.youtube.com/search?q=` URL does **not** dispatch to the music extractor).

**Public id:** `YtMusicProvider.ID` (composition roots only — never UI/domain).

### Hermetic fixture-first testing (ADR-0002)
- `RecordingDownloader` + `RecordedWire` captured a genuine live run (search "alpha" + resolve) via the **`liveCapture` JavaExec task** (authoring-only, never in CI — sidesteps the JUnit-discovery flakiness seen earlier).
- 11 exchanges committed at `source/ytmusic/src/test/resources/fixtures/ytmusic/search_resolve_happy.json`; volatile headers (`set-cookie`/`date`/`expires`) scrubbed; body stream URLs are ephemeral anonymous strings used only as opaque `playbackUri`.
- `FixtureDownloader` + `FixtureRouter` replay by URL substring so the **real** extraction + mapping pipeline runs hermetically in CI, zero network.
- Tests: `YtMusicProviderTest` (4 — search/resolve/deterministic replay), `YtMusicErrorMappingTest` (10), `YtMusicMapperTest` (3). **17/17 green.**

### Composition-root pattern (default vs test config)
The single-source default is a **DI-injected `ProviderScope`** — never a hard-coded provider id in the domain, never an environment branch in production code.
- `SearchUseCase(catalog, defaultScope)` — `invoke` falls back to the injected default; explicit scope overrides (`core/domain`).
- `AppGraph(playerController, providers, defaultScope)` — injectable config. **Production defaults** register `AppDemoConfig.providers() + YtMusicProvider(YtMusicTransport.okHttp())` with `defaultScope = Single(YtMusicProvider.ID)` → real YT Music, live network, single-source (no cross-source fan-out).
- `AppDemoConfig` (`com.cadence.app.di`) — explicitly-named demo (fake) config tests use to build a **different** `AppGraph` (`providers = [DemoProvider]`, `defaultScope = Single(demoId)`).
- No "detect test → swap provider" logic anywhere; the Phase 4B demo slice stays green and offline via the explicit demo graph.

**Proving tests:** `SearchUseCaseTest` (3 — injected default flows to port; explicit scope overrides; default ctor `AllRegistered`), `AppGraphProviderConfigTest` (3 — production selects YT Music single default + registers `{demo, ytmusic}`; demo graph selects Demo + `{demo}`; multi-provider registry). All hermetic — constructing either graph performs no network.

### Honest states
`ErrorTextTest` (4) pins the exhaustive `SourceError → UserError` matrix (`app/ui/ErrorText.kt`): every typed error the real provider can throw maps to friendly copy and never leaks provider names/exceptions/URLs/tokens. `SearchUiState` (Idle/Loading/Results/Error) surfaces them honestly.

## 2. Architecture enforcement
- `ArchEnvironment.productionModules` + `tests/architecture/build.gradle.kts` `scannedRuntime` include `:source:ytmusic`.
- `SourceBoundaryChecks` **rule C**: ytmusic is a provider-impl module (leaks nowhere except its own module); **rule I**: ytmusic wire types never reach domain.
- `AppBoundaryChecks` **rule P**: `com.cadence.source.ytmusic` only in the composition root (`app/di`).
- Result: `AppBoundaryChecks` (4), `CompiledArtifactChecks` (5), `SourceBoundaryChecks` (9) all pass, 0 skipped/failed.

## 3. Verification results

| Gate | Result |
|------|--------|
| `:source:ytmusic:compileKotlin` | ✅ PASS (deprecation warnings only) |
| `:source:ytmusic:test` — 3 classes / 17 tests | ✅ PASS |
| `:core:domain:test` — incl. `SearchUseCaseTest` (3) | ✅ PASS |
| `:app:testDebugUnitTest` — slice (2) + config (3) + error (4) | ✅ PASS (9/9) |
| `:app:assembleDebug` (APK) | ✅ PASS (~22 MB debug APK) |
| `:app:lintDebug` | ✅ 0 errors (99 warnings, benign/pre-existing) |
| `:tests:architecture:test` | ✅ PASS (18 tests) |
| `:tests:e2e:test` | ✅ PASS |
| `./gradlew test` (all modules) | ✅ PASS — **248 tests, 0 failures, 0 errors** |

### Notes on the monolithic build
A single `./gradlew build` exceeded the tooling timeout, so verification was split: `./gradlew test` (all unit/arch/e2e) succeeded, then `:app:lintDebug` / `:app:assembleDebug` succeeded separately (8m55s). Aggregate result is a green, lint-clean, fully-assembled build.

## 4. Deviations / notes
- **Provisioned decision (user):** production `AppGraph` defaults to YT Music single-source; tests construct a distinct demo `AppGraph` (no environment detection). Recorded as the intended composition-root pattern (see `docs/PHASE5A_PROVIDER.md`).
- **Fixture hygiene:** volatile response headers scrubbed; stream URLs left as opaque anonymous playback URIs (matched the earlier plan).
- The Phase 4B demo slice is preserved and green, now driven by the explicit demo graph rather than the production default.
- Media3 remains confined to `:android:player` (rule O unchanged).

## 5. What's deliberately NOT in scope (future phases)
Playlists, downloads, lyrics, EQ, accounts, sync, recommendations, cross-source search, provider-switching UI, Auto, Wear OS, ProGuard keep rules (minify still off).

## 6. Recommendation
Proceed. Deliverables are complete: a real, live-verified YT Music provider (GPL-licensed under conditions), hermetic fixture-first tests, correctness fixes in the extractor handshake, single-source wiring at the composition root with injected default scope, architecture enforcement, honest states, and documentation. Whole project builds, lints clean, and all 248 tests pass.
