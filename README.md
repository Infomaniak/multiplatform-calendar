# multiplatform-calendar

## Table of Contents

- [Prerequisites](#prerequisites)
- [Contributing](#contributing)
- [License](#license)

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

## Contributing

If you see a bug or an enhancement point, feel free to create an issue, so that we can discuss it. Once approved, we or you (
depending on the priority of the bug/improvement) will take care of the issue and apply a merge request. Please, don't do a merge
request before creating an issue.

## License

This project is under GPLv3 license. See the LICENSE file for more details.
