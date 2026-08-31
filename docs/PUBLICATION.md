# Publication plan — cadence-android (draft)

This document is the **plan** for taking Cadence public. Nothing here is
executed by this document alone — repository creation, settings, remotes, and
pushes must be performed manually and only after the pre-publication audit
gate passes.

> **Status:** PLAN ONLY. No GitHub repository, remote, push, release, or
> published binary exists yet at the time of writing.

## Product identity

- **Product name:** Cadence (do **not** rename to "Keepsake"; that was an
  unadopted Phase 4A exploration).
- **Repository name:** `cadence-android`
- **Planned owner:** `nexusauth0-cloud/cadence-android`
- **Visibility:** PUBLIC
- **Package identity:** `com.cadence` / `com.cadence.app` (namespace &
  applicationId). App label: "Cadence".
- **License:** GNU GPL v3.0 (`LICENSE`, `NOTICE`).

## Versioning

Current development version: **0.1.0** (`versionCode 1`, `versionName
"0.1.0"`). `CURRENT_APP_VERSION` in `UpdateSource.kt` mirrors `versionName`.

Intended future strategy:

```
version tag (e.g. v0.1.0)
  → CI builds & runs tests
  → signed release artifact (keystore via GitHub secrets, never committed)
  → GitHub Release (source + binaries)
  → release metadata (changelog, checksums, description)
  → update system (a real UpdateSource feed once a release exists)
```

Do not bump to a public release version arbitrarily; keep `versionName` in
sync with `CURRENT_APP_VERSION` and with release tags.

## GitHub repository settings (manual checklist)

Performed after the audit gate passes, manually, in the GitHub UI/CLI:

- Visibility: **Public**
- Issues: **Enabled**
- Actions: **Enabled**
- Wiki: **Disabled** unless needed
- Discussions: Optional
- Projects: Optional
- Default branch: `main`
- Description: "Private, offline-first Android music player with durable local
  libraries, reliable playback, downloads, and source-agnostic architecture."
- Topics: `android`, `kotlin`, `music-player`, `offline-first`,
  `jetpack-compose`, `media3`, `room`, `yt-music`, `open-source`

Security (verify/enable, subject to account/repo availability):

- Dependabot alerts
- Secret scanning
- Push protection
- Code scanning (where available)

Do **not** claim any of these are enabled unless actually verified in the UI.

## Release / APK policy

- **Never commit build APKs** to Git. Repo is source + reproducible build
  configuration only.
- Published binaries, when they appear, go through **GitHub Releases**.
- Release signing (`.jks`/`.keystore`) is stored as GitHub secrets and read at
  CI time; signing material is git-ignored and never committed.
- CI is least privilege (`permissions: contents: read`).

## First-public-push sequence (do not run yet)

```
LOCAL AUDIT  → GREEN
LICENSE VERIFIED  (GPL-3.0, conditions satisfied)
README / SECURITY / PRIVACY / CONTRIBUTING  (present & accurate)
.gitignore VERIFIED
CI VERIFIED
git status CLEAN
CREATE PUBLIC REPOSITORY
CONFIGURE SECURITY SETTINGS
ADD REMOTE
PUSH main
VERIFY GITHUB ACTIONS
VERIFY REPOSITORY PRESENTATION
```

## Landing page (future)

Before building a public landing page:

- App overview + accurate feature explanations (extracted from README).
- Real screenshots (none exist yet — do not fabricate).
- Downloads → GitHub Releases once available.
- Release notes / changelog.
- Link to PRIVACY.md and SECURITY.md.
- GitHub repository link and update metadata.
- No fake links; no claim a website exists until it does.

Hosting, domain, and tooling for a landing page are undecided and out of scope
for the audit.
