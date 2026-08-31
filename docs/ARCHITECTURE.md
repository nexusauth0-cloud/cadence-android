# Architecture

Cadence is a layered, port-and-adapter Android application. This document
explains the boundaries that keep playback, storage, and providers
independent from one another and testable.

## Layering

```
UI (Compose)
   │
   ▼
Presentation (view models)
   │
   ▼
Domain (use cases, ports, policies)
   │
   ▼
Infrastructure (Android adapters, providers)
```

Dependencies point inward. Domain and presentation never depend on concrete
Android or provider classes; they depend on interfaces defined close to the
domain.

## Major boundaries

### Search

Search is a domain use case that calls a `SourceProvider`. It returns a
provider-independent model (`core:model`) — never provider-internal types.
The `:source:api` module defines the SPI; `:source:demo` and `:source:ytmusic`
implement it.

### SourceProvider

The `SourceProvider` SPI is the single place the app talks to a music source.
It hides provider details (URLs, wire formats, playback URL resolution) behind
a stable interface. Providers are isolated in their own modules so a provider's
types cannot leak into the app.

### PlayerController

`PlayerController` (`core:media`) owns playback semantics — states, commands,
and the audio effect chain. `Media3PlayerController` (`android:player`) is an
adapter. The app expresses intent through the controller contract; it does not
reach into Media3 directly.

### PlaylistRepository / LibraryRepository / QueueStore

Local, durable storage driven by use cases. The domain depends on repository
interfaces; Room (`android:roomdb`) and in-memory stores implement them. Library,
playlists, history, and queue live in application storage and are
provider-independent.

### CredentialVault

Credentials flow exclusively through the `CredentialVault` interface
(`core:datastore`), scoped by `CredentialScope`. The Android implementation
(`android:vaultstore`) encrypts at rest via the Keystore. The UI never handles
credential material.

### DownloadRepository / Cache

Offline-first playback relies on a local download tier resolved before any
network source. `Cache` tiers (core + `android:diskcache`) and the
`DownloadRepository`/`OfflinePlayback` domain logic keep downloads and
streaming resolution separate from any single provider's availability.

### Cache

The cache is tiered (`core:cache`) with a `CacheHousekeeper` policy, backed by
an in-memory store and a file-backed tier (`android:diskcache`), with
housekeeping also driven by WorkManager (`android:worker`).

## Why these boundaries

- **Provider independence:** a source can be added, removed, or fail without
  redesigning the app.
- **Offline-first:** local data and downloaded content are first-class, not a
  fallback hack.
- **Testability:** ports allow cheap fakes (`core:testing`); fixture-first
  tests run with no network.
- **Security:** credentials are scoped and encrypted at the infrastructure
  edge, invisible to the UI.
- **Enforcement:** `tests:architecture` codifies the boundaries so they stay
  true over time.

## Enforcement

`./gradlew :tests:architecture:test` compiles the modules and runs checks that
enforce the boundaries above: module dependency direction, a ban on
`core:media` types leaking into UI/presentation code (with documented
exceptions for the composition root and application wiring), bytecode-scan
budgets, blocking-call detection, and batching invariants.

## Conventions

- Ports in domain; adapters in infrastructure.
- NewPipe Extractor is confined to `:source:ytmusic` (`docs/LICENSE_DECISION.md`).
- No live network in tests/CI; use recorded, scrubbed fixtures.
- Small, focused changes over broad refactors (`CONTRIBUTING.md`).
