
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEMO="RecurrenceWeb/demo"
BUILD="RecurrenceWeb/build"

mkdir -p "$DEMO"

echo "=== Building Kotlin/JS ==="
start=$SECONDS
./gradlew :RecurrenceWeb:jsBrowserProductionWebpack --console=plain 2>&1 | tee "$DEMO/js-build.log"
js_seconds=$((SECONDS - start))

echo "=== Building Kotlin/Wasm ==="
start=$SECONDS
./gradlew :RecurrenceWeb:wasmJsBrowserProductionWebpack --console=plain 2>&1 | tee "$DEMO/wasm-build.log"
wasm_seconds=$((SECONDS - start))

printf '{"jsSeconds":%d,"wasmSeconds":%d}\n' \
    "$js_seconds" "$wasm_seconds" > "$DEMO/build-timing.json"

echo "=== Packaging web artifacts and Compose Resources ==="

python3 - <<'PY'
from pathlib import Path
from shutil import copytree, rmtree, copy2
import hashlib

base = Path("RecurrenceWeb")
build = base / "build"
demo = base / "demo"

targets = (
    ("js", "js", "recurrence-js.js"),
    ("wasmJs", "wasm", "recurrence-wasm.js"),
)

def find_bundle(target, filename):
    candidates = [
        build / "dist" / target / "productionExecutable",
        build / "kotlin-webpack" / target / "productionExecutable",
        build / "kotlin-webpack" / target / "production",
    ]

    candidates.extend(
        sorted(
            (
                p.parent
                for p in build.rglob(filename)
                if not any(
                    part in p.parts
                    for part in ("compileSync", "node_modules", "tmp")
                )
            ),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
    )

    for candidate in candidates:
        if (candidate / filename).is_file():
            return candidate

    raise RuntimeError(
        f"Webpack bundle not found: {filename}\n"
        f"Checked:\n" +
        "\n".join(str(p) for p in candidates)
    )

# Copy production webpack artifacts.
for target, output, filename in targets:
    source = find_bundle(target, filename)
    destination = demo / "dist" / output

    if destination.exists():
        rmtree(destination)

    copytree(source, destination)

    bundle = destination / filename
    if not bundle.is_file():
        raise RuntimeError(f"Missing copied bundle: {bundle}")

    print(
        f"[OK] {target}: {source} -> {destination} "
        f"({bundle.stat().st_size:,} bytes)"
    )

# Locate the actual generated Compose resources.
# Do not assume a particular processedResources directory.
resource_roots = []

for resource in build.rglob("*.cvr"):
    if any(
        part in resource.parts
        for part in ("node_modules", "compileSync")
    ):
        continue

    for parent in resource.parents:
        if parent.name == "composeResources":
            resource_roots.append(parent)
            break

if not resource_roots:
    raise RuntimeError(
        "No generated Compose Resources (.cvr) found.\n"
        "Inspect with:\n"
        "find RecurrenceWeb/build -name '*.cvr' -type f"
    )

resource_roots = sorted(
    set(resource_roots),
    key=lambda p: str(p)
)

destination = demo / "composeResources"

if destination.exists():
    rmtree(destination)

destination.mkdir(parents=True)

# Merge resources generated for JS and Wasm.
# Both targets must expose the same resource paths.
for source in resource_roots:
    for file in source.rglob("*"):
        if not file.is_file():
            continue

        relative = file.relative_to(source)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)

        if target.exists():
            old = hashlib.sha256(target.read_bytes()).digest()
            new = hashlib.sha256(file.read_bytes()).digest()

            if old != new:
                raise RuntimeError(
                    f"Conflicting Compose Resource: {relative}\n"
                    f"Source: {file}"
                )
            continue

        copy2(file, target)

        print(f"[RESOURCE] {relative}")

# Verify the resources required by the demo.
package = (
    "com.infomaniak.calendar.web.resources"
)

required = [
    destination / package / "values" / "strings.commonMain.cvr",
    destination / package / "values-fr" / "strings.commonMain.cvr",
]

for file in required:
    if not file.is_file():
        raise RuntimeError(
            f"Missing required Compose Resource: {file}\n"
            "Check generated resource package and localization."
        )

    print(f"[OK] {file}")

print()
print("=== Build completed ===")
print("Kotlin/JS:   packaged")
print("Kotlin/Wasm: packaged")
print("Compose Resources: packaged")
print()
print("Run:")
print("  cd RecurrenceWeb/demo")
print("  python3 -m http.server 8080")
print()
print("Open http://localhost:8080")
PY
