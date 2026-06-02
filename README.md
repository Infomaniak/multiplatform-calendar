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
├── src/                        # Internal KMP bridge module (Rust/UniFFI + remote CalDAV layer)
│   └── commonMain/             # RustCaldavBridge, CaldavClientModule, remote models, remote client interface
├── rust/caldav_bridge/         # Rust crate: CalDAV operations via fast-dav-rs + icalendar
├── build.gradle.kts            # Root module build (Gobley/UniFFI, Metro bridge module)
├── Core/build.gradle.kts       # Public library build (SKIE, Metro, XCFramework)
├── buildRelease                # Script to build & zip KmpCalendar.xcframework for iOS/macOS release
└── buildRust                   # Script for standalone Rust compilation (optional, Gradle handles it)
```

### Modules

| Module   | Purpose                                                                                      |
|----------|----------------------------------------------------------------------------------------------|
| **Core** | Public API: domain models, Room database, DAOs, repositories, managers, Apple `CalendarSDK` |
| **Root** | Internal bridge: Rust/UniFFI CalDAV bridge, remote CalDAV models/client, `CaldavClientModule` |

### XCFramework

The `KmpCalendar.xcframework` is now produced by the **Core module** and exports the Root bridge module via `export(project(":"))`.
Apple consumers import `KmpCalendar` and access the SDK through:

```swift
import KmpCalendar

let sdk = CalendarSDKProvider.shared.sdk
sdk.accountManager.initAccount(...)
sdk.calendarManager.observeCalendars(...)
```

### DI (Metro)

- **Android**: `AppGraph` (in the Android app) is the `@DependencyGraph`. Core contributes shared graph accessors
  (`CalendarCoreGraph`) plus `AndroidDatabaseModule` and `DatabaseModule`. The Root module contributes `CaldavClientModule`.
- **Apple**: `CalendarSDK` (in `Core/appleMain`) is the public `@DependencyGraph`. It provides the Apple Room database,
  inherits `CalendarCoreGraph` explicitly to export `accountManager` / `calendarManager`, and inherits Root's
  `CaldavClientModule` explicitly to obtain the CalDAV bridge binding. It is accessed via `CalendarSDKProvider.shared.sdk`.

## Prerequisites

Before you begin, ensure you have met the following requirements:

- You are using a Linux, macOS, or Windows machine.
- You have installed Java Development Kit (JDK) 21 or later.
- You have Android Studio installed.
- You have [Rust](https://rustup.rs/) installed with cross-compilation targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android \
                     aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin
  ```
- You have an active internet connection to download project dependencies.

## Rust CalDAV Bridge

The `rust/caldav_bridge` crate provides CalDAV operations (discover calendars, CRUD events) via [fast-dav-rs](https://github.com/Goopil/fast-dav-rs).
iCalendar data is parsed into typed fields using the [icalendar](https://docs.rs/icalendar) crate.

The Rust → Kotlin/Swift bridge is handled automatically by [Gobley](https://github.com/aspect-build/gobley) + [UniFFI](https://mozilla.github.io/uniffi-rs/) — no manual JNI, cinterop, or JSON serialization needed.

```
Kotlin/Swift  ←──UniFFI bindings──→  Rust lib.rs  →  fast-dav-rs (CalDAV)  →  icalendar (parsing)
```

Rust compilation and binding generation are integrated into the Gradle build via the `dev.gobley.cargo` and `dev.gobley.uniffi` plugins. No separate build step is required — just run `./gradlew assembleDebug`.

## Build Commands

```bash
# Build the KmpCalendar XCFramework (iOS/macOS)
./gradlew :Core:assembleKmpCalendarReleaseXCFramework

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
