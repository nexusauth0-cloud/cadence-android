# Phase 5B — Library + Playlists — GO/NO-GO

Date: 2026-08-31
Scope: real local library (save/favorite), Room-backed playlists (CRUD, reorder, add/remove, >999-id batching), play history, playlist→queue→`PlayerController` playback, search↔library integration, honest offline-aware UI states, architecture enforcement, full test/UI-state matrix.

## Verdict: **GO** ✅

All build, lint, unit, architecture, and e2e gates are green. No blocking issues.

---

## 1. What was built (summary)

- **Library & history (persistent, offline):** `SavedTrack`/`PlayHistoryEntry`/`PlaylistTrack` models with `toTrack()` reconstruction from cached columns (ADR-0003). `Core:database` ports + in-memory impls; `android:roomdb` Room impls (`saved_tracks`, `play_history`) via `CadenceDatabase` v2 + forward-only `MIGRATION_1_2`. Domain use cases: save/unsave/observe library, record/observe/clear history.
- **Playlists (Room-backed):** CRUD + rename + reorder + add/remove; `PlaylistDao.positionsIn` batches lookups to stay under the SQLite variable limit (>999 ids); `AddToPlaylistUseCase` returns `AddToPlaylistResult(added, alreadyPresent)` and never silently re-inserts duplicates.
- **Playback:** `PlaylistSession(tracks, startIndex)` → `PlaybackViewModel.playSession()`; session-aware `skipToNext`/`skipToPrevious`; no second ExoPlayer (single shared `PlayerController`, ADR-0006). History recorded only on genuine `Playing`/`Ready` via a `recordedRefs` dedup set.
- **Search↔library:** `SearchScreen` rows carry save-heart + add-to-playlist affordances.
- **Honest UI:** heart says "Save/Remove from library", never "Available offline"; Albums/Artists show "Soon" (no fake counts); add-to-playlist dialog reports Add/Added/Already there/partial.

## 2. Architecture enforcement

- Fixed `rule Q` data-adapter false positive (package-prefix matching: `com.cadence.core.data.`).
- Added `rule R–U` (no Room entities/DAOs in UI; repo interfaces only in composition root; no CredentialVault in UI; no media3 outside composition root) and a new `DependencyProbeChecks` class asserting cross-module direction (persistence⟘UI, persistence⟘Media3, persistence⟘providers, providers⟘UI, domain free of Room annotations, ViewModels/Screens never reach `android:roomdb`).
- Results: `AppBoundaryChecks`, `SourceBoundaryChecks`, `CompiledArtifactChecks`, `DependencyProbeChecks` all green (28 architecture tests).

## 3. Verification results

| Gate | Result |
|------|--------|
| `:core:database:test` | ✅ PASS |
| `:android:roomdb` — library (5) + batching semantics (10) + migration | ✅ PASS |
| `:core:domain:test` — library/playlist/history/playlist-playback use cases | ✅ PASS |
| `:app:testDebugUnitTest` — playback session (6) + library VM (3) + add-to-playlist VM (3) + search slice + error matrix | ✅ PASS |
| `:app:testDebugUnitTest` — `LibraryUiMatrixTest` (Robolectric Compose UI-state matrix, 5) | ✅ PASS |
| `:tests:architecture:test` | ✅ PASS (28 tests) |
| `:tests:e2e:test` | ✅ PASS |
| `./gradlew test` (all modules, both build-type variants) | ✅ PASS — **262 tests, 0 failures, 0 errors** |
| `:app:lintDebug` | ✅ 0 errors |
| `:app:assembleDebug` | ✅ PASS |

### Test/UI-state matrix coverage
`core:database` (24) · `android:roomdb` (28) · `core:domain` (22) · `app` (26 incl. UI-state matrix) · architecture (28) — all green. UI-state matrix pins honest copy + accessibility (content descriptions) and the offline honesty rule.

## 4. Notes / deviations
- Playlist playback reconstructs playable `Track`s from the cached metadata columns because the SPI has no `track(id)` (ADR-0002 post-spike TRACK_DETAIL/RELATED/RADIO remain unimplemented) — ADR-0003 cache-columns design.
- `./gradlew build` split for verification (tooling timeout): `./gradlew test` then `:app:lintDebug` / `:app:assembleDebug`.
- Compose UI-state tests are `testDebugUnitTest`-exclusive (they need the debug-only Compose `ui-test-manifest`); the release unit-test variant remains clean.

## 5. Deliberately NOT in scope (future)
Downloads/offline, lyrics, EQ, radio/recommendations, accounts/sync, Auto, Wear OS, crossfade, album/artist grouping.

## 6. Recommendation
Proceed. Deliverables complete: real Room-persisted library + playlists with >999-id batching, honest offline-aware UI states, playlist→queue→PlayerController playback, search↔library integration, architecture enforcement (incl. new cross-module probes), and a full test/UI-state matrix. The whole project builds, lints clean, and all 262 tests pass.
