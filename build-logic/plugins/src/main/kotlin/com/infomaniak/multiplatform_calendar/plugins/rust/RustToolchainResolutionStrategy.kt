package com.infomaniak.multiplatform_calendar.plugins.rust

enum class RustToolchainResolutionStrategy {
    /**
     * Prefer the project-local Rust toolchain when available.
     *
     * If the project-local toolchain has not been bootstrapped yet, the generated
     * shim falls back to the system Cargo/Rustup installation when available.
     *
     * This is the recommended default for KMP projects using Gobley:
     * - Gradle sync can work before the local toolchain exists.
     * - Real builds still bootstrap and use the project-local toolchain.
     */
    LOCAL_THEN_SYSTEM,

    /**
     * Prefer the system Rust installation when available.
     *
     * Falls back to the project-local Rust toolchain when no valid system
     * installation is found.
     */
    SYSTEM_THEN_LOCAL,

    /**
     * Always use the project-local Rust toolchain.
     *
     * This is the most reproducible mode, but Gradle sync may fail before
     * `bootstrapRust` has run if an external plugin tries to invoke Cargo during
     * configuration.
     */
    LOCAL_ONLY,

    /**
     * Always use the system Rust installation.
     *
     * This mode does not guarantee reproducible builds across developer machines.
     */
    SYSTEM_ONLY,
}
