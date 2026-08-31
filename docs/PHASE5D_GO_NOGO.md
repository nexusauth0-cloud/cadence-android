# Phase 5D — Full Player + Queue + Update — GO/NO-GO

Date: 2026-08-31
Scope: full player + mini-player + queue experience with durable queue persistence/restoration, playlist/radio context, playback-state UX, history integration, audio normalization capability (`SetNormalization` command), `UpdateManager` foundation + update modal, and comprehensive UI/a11y tests.

## Verdict: **GO** ✅

All build, lint, unit, architecture, and UI/accessibility gates are green. No blocking issues. The player/queue surface is honest (offline/playing/update availability are distinct, verified states) and the app-shell depends on the presentation contract only — no `core:media` types reach the UI.

---

## 1. What was built (summary)

- **Queue domain + use cases (`core:database`, `core:domain`):** `QueueModels`, `QueueStore` port, `InMemoryQueueStore`, `QueueUseCases`.
- **Room queue persistence (DB v4):** `QueueEntities` (singleton session + positioned items), `QueueDao.replaceAll`, `RoomQueueStore`, forward-only `MIGRATION_3_4` (entity declares no index → no index created). Hand-built-DB migration test green.
- **Player presentation (`app/viewmodel`):** `PlayerViewState.kt` contract (UiState/Actions/Status/mapper + `isPlaying`), `PlaybackViewModel` queue integration (restore, playQueueItem, skip/reorder/remove/play-next/clear, `playSession(session, contextLabel)`, offline indicator, genuine-start history dedup). UI is rule-Z compliant: no `core:media`/`PlaybackState`/`PlaybackError` imports in screens/components.
- **Full player + mini-player + queue sheet UI:** `PlayerScreen`, `MiniPlayer`, `QueueSheet` (ModalBottomSheet, spoken positions for reorder, context label, remove/clear).
- **Audio normalization:** `PlaybackCommand.SetNormalization` handled in `Media3PlayerController` (toggles `LoudnessNormalizationEffect` on the effect chain) and `PlayerControllerFake` (records flag); `PlaybackViewModel.normalizeAudio` + `setNormalization()`; `NormalizationToggle` row; command-reaches-controller + state-flip test.
- **Update foundation (`core:domain/update`) + shell:** `SemanticVersion`, `UpdateModels`, `UpdateSource` port (https allow-listed store host), `UpdateManager` (event-driven `check()/dismiss()`, `UpdatePolicy.canDefer = !critical`), `NoopUpdateSource`, `FakeUpdateSource`, `UpdateViewModel`, `UpdateDialog` (critical not dismissible), one-time `check()` at startup (no polling), `onOpenUpdate` opens the validated store URL.

## 2. Honest UX invariants (validated by tests)
- Offline badge only for a **verified** local download — never for queued/failed/unavailable (honesty pinned by UI matrix).
- A track row shows "playing" only when it is the current track AND audio is genuinely playing (presentation `isPlaying`).
- Queue sheet announces playlist context, the now-playing row, and spoken reorder positions for screen readers.
- Update modal: `NoopUpdateSource` reports nothing; critical updates have no dismiss affordance; store URL is https + allow-listed.

## 3. Verification results

| Gate | Result |
|------|--------|
| `./gradlew test` (JVM modules) | ✅ 255 tests, 0 failures, 0 errors |
| app debug unit tests (queue/player UI/update UI/normalization) | ✅ 59 tests, 0 failures |
| `android:roomdb` (RoomQueueStore + migration) | ✅ 50 tests |
| `android:player` | ✅ 4 tests |
| `android:worker` / `android:diskcache` | ✅ 13 / 8 tests |
| `tests:architecture` (incl. rule Z) | ✅ 36 tests |
| `:app:testReleaseUnitTest` (UI-matrix exclude list) | ✅ PASS |
| `:app:lintDebug` | ✅ 0 errors |

## 4. Notes / deviations
- Rule **Z** was added: screens/components must never import `core:media`. Three pre-existing UI files that reached into `PlaybackState`/`PlaybackError` were refactored onto the presentation contract (`SearchScreen`, `LibrarySections`) and the adapter boundary (`ErrorText` → `viewmodel/PlaybackErrorUi`). `CadenceApplication` (Application composition root wiring `PlayerController`) and the `viewmodel`/`di` packages are the only allowed `core:media` dependencies.
- Compose UI-state tests (`PlayerUiMatrixTest`, `UpdateDialogUiTest`) are `testDebugUnitTest`-exclusive and added to the release unit-test exclude list (Compose `ui-test-manifest` is debug-only), mirroring earlier phases.
- The `SetNormalization` command is routed through the controller effect chain; the `LoudnessNormalizationEffect` toggle is delivered via the effect chain, not the player request itself.

## 5. Deliberately NOT in scope (future)
EQ/trim-silence UI, lyrics, crossfade, radio/recommendations engine, cross-source search, accounts/sync, Auto/Wear, visualizer, social, real network update feed (source is injectable; `NoopUpdateSource` ships as the safe default).

## 6. Recommendation
**Proceed.** Deliverables complete: a durable, restorable queue with playlist/radio context, a full presentation-driven player + mini-player + queue-sheet experience with honest playback/offline state, an audio-normalization capability, an update-manager foundation with a critical-aware modal, and comprehensive UI/a11y tests. Whole project builds, lints clean, architecture boundaries enforced, and the full suite is green.
