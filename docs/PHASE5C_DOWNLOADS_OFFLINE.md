# Phase 5C — Downloads + Offline vertical slice

Date: 2026-08-31
Scope: real track → resolve → download → persistent local file → metadata association → Downloads/Library surface → offline availability → offline playback (PlaybackViewModel resolves local-first silently).

## 1. The vertical slice

The slice proves the full download-and-offline loop end to end, truthfully:

```
Track  ──(enqueue)──▶ DownloadEntry(QUEUED)
                          │ (WorkManager CoroutineWorker)
                          ▼
        resolution.resolve(ref, ResolutionHints())  ── remote stream URL
                          │
                          ▼
   HttpByteSource.open(playbackUri) ──▶ DownloadStorage.writeStream (atomic tmp→rename)
                          │
                          ▼
       integrity verify (exists + non-empty + size matches)
                          │
                          ▼
        DownloadRepository.complete(localUri=file://..., fileSizeBytes)
                          │
                          ▼
       OfflineAvailability.Available(localUri, fileSizeBytes)  ──▶ PLAYBACK
```

Playback needs **zero player changes**: `PlayerController.load` already uses
`MediaItem.Builder().setUri(playbackUri)` and accepts a `file://` URI. The
resolution port is decorated in the composition root so `PlaybackViewModel`
resolves local-first transparently.

## 2. Truthful offline semantics (the core promise)

"Saved to library" and "Available offline" are **distinct** states, and nothing
is ever labeled available offline before the file is durable and verified.

| State | Meaning | UI label |
|-------|---------|----------|
| `QUEUED` | Enqueued, not yet started | "Queued" (progress, cancel) |
| `DOWNLOADING` | Streaming in progress | "Downloading · N%" (progress, cancel) |
| `COMPLETED` | File verified durable + size-matched | **"Available offline"** (remove) |
| `FAILED` | Transient failure; retriable | "Failed" (retry) |
| `CANCELLED` | User/stop cancelled | "Cancelled" (retry) |
| (missing) | Not enqueued | download button (Never claims offline) |

`OfflineAvailability` is the sealed domain answer the UI consults; the UI never
inspects filesystem paths (`OfflineAvailability.Available` only after a verified
file, section 17). A track mid-download, failed, cancelled, or missing is
**never** labeled available offline (verified by `DownloadsUiMatrixTest`).

## 3. Architecture (layering preserved)

```
core:database   DownloadModels + DownloadRepository port (pure), InMemory impl, tests
core:domain     DownloadUseCases + OfflineFirstResolutionPort (policy), tests
android:worker  DownloadStorage, HttpByteSource, DownloadExecutor, DownloadWorker, DownloadService
android:roomdb  DownloadEntity/Dao + RoomDownloadRepository (DB v3, MIGRATION_2_3)
app/di          AppGraph wires OfflineFirstResolutionPort over the SPI adapter + Room repo
app/viewmodel   DownloadsViewModel (honest Downloads surface)
app/screen      DownloadsScreen + Library "Only offline" filter + download toggles
```

Enforced boundary rules (new, section 22 in `AppBoundaryChecks` +
`DependencyProbeChecks`):
- **UI → DownloadWorker/fs/cache forbidden** — screens/ViewModels depend only on
  `DownloadOperationsPort` + domain types (`DownloadEntry`/`OfflineAvailability`);
  worker/storage/OkHttp internals are confined to the `di` package and
  `CadenceApplication.kt`.
- `PlayerController` / player module never imports download internals or the
  offline adapter.
- `core:data` (SPI adapters) never depends on download persistence.
- No credentials/secrets in the download tier (streams use `ResolvedSource`
  headers only; `CredentialVault` stays out of the UI).

## 4. Components

### DownloadRepository (port) — `core:database`
`enqueue / markDownloading / updateProgress / complete / fail / cancel / remove /
retry` + `observeAll / observe / get / getByRef / offlineAvailability /
offlineUri / storageReport / completedCount / observeCompletedRefs`. Dedup:
`enqueue` reuses the existing non-`COMPLETED` entry for the same ref; a completed
entry is deleted to allow re-download (unique index on `provider_id+native_id`).

### DownloadStorage — `android:worker`
Owns durable files (ADR-0007 DOWNLOADS tier semantics). Atomic writes via
`.tmp` → rename so a partial/corrupt file is never mistaken for a completed
asset. `verify(key, expectedSize)` checks exists + non-empty + size match
(section 17). `isPlayableOffline` is derived only after that verify passes.
Storage reports human-readable usage for the Downloads screen (never raw tier
internals).

### DownloadExecutor — `android:worker`
The testable core: resolve → stream → atomic write → integrity-verify → commit,
honoring a suspend `isStopped` predicate for cancellation between chunks. Maps
outcomes to `Completed` / `Retriable`.

### DownloadWorker — `android:worker`
Thin WorkManager `CoroutineWorker` adapter feeding `DownloadExecutor`; survives
process death (WorkManager + Room). Enqueue uses `ExistingWorkPolicy.KEEP` for
dedup; cancel via unique work name.

### DownloadService — `android:worker`
Application-level orchestrator implementing `DownloadOperationsPort`
(`downloadAll`, `downloaded/completed download`, cancel, remove-with-file-cleanup,
retry). The UI sees only this domain seam, never WorkManager.

### OfflineFirstResolutionPort — `core:domain`
Source-selection policy (section 9):
1. verified local download → return local `file://` `ResolvedSource` (no network)
2. else if `hints.offlineOnly` → honest `SourceErrorUnavailable` (no network)
3. else → remote resolve.

Wired in `AppGraph` so `PlaybackViewModel` (unchanged) plays local-first. When
offline-mode is active and nothing local exists, the failure is honest — never a
fake "available offline".

## 5. UI surface

- **DownloadsScreen (real):** queued / downloading / downloaded / failed /
  cancelled rows with storage strip; cancel / remove / retry; truthful empty
  state.
- **Library Songs "Only offline" filter:** shows only saved tracks whose
  download is VERIFIED complete (truthful filter, never in-progress tracks).
- **Per-track DownloadToggle** in Songs: download / progress(cancel) /
  retry / remove; shows "available offline" only when verified.
- **Playlist detail "Download all":** batch-downloads non-completed tracks.

## 6. Verification

| Gate | Result |
|------|--------|
| `./gradlew test` (all modules, both build-type variants) | ✅ **329 tests, 0 failures, 0 errors** |
| `core:database` download port tests (incl. `OfflineFirstResolutionPortTest` in domain) | ✅ |
| `android:roomdb` `RoomDownloadRepositoryTest` (43 roomdb tests overall) | ✅ |
| `android:worker` `DownloadStorageTest` + `DownloadExecutorTest` (+ CacheHousekeepingWorker) | ✅ |
| `core:domain` `OfflineFirstResolutionPortTest` (local-first / offlineOnly honesty) | ✅ |
| `app` `DownloadsViewModelTest` + `DownloadsUiMatrixTest` (honest offline states) | ✅ |
| `tests:architecture` boundary rules V–Y + new probes | ✅ (35 tests) |
| `:app:lintDebug` | ✅ 0 errors |
| `:app:assembleDebug` | ✅ PASS |

## 7. Deliberately NOT in scope (future)
Accounts/sync, auto-download preferences, lyrics, EQ, radio/recommendations,
cross-fade, cross-source search, Auto/Wear, trimming/silence, gated DRM-licensed
downloads beyond the honest availability model.

## 8. Recommendation
Proceed to Phase 5D. The full track→resolve→download→durable-file→
metadata-association→offline-availability→offline-playback loop is implemented
truthfully, persistence is Room-backed at DB v3 with a forward-only migration,
architecture boundaries are enforced by new probes, and the whole project builds,
lints clean, and passes 329 tests.
