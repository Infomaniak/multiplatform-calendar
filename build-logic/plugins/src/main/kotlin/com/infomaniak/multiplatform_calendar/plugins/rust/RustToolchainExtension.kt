package com.infomaniak.multiplatform_calendar.plugins.rust

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class RustToolchainExtension @Inject constructor(
    objects: ObjectFactory,
) {
    /**
     * Directory where the project-local Rust toolchain and generated shim scripts
     * are stored.
     *
     * By default, the plugin uses:
     *
     * `.gradle/rust`
     *
     * under the root project directory.
     */
    val rustDirectory: DirectoryProperty =
        objects.directoryProperty()

    /**
     * Rust toolchain to install for the project-local environment.
     *
     * Examples:
     *
     * - `stable`
     * - `beta`
     * - `nightly`
     * - `1.86.0`
     */
    val toolchain: Property<String> =
        objects.property(String::class.java).convention("stable")

    /**
     * Defines how the generated Cargo/Rustup shim scripts resolve the actual
     * Rust tools.
     *
     * The recommended default is [RustToolchainResolutionStrategy.LOCAL_THEN_SYSTEM].
     *
     * With this strategy:
     * - Gradle sync can still work before `bootstrapRust` has installed the
     *   project-local toolchain, as long as a system Cargo installation exists.
     * - Real builds run `bootstrapRust` first and then use the project-local
     *   toolchain.
     */
    val resolutionStrategy: Property<RustToolchainResolutionStrategy> =
        objects.property(RustToolchainResolutionStrategy::class.java)
            .convention(RustToolchainResolutionStrategy.LOCAL_THEN_SYSTEM)

    /**
     * Whether the plugin should install Rust targets with:
     *
     * `rustup target add ...`
     *
     * This is not required just to fix `Could not start 'cargo'`.
     * Enable it when the Rust toolchain must also be prepared for iOS, macOS,
     * Android, or other cross-compilation targets.
     */
    val installRustTargets: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /**
     * Whether Kotlin/Native targets declared in the KMP DSL should be converted
     * to Rust target triples automatically.
     *
     * For example:
     *
     * - `iosArm64()` -> `aarch64-apple-ios`
     * - `iosSimulatorArm64()` -> `aarch64-apple-ios-sim`
     * - `macosArm64()` -> `aarch64-apple-darwin`
     */
    val autoDetectKotlinNativeTargets: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Android Rust targets to install.
     *
     * Android cannot be reliably inferred from `androidTarget()` alone because
     * `androidTarget()` does not specify which native ABIs should be built.
     *
     * Prefer using [androidAbis] from the Gradle DSL.
     */
    val androidTargets: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    /**
     * Additional Rust targets to install.
     *
     * Use this for targets that are not covered by Kotlin/Native auto-detection
     * or Android ABI mapping.
     */
    val extraTargets: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    /**
     * Whether the plugin should generate Cargo/Rustup shim scripts during Gradle
     * configuration.
     *
     * This must stay enabled for external plugins such as Gobley because they may
     * need a Cargo path during Gradle sync/configuration, while Gradle tasks such
     * as `bootstrapRust` only run later during the execution phase.
     */
    val generateShimsAtConfigurationTime: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Whether the plugin should configure Gobley's Rust plugin when it is applied.
     *
     * When enabled, this plugin sets Gobley's `rust.toolchainDirectory` to the
     * generated shim directory:
     *
     * `.gradle/rust/shims/bin`
     *
     * This is required because Gobley Cargo and UniFFI tasks do not use this
     * plugin's [CargoExecTask]. They use their own tasks and need to know where
     * Cargo is.
     */
    val configureGobleyRust: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Whether the plugin should automatically run `bootstrapRust` before Android
     * pre-build tasks.
     */
    val wireIntoAndroidPreBuild: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Whether the plugin should automatically run `bootstrapRust` before tasks
     * that are likely to use Cargo, Rust, or UniFFI.
     */
    val wireIntoRustConsumerTasks: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Adds Android ABIs and converts them to Rust target triples.
     *
     * Example:
     *
     * ```
     * androidAbis("arm64-v8a", "x86_64")
     * ```
     *
     * becomes:
     *
     * - `aarch64-linux-android`
     * - `x86_64-linux-android`
     */
    fun androidAbis(vararg abis: String) {
        androidTargets.addAll(
            abis.mapNotNull(::androidAbiToRustTriple),
        )
    }
}
