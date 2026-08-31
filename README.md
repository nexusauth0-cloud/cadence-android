# Cadence

A private, offline-first Android music player built around durable local music
data, reliable playback, and a source-agnostic architecture.

Cadence keeps your music database — library, playlists, history, queue, and
downloads — local and durable, plays back through a pluggable engine, and
isolates third-party providers behind a small source abstraction so no single
provider dictates the app's design.

> **Status: EARLY DEVELOPMENT (experimental).** Cadence is not yet a
> production-ready release. APIs, storage formats, and behavior are still
> evolving. See [Product status](#product-status).

---

## Overview

Cadence is an Android application written in Kotlin and Jetpack Compose. Its
architecture treats the *player* and the *source* as interchangeable ports:

- The **UI** (Compose) talks only to presentation view models.
- **Domain** use cases and the `PlaybackEngine` port define *what* happens.
- **Infrastructure** (Media3, Room, WorkManager, Keystore, cache) provides
  *how* it happens.
- **Providers** (`SourceProvider`) resolve and download music behind a
  stable interface.

This design keeps playback logic independent of any particular streaming
service and keeps the app honest about where data lives.

## Features

### Available

- **YT Music search** — search songs via the YT Music source provider.
- **Playback** — play tracks through a Media3-powered `PlayerController`, with
  play/pause, seek, progress, and playback-state handling (buffering, ready,
  playing, paused, ended, failed).
- **Library & playlists** — local persistent library, custom playlists, and
  playlist details (Room-backed).
- **Queue** — play next, skip, reorder, remove, shuffle session, and clear;
  the queue is persisted (Room) and restored across app restarts.
- **Downloads & offline** — offline-first downloads (WorkManager + byte cache)
  with download/offline status shown in the UI; downloaded tracks can play
  without a provider dependency.
- **History** — recently played history is captured.
- **Encrypted credentials** — optional provider credentials are stored via the
  Android Keystore-backed vault (`AndroidCredentialVault`).
- **Audio normalization** — toggles loudness normalization on the playback
  engine.
- **Update foundation** — a deterministic, provider-independent update-check
  state machine with a modal; no production source is wired to a real network
  feed yet.

### Planned / Incomplete

- **Google / YouTube account sign-in** with full session persistence.
- **Background media button / notification** integration (MediaSession
  notifications are not yet surfaced).
- **Chromecast/Auto/Wear** support.
- **Cross-source search** and unified across-provider results.
- **Lyrics, crossfade, and EQ** advanced playback features.
- **Backup/restore** of the full library.
- **Public release channels** (Google Play and/or direct APK store).

## Why Cadence

- **Offline-first and private.** Your library, playlists, history, queue, and
  downloads live on your device. There is no analytics or telemetry.
- **Source-agnostic.** Providers are isolated behind `SourceProvider`; the app
  does not couple to a single service's internals.
- **Durable local data.** Persistence is explicit (Room + cache), not an
  afterthought.
- **Testable architecture.** Pure domain, port-based infrastructure, and
  fixture-first testing keep boundaries clean and fast.

## Architecture

```
UI (Compose)
   │
   ▼
Presentation (view models)
   │
   ▼
Domain (use cases, PlaybackEngine, ports)
   │
   ├──► SourceProvider (source/api) ──► providers (demo, ytmusic)
   ├──► PlayerController (core/media) ──► Media3
   ├──► PlaylistRepository / LibraryRepository / QueueStore (Room)
   ├──► CredentialVault (datastore) ──► Android Keystore
   ├──► DownloadRepository / Cache (Room + disk)
   └──► UpdateSource (update foundation)
```

Key boundaries, and why they exist:

- **Search / SourceProvider** — resolve playback URLs without leaking
  provider-internal types into the app.
- **PlayerController / PlaybackEngine** — the app owns playback semantics; the
  engine adapter (Media3) is an interchangeable detail.
- **Playlist / Library / Queue repositories** — local, durable, and
  provider-independent storage.
- **CredentialVault** — credentials only ever flow through a scoped,
  encrypted interface, never through the UI.
- **DownloadRepository / Cache** — offline-first resolution independent of any
  single provider's availability.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for more detail.

## Offline behavior

Cadence is designed to be useful without a network:

- Downloaded tracks resolve and play entirely offline.
- The queue is persisted locally and restored on restart.
- Library, playlists, and history are always local.

Offline playback never depends on a single provider being reachable; it uses
the download/local tier first.

## Privacy

Cadence is local-first and does not include analytics, telemetry, or crash
reporting. What data Cadence stores, what it sends over the network, and what
permissions it uses are documented in [PRIVACY.md](PRIVACY.md).

## Installation

Cadence is not yet published to a release channel. To try the current
development build, build it yourself:

1. Install a JDK 17+ (CI uses Temurin 21) and the Android SDK.
2. Create `local.properties` with `sdk.dir=<your-android-sdk>`.
3. `./gradlew :app:assembleDebug`
4. Install the resulting APK from `app/build/outputs/apk/debug/`.

No binaries are committed to this repository; published binaries (when
available) will be delivered via GitHub Releases.

## Development

Requirements and workflow are documented in
[CONTRIBUTING.md](CONTRIBUTING.md). In brief:

- JDK 17+ (20 recommended; CI uses Temurin 21).
- Fixture-first testing: no live network in tests or CI.
- Build/tests/lint: `./gradlew build`, `./gradlew :app:lintDebug`,
  `./gradlew :tests:architecture:test`.

## Testing

Cadence separates concerns so each layer is tested directly:

- **Unit / view-model / UI-matrix tests** (`:app`) using JUnit + Robolectric.
- **Domain** tests for use cases, policies, and the update/version logic.
- **Android** tests (Room migrations, Media3, vault, disk cache, workers).
- **Architecture** enforcement tests (`:tests:architecture`) that verify
  module boundaries, bytecode scan limits, batching, and no UI→media leaks.
- **E2E** spike proof (`:tests:e2e`).

Recorded provider fixtures drive the YT Music provider tests; CI never makes
live network calls.

## Project structure

| Path | Purpose |
|---|---|
| `app/` | Android application: Compose UI, view models, composition root |
| `core/model`, `core/common`, `core/domain`, `core/data` | Domain model, use cases, ports, SPI adapters |
| `core/database`, `core/datastore`, `core/cache`, `core/media` | Persistence, credentials, cache, playback contracts |
| `source/api`, `source/demo`, `source/ytmusic` | Source provider SPI + implementations |
| `android/player`, `android/roomdb`, `android/vaultstore`, `android/diskcache`, `android/worker` | Android infrastructure adapters |
| `tests/architecture`, `tests/e2e` | Enforcement + integration proof |

## Provider architecture

Cadence does **not** embed a proprietary client and does **not** claim
affiliation with Google or YouTube. The YT Music provider wraps
[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0),
isolated entirely within `:source:ytmusic`. Access depends on the provider's
public services; Cadence makes no guarantee of provider uptime or unrestricted
content access. See [docs/LICENSE_DECISION.md](docs/LICENSE_DECISION.md).

## Downloads

Downloads are tracked locally (Room + byte cache) and executed by WorkManager.
They are never committed to source control. See [PRIVACY.md](PRIVACY.md) for
what download metadata Cadence stores.

## Updates

Cadence's update system is a deterministic state machine over a
provider-independent `UpdateSource` port. It is currently wired to a
"no update" default; no production source activates until a real release
exists. Store links are validated against an https allow-list — Cadence never
installs arbitrary APKs from unchecked URLs. See
[docs/PHASE5D_PLAYER_QUEUE_UPDATES.md](docs/PHASE5D_PLAYER_QUEUE_UPDATES.md).

## Roadmap

- Account sign-in and full session persistence.
- Media-button / notification integration.
- Public release channel + signed builds (GitHub Actions → GitHub Releases).
- Backup/restore of library data.
- Broader device support (Wear/Auto).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Small, focused changes are preferred
over broad unrelated refactors. Report security issues per
[SECURITY.md](SECURITY.md).

## License

Cadence is licensed under the **GNU General Public License v3.0** (GPL-3.0).
See [LICENSE](LICENSE). Third-party notices are in [NOTICE](NOTICE). The YT
Music provider uses NewPipe Extractor, also GPL-3.0; see
[docs/LICENSE_DECISION.md](docs/LICENSE_DECISION.md).

## Third-party notices

See [NOTICE](NOTICE) for the licenses and copyright holders of the libraries
Cadence depends on.

## Product status

- **Development status:** experimental / early development.
- **Version:** 0.1.0 (pre-release). No stable API or storage-format guarantees
  yet.
- **Not** production-ready, **not** feature-complete, **not** associated with
  Google, YouTube, or NewPipe the app.
