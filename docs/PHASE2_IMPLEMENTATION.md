# Phase 2 — Implementation Documentation

## Module Graph (updated)

```
:core:model          (pure Kotlin/JVM — value types)
:core:common          (pure Kotlin/JVM — SourceLog, Batching, FixtureLoader)
:core:domain          (pure Kotlin/JVM — ports, use cases, RecoveryPolicy)
:datastore            (pure Kotlin/JVM — CredentialVault port + in-memory impl)
:core:database        (pure Kotlin/JVM — PlaylistRepository + inBatches, in-memory impl)
:core:media           (pure Kotlin/JVM — PlayerController port, PlaybackState, AudioEffects)
:core:cache           (pure Kotlin/JVM — CacheTier, CacheAccounting, CacheHousekeeper)
:core:data            (pure Kotlin/JVM — port adapters bridging registry→ports)
:core:testing         (pure Kotlin/JVM — fakes for all ports)
:source:api           (pure Kotlin/JVM — SourceProvider SPI)
:source:demo          (pure Kotlin/JVM — fixture-backed demo provider)
:tests:e2e            (pure Kotlin/JVM — end-to-end spike proof)
:tests:architecture   (pure Kotlin/JVM — boundary/artifact checks)
```

### Dependency graph (new modules highlighted)

```
:app → (future — wiring only)
  └─ :feature:* → :core:{domain,model,designsystem,common}, :source:api
      └─ :core:data → :core:domain, :core:model, :source:api, :core:database, :core:common
          └─ :core:database → :core:model
          └─ :core:datastore → :core:model
          └─ :core:media → :core:model
          └─ :core:cache → :core:model
```

## Deviations from Phase 0 ADRs

| # | ADR | Deviation | Rationale |
|---|-----|-----------|-----------|
| 1 | ADR-0004 | CredentialVault uses InMemory impl (no Keystore) | Pure JVM project; Android Keystore requires Android runtime. Port is proven; real impl deferred to Android phase. |
| 2 | ADR-0006 | PlayerController port only (no Media3 adapter) | Media3 is Android-only. Port + fake prove the boundary. Real adapter needs Android project. |
| 3 | ADR-0003 | PlaylistRepository uses in-memory impl (no Room) | Room is Android-only. Repository interface + in-memory impl prove the contract. |
| 4 | ADR-0007 | Cache uses in-memory tiers (no file-system/Coil) | File-based caches need Android context. In-memory tiers prove budgets/eviction/housekeeping. |
| 5 | ADR-0005 | `inBatches()` lives in `:core:common` | ADR says `:core:database`, but the utility has no Room dependency. Placing in `:core:common` allows any module to use it without pulling Room. |
| 6 | ADR-0001 | No `:core:network` module yet | HTTP engine (Ktor/OkHttp) deferred to Phase 3 when provider extraction is added. |

## License Decision

See [LICENSE_DECISION.md](LICENSE_DECISION.md) for full analysis.

**Summary:** NewPipe Extractor (GPL-3.0) is the primary candidate, DEFERRED to Phase 3 when `:source:ytmusic` is built. No extraction library is added this phase. The architecture is proven via `:source:demo`.

## CredentialVault

**Port:** `com.cadence.core.datastore.CredentialVault`
**Scope enum:** `CredentialScope { ACCOUNT, PLAYBACK, SYNC }`
**State:** `CredentialState { Absent, Present(ageMs), Unavailable(reason) }`
**Exceptions:** `CredentialVaultException { Locked, Corrupted, StorageFailure, CryptoUnavailable }`
**Impl:** `InMemoryCredentialVault` (test-only, mutex-protected, defensive copies)

Tests verify: round-trip, overwrite, delete, scope isolation, clearScope, clearAll, state, defensive copies, no secret leakage.

## Playback

**Port:** `com.cadence.core.media.PlayerController`
**States:** `PlaybackState { Idle, Buffering, Ready, Playing, Paused, Ended, Failed }`
**Commands:** `PlaybackCommand { SeekTo, SetSpeed, SetVolume, SkipToNext, SkipToPrevious, Stop }`
**Events:** `PlayerEvent { PositionChanged, AudioSessionChanged, MetadataChanged, Error }`
**Effects:** `AudioEffectChain` + `AudioEffect` interface (EqPreset, LoudnessNormalization)
**Errors:** `PlaybackError { Source, Engine, AudioFocus }`
**Fake:** `FakePlayerController` — records loads/plays/pauses/commands

Tests verify: state transitions, play/pause, command recording, effect chain, error handling, release, single-player invariant documentation.

## Database

**Repository:** `com.cadence.core.database.PlaylistRepository`
**Models:** `PlaylistId`, `PlaylistMetadata`, `PlaylistTrack(AddedReason)`, `DuplicateCheckResult`
**Impl:** `InMemoryPlaylistRepository` (mutex-protected)

Tests verify: CRUD, ordering, reordering, atomic replaceTracks, duplicate check (1200 IDs), 1001-ID batch, empty operations, repeated IDs, position removal.

## Batching

**Utility:** `com.cadence.core.common.inBatches()`
**Config:** `BatchConfig.BATCH_SIZE = 900` (ADR-0005 safe ceiling)

Tests verify: empty input, single batch, sub-batch, multi-batch, dedup preserving order, boundary dedup, all-duplicates, batch-size-1, stable ordering.

## Cache

**Tiers:** `CacheTierId { DOWNLOADS, STREAM, METADATA, IMAGES }`
**Budget:** `CacheBudget(tierId, maxBytes, evictionPolicy)`
**Policies:** `EvictionPolicy { Never, Lru(maxBytes), Ttl(ttlMs) }`
**Store:** `InMemoryCacheTier` (mutex-protected, LRU eviction, TTL pruning, `deferEviction` flag for housekeeper tests)
**Accounting:** `CacheAccounting` — per-tier and aggregate usage tracking
**Housekeeper:** `CacheHousekeeper` — prunes expired T3, evicts over-budget T2/T4, idempotent

Tests verify: put/get round-trip, LRU eviction, TTL expiration, never-evict, accounting, budget enforcement, housekeeping (expired, over-budget, idempotent).

## Architecture Checks (Phase 2 extensions)

| Rule | Check | Enforcement |
|------|-------|-------------|
| G | Domain cannot depend on Room/Media3/Keystore | Source import scan |
| H | Source API cannot depend on extraction libraries | Source import scan |
| I | Provider DTOs cannot leak into domain | Source text scan |
| J | Room entities cannot leak into domain | Source text scan |
| K | Domain bytecode contains no Media3/Room/Keystore refs | Bytecode scan |

All 3 new violation probes verified RED.

## Testing Strategy

| Layer | Tests | Tool |
|-------|-------|------|
| CredentialVault | 12 | coroutines-test |
| PlaylistRepository | 19 | coroutines-test |
| inBatches | 9 | coroutines-test |
| PlayerController | 10 | coroutines-test |
| Cache (tiers + accounting + housekeeper) | 19 | coroutines-test |
| Architecture | 12 | source + bytecode scans |
| Phase 1 existing | 51 | (unchanged) |
| **Total** | **132** | **0 failures** |
