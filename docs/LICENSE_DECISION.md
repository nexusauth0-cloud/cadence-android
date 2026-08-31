# License Decision — Extraction Libraries (Phase 3 Update)

- **Date:** 2026-08-26 (Phase 3 re-check)
- **Gate:** Phase 3 §0 — provider integration gate

## Re-check Summary

The Phase 2 decision was DEFERRED. This re-check evaluates whether the license
is compatible with our intended distribution model to make a final APPROVED/REJECTED call.

## Intended Distribution Model

Cadence is a **free/open-source Android application**. The project will be
released under **GPL-3.0** (matching the reference Metrolist project's license
lineage and ensuring legal clarity). Source code will be publicly available.
APK distribution will be through F-Droid and/or direct GitHub Releases.

This means:
- The entire application binary is subject to GPL-3.0 copyleft
- All source code is available to recipients
- No proprietary closed-source components exist in the distribution

## Candidate: NewPipe Extractor v0.26.5

| Field | Value |
|---|---|
| Repository | https://github.com/TeamNewPipe/NewPipeExtractor |
| Latest version | v0.26.5 (2025-08-24) |
| License | GNU General Public License v3.0 |
| Artifact (JitPack) | `com.github.teamnewpipe:NewPipeExtractor:v0.26.5` |
| Artifact (Maven Central snapshot) | `net.newpipe:extractor:<hash>-SNAPSHOT` |
| Transitive deps | OkHttp (Apache-2.0), jsoup (MIT), nanojson (MIT), protobuf-javanano (BSD-3), Rhino (MPL-2.0) |
| minSdk requirement | >=33 or use core library desugaring for <33 |
| Maintenance | Very active: 1926 stars, 585 forks, 180 open issues |
| ProGuard rules | Keep `org.mozilla.javascript.**` and `org.mozilla.classfile.ClassFileWriter` |

## Compatibility Analysis

### GPL-3.0 → GPL-3.0

Since Cadence will be distributed under GPL-3.0, incorporating a GPL-3.0
library is **fully compatible**. There is no license conflict — the entire
combined work is GPL-3.0.

### Transitive dependency analysis

| Transitive | License | Compatible with GPL-3.0? |
|---|---|---|
| OkHttp | Apache-2.0 | Yes — Apache-2.0 is GPL-3.0 compatible |
| jsoup | MIT | Yes — MIT is GPL-3.0 compatible |
| nanojson | MIT | Yes — MIT is GPL-3.0 compatible |
| protobuf-javanano | BSD-3 | Yes — BSD-3 is GPL-3.0 compatible |
| Rhino (Mozilla) | MPL-2.0 | Yes — MPL-2.0 is file-level copyleft, compatible when dynamically or statically linked with GPL-3.0 |

No transitive dependency conflicts with GPL-3.0.

### Attribution requirements

GPL-3.0 §4 requires:
1. Complete corresponding source code for the library
2. GPL-3.0 license text included in distribution
3. Copyright notice preserved
4. A statement that warranty is disclaimed

For Android distribution:
- F-Droid handles source code disclosure automatically
- We include GPL-3.0 notices in the APK's `NOTICE` file
- ProGuard/R8 minification preserves the required class files

### Technical considerations

- minSdk <33: Cadence targets minSdk 26 (Android 8.0). NewPipe Extractor
  requires Java 8+ features. Core library desugaring (`coreLibraryDesugaring`)
  must be enabled in the app module. This is standard practice.
- Rhino (Mozilla JavaScript engine) is bundled as a transitive dependency and
  used by NewPipe Extractor for cipher deobfuscation. It adds ~1.8 MB to APK size.

## Final Decision

### **APPROVED WITH CONDITIONS**

| Condition | Detail |
|---|---|
| 1. App is GPL-3.0 | The entire Cadence application must be released under GPL-3.0 |
| 2. Attribution | Include NewPipe Extractor copyright notice and GPL-3.0 text in distribution |
| 3. ProGuard keep rules | Apply required ProGuard rules for Rhino/NewPipe Extractor classes |
| 4. Core library desugaring | Enable for minSdk <33 compatibility |
| 5. Adapter isolation | NewPipe Extractor types must NEVER leak beyond `:source:ytmusic` |
| 6. JitPack repository | Add `https://jitpack.io` to project repositories |

### What this means for Phase 3

- `:source:ytmusic` **may be created** with NewPipe Extractor v0.26.5 as dependency
- The adapter pattern proven by `:source:demo` is reused
- Architecture checks already enforce the extraction-library boundary (Rule H)
- Phase 3 §15 (Provider Integration) is unblocked

### What this does NOT mean

- We are NOT copying any NewPipe (app) code — only using the Extractor library
- We are NOT adopting NewPipe's GPL-3.0 app code or UI
- The Metrolist reference repo remains untouched
- Provider types remain isolated in `:source:ytmusic`
