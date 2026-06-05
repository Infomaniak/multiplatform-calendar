package com.infomaniak.multiplatform_calendar.plugins.rust

import java.io.File

/**
 * Writes the `cargo`/`rustup` shim scripts into `<rustDirectory>/shims/bin`.
 *
 * The shims are the cornerstone of the "Rust venv" behaviour: they are tiny
 * `cargo`/`rustup` executables that decide, at invocation time, which real tool
 * to run based on [strategy]. Both POSIX (`sh`) and Windows (`.cmd`) variants are
 * generated so the project builds on every platform.
 *
 * Each generated shim can:
 * - run the project-local toolchain in `<rustDirectory>/cargo/bin` when present,
 * - fall back to a system `cargo`/`rustup` discovered on `PATH`, `CARGO_HOME`, or
 *   `~/.cargo/bin`, and
 * - bootstrap the project-local toolchain on the fly (download `rustup-init` and
 *   install the requested [toolchain]) when nothing else is available.
 *
 * Because the shims are written during Gradle configuration, a usable `cargo`
 * path exists before any task runs, which is what allows Gradle sync to succeed
 * even on a freshly cloned machine that has never installed Rust.
 */
internal fun writeRustShims(
    rustDirectory: File,
    strategy: RustToolchainResolutionStrategy,
    toolchain: String,
) {
    val shimBinDirectory = File(rustDirectory, "shims/bin")
    shimBinDirectory.mkdirs()

    writeUnixShim(
        output = File(shimBinDirectory, "cargo"),
        toolName = "cargo",
        rustDirectory = rustDirectory,
        strategy = strategy,
        toolchain = toolchain,
    )

    writeUnixShim(
        output = File(shimBinDirectory, "rustup"),
        toolName = "rustup",
        rustDirectory = rustDirectory,
        strategy = strategy,
        toolchain = toolchain,
    )

    writeWindowsShim(
        output = File(shimBinDirectory, "cargo.cmd"),
        toolName = "cargo",
        rustDirectory = rustDirectory,
        strategy = strategy,
        toolchain = toolchain,
    )

    writeWindowsShim(
        output = File(shimBinDirectory, "rustup.cmd"),
        toolName = "rustup",
        rustDirectory = rustDirectory,
        strategy = strategy,
        toolchain = toolchain,
    )
}

private fun writeUnixShim(
    output: File,
    toolName: String,
    rustDirectory: File,
    strategy: RustToolchainResolutionStrategy,
    toolchain: String,
) {
    output.parentFile.mkdirs()

    output.writeText(
        """
        #!/usr/bin/env sh
        set -eu

        RUST_DIR="${rustDirectory.absolutePath}"
        TOOLCHAIN="$toolchain"

        LOCAL_CARGO_HOME="${'$'}RUST_DIR/cargo"
        LOCAL_RUSTUP_HOME="${'$'}RUST_DIR/rustup"
        LOCAL_BIN="${'$'}LOCAL_CARGO_HOME/bin"
        LOCAL_TOOL="${'$'}LOCAL_BIN/$toolName"

        INSTALLER_DIR="${'$'}RUST_DIR/installer"

        SHIM_BIN="${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")" && pwd -P)"

        find_system_tool() {
            OLD_IFS="${'$'}IFS"
            IFS=":"

            for DIR in ${'$'}PATH; do
                if [ -z "${'$'}DIR" ]; then
                    continue
                fi

                if [ "${'$'}DIR" = "${'$'}SHIM_BIN" ]; then
                    continue
                fi

                CANDIDATE="${'$'}DIR/$toolName"

                if [ -x "${'$'}CANDIDATE" ]; then
                    IFS="${'$'}OLD_IFS"
                    echo "${'$'}CANDIDATE"
                    return 0
                fi
            done

            IFS="${'$'}OLD_IFS"

            if [ -n "${'$'}{CARGO_HOME:-}" ] && [ -x "${'$'}CARGO_HOME/bin/$toolName" ]; then
                echo "${'$'}CARGO_HOME/bin/$toolName"
                return 0
            fi

            if [ -n "${'$'}{HOME:-}" ] && [ -x "${'$'}HOME/.cargo/bin/$toolName" ]; then
                echo "${'$'}HOME/.cargo/bin/$toolName"
                return 0
            fi

            return 1
        }

        host_triple() {
            OS_NAME="${'$'}(uname -s)"
            ARCH_NAME="${'$'}(uname -m)"

            case "${'$'}ARCH_NAME" in
                arm64|aarch64)
                    CPU="aarch64"
                    ;;
                x86_64|amd64)
                    CPU="x86_64"
                    ;;
                *)
                    echo "Unsupported architecture: ${'$'}ARCH_NAME" >&2
                    exit 1
                    ;;
            esac

            case "${'$'}OS_NAME" in
                Darwin)
                    echo "${'$'}CPU-apple-darwin"
                    ;;
                Linux)
                    echo "${'$'}CPU-unknown-linux-gnu"
                    ;;
                *)
                    echo "Unsupported OS: ${'$'}OS_NAME" >&2
                    exit 1
                    ;;
            esac
        }

        download_file() {
            URL="${'$'}1"
            OUTPUT="${'$'}2"

            if command -v curl >/dev/null 2>&1; then
                curl -L --fail "${'$'}URL" -o "${'$'}OUTPUT"
                return 0
            fi

            if command -v wget >/dev/null 2>&1; then
                wget -O "${'$'}OUTPUT" "${'$'}URL"
                return 0
            fi

            echo "Neither curl nor wget is available to download rustup-init." >&2
            exit 1
        }

        bootstrap_local_rust() {
            if [ -x "${'$'}LOCAL_BIN/cargo" ] && [ -x "${'$'}LOCAL_BIN/rustup" ]; then
                return 0
            fi

            mkdir -p "${'$'}INSTALLER_DIR"

            TRIPLE="${'$'}(host_triple)"
            INSTALLER="${'$'}INSTALLER_DIR/rustup-init"
            URL="https://static.rust-lang.org/rustup/dist/${'$'}TRIPLE/rustup-init"

            echo "Project-local Rust toolchain not found." >&2
            echo "Downloading rustup-init from ${'$'}URL" >&2

            download_file "${'$'}URL" "${'$'}INSTALLER"
            chmod +x "${'$'}INSTALLER"

            echo "Installing project-local Rust toolchain '${'$'}TOOLCHAIN' into ${'$'}RUST_DIR" >&2

            CARGO_HOME="${'$'}LOCAL_CARGO_HOME" \
            RUSTUP_HOME="${'$'}LOCAL_RUSTUP_HOME" \
            "${'$'}INSTALLER" -y --no-modify-path --profile minimal --default-toolchain "${'$'}TOOLCHAIN"

            if [ ! -x "${'$'}LOCAL_BIN/cargo" ]; then
                echo "Rust was installed, but local cargo is still missing: ${'$'}LOCAL_BIN/cargo" >&2
                exit 1
            fi
        }

        run_local_tool() {
            if [ -x "${'$'}LOCAL_TOOL" ]; then
                export CARGO_HOME="${'$'}LOCAL_CARGO_HOME"
                export RUSTUP_HOME="${'$'}LOCAL_RUSTUP_HOME"
                export PATH="${'$'}LOCAL_BIN:${'$'}PATH"
                exec "${'$'}LOCAL_TOOL" "${'$'}@"
            fi

            return 1
        }

        run_system_tool() {
            SYSTEM_TOOL="${'$'}(find_system_tool || true)"

            if [ -n "${'$'}SYSTEM_TOOL" ]; then
                exec "${'$'}SYSTEM_TOOL" "${'$'}@"
            fi

            return 1
        }

        bootstrap_then_run_local() {
            bootstrap_local_rust
            run_local_tool "${'$'}@"
        }

        case "${strategy.name}" in
            LOCAL_THEN_SYSTEM)
                run_local_tool "${'$'}@" || run_system_tool "${'$'}@" || bootstrap_then_run_local "${'$'}@"
                ;;

            SYSTEM_THEN_LOCAL)
                run_system_tool "${'$'}@" || run_local_tool "${'$'}@" || bootstrap_then_run_local "${'$'}@"
                ;;

            LOCAL_ONLY)
                run_local_tool "${'$'}@" || bootstrap_then_run_local "${'$'}@"
                ;;

            SYSTEM_ONLY)
                run_system_tool "${'$'}@"
                ;;
        esac

        echo "Unable to find $toolName." >&2
        exit 127
        """.trimIndent(),
    )

    output.setExecutable(true)
}

private fun writeWindowsShim(
    output: File,
    toolName: String,
    rustDirectory: File,
    strategy: RustToolchainResolutionStrategy,
    toolchain: String,
) {
    output.parentFile.mkdirs()

    output.writeText(
        """
        @echo off
        setlocal

        set "RUST_DIR=${rustDirectory.absolutePath}"
        set "TOOLCHAIN=$toolchain"

        set "LOCAL_CARGO_HOME=%RUST_DIR%\cargo"
        set "LOCAL_RUSTUP_HOME=%RUST_DIR%\rustup"
        set "LOCAL_BIN=%LOCAL_CARGO_HOME%\bin"
        set "LOCAL_TOOL=%LOCAL_BIN%\$toolName.exe"
        set "INSTALLER_DIR=%RUST_DIR%\installer"
        set "INSTALLER=%INSTALLER_DIR%\rustup-init.exe"

        if "${strategy.name}"=="LOCAL_THEN_SYSTEM" goto local_then_system
        if "${strategy.name}"=="SYSTEM_THEN_LOCAL" goto system_then_local
        if "${strategy.name}"=="LOCAL_ONLY" goto local_only
        if "${strategy.name}"=="SYSTEM_ONLY" goto system_only

        :local_then_system
        call :run_local %*
        if %ERRORLEVEL% EQU 0 exit /b 0
        call :run_system %*
        if %ERRORLEVEL% EQU 0 exit /b 0
        call :bootstrap_local
        call :run_local %*
        exit /b %ERRORLEVEL%

        :system_then_local
        call :run_system %*
        if %ERRORLEVEL% EQU 0 exit /b 0
        call :run_local %*
        if %ERRORLEVEL% EQU 0 exit /b 0
        call :bootstrap_local
        call :run_local %*
        exit /b %ERRORLEVEL%

        :local_only
        call :run_local %*
        if %ERRORLEVEL% EQU 0 exit /b 0
        call :bootstrap_local
        call :run_local %*
        exit /b %ERRORLEVEL%

        :system_only
        call :run_system %*
        exit /b %ERRORLEVEL%

        :run_local
        if exist "%LOCAL_TOOL%" (
          set "CARGO_HOME=%LOCAL_CARGO_HOME%"
          set "RUSTUP_HOME=%LOCAL_RUSTUP_HOME%"
          set "PATH=%LOCAL_BIN%;%PATH%"
          "%LOCAL_TOOL%" %*
          exit /b %ERRORLEVEL%
        )
        exit /b 1

        :run_system
        if defined CARGO_HOME (
          if exist "%CARGO_HOME%\bin\$toolName.exe" (
            "%CARGO_HOME%\bin\$toolName.exe" %*
            exit /b %ERRORLEVEL%
          )
        )

        if exist "%USERPROFILE%\.cargo\bin\$toolName.exe" (
          "%USERPROFILE%\.cargo\bin\$toolName.exe" %*
          exit /b %ERRORLEVEL%
        )

        where $toolName >nul 2>nul
        if %ERRORLEVEL% EQU 0 (
          $toolName %*
          exit /b %ERRORLEVEL%
        )

        exit /b 1

        :bootstrap_local
        if exist "%LOCAL_BIN%\cargo.exe" (
          if exist "%LOCAL_BIN%\rustup.exe" (
            exit /b 0
          )
        )

        mkdir "%INSTALLER_DIR%" >nul 2>nul

        powershell -NoProfile -ExecutionPolicy Bypass -Command ^
          "Invoke-WebRequest -Uri 'https://static.rust-lang.org/rustup/dist/x86_64-pc-windows-msvc/rustup-init.exe' -OutFile '%INSTALLER%'"

        if not exist "%INSTALLER%" (
          echo Unable to download rustup-init.exe. 1>&2
          exit /b 1
        )

        set "CARGO_HOME=%LOCAL_CARGO_HOME%"
        set "RUSTUP_HOME=%LOCAL_RUSTUP_HOME%"

        "%INSTALLER%" -y --no-modify-path --profile minimal --default-toolchain "%TOOLCHAIN%"

        exit /b %ERRORLEVEL%
        """.trimIndent(),
    )
}
