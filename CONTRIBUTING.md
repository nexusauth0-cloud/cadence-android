# Contributing

Thanks for your interest in Cadence. This guide explains how to set up, build,
test, and contribute to the project, and the conventions we expect contributions
to follow.

## Quick principle

**Small, focused changes are preferred over broad unrelated refactors.** A pull
request should address one coherent concern. If you find several unrelated
issues, open separate pull requests.

## Requirements

- **JDK 17+** (the build targets JVM 17; CI uses Temurin 21).
- **Android SDK** — create a `local.properties` file with `sdk.dir=<your-android-sdk>`
  (this file is git-ignored and must never be committed).
- **Gradle** — use the provided wrapper (`./gradlew`); do not rely on a
  system Gradle.

## Setup

```bash
git clone <repo-url>
cd cadence
# configure your Android SDK path
echo "sdk.dir=/path/to/your/android-sdk" > local.properties
./gradlew --version
```

## Building and testing

```bash
./gradlew build                          # compile + run all JVM tests
./gradlew :app:assembleDebug             # build the debug APK
./gradlew :app:lintDebug                 # Android lint
./gradlew :tests:architecture:test       # architecture enforcement checks
./gradlew :tests:e2e:test                # integration proof
```

Test result summary across the whole project is produced by `./gradlew test`.

## Architecture and module boundaries

Cadence is layered:

```
UI (Compose)
  │
  ▼
Presentation (view models)
  │
  ▼
Domain + ports (use cases, PlaybackEngine, SourceProvider, repositories)
  │
  ▼
Infrastructure (Media3, Room, Keystore, WorkManager, cache, providers)
```

Conventions:

- **Ports live in domain; adapters live in infrastructure.** The UI/domain
  layers depend on interfaces, not concrete Android or provider classes.
- **`core:media` types must never leak into `ui/domain`-facing code.** See the
  enforcements in `tests/architecture`.
- **NewPipe Extractor is isolated to `:source:ytmusic`.** Never import
  extraction-library types outside that module (per `docs/LICENSE_DECISION.md`).
- **Provider types stay behind `SourceProvider`.** `:source:api` defines the
  SPI; the UI never talks to provider endpoints directly.
- If a change crosses a module boundary, keep the port/implementation split
  intact and add an architecture check if the boundary is not already enforced.

When you add a module or dependency, update `settings.gradle.kts`, the version
catalog, and this architecture documentation as appropriate.

## Fixture-first testing

- **No live network in tests or CI.** Provider tests use recorded fixtures and
  fake transports.
- New provider fixtures must be **scrubbed** of credentials, cookies, personal
  data, and private URLs — see `docs/` fixture guidance. Never record a
  fixture with a logged-in session.
- Prefer in-memory fakes (see `core:testing`) for unit tests; use Room/Android
  fakes only where the behavior under test requires them.
- The `liveCapture` Gradle task that records YT Music wire fixtures is an
  authoring tool and **must never run in CI**; never commit a capture that
  contains sensitive data.

## Secret handling

- Never commit API keys, tokens, passwords, cookies, private keys, signing
  material, or `local.properties`.
- `local.properties`, keystores (`.jks`/`.keystore`), and `.env` files are
  git-ignored. If you add a new secret-like file, add a matching ignore rule.
- Use explicit fake values (e.g. `example-secret-123`) in tests and examples.
- If you spot a printed secret, redact it and follow `SECURITY.md`.

## Provider boundary

- The real YT Music provider depends on NewPipe Extractor (GPL-3.0). Cadence is
  itself GPL-3.0, which is compatible. Keep the extraction library confined to
  `:source:ytmusic`.
- Do not introduce proprietary client code or claim affiliation with Google,
  YouTube, or NewPipe (the app).

## UI testing expectations

- Compose UI-state matrix tests (Robolectric `createComposeRule`) live in
  `app`'s `testDebugUnitTest`. They are added to the release unit-test
  exclusion list in `app/build.gradle.kts` so `./gradlew test` stays green.
- Keep business logic out of Compose; screens render view-model state. Adding a
  UI feature should come with a corresponding UI-matrix or view-model test.

## Pull request expectations

See `PULL_REQUEST_TEMPLATE.md`. In short, a PR should include:

- A clear summary and motivation.
- A description of the implementation.
- Tests for the changed behavior, and evidence they pass.
- Screenshots for UI changes.
- Notes on architecture, security/privacy, and provider/license impact.

Checklist (the PR template also covers this):

- [ ] `./gradlew test` passes.
- [ ] Architecture checks pass (`:tests:architecture:test`).
- [ ] No secrets introduced.
- [ ] No unrelated changes.
- [ ] Documentation updated as needed.

## Documentation

Update documentation when behavior changes:

- User-facing behavior → `README.md`, `PRIVACY.md`.
- Module/architecture changes → `docs/ARCHITECTURE.md` and this file.
- Permission/network/analytics changes → `PRIVACY.md`.
- License/dependency changes → `NOTICE` and `docs/LICENSE_DECISION.md`.

## Code of conduct

By participating, you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).
