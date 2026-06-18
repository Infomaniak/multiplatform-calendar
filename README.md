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
├── Core/                       # Public KMP library (domain, Room DB, repositories, managers, Apple SDK)
│   ├── src/commonMain/         # Cross-platform: domain, Room DB, repositories, DI graph contracts/mappers
│   ├── src/androidMain/        # Android Room database provider (via Metro DI)
│   ├── src/appleMain/          # CalendarSDK + CalendarSDKProvider (Apple public DI graph)
│   └── src/commonTest/         # Shared unit tests
├── kmpdav/                     # Internal KMP bridge module (Rust/UniFFI + remote CalDAV layer)
│   ├── src/commonMain/         # RustCaldavBridge, CaldavClientModule, remote models, remote client interface
│   ├── rust/caldav_bridge/     # Rust crate: CalDAV operations via fast-dav-rs + icalendar
│   └── build.gradle.kts        # Bridge module build (Gobley/UniFFI, Metro)
├── build.gradle.kts            # Root aggregator (no sources)
├── Core/build.gradle.kts       # Public library build (SKIE, Metro, XCFramework)
├── buildRelease                # Script to build & zip KmpCalendar.xcframework for iOS/macOS release
└── buildRust                   # Script for standalone Rust compilation (optional, Gradle handles it)
```

### Modules

| Module     | Purpose                                                                                       |
|------------|-----------------------------------------------------------------------------------------------|
| **Core**   | Public API: domain models, Room database, DAOs, repositories, managers, Apple `CalendarSDK`   |
| **kmpdav** | Internal bridge: Rust/UniFFI CalDAV bridge, remote CalDAV models/client, `CaldavClientModule` |

### XCFramework

The `KmpCalendar.xcframework` is produced by the **Core module**. `:kmpdav` is a plain `implementation` dependency
(not exported): the public Apple API only exposes Core-owned types — e.g. credentials are passed as the Core
`DavCredentials` (mapped to the internal `:kmpdav` `DavAccount` at the repository boundary). The only `:kmpdav` symbol
left in the generated header is an **empty** `CaldavClientModule` marker protocol that `CalendarSDK` must conform to for
DI (see the DI note below); no `:kmpdav` data type is exposed.

Apple consumers import `KmpCalendar` and access the SDK through:

```swift
import KmpCalendar

let sdk = CalendarSDKProvider.shared.sdk
sdk.accountManager.initAccount(...)
sdk.calendarManager.observeCalendars(...)
```

### DI (Metro)

- **Android**: `AppGraph` (in the Android app) is the `@DependencyGraph`. Core contributes shared graph accessors
  (`CalendarCoreGraph`) plus `AndroidDatabaseModule` and `DatabaseModule`. The `:kmpdav` module contributes `CaldavClientModule`.
- **Apple**: `CalendarSDK` (in `Core/appleMain`) is the public `@DependencyGraph`. It provides the Apple Room database,
  inherits `CalendarCoreGraph` explicitly to export `accountManager` / `calendarManager`, and inherits `:kmpdav`'s
  `CaldavClientModule` explicitly to obtain the CalDAV bridge binding. It is accessed via `CalendarSDKProvider.shared.sdk`.

## Prerequisites

Before you begin, ensure you have met the following requirements:

- You are using a Linux, macOS, or Windows machine.
- You have installed Java Development Kit (JDK) 21 or later.
- You have Android Studio installed.
- **Android SDK Command-line Tools**: Required for automatic NDK version management. Install via Android Studio:  
  *Settings > Android SDK > SDK Tools > Android SDK Command-line Tools*
- **NDK 30.0.14904198** (or newer): The build requires NDK 30+ for 16KB page size alignment support. The
  `ensureNdkVersion` Gradle plugin automatically manages the NDK version — necessary because AGP [doesn't handle NDK updates
  for KMP projects](https://issuetracker.google.com/u/0/issues/439746703). It will download the correct version if the Android
  SDK Command-line Tools are installed, or reuse a newer installed NDK.
- You have [Rust](https://rustup.rs/) installed with cross-compilation targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android \
                     aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin
  ```
- You have an active internet connection to download project dependencies.

## Rust CalDAV Bridge

The `kmpdav/rust/caldav_bridge` crate provides CalDAV operations (discover calendars, CRUD events)
via [fast-dav-rs](https://github.com/Goopil/fast-dav-rs).
iCalendar data is parsed into typed fields using the [icalendar](https://docs.rs/icalendar) crate, and extra WebDAV
collection properties (privileges, owner, color) via [roxmltree](https://docs.rs/roxmltree) (see *Extra CalDAV properties* below).

The Rust → Kotlin/Swift bridge is handled automatically
by [Gobley](https://github.com/aspect-build/gobley) + [UniFFI](https://mozilla.github.io/uniffi-rs/) — no manual JNI, cinterop, or
JSON serialization needed.

```
Kotlin/Swift  ←──UniFFI bindings──→  Rust lib.rs  →  fast-dav-rs (CalDAV)  →  icalendar (parsing)
```

Rust compilation and binding generation are integrated into the Gradle build via the `dev.gobley.cargo` and `dev.gobley.uniffi`
plugins. No separate build step is required — just run `./gradlew assembleDebug`.

### Extra CalDAV properties

`fast-dav-rs` only surfaces a fixed subset of collection properties. When we need others — the `current-user-privilege-set`
(RFC 3744, mapped to a `CalendarAccessLevel`), the `DAV:owner`, or the Apple `calendar-color` — the crate issues its own
`Depth: 1` PROPFIND and parses the multistatus with [roxmltree](https://docs.rs/roxmltree) (see
`rust/caldav_bridge/src/props.rs`).
Parsing matches on **local names** so it is agnostic to the server's namespace prefix, and is **best-effort**: a missing or
unsupported property never breaks calendar discovery. To fetch a new property, add it to `PROPS_BODY` and to `CollectionProps`.

### Rust build profiles & binary size

The Rust artifacts are **huge in debug and small in release** — always compare like-for-like:

| Artifact                         | Debug     | Release     |
|----------------------------------|-----------|-------------|
| Android `.so` (per ABI, shipped) | ~66–77 MB | **~4.8 MB** |
| Apple `.a` (per slice)           | ~140 MB   | **~14 MB**  |

The `.a` static archive is **never shipped**: only the linked, stripped `.so` (Android) or the framework binary (Apple) goes
into the app. The release profile (`lto`, `opt-level = "s"`, `strip`) is configured in `rust/caldav_bridge/Cargo.toml`.

> ⚠️ Do **not** set `panic = "abort"`: UniFFI relies on catching Rust panics to convert them into FFI errors; aborting would
> crash the app instead.

**Profile selection per consumer (Gobley):**

- **Android** — follows the AGP build type automatically: `assembleDebug` → Rust `dev` profile, `assembleRelease` /
  `bundleRelease` → Rust `release` profile. Nothing to configure.
- **Apple / Kotlin-Native** — ⚠️ **gotcha**: the Rust static lib is embedded at **cinterop** time, which is *variant-agnostic*
  (a single klib), and Gobley defaults the native build to **`Debug`**. So `assembleKmpCalendarReleaseXCFramework` would
  otherwise link the ~140 MB **debug** `.a` into the *release* XCFramework (a ~250 MB zip). To force a release native Rust
  build, pass `-PrustNativeRelease=true` (already wired into `buildRelease`):

  ```bash
  ./gradlew :Core:assembleKmpCalendarReleaseXCFramework -PrustNativeRelease=true
  ```

  This is opt-in so day-to-day Apple builds keep a debug Rust (faster rebuilds, native debug symbols).

  **Static framework keeps DWARF** — the Apple framework is *static* (`isStatic = true`), i.e. an unlinked `ar` archive, so
  both the Kotlin and Rust objects keep their debug info (`__DWARF`) even in release (Cargo's `strip` only applies to *linked*
  binaries like the Android `.so`, not to static `.a`). That is ~30% of dead weight per slice (e.g. iosArm64: 31 MB → 22 MB).
  `buildRelease` therefore runs `strip -S` (+ `ranlib`) on each XCFramework slice before zipping; `strip -S` removes the debug
  sections while keeping the global symbols required for linking.

**Build the app in debug but Rust in release** (small/optimized native lib in a debug app): repoint the `debug` Cargo
variant to the `release` profile in `kmpdav/build.gradle.kts`:

```kotlin
import gobley.gradle.cargo.profiles.CargoProfile

cargo {
    packageDirectory = layout.projectDirectory.dir("rust/caldav_bridge")
    debug.profile = CargoProfile.Release
}
```

## Build Commands

```bash
# Build the KmpCalendar XCFramework (iOS/macOS) — release Rust (see "Rust build profiles")
./gradlew :Core:assembleKmpCalendarReleaseXCFramework -PrustNativeRelease=true

# Build & zip for iOS release (updates `Package.swift` checksums when the file exists)
./buildRelease <version>

# Build Android library (debug)
./gradlew assembleDebug

# Run unit tests
./gradlew :Core:allTests

# Clean
./gradlew clean
```

## Contributing

If you see a bug or an enhancement point, feel free to create an issue, so that we can discuss it. Once approved, we or you (
depending on the priority of the bug/improvement) will take care of the issue and apply a merge request. Please, don't do a merge
request before creating an issue.

## License

This project is under GPLv3 license. See the LICENSE file for more details.
