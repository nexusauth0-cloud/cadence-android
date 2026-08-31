## Summary

One or two sentences describing what this PR does.

## Motivation

Why is this change needed? Link related issue(s) if any.

## Implementation

Describe the approach taken and any design trade-offs.

## Tests

What tests were added or changed, and what do they verify?

- [ ] `./gradlew test` passes.
- [ ] `./gradlew :tests:architecture:test` passes.
- [ ] `./gradlew :app:lintDebug` passes.
- [ ] New UI changes have a corresponding UI-matrix / view-model test.

## Screenshots

For UI changes, add before/after screenshots.

## Architecture impact

Which modules/boundaries are touched? Does the change affect the
[architecture](docs/ARCHITECTURE.md) or module boundaries?

## Security / privacy impact

Does this change affect credentials, permissions, network use, analytics,
or stored data? If so, update `PRIVACY.md` and/or `SECURITY.md` as needed and
note it here. Do not include real secrets anywhere.

## Provider / license impact

Does this change affect a provider (`SourceProvider`) or third-party
licensing? If so, update `NOTICE` / `docs/LICENSE_DECISION.md` as needed.

---

### Checklist

- [ ] Tests pass.
- [ ] Architecture checks pass.
- [ ] No secrets introduced.
- [ ] No unrelated changes.
- [ ] Documentation updated.
- [ ] Screenshots added for UI changes (where appropriate).
