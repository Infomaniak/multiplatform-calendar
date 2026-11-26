# Recurrence

Standalone Kotlin Multiplatform library for RFC 5545 recurrence rules, available on **Android, iOS, macOS, JavaScript and WebAssembly (Wasm/JS)**.

The module owns the recurrence model, RRULE parser and serializer, and human-readable formatter with its own
Compose Multiplatform resources and translations. It does not depend on `CalendarCore` or `CalendarResources`.
`CalendarCore` consumes it for event recurrence handling; web clients can use it independently.

## Web API

The shared `webMain` source set exports the following function through `@JsExport` for both JS and Wasm:

```kotlin
fun formatRRule(
    rawRRule: String,
    start: String,
    timeZoneId: String?,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
)
```

`start` is the recurrence master's local DTSTART in ISO-8601 format, for example `2026-01-01T10:00:00`.
It provides the context for selectors omitted from the RRULE. The optional time-zone ID is used to display UTC
UNTIL values in the master's local date; `null` uses UTC.

Formatting runs asynchronously in a dedicated coroutine scope. The success callback receives the localized description,
and the failure callback receives an error message. JavaScript callers can wrap these callbacks in a `Promise` to use
`await`. Consult the generated TypeScript declarations for the exports of each target.

### Usage

After installing the local package and serving its translation resources (see below), select the import matching
the installed target. The current JS and Wasm distributions expose different export shapes.

For the **JavaScript** package:

```javascript
import { com } from "multiplatform-calendar-Recurrence";

const { formatRRule } = com.infomaniak.multiplatform_calendar.recurrence;
```

For the **Wasm** package:

```javascript
import { formatRRule } from "multiplatform-calendar-Recurrence";
```

With either import, format a rule using callbacks:

```javascript
formatRRule(
  "FREQ=WEEKLY;BYDAY=MO,WE",
  "2026-01-05T10:00:00",
  null,
  (description) => console.log(description),
  (message) => console.error("Could not format the recurrence:", message),
);
```

Alternatively, wrap the same API in a Promise:

```javascript
function formatRRuleAsync(rawRRule, start, timeZoneId = null) {
  return new Promise((resolve, reject) => {
    formatRRule(rawRRule, start, timeZoneId, resolve, (message) => {
      reject(new Error(message));
    });
  });
}

async function showRecurrence() {
  try {
    const description = await formatRRuleAsync(
      "FREQ=WEEKLY;BYDAY=MO,WE",
      "2026-01-05T10:00:00",
    );
    console.log(description);
  } catch (error) {
    console.error("Could not format the recurrence:", error);
  }
}

showRecurrence();
```

The description is localized using the runtime's resource locale; its exact text depends on the selected language.

## Build a local npm package

Run the Gradle commands from the repository root (`multiplatform-calendar/`).
The publishing plugin reads **`core.version`**, not `version`, to set the generated package version.
The following commands build local distributions only; they do not publish anything.

### JavaScript

```bash
./gradlew :Recurrence:jsBrowserProductionLibraryDistribution \
  -Pcore.version=0.1.0-local
```

Create a shareable npm archive from the generated distribution:

```bash
cd Recurrence/build/dist/js/productionLibrary
npm pack
```

### WebAssembly

```bash
./gradlew :Recurrence:wasmJsBrowserProductionLibraryDistribution \
  -Pcore.version=0.1.0-local
```

Create a shareable npm archive from the generated distribution:

```bash
cd Recurrence/build/dist/wasmJs/productionLibrary
npm pack
```

Wasm consumers need a browser with WasmGC support.

### Install and serve the package

Share the generated `.tgz` file. In the consuming web project:

```bash
npm install /path/to/package.tgz
```

JS and Wasm are separate distributions, currently generated with the same npm package name and version.
Keep their archives separate and choose the desired target when installing.

Keep the generated JavaScript modules, TypeScript declarations, and Wasm files together.
The consuming application must also serve the package's `composeResources/` directory at the corresponding
`composeResources/` URL relative to the page, for example by copying it into its public assets directory.
Installing the npm package alone does not make these translation files accessible to the browser.

Do not edit generated files under `build/`; rebuilding overwrites them.
Direct publication to the npm registry is not configured yet.
