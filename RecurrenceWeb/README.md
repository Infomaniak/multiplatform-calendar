# Kotlin Web demonstration (isolated POC)

This patch **does not extract the production RecurrenceTextFormatter**. That formatter depends on CalendarCore's recurrence model, Compose resources and internal helper code. To avoid silently depending on CalendarCore (Room/CalDAV/etc.), this first, independently compilable proof-of-concept has a deliberately limited formatter in `commonMain` targeting **both JS and Wasm**. It rejects unsupported fields rather than showing a wrong translation. Supported: DAILY/WEEKLY/MONTHLY/YEARLY, INTERVAL, WEEKLY BYDAY, COUNT; languages: French and English. This is not a general-purpose RFC 5545 formatter or a production extraction.

## Run

From repository root:

```bash
./RecurrenceWeb/build-demo.sh
cd RecurrenceWeb/demo && python3 -m http.server 8080
```

Visit http://localhost:8080/. Both bundles must be successfully built and served via HTTP. If webpack outputs a non-ES-module file for a target on your specific Kotlin plugin version, adjust its webpack output configuration or import its generated entrypoint rather than renaming a CommonJS bundle. No browser execution or Gradle compilation has been verified in the authoring environment.

## Measurements

`build-demo.sh` reports sequential wall-clock build durations (coarse seconds). They are influenced by caches, filesystem, downloads and task ordering; run cold and warm multiple times, swap target order, and record machine specifications. Browser button times exactly the same Kotlin-exported function and locale for both targets using `performance.now()`, warmup + medians, and reports browser info. This includes the JS↔Wasm bridge. It excludes module loading, compilation, download, and startup times. **Do not claim Wasm is always faster** from this test. For serious data add a Playwright harness recording `performance` entries for initialization, asset sizes, and repeated benchmark distributions.

## Publishing

Both targets can use npm as their **distribution registry**; npm is not a runtime or compiler. JS npm tarball includes emitted JS plus `.d.ts`, package metadata and license. Wasm npm tarball includes the Wasm binary, JS bootstrap/glue, `.d.ts`, metadata, and license. Browser/bundler must serve/rewrite wasm assets correctly, and JS/Wasm interop + browser feature support must be considered. Packages can alternatively be hosted on a CDN. Publishing is intentionally not configured in this demo; no live registry interaction occurs.

TypeScript declarations for both targets are enabled using `generateTypeScriptDefinitions()`. Kotlin/Wasm exports appear on the generated ESM default export; Kotlin/JS uses named exports. Neither is forced to have a manually handwritten TS bridge.

## Next step for production reuse

Extract RecurrenceRule, parsing, formatting, resources and related pure helpers to a dedicated shared module with Android/iOS/JS/Wasm targets, then make CalendarCore depend on it; preserve `toLocalizedString()` and the event-based helper in CalendarCore. Add tests comparing all 4 targets and all resource locales before publication.

This demo uses a limited formatter and is not the mobile production implementation. The repository is licensed GPL-3.0-or-later; publishing an npm package requires appropriate license/compliance review.
