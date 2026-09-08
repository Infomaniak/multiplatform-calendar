# multiplatform-calendar

## Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Build Commands](#build-commands)
- [Contributing](#contributing)
- [License](#license)

## Architecture

```
multiplatform-calendar/
├── CalendarCore/                    # Public KMP library (domain, Room DB, repositories, managers, Apple SDK)
│   ├── src/commonMain/              # Cross-platform: domain, Room DB, repositories, DI graph contracts/mappers
│   ├── src/androidMain/             # Android Room database provider (via Metro DI)
│   ├── src/appleMain/               # CalendarSDK + CalendarSDKProvider (Apple public DI graph)
│   ├── src/commonTest/              # Shared unit tests
│   └── build.gradle.kts             # Public library build (SKIE, Metro, XCFramework)
├── CalendarKmpDav/                  # Internal KMP bridge module (Rust/UniFFI + remote CalDAV layer)
│   ├── src/commonMain/              # RustCaldavBridge, CaldavClientModule, remote models, remote client interface
│   ├── rust/caldav_bridge/          # Rust crate: CalDAV operations via fast-dav-rs + icalendar
│   └── build.gradle.kts             # Bridge module build (UniFFI/Cargo, Metro)
├── build.gradle.kts                 # Root aggregator (no sources)
├── buildRelease                     # Script to build & zip MultiplatformCalendar.xcframework for iOS/macOS release
└── buildRust                        # Script for standalone Rust compilation (optional, Gradle handles it)
```

### Modules

| Module               | Purpose                                                                                       |
|----------------------|-----------------------------------------------------------------------------------------------|
| **CalendarCore**     | Public API: domain models, Room database, DAOs, repositories, managers, Apple `CalendarSDK`   |
| **CalendarKmpDav**   | Internal bridge: Rust/UniFFI CalDAV bridge, remote CalDAV models/client, `CaldavClientModule` |

### XCFramework

The `MultiplatformCalendar.xcframework` is produced by the **CalendarCore module**. `:CalendarKmpDav` is a plain `implementation` dependency
(not exported): the public Apple API only exposes Core-owned types — e.g. credentials are passed as the Core
`DavCredentials` (mapped to the internal `:CalendarKmpDav` `DavAccount` at the repository boundary). The only `:CalendarKmpDav` symbol
left in the generated header is an **empty** `CaldavClientModule` marker protocol that `CalendarSDK` must conform to for
DI (see the DI note below); no `:CalendarKmpDav` data type is exposed.

Apple consumers import `MultiplatformCalendar` and access the SDK through:

```swift
import MultiplatformCalendar

let sdk = CalendarSDKProvider.shared.sdk
sdk.accountManager.initAccount(...)
sdk.calendarManager.observeCalendars(...)
```

### DI (Metro)

- **Android**: `AppGraph` (in the Android app) is the `@DependencyGraph`. Core contributes shared graph accessors
  (`CalendarCoreGraph`) plus `AndroidDatabaseModule` and `DatabaseModule`. The `:CalendarKmpDav` module contributes `CaldavClientModule`.
- **Apple**: `CalendarSDK` (in `CalendarCore/appleMain`) is the public `@DependencyGraph`. It provides the Apple Room database,
  inherits `CalendarCoreGraph` explicitly to export `accountManager` / `calendarManager`, and inherits `:CalendarKmpDav`'s
  `CaldavClientModule` explicitly to obtain the CalDAV bridge binding. It is accessed via `CalendarSDKProvider.shared.sdk`.

## Prerequisites

Before you begin, ensure you have met the following requirements:

- You are using a Linux, macOS, or Windows machine.
- You have installed Java Development Kit (JDK) 21 or later.
- You have Android Studio installed.
- **NDK 30.0.14904198 or newer**: required for the 16 KB page size alignment Android 15+ mandates. Nothing to do manually:
  the `ensure-ndk-version` convention plugin reuses any newer installed NDK and otherwise downloads this one through
  `sdkmanager`. See [NDK handling](#ndk-handling).
- You have [Rust](https://rustup.rs/) installed with cross-compilation targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android \
                     aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin
  ```
- You have an active internet connection to download project dependencies.

## Rust CalDAV Bridge

The `CalendarKmpDav/rust/caldav_bridge` crate provides CalDAV operations (discover calendars, CRUD events)
via [fast-dav-rs](https://github.com/Goopil/fast-dav-rs).
iCalendar data is parsed into typed fields using the [icalendar](https://docs.rs/icalendar) crate, and extra WebDAV
collection properties (privileges, owner, color) via [roxmltree](https://docs.rs/roxmltree) (see *Extra CalDAV properties* below).

The Rust → Kotlin/Swift bridge is handled automatically
by [uniffi-kotlin-multiplatform-bindings](https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings)
+ [UniFFI](https://mozilla.github.io/uniffi-rs/) — no manual JNI, cinterop, or JSON serialization needed.

```
Kotlin/Swift  ←──UniFFI bindings──→  Rust lib.rs  →  fast-dav-rs (CalDAV)  →  icalendar (parsing)
```

Rust compilation and binding generation are integrated into the Gradle build via the single `ch.ubique.uniffi.plugin`
plugin. No separate build step is required — just run `./gradlew assembleAndroidMain`.

> ℹ️ The Android ABIs shipped are `arm64-v8a`, `armeabi-v7a` and `x86_64`. The 32-bit `x86` ABI is **not** supported by the
> plugin; it only concerns long-obsolete 32-bit emulators.

### NDK handling

Android 15+ requires shared libraries to be aligned on 16 KB pages, which needs NDK r28 (30.x) or newer.

`com.android.kotlin.multiplatform.library` exposes no `ndkVersion` of its own — AGP 9 removed it from `CommonExtension` —
so the NDK used to cross-compile Rust is the one given to `cargo { ndkVersion = ... }`. That plugin does **not** fail when
the requested version is missing: it silently falls back to the newest installed NDK, so a machine without a 30.x NDK
would build unaligned libraries and still report a successful build.

The `ensure-ndk-version` convention plugin (`build-logic/plugins/.../ndk`) closes that hole:

```kotlin
ensureNdkVersion {
    minimumVersion = "30.0.14904198"
}

cargo {
    ndkVersion = ensureNdkVersion.resolvedVersion
}
```

`minimumVersion` is a **minimum**, not a pin: a newer installed NDK satisfies it and is reused, and only when none
qualifies is the declared version downloaded via `sdkmanager`. Wiring `resolvedVersion` into `cargo` is what makes the
check effective — repeating the version as a literal there would reintroduce the silent fallback.

Run `./gradlew :CalendarKmpDav:ensureNdkVersion` to check or provision the NDK on its own. Automatic installation needs
the *Android SDK Command-line Tools* (*Settings > Android SDK > SDK Tools*); without them the build explains how to
install the NDK manually.

### Async & cancellation

All network exports are `async fn` on the Rust side, exported with
`#[uniffi::export(async_runtime = "tokio")]`. UniFFI generates Kotlin `suspend fun`
bindings that carry `@Throws(CaldavException, CancellationException)`, so cancelling
the calling coroutine drops the Rust future, which drops the underlying reqwest
request.

- **Reads** (`discoverCalendars`, `getEvents`, `getEventsInRange`, `syncCollection`,
  `getEventsByUrls`) are fully cancellable and safe to cancel at any time.
- **Writes** (`updateCalendar`, `createEvent`, `updateEvent`, `deleteEvent`) are best-effort cancellable:
  cancelling after the request has been dispatched to the server leaves the outcome
  **undefined** (the server may or may not have applied the change). Callers must
  re-sync to reconcile.
- **Pure-CPU exports** (`patchEventIcs`, `buildEventIcs`) stay synchronous and are
  wrapped in `withContext(Dispatchers.Default)` inside `RustCaldavBridge` so they
  never block the caller's thread.

### Extra CalDAV properties

`fast-dav-rs` only surfaces a fixed subset of collection properties. When we need others — the `current-user-privilege-set`
(RFC 3744, mapped to a `CalendarAccessLevel`), the `DAV:owner`, or the Apple `calendar-color` — the crate issues its own
`Depth: 1` PROPFIND and parses the multistatus with [roxmltree](https://docs.rs/roxmltree) (see
`rust/caldav_bridge/src/props.rs`).
Parsing matches on **local names** so it is agnostic to the server's namespace prefix, and is **best-effort**: a missing or
unsupported property never breaks calendar discovery. To fetch a new property, add it to `PROPS_BODY` and to `CollectionProps`.

### Rust build profiles & binary size

The Rust artifacts are **huge in debug and small in release** — always compare like-for-like:

| Artifact                         | Debug     | Release        |
|----------------------------------|-----------|----------------|
| Android `.so` (per ABI, shipped) | ~66–77 MB | **~3.4–4.9 MB** |
| Apple `.a` (per slice)           | ~140 MB   | **~14 MB**     |

The `.a` static archive is **never shipped**: only the linked, stripped `.so` (Android) or the framework binary (Apple) goes
into the app. The release profile (`lto`, `opt-level = "s"`, `strip`) is configured in
`CalendarKmpDav/rust/caldav_bridge/Cargo.toml`.

> ⚠️ Do **not** set `panic = "abort"`: UniFFI relies on catching Rust panics to convert them into FFI errors; aborting would
> crash the app instead.

**Profile selection:**

The Cargo profile is chosen by a single Gradle property, `releaseBuild`, which applies to every target (Android and
Apple alike). It defaults to `true` in `gradle.properties`, because a debug build weighs ~77 MB per Android ABI and
~140 MB for the Apple static lib, against ~4 MB / ~14 MB in release. When iterating on the crate itself, pass
`-PreleaseBuild=false` for much faster Rust rebuilds and native debug symbols.

```bash
# Fast local Rust rebuilds
./gradlew :CalendarCore:assembleMultiplatformCalendarDebugXCFramework -PreleaseBuild=false
```

#### Stripping

`strip = true` in the Cargo release profile only pays off on **Android**, where the shipped artifact is the `cdylib`
itself: it drops the `.so` symbol table while keeping the dynamic export table, so UniFFI's symbol lookups still resolve
(AAR: 8.0 MB → 6.5 MB). On **Apple** it changes nothing, because the shipped artifact is not the crate's own output —
the `.a` must keep its symbols to be linked into the framework. This is safe for binding generation too: the Ubique
plugin builds a *separate host library* (`buildLibraryForBindings`) that uniffi-bindgen reads, so the per-target
artifacts never need to be introspected.

The Apple framework is **dynamic** (`isStatic` not set), and the Rust `.a` is linked *into* it. Once that link is done
the ~34k local symbols coming from the static archive are dead weight — they inflate `__LINKEDIT` to ~5 MB. `buildRelease`
therefore runs `strip -x` on each XCFramework slice before zipping, which drops the local symbols while preserving the
global ones needed for linking and for dyld at runtime:

| Per slice (iosArm64) | Before      | After      |
|----------------------|-------------|------------|
| Binary               | 19.3 MB     | **15.0 MB** |
| `__LINKEDIT`         | 5.0 MB      | **737 KB**  |
| Symbols              | 58,713      | **6,115**   |

> ℹ️ `strip -S` (drop DWARF) is deliberately **not** used: Kotlin/Native already sequesters the debug info into the
> companion `.dSYM`, so it is a no-op here. The `.dSYM` bundles are kept in the zip and their UUIDs still match the
> stripped binaries, so crashes remain symbolicable.

#### Packaging

`buildRelease` archives with `zip -r --symlinks`. The flag is **required**: a macOS framework bundle exposes its binary
as `Versions/A/<name>` plus two symlinks (`Versions/Current` and the top-level one), and without `--symlinks` `zip`
dereferences them and stores the 14 MB binary three times (zip: 45 MB → 32 MB).

> ⚠️ The zip published by CI is **not** produced by `buildRelease` but by the reusable workflow
> `Infomaniak/.github/.github/workflows/kmp-build-xcframework.yml`, so any packaging change must be mirrored there.

## Build Commands

```bash
# Build the MultiplatformCalendar XCFramework (iOS/macOS) — release Rust (see "Rust build profiles")
./gradlew :CalendarCore:assembleMultiplatformCalendarReleaseXCFramework -PreleaseBuild=true

# Build & zip for iOS release (updates `Package.swift` checksums when the file exists)
./buildRelease <version>

# Build the Android library (single variant AAR)
./gradlew assembleAndroidMain

# Run unit tests
./gradlew :CalendarCore:allTests

# Clean
./gradlew clean
```

## Contributing

If you see a bug or an enhancement point, feel free to create an issue, so that we can discuss it. Once approved, we or you (
depending on the priority of the bug/improvement) will take care of the issue and apply a merge request. Please, don't do a merge
request before creating an issue.

## License

This project is under GPLv3 license. See the LICENSE file for more details.
