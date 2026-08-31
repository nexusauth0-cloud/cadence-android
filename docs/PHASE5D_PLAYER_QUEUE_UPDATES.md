# Phase 5D — Full Player + Queue + Update Foundation

Date: 2026-08-31
Scope: complete player + mini-player + queue experience with durable queue persistence/restoration, playlist/radio context, playback-state UX, history integration, audio normalization capability (`SetNormalization` command), `UpdateManager` foundation + update modal, and comprehensive UI/a11y tests.

## 1. What was built

### Queue domain + persistence (`core:database`, `core:domain`)
- `QueueModels` (`QueueSession`/`QueueItem`/`QueueAddedReason`/`QueueKind`), `QueueStore` port + `InMemoryQueueStore` (seed/add/playNext/reorder/removeAt/move/clear), `QueueUseCases`.
- Room queue persistence (`android:roomdb`): `QueueSessionEntity` (id=1 singleton) + `QueueItemEntity` (composite PK (session_id, position)), `QueueDao.replaceAll`, `RoomQueueStore` mirroring the in-memory store, DB v3→v4 with forward-only `MIGRATION_3_4` (declared entity has no index → migration creates none). `RoomQueueStoreTest` + hand-built-DB migration test green.

### Player presentation + queue integration (`app/viewmodel`)
- `PlayerViewState.kt`: `PlayerUiState`/`PlayerViewActions`/`PlayerViewStatus`/`PlayerViewState.status()`/`PlayerViewStateMapper`; non-retryable `currentError` → `Unavailable(message)`, retryable → `Failed` with `canRetry`.
- `PlaybackViewModel`: `queue: StateFlow<QueueSession?>`, `queueStore`/`downloadRepository` params, `playQueueItem(position)` + `trackFor(item)`, restore-on-init, skip/reorder/remove/play-next/clear, `playSession(session, contextLabel)`, offline indicator, history recording only on genuine Ready/Playing with a dedup set.
- `PlayerViewState.isPlaying` extension (Playing || OfflinePlaying) + truthful row indicators (a track row shows "playing" only when it is the current track AND audio is genuinely playing).

### Full player + mini-player + queue sheet UI (`app/ui`, `app/screen`)
- `PlayerScreen.kt`: consumes the presentation contract (playerUiState, positionMs, currentTrack); status line, offline badge (verified-only), playlist context label ("Playing from X"), queue entry, seek/transport/retry.
- `MiniPlayer.kt`: drops `core:media`, takes `track`/`visible`/`playing`/`offlineAvailable`; offline badge only for a verified local download (honesty pinned by test).
- `QueueSheet.kt`: `ModalBottomSheet` with now-playing/up-next rows, explicit move-up/down with spoken position semantics, remove, clear, context label.

### Audio normalization capability
- `PlaybackCommand.SetNormalization(enabled)` in `core:media`; `Media3PlayerController.command(...)` toggles `LoudnessNormalizationEffect` on the effect chain; `PlayerControllerFake` records the last normalization flag; `PlaybackViewModel` exposes `normalizeAudio` StateFlow + `setNormalization()`; `NormalizationToggle` setting row. Test asserts the command reaches the controller and the state flips.

### Update foundation (`core:domain/update`) + app shell
- `SemanticVersion` (strict parse + full SemVer precedence incl. numeric<alphanumeric prerelease), `UpdateModels` (channel/info/state Hidden|Checking|Available|NotAvailable|Failed), `UpdateSource` port (`CURRENT_APP_VERSION=1.0.0`, allow-listed https store host), `UpdateManager` (event-driven `check()`/`dismiss()`, `isSafeStoreUrl`, `UpdatePolicy.canDefer = !critical`), `NoopUpdateSource`, `FakeUpdateSource` (core:testing).
- `UpdateViewModel` (thin presenter over the manager) + `UpdateDialog` (Available→store CTA+"Later", critical→no dismiss; Checking progress; Failed retry/ignore). Wired in `CadenceApp` via `AppGraph.updateManager` with a one-time `check()` (no polling loop); `onOpenUpdate` opens the validated store URL.

## 2. Honest playback-state UX (pinned by tests)
- Offline badge appears only for a verified local download (`MiniPlayer`/`PlayerScreen`); unavailable/queued/failed states never fake availability.
- Queue sheet tells the screen reader the playlist context, the now-playing row, and spoken positions for reordering.
- APA transitions stay Idle→Buffering→Ready→Playing↔Paused, →Ended, any→Failed, delivered by the unchanged `PlayerController` (single-player invariant, ADR-0006).
- The update modal never fabricates an update: `NoopUpdateSource` reports nothing; any real source is injected at the composition root.

## 3. Architecture enforcement
- New boundary **rule Z** (`AppBoundaryChecks`): screens/components never import `core:media` (excludes `viewmodel` + `di` + `CadenceApplication`). Refactored `SearchScreen`/`LibrarySections`/`ErrorText` so the UI reaches playback only through the presentation contract / `viewmodel` adapter — any `core:media` / `PlaybackState` / `PlaybackError` import in the UI now fails the build.
- Existing rules O/R/U/V/W/X (no Media3/Room/fs/Worker/OkHttp in UI) and cross-module probes still enforced and green.

## 4. Verification
Full `./gradlew test` + `:app:testDebugUnitTest` + `:app:testReleaseUnitTest` + `:tests:architecture:test` + `:app:lintDebug` all green.

| Gate | Result |
|------|--------|
| `./gradlew test` (JVM modules: core, source, architecture) | ✅ 255 tests |
| app debug unit tests (incl. new queue/player UI/update UI/normalization tests) | ✅ 59 tests |
| `android:roomdb` (RoomQueueStore + MIGRATION_3_4) | ✅ 50 tests |
| `android:player` (NormalizationEffect path) | ✅ 4 tests |
| `android:worker` / `android:diskcache` | ✅ 13 / 8 tests |
| `tests:architecture` (incl. rule Z) | ✅ 36 tests |
| `:app:lintDebug` | ✅ 0 errors |

## 5. Deliberately NOT in scope (per Phase 5D absolute rules / ADR-0006)
No Metrolist edits, no PR #4301 changes, no second player, no player logic in Compose, no bypassing `PlayerController`, no polling/watchdog loops, no string-based recovery, no Media3 types through UI/domain APIs, no EQ/trim-silence UI / lyrics / crossfade / radio engine / cross-source search / accounts/sync / Auto / Wear / visualizer / social / recommendations.
