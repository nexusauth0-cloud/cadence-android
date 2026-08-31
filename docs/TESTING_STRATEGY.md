# Testing strategy (Phase 1)

## Layers

| Layer | Where | Tooling |
|---|---|---|
| Model invariants | core:model tests | kotlin-test |
| Redaction/envelope | core:common tests | kotlin-test |
| SPI conformance | core:testing tests | coroutines-test (virtual time) |
| Adapter/fixture matrix | source:demo tests | ClasspathFixtureTransport |
| Port adapters | core:data tests | fakes |
| Policy/outcomes | core:domain tests | local engine stub (no cycle) |
| E2E composition | tests:e2e | ports + fakes only |

## Determinism rules

- No wall-clock reads in production mappers: `java.time.Clock` injected,
  `MutableClock` driven in tests.
- Delays run on the caller's dispatcher; tests use `runTest` virtual time.
- Fixtures carry fixed-string timestamps only; assertions compare parsed
  domain models, never byte equality.

## Cancellation conformance

Two dedicated proofs exist:
1. `FakeSourceProviderTest.cancelled-immediately preserves cancellation identity`
2. `E2ESpikeTest.cancellation identity is preserved through the whole chain`

Both assert the thrown type IS `CancellationException` and NOT `SourceError`,
plus zero leaked engine loads / zero active provider work after unwind.

## Mutation-testing equivalent (spec section 14 allowance)

Full mutation tooling (pitest/kover+mutate) is premature for ~30 production
files. Deterministic equivalent adopted:

**Violation probes.** Each architectural check must be demonstrated to fail by
temporarily introducing its exact violation, then reverting:

| Probe introduced | Expected red check | Result |
|---|---|---|
| `import com.cadence.source.demo.DemoMapper` in tests/e2e | LayeringGuardianTest + SourceBoundaryChecks.rule B/C | ✅ verified red |
| `runBlocking { }` inside DemoProvider.resolve | CompiledArtifactChecks.rule E | ✅ verified red |
| plain `Exception("x")` thrown in source.api | CompiledArtifactChecks.rule D | ✅ verified red |
| 900-line scratch file | CompiledArtifactChecks.rule F | ✅ verified red |

Probes are executed as part of phase validation and ticked off there; CI keeps
them enforced permanently thereafter. (Checked boxes = probe verified red.)

## Planned escalation

- pitest mutation runs on `:core:domain` when logic grows past policy/use-cases.
- detekt custom rules replacing text scans if rule count exceeds ~10.
