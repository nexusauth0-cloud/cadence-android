# Phase 5B — Library + Playlists (vertical slice)

Date: 2026-08-31
Scope: real local library (save/favorite tracks), Room-backed playlists (CRUD, reorder, add/remove, >999-id batching), play history, playlist→queue→`PlayerController` playback, search↔library integration, honest UI states with offline honesty, architecture enforcement, full test/UI-state matrix, and a GO/NO-GO report.

Out of scope (this slice): downloads, lyrics, EQ, radio/recommendations, accounts/sync, Auto, Wear OS, crossfade, album/artist grouping.

## 1. What was built

### Local library (save/favorite), play history — persistent & offline
- **`core/database` ports + models:** `LibraryRepository` (port), `SavedTrack`, `PlayHistoryEntry` (both with `toTrack()` reconstructing a playable `Track` from cached columns — ADR-0003 "CACHE COLUMNS"), `InMemoryLibraryRepository` (+5 tests).
- **`android/roomdb` implementation:** `LibraryEntities` (`SavedTrackEntity`, `PlayHistoryEntity`), `LibraryDao`, `RoomLibraryRepository`. `CadenceDatabase` v2 with `MIGRATION_1_2` that ALTERs `playlist_tracks` (adds cached `title`/`artist`/`durationMs`/`artworkUrl` from ADR-0003) and CREATEs `saved_tracks` + `play_history`. DB stays at v2 (v2 never released; migrations forward-only, never destructive — ADR-0005).
- **Domain use cases:** `SaveTrackUseCase`, `UnsaveTrackUseCase`, `ObserveLibraryUseCase`, `RecordPlayHistoryUseCase`, `ObserveHistoryUseCase`, `ClearHistoryUseCase`. `RecordPlayHistoryUseCase` is the single choke point: it is invoked **only after** a track has resolved and started playing (see §3), so failed/cancelled attempts are never recorded.

### Room-backed playlists
- **`core/database`:** `PlaylistId`, `PlaylistMetadata`, `PlaylistTrack`, ports `PlaylistRepository`; `InMemoryPlaylistRepository` implements the full contract.
- **`android/roomdb`:** `PlaylistEntities`/`PlaylistDao`, `RoomPlaylistRepository`; **`PlaylistDao.positionsIn`** batches the add/remove lookups.
- **Domain use cases (`PlaylistUseCases.kt`):** `CreatePlaylistUseCase`, `RenamePlaylistUseCase`, `DeletePlaylistUseCase`, `RemoveTrackFromPlaylistUseCase`, `ReorderPlaylistUseCase`, `ListPlaylistsUseCase`, `GetPlaylistTracksUseCase`, `GetPlaylistMetadataUseCase`, `AddToPlaylistUseCase`.

### Playlist→queue→PlayerController playback (no second ExoPlayer)
- `PlaylistSession(tracks, startIndex)` — a point-in-time **ordered view**; the playlist is the durable source (no queue table, ADR-0003 intent).
- `PlaylistPlaybackUseCase` (domain) reconstructs displayable `Track`s from cached metadata via `toTrack()` — required because `SourceProvider.track(id)` does **not** exist in the SPI (only `search` + `resolve`; the ADR-0002 post-spike crater features remain unimplemented).
- `PlaybackViewModel.playSession()` sets `session`/`sessionIndex` StateFlows; `skipToNext`/`skipToPrevious` become session-aware (advance via `launchLoad` on the neighbour track); `loadAndPlay` clears the session. History is recorded by observing `controller.state` at `Playing`/`Ready` with a `recordedRefs` dedup set.

### Search ↔ library integration
`SearchScreen` rows gain a `SavedHeartButton` (via `observeIsSaved(ref)`) and a `TrackOverflowMenu` opening `AddToPlaylistDialog` — a track found in search can be saved or filed into a playlist without leaving the flow.

### Honest UI states + offline honesty
- Saving reports **"Saved to library"** / **"Remove from library"** — **never** "Available offline" or "Download". Heart reflects real saved-state.
- Albums/Artists render as honest **"Soon"** rows with no fake counts (this slice stores no album/artist grouping).
- `AddToPlaylistDialog` gives per-playlist feedback: **Add / Added / Already there / partial** (`Added N · M already`); duplicates are never silently re-inserted (`AddToPlaylistResult.added/alreadyPresent`, `.partial`).
- `LibrarySectionRow` shows **real** counts, and honest "Soon" where data does not exist.

## 2. Architecture enforcement (new, Phase 5B)

Extended `tests/architecture`:

- **`AppBoundaryChecks` rule Q fix:** data-adapter matching is now package-prefix precise (`com.cadence.core.data.` with trailing dot), so `com.cadence.core.database.*` is no longer a false positive.
- **New rules R–U:** UI never imports `androidx.room` (covers `@Entity`/`@Dao`/`@Database`), repository interfaces live only in the composition root, `CredentialVault` never reaches the UI, and no media3/ExoPlayer import beyond the composition root.
- **New `DependencyProbeChecks`** (cross-module direction probes): persistence (`android:roomdb`/`core:database`) must not depend on UI, Media3, or provider impls; providers must not depend on UI; domain/`core:database` stay free of Room annotations; ViewModels/Screens never reach the `android:roomdb` package.

## 3. Verification results

| Gate | Result |
|------|--------|
| `:core:database:test` — library + playlist models/in-memory | ✅ PASS |
| `:android:roomdb` — `RoomLibraryRepositoryTest` (5) + `PlaylistBatchingSemanticsTest` (10) + `CadenceDatabaseMigrationTest` | ✅ PASS |
| `:core:domain:test` — library/playlist/history/playlist-playback use cases | ✅ PASS |
| `:app:testDebugUnitTest` — `PlaybackSessionTest` (6) + `LibraryViewModelTest` (3) + `AddToPlaylistViewModelTest` (3) + search slice + error matrix | ✅ PASS |
| `:app:testDebugUnitTest` — `LibraryUiMatrixTest` (Robolectric Compose UI-state matrix, 5) | ✅ PASS |
| `:tests:architecture:test` | ✅ PASS (28 tests) |
| `:tests:e2e:test` | ✅ PASS |
| `./gradlew test` (all modules, both build-type unit variants) | ✅ PASS — **262 tests, 0 failures, 0 errors** |
| `:app:lintDebug` | ✅ 0 errors |
| `:app:assembleDebug` | ✅ PASS |

### UI-state matrix (Robolectric Compose)
Render checks that pin the honest-state contract without ExoPlayer: unsaved heart offers "Save to library" and never "Available offline"/"Download"; saved heart reports "Remove from library"; overflow menu opens Add-to-playlist; section rows show real counts and honest "Soon" for Albums/Artists. The Compose `ui-test-manifest` (debug-only `ComponentActivity`) makes these `testDebugUnitTest`-exclusive so the release unit-test variant stays clean.

## 4. Notes / deviations
- Playlist playback reconstructs playable `Track`s from cached columns because the SPI exposes no `track(id)` (ADR-0002 post-spike features like TRACK_DETAIL/RELATED/RADIO remain unimplemented). This matches ADR-0003's cache-columns design.
- `./gradlew build` was split for verification (tooling timeout on the monolithic build): `./gradlew test` first, then `lintDebug`/`assembleDebug`.
- Domain library models live in `core:database` (pure, no Room annotations) and are consumed as models only; repository *interfaces* are confined to the composition root.

## 5. Deliberately NOT in scope (future)
Downloads/offline, lyrics, EQ, radio/recommendations, accounts/sync, Auto, Wear OS, crossfade, album/artist grouping UI.
