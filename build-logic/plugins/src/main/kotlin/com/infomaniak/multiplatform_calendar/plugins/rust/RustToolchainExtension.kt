package com.infomaniak.multiplatform_calendar.plugins.rust

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class RustToolchainExtension @Inject constructor(
    objects: ObjectFactory,
) {
    val rustDirectory: DirectoryProperty =
        objects.directoryProperty()

    val toolchain: Property<String> =
        objects.property(String::class.java).convention("stable")

    /**
     * Whether the plugin should try to use an existing Cargo installation before
     * bootstrapping a project-local Rust toolchain.
     *
     * When enabled, the plugin searches for Cargo in the current process `PATH`,
     * in `CARGO_HOME/bin`, and in the default Rust installation directory
     * `~/.cargo/bin`. This is useful because IDEs such as Android Studio may not
     * inherit the same `PATH` as the user's terminal.
     *
     * If no valid Cargo executable is found, the plugin falls back to installing
     * Rust locally inside [rustDirectory].
     *
     * Set this to `false` to force all developers and CI environments to use the
     * project-local Rust toolchain, which can improve build reproducibility.
     */
    val preferSystemCargo: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * False by default because fixing "Could not start cargo" does not require Rust targets.
     *
     * Enable this only when you also want the plugin to run:
     * rustup target add ...
     */
    val installRustTargets: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /**
     * Detects Kotlin/Native targets from:
     *
     * kotlin {
     *     iosArm64()
     *     iosSimulatorArm64()
     *     macosArm64()
     * }
     */
    val autoDetectKotlinNativeTargets: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Android cannot be reliably inferred from androidTarget().
     *
     * androidTarget() tells Kotlin that Android is enabled, but it does not say which
     * native ABIs you want for Rust.
     */
    val androidTargets: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    val extraTargets: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    fun androidAbis(vararg abis: String) {
        androidTargets.addAll(
            abis.mapNotNull(::androidAbiToRustTriple),
        )
    }
}
