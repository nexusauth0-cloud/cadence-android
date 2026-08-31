# Cadence — Phase 1 implementation workspace

Internal codename; final public identity is intentionally undecided (ADR-0002
spike spec section 20 Q6). Brand-neutral architecture: no product naming exists
in code beyond the neutral `com.cadence` package.

**Status:** ADR-0002 source-resolution spike (see `../cadence-phase0/spike/0002-source-resolution-spike.md`).

## Quick start

```bash
./gradlew build          # compile + all tests (unit, fixture, e2e, architecture)
./gradlew :tests:e2e:test            # the spike proof only
./gradlew :tests:architecture:test  # enforcement checks only
```

Requires JDK 21. No Android SDK required — every module is pure Kotlin/JVM by
design (compile-time layer purity, ADR-0001).

See `docs/IMPLEMENTATION.md` for the full map.
