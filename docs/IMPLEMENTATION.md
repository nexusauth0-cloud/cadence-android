# Phase 1 implementation notes — ADR-0002 spike

Source of truth remains `cadence-phase0/`; this document records what was built
and why, without restating the specs.

## Module graph & dependency direction

```
                ┌──────────────┐
                │ tests:e2e    │  composition proof (ports + fakes ONLY)
                └──┬───┬───┬───┘
   ┌───────────────┘   │   └────────────────┐
   ▼                   ▼                    ▼
core:data ─────────► core:domain ◄───── core:testing (test infra lib)
   │                   │  ▲                 │      │
   │ implements ports  │  │ ports           ▼      │
   ▼                   │  │              model ◄──┘
source:api ────────────┘  │
   ▲                      │
   ├── core:model         │
   ▼                      │
source:demo (adapter shape; fixtures; NO real backend)
tests:architecture (scans compiled artifacts + sources; depends on nothing)
```

Rules enforced (strongest mechanism listed first):

1. **Pure-JVM modules** (`kotlin("jvm")`, no Android plugin anywhere): domain,
   model, and `source:api` physically cannot reference Android/Room/OkHttp —
   those classpaths do not exist. This is check #2's compile-time guarantee.
2. **No provider impl on e2e classpath**: `tests/e2e/build.gradle.kts` omits
   `:source:demo` deliberately; leakage into the proof is impossible to even
   express (checks #1/#6).
3. **Source scans** (`SourceBoundaryChecks`): import bans per module family,
   test-infra-only rule for `:core:testing`, provider-impl leak rule.
4. **Compiled-artifact scans** (`CompiledArtifactChecks`): bytecode search for
   `runBlocking` / `Thread.sleep` in our production jars (harder to bypass than
   text greps), reflection audit that *every* Throwable in `source.api` belongs
   to the sealed `SourceError` family, file-size budget ≤800 lines.

Escalation plan (documented, not yet needed): detekt custom rules once the rule
set grows beyond these ten checks.

## Contracts implemented

- `core/model`: ProviderId, TrackRef(+EntityRef/ArtistRef), Track
  (identity-by-ref equals/hashCode), Artwork, ResolvedSource (+SourceMetadata,
  OpaquePayload), PlaybackRequest (+AudioTraits), ResolutionHints.
- `source/api`: SourceProvider (search w/ cursor + resolve), Capability,
  ProviderAuth (non-secret subject only), SearchQuery/ProviderScope/PageCursor/
  PagedResult, sealed SourceError with all ten families + enums,
  ProviderRegistry + DefaultProviderRegistry.
- `core/domain`: CatalogSearchPort, SourceResolutionPort, PlaybackEngine port +
  PlaybackState/PlaybackFailure, RecoveryPolicy (+DefaultRecoveryPolicy),
  SearchUseCase, PreparePlaybackUseCase (cancellation-first error handling;
  no catch-all conversion of foreign throwables).
- `core/data`: SpiCatalogSearchAdapter / SpiResolutionAdapter bridging registry→ports.
- `source/demo`: adapter-shaped synthetic provider over committed fixtures:
  wire DTOs → DemoMapper → domain models; DemoErrorMapping for typed errors;
  transport is an injected seam (no HTTP dependency exists in this repo).

## Fixture system

Format, envelope schema v1, loader semantics: see `core/common/Fixture.kt`.
15 committed fixtures under `source/demo/src/test/resources/fixtures/demo/**`
covering happy/pagination/rich-metadata/metadata-absent/expiry plus one fixture
per SPI error family and four malformed classes. All timestamps fixed strings;
mappers take injected `java.time.Clock` (MutableClock in tests). Zero network.

## Fake infrastructure (`core/testing`)

- `FakeSourceProvider`: seven scripted scenarios keyed by nativeId prefix
  (`ok/ unavailable/ rate-limited/ auth-failed/ malformed/
  cancelled-immediately/ delayed/<ms>-<name>`); virtual-time delays via caller
  dispatcher; invocation counters; hints echo; activeWork leak probe.
- `SecondFakeProvider`: distinct host proving multi-provider routing.
- `FakePlaybackEngine`: fully deterministic state machine, recorded loads.
- `MutableClock`: manually advanced time source.

## Deviations from the spike spec (complete list)

1. `:source:ytmusic` mapper layer deferred to Phase 2 pending spec §20 Q1
   (extraction-library choice + license vetting). The demo adapter carries the
   adapter-pattern proof instead. Real providers were explicitly out of scope
   for this phase's instructions.
2. `TRACK_DETAIL/RELATED/RADIO` methods omitted from the SPI surface entirely
   (spec already marked them post-spike; omission beats stub-throwing).
3. Continuation input channel added: `SourceProvider.search(query, cursor)`.
   The spec defined `nextCursor` output but left the follow-up input implicit.
4. `SearchRepository` realized as two narrower ports (`CatalogSearchPort`,
   `SourceResolutionPort`) matching the two use cases; same boundary, finer seams.
5. Konsist not adopted; hand-rolled checks (bytecode + reflection + scans)
   provide stronger guarantees for this module count. Escalation path documented.
6. Mutation testing replaced by the documented violation-probe procedure in
   `docs/TESTING_STRATEGY.md` (spec section 14 allowed a deterministic
   equivalent at this project size).

## Commands

```bash
./gradlew build                       # everything
./gradlew :tests:e2e:test             # spike proof
./gradlew :tests:architecture:test    # enforcement
./gradlew :source:demo:test           # fixture/adapter suite
```
