# Rust CalDAV Bridge (`caldav_bridge`)

Thin [UniFFI](https://mozilla.github.io/uniffi-rs/) wrapper around [libdav](https://docs.rs/libdav) for KMP.

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
                  aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

## Build

No manual build step required. The Gradle plugins `dev.gobley.cargo` and `dev.gobley.uniffi` handle everything:

```bash
# From the android-calendar root:
./gradlew :multiplatform-calendar:assembleDebug   # Android
./gradlew :multiplatform-calendar:compileKotlinIosArm64   # iOS
```

Gobley compiles the Rust crate for each KMP target and generates Kotlin/Swift bindings automatically.

## Architecture

```
Kotlin/Swift (commonMain)
    │
    ├── RustCaldavBridge : CaldavClient     ← our mapping layer (val, no backticks)
    │       │
    │       └── uniffi.caldav_bridge.*      ← auto-generated UniFFI bindings (expect/actual)
    │
    └── Rust lib.rs                         ← ~130 lines, thin wrapper
            ├── libdav (CalDAV HTTP)
            └── icalendar (iCS parsing)
```
