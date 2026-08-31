# Phase 5C — Downloads + Offline — GO/NO-GO

Date: 2026-08-31
Scope: real track → resolve → download → persistent local file → metadata association → Downloads/Library surface → offline availability → offline playback (PlaybackViewModel resolves local-first silently, `PlayerController` unchanged).

## Verdict: **GO** ✅

All build, lint, unit, architecture, and offline-flows gates are green. No blocking issues. The offline experience is truthful: "Saved to library" and "Available offline" are distinct, verified states.

---

## 1. What was built (summary)

- **Download domain (`core:database`, pure):** `DownloadModels` (id/state/failure/metadata/entry/`OfflineAvailability`/`DownloadStorageReport`) + `DownloadRepository` port + `InMemoryDownloadRepository`. Dedup by ref; re-download of a completed entry allowed (unique index on provider_id+native_id).
- **Room persistence (DB v3):** `DownloadEntity`/`DownloadDao` + `RoomDownloadRepository`, forward-only `MIGRATION_2_3` adding the `downloads` table. `MIGRATION_1_2` preserved.
- **Storage + execution (`android:worker`):** `DownloadStorage` (atomic tmp→rename, size/verify), `HttpByteSource` (OkHttp byte-stream seam), `DownloadExecutor` (testable resolve→stream→write→verify→commit with cancellation), `DownloadWorker` (thin WorkManager `CoroutineWorker`, process-death-safe, dedup via unique work + `KEEP`), `DownloadService` (implements `DownloadOperationsPort`).
- **Offline source-selection (`core:domain`):** `OfflineFirstResolutionPort` policy — verified local → use local (no network); else `offlineOnly` → honest unavailable; else remote resolve. `DownloadUseCases`. Decorated in `AppGraph` so **`PlaybackViewModel` and `PlayerController` are unchanged** (a `file://` URI plays via the existing `MediaItem.Builder().setUri` path — zero player changes, ADR-0006 single player preserved).
- **UI (honest):** real `DownloadsScreen` + `DownloadsViewModel`, per-track `DownloadToggle` in Songs, Library "Only offline" filter (verified-complete only), Playlist detail "Download all".
- **Architecture enforcement:** new boundary rules V–Y (`AppBoundaryChecks`) + cross-module probes (`DependencyProbeChecks`) asserting UI⟘worker/fs/cache/OkHttp, player⟘download-internals, `core:data`⟘download-persistence.

## 2. Truthful offline semantics (validated by tests)

- `OfflineAvailability.Available` only after a **verified** COMPLETED file (exists + non-empty + size matched) — `DownloadExecutor` integrity check + `DownloadStorage.verify`.
- Downloading/queued/failed/cancelled tracks are **never** labeled "Available offline" — pinned by `DownloadsUiMatrixTest`.
- Library "Only offline" lists only saved tracks with verified downloads; a mid-download track never appears.
- Offline-mode (`hints.offlineOnly`) with no local file fails **honestly** (`SourceErrorUnavailable`), never fabricates availability — pinned by `OfflineFirstResolutionPortTest`.

## 3. Verification results

| Gate | Result |
|------|--------|
| `./gradlew test` (all modules, debug+release variants) | ✅ **329 tests, 0 failures, 0 errors** (5B: 262 → 5C: +67) |
| `core:database` download models + InMemory repo | ✅ |
| `android:roomdb` `RoomDownloadRepositoryTest` + migration | ✅ (43 roomdb tests) |
| `android:worker` `DownloadStorageTest` + `DownloadExecutorTest` (+ CacheHousekeepingWorker) | ✅ (13 worker tests) |
| `core:domain` `OfflineFirstResolutionPortTest` | ✅ (30 domain tests) |
| `app` `DownloadsViewModelTest` + `DownloadsUiMatrixTest` | ✅ (33 app tests) |
| `tests:architecture` (rules V–Y + probes) | ✅ (35 tests) |
| `:app:lintDebug` | ✅ 0 errors |
| `:app:assembleDebug` | ✅ PASS |

### Test/UI-state matrix — download honesty
- `DownloadsUiMatrixTest`: not-downloaded→Download; downloading→progress(never "offline"); failed→Retry(never "offline"); verified-complete→Remove("available offline").
- `OfflineFirstResolutionPortTest`: local-first (no remote call), normal fallback, offlineOnly honesty for every non-verified state.
- `DownloadExecutorTest`: completed persistence, network-failure (no asset left), integrity mismatch (no false asset), mid-stream cancellation (record CANCELLED + asset removed).
- `DownloadStorageTest`: atomic write, deterministic keying, verify exists/size, delete, total-bytes accounting.

## 4. Notes / deviations
- No Android filesystem path reaches the UI: `OfflineAvailability`/`DownloadEntry` are the only domain answers; file paths stay in `android:worker` (enforced).
- Download execution is split into the WorkManager adapter (`DownloadWorker`) and the directly-testable `DownloadExecutor` to keep worker plumbing thin and the resolve→store→verify logic unit-testable without WorkManager.
- Compose UI-state tests (`DownloadsUiMatrixTest`) are `testDebugUnitTest`-exclusive (Compose `ui-test-manifest` is debug-only), mirroring `LibraryUiMatrixTest`; the release unit-test variant stays clean and is excluded for those two classes.
- `ResolvedSource.httpHeaders` carries only non-secret headers; no credentials are stored in the download/cache tier (ADR-0004/ADR-0002).

## 5. Deliberately NOT in scope (future)
Accounts/sync, auto-download preferences, lyrics, EQ, radio/recommendations, cross-fade, cross-source search, Auto/Wear, trimming/silence.

## 6. Recommendation
**Proceed.** Deliverables complete: a real, truthful end-to-end download-and-offline vertical slice — resolve → download → durable verified file → Downloads/Library surface → offline availability → offline playback via the unchanged `PlayerController`. The whole project builds, lints clean, and passes 329 tests (0 failures, 0 errors).
