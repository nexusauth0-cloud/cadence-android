# Privacy

Cadence is a local-first, offline-first music player. This document describes
what data Cadence stores, what it sends over the network, and what permissions
it uses. It reflects the state of the source at the time of writing and should
be updated whenever application behavior changes.

## Summary

Cadence does **not** include analytics, telemetry, advertising, or crash
reporting. It does not build user profiles and does not sell or share user
data. Nearly all user data lives only on your device.

## Data stored locally

Cadence stores the following data on your device:

- **Library** — tracks you have saved/added to your library.
- **Playlists** — your custom playlists and their contents.
- **History** — a record of recently played tracks.
- **Queue** — your currently queued tracks and their order.
- **Downloads** — metadata about downloads (track identity, status, and the
  local file paths to your downloaded audio).
- **Cache** — cached provider responses and cached audio bytes (subject to the
  cache housekeeping policy).
- **Credentials** — if you connect a provider account, credentials are stored
  in the Android Keystore-backed vault, encrypted at rest and scoped. Credentials
  never pass through the UI and are never logged.

All of the above are stored in application-private storage (Room databases,
the application cache, and the Keystore-protected vault). None of it is
surfaced outside the app except as described below.

## Permissions

Cadence's own application manifest declares **no permissions**. Network access
is exercised only by the provider/infrastructure modules within the app's
normal operation (fetching search results, streaming/downloading audio). No
special permissions such as location, contacts, storage, or microphone are
requested.

## Network requests

Cadence makes network requests only when you use a provider that requires
them. Specifically:

- The YT Music provider performs searches and (for downloads) fetches audio,
  talking only to the provider's public endpoints through the isolated
  `:source:ytmusic` module (NewPipe Extractor). The app does not benefit from a
  private channel or a separate analytics backend.
- The app is designed to work fully offline for downloaded content.

Cadence does not operate its own telemetry server and makes no calls to Cadence
operated analytics/CDN/ads endpoints.

## External provider communication

Requests to providers go through the `SourceProvider` abstraction. The domain
and UI layers never talk to provider endpoints directly. Provider requests do
**not** include your local library, playlists, history, or queue contents.

## Analytics / telemetry / crash reporting

Cadence has **no** analytics SDK, **no** telemetry, and **no** automatic crash
reporting. Error information shown in the UI is for the user's benefit and is
not transmitted anywhere.

## Data deletion

Because all personal data is stored locally in application-private storage,
removing Cadence from your device (or clearing its app data) deletes your
local library, playlists, history, queue, downloads, and stored credentials.
Provide restore/export tooling is planned but not yet available.

## Contact

For questions about this policy, or to report a privacy concern, open an issue
in this repository (do not include credentials or personal data) or follow the
guidance in [SECURITY.md](SECURITY.md).
