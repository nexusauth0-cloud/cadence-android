# Phase 5A — Real YT Music Provider

Status: **IN PROGRESS** (wiring to composition root + hermetic tests complete;
see `docs/PHASE5A_GO_NOGO.md` for the gate report).

## Mission

Replace the dev/demo content path with a real YT Music provider behind the
proven `SourceProvider` SPI (ADR-0002): real search + resolution over the
NewPipe Extractor library, wired at the composition root as the single-source
default. Playlists/downloads/lyrics/EQ/accounts/sync/recommendations/
cross-source search/Auto/Wear OS are explicitly out of scope.

## License posture

NewPipe Extractor **v0.26.5** (GPL-3.0) is approved **with conditions**
(`docs/LICENSE_DECISION.md`):

- app is GPL-3.0 (satisfied);
- attribution on distribution;
- ProGuard keep rules for Rhino/NewPipe (planned; minify still off);
- core-library desugaring for minSdk<33 (already enabled in `:app`);
- **adapter isolation** — NewPipe types never leak beyond `:source:ytmusic`.

No implementation code is copied from Metrolist / PR #4301.

## Provider module `:source:ytmusic`

Adapter-isolated: only `com.cadence.core.model` values cross the SPI boundary;
NewPipe and HTTP types never leave this module.

| File | Responsibility |
| --- | --- |
| `YtMusicProvider.kt` | `SourceProvider` impl; runs NewPipe extractor over a transport seam; cancellation via `runInterruptible(IO)` |
| `YtMusicTransport.kt` | fun-interface seam supplying the `Downloader` (production `okHttp()`, tests fixture) |
| `YtMusicDownloader.kt` | OkHttp-backed NewPipe `Downloader` |
| `YtMusicMapper.kt` | NewPipe wire -> domain `Track`/`ResolvedSource` (direct non-HLS preference) |
| `YtMusicErrorMapping.kt` | NewPipe exception taxonomy -> typed `SourceError` (specific-first) |

Key API facts (verified via `javap`):
- `getSearchQHFactory().fromQuery(q, ["music_songs"], "")` -> music search handler.
- `extractor.fetchPage()` MUST precede `getInitialPage()` (else `initialData` NPE).
- `ID` exposed publicly as `YtMusicProvider.ID` for composition roots only.

## Networking & errors

- suspend-only, caller-owned cancellation; `CancellationException` never wrapped.
- no `runBlocking`, no blocking IO on coroutine threads (`runInterruptible(IO)`).
- no retry middleware; HTTP types stay in `:source:ytmusic`.
- typed `SourceError` family only (never raw provider types).

## Hermeticity (fixture-first)

`RecordingDownloader` captured a genuine live run (search "alpha" + resolve) via
the `:source:ytmusic:liveCapture` JavaExec task (authoring-only, never in CI).
The wire (11 exchanges) is committed at
`source/ytmusic/src/test/resources/fixtures/ytmusic/search_resolve_happy.json`
(volatile headers — `set-cookie`/`date`/`expires` — scrubbed). `FixtureDownloader`
replays it keyed by URL substring so the **real** extraction + mapping pipeline
runs hermetically in CI, zero network.

Test coverage (all green):
- `YtMusicProviderTest` — hermetic search + resolve + deterministic replay (4).
- `YtMusicErrorMappingTest` — NewPipe exception taxonomy -> typed `SourceError` (10).
- `YtMusicMapperTest` — pure mapping edges: video id parsing, no-stream -> `ResolutionFailed` (3).

## Composition-root pattern (default / test config)

The single-source default is a **DI-injected `ProviderScope`** — never a
hard-coded provider id in the domain, and never an environment branch in
production code.

- `SearchUseCase(catalog, defaultScope)` — `invoke` uses the injected default when
  no scope is given (`core/domain`).
- `AppGraph(playerController, providers, defaultScope)` — injectable config.
- Production defaults: `providers = AppDemoConfig.providers() + YtMusicProvider(YtMusicTransport.okHttp())`,
  `defaultScope = ProviderScope.Single(YtMusicProvider.ID)` — real YT Music,
  live network, single-source (no cross-source fan-out).
- `AppDemoConfig` (`com.cadence.app.di`) — explicitly-named demo (fake) config
  used by hermetic tests to build a **different** `AppGraph`:
  `providers = [DemoProvider]`, `defaultScope = Single(demoId)`.

There is **no** "detect test, swap provider" logic anywhere. Tests construct a
distinct graph. The Phase 4B demo slice stays green and offline through the
explicit demo graph (`SearchToPlaybackSliceTest`).

Proving tests:
- `SearchUseCaseTest` (domain) — injected default flows to the port; explicit
  scope overrides; default ctor is `AllRegistered`.
- `AppGraphProviderConfigTest` (app) — production graph selects YT Music single
  default + registers `{demo, ytmusic}`; demo graph selects Demo + registers
  `{demo}`; multi-provider registry. All hermetic (constructing either graph
  performs no network).

## Architecture enforcement

- `ArchEnvironment.productionModules` + `tests/architecture/build.gradle.kts`
  `scannedRuntime` now include `:source:ytmusic`.
- `SourceBoundaryChecks` rule C: ytmusic is a provider-impl module (leaks nowhere
  except own module); rule I: ytmusic wire types never reach domain.
- `AppBoundaryChecks` rule P: `com.cadence.source.ytmusic` only in the
  composition root (`app/di`).

## Honest states

`SourceError -> UserError` (`app/ui/ErrorText.kt`) is exhaustive over the typed
taxonomy and never leaks provider names, exceptions, URLs, or tokens. The
`SearchUiState` sealed family (Idle/Loading/Results/Error) surfaces them honestly.
`ErrorTextTest` pins the contract for the real provider's failure paths.

## Out of scope (deferred)

Playlists, downloads, lyrics, EQ, accounts, sync, recommendations,
cross-source search, provider-switching UI, Auto, Wear OS.
