# Security

Cadence is an open-source Android application under active development. We ask
the community to report security issues responsibly.

## Supported versions

Cadence is in early development (current version `0.1.0`, pre-release) and has
no actively distributed release channel yet. Security fixes are applied to the
default branch (`main`) as development proceeds. As of now there is no stable
release line to receive backported fixes.

## Reporting a vulnerability

Please **do not** open a public issue for a security vulnerability. Instead,
report it privately so it can be addressed before disclosure.

- **Preferred:** use GitHub's private vulnerability reporting for this
  repository (Settings → Security → *Private vulnerability reporting*), once
  the repository exists and the feature is enabled. Private reports are
  visible only to maintainers.
- **Alternative:** open a normal issue **only** if you are confident the issue
  is not a security vulnerability.

If private vulnerability reporting is not yet enabled on the repository, contact
a maintainer through a private channel they have published before filing an
issue. Do not include sensitive values in public text.

### What to include in a report

To help us triage quickly, please include:

- The vulnerability type and a concise description.
- Cadence version / commit (`git rev-parse HEAD`) and build type.
- Android OS version and device model.
- Affected module/files, if known.
- Steps to reproduce.
- Expected vs. actual behavior.
- Proof-of-concept code **without** real credentials, tokens, or cookies.

### What not to include

- **Never** include real credentials, API keys, access/refresh tokens,
  session cookies, passwords, or personal data in a report.
- If you must describe a credential-handling bug, use clearly fake/redacted
  values (for example `REDACTED` or `example-secret-123`).

## Scope

Security-sensitive areas in Cadence include:

- The **credential vault** (`android:vaultstore`) and how provider credentials
  are scoped and encrypted at rest.
- The **update system** and its https allow-list for store links (`UpdateSource`
  / `UpdateManager`).
- The **provider boundary** (`:source:ytmusic` / NewPipe Extractor) and any user
  content it processes.
- The **download/cache** subsystem's handling of remote byte sources.

Please also report behavior that may violate the project's intended privacy
properties (for example, data leaving the device unintentionally).

## Responsible disclosure expectations

We will acknowledge receipt of a valid report, investigate, and aim to keep you
informed of progress. We ask that you refrain from public disclosure until a fix
is available or we agree on a disclosure timeline.

## Security configuration

For maintainers: before making the repository public, review the GitHub security
features for the repository (Dependabot alerts, secret scanning, push
protection, and code scanning where supported) and enable them per the actions
documented during publication setup. Do not commit signing material; configure
release signing through GitHub secrets when a release pipeline is added.
