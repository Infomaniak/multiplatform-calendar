# `infomaniak.rustToolchain` convention plugin

This convention plugin lets anyone clone the project and build the Rust
dependency (`kmpdav`) **without manually installing Rust**. It works like a
"virtual environment" for Rust: a project-local toolchain is installed under
`.gradle/rust` (git-ignored) and used automatically by the build.

## Why it is needed

`kmpdav` uses [Gobley](https://gobley.dev/) to compile a Rust crate and generate
UniFFI bindings. Gobley invokes `cargo` (for example `cargo metadata`) **during
Gradle configuration**, which is exactly what happens during an IDE "Gradle
sync". On a freshly cloned machine that has never installed Rust, that call would
fail with `Could not start 'cargo'`.

This plugin guarantees that a working `cargo` is always available, even before
any Gradle task has run.

## How it works

```
.gradle/rust/
├── cargo/      # project-local CARGO_HOME
├── rustup/     # project-local RUSTUP_HOME
├── installer/  # downloaded rustup-init
└── shims/bin/  # generated cargo / rustup shim scripts
```

1. **Shims (`RustShimWriter`)** – During configuration the plugin writes small
   `cargo`/`rustup` shim scripts (POSIX `sh` and Windows `.cmd`). Because they
   exist before any task runs, Gobley finds a `cargo` to call during sync.

2. **Resolution strategy (`RustToolchainResolutionStrategy`)** – Each shim
   decides which real tool to run. The default `LOCAL_THEN_SYSTEM`:
   - uses the project-local toolchain when it is installed,
   - falls back to a system Rust install so sync works immediately,
   - bootstraps the project-local toolchain on the fly as a last resort.

3. **Gobley wiring (`RustToolchainPlugin.configureGobleyRustIfAvailable`)** – The
   plugin sets Gobley's `rust.toolchainDirectory` to the shim directory. Gobley
   prepends that directory to `PATH` when it runs Cargo, so Gobley's own Cargo and
   UniFFI tasks use the shims too.

4. **Bootstrap task (`BootstrapRustTask`)** – For real builds, the `bootstrapRust`
   task installs the project-local toolchain (downloading `rustup-init` if
   needed), optionally installs the requested Rust target triples, and records the
   resolved installation in `build/rustToolchain/resolved.properties`.

5. **Lifecycle wiring (`RustToolchainPlugin.wireBootstrapRustIntoBuildLifecycle`)**
   – Android `preBuild` and any Cargo/Rust/UniFFI task is made to depend on
   `bootstrapRust`, and `Exec` tasks get the resolved toolchain injected into
   their environment.

## Usage

```kotlin
plugins {
    id("infomaniak.rustToolchain")
    // ... Gobley plugins
}

rustToolchain {
    toolchain.set("stable")
    resolutionStrategy.set(RustToolchainResolutionStrategy.LOCAL_THEN_SYSTEM)

    installRustTargets.set(true)
    androidAbis("arm64-v8a", "x86_64")
}
```

See the KDoc on `RustToolchainExtension` for every available option.
