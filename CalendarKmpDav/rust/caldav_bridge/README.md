# Rust CalDAV Bridge (`caldav_bridge`)

Thin [UniFFI](https://mozilla.github.io/uniffi-rs/) wrapper around [fast-dav-rs](https://github.com/Goopil/fast-dav-rs) for KMP.

## What it does

- **Discover** calendars for a given CalDAV account
- **Fetch** events with typed iCalendar fields (summary, dtstart, dtend, rrule, …) parsed via [icalendar](https://docs.rs/icalendar)
- **Create / Update / Delete** events

## Prerequisites

```bash
# 1. Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 2. Add cross-compilation targets
rustup target add aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin \
                  aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
```

## Build

No manual build step required. The `ch.ubique.uniffi.plugin` Gradle plugin handles everything:

```bash
# From the multiplatform-calendar root:
./gradlew :CalendarKmpDav:assembleAndroidMain      # Android
./gradlew :CalendarKmpDav:compileKotlinIosArm64    # iOS
```

The plugin compiles the Rust crate for each KMP target (via the NDK for Android) and generates the Kotlin bindings
automatically. `.cargo/config.toml` pins the iOS link deployment target — see the comment in that file.

## Architecture

```
Kotlin/Swift (commonMain)
    │
    ├── RustCaldavBridge : CalendarSyncRemoteSource   ← our mapping layer (val, no backticks)
    │       │
    │       └── uniffi.caldav_bridge.*      ← auto-generated UniFFI bindings (expect/actual)
    │
    └── Rust lib.rs                         ← thin wrapper
            ├── fast-dav-rs (CalDAV HTTP)
            └── icalendar (iCS parsing)
```
