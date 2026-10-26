# multiplatform-calendar

## Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Build Commands](#build-commands)
- [Contributing](#contributing)
- [License](#license)

## Architecture

```
multiplatform-calendar/
├── Account/                         # Shared identity types (AccountId, AccessToken)
│   └── build.gradle.kts             # Account module build (publish)
├── CalendarCore/                    # Public KMP library (domain, Room DB, repositories, managers, Apple SDK)
│   ├── src/commonMain/              # Cross-platform: domain, Room DB, repositories, DI graph contracts/mappers
│   ├── src/androidMain/             # Android Room database provider (via Metro DI)
│   ├── src/appleMain/               # CalendarSDK + CalendarSDKProvider (Apple public DI graph)
│   ├── src/commonTest/              # Shared unit tests
│   └── build.gradle.kts             # Public library build (SKIE, Metro, XCFramework)
├── CalendarKmpDav/                  # Internal KMP bridge module (Rust/UniFFI + remote CalDAV layer)
│   ├── src/commonMain/              # RustCaldavBridge, CaldavClientModule, remote models, remote client interface
│   ├── rust/caldav_bridge/          # Rust crate: CalDAV operations via fast-dav-rs + icalendar
│   └── build.gradle.kts             # Bridge module build (UniFFI/Cargo, Metro)
├── CalendarResources/               # Shared Compose Multiplatform recurrence strings and translations
│   └── src/commonMain/composeResources/  # values/ and values-<lang>/strings.xml
├── Contacts/                        # Standalone KMP contacts module (server address book + device contacts)
│   ├── src/commonMain/              # ContactsManager, merge/search/sync logic, Room tables and database, Ktor remote source
│   ├── src/androidMain/             # AndroidDeviceContactsProvider (ContactsContract)
│   ├── src/appleMain/               # AppleDeviceContactsProvider (Contacts framework), standalone database opener
│   ├── src/appleTargetMain/         # Added to each Apple target, reaches the Room-generated contacts DAO
│   └── build.gradle.kts             # Contacts module build (Room, Ktor, publish)
├── Meet/                            # Standalone KMP module generating kMeet video-conference room URLs
│   ├── src/commonMain/              # MeetManager, Ktor remote source
│   └── build.gradle.kts             # Meet module build (Ktor, publish)
├── BookableResources/               # Standalone KMP module for the Infomaniak bookable meeting rooms
│   ├── src/commonMain/              # BookableResourcesManager, sync, availability, busy periods, Room tables, Ktor remote source
│   └── build.gradle.kts             # BookableResources module build (Room, Ktor, publish)
├── Organisations/                   # Standalone KMP module for the Infomaniak organisations of the account
│   ├── src/commonMain/              # OrganisationsManager, sync, Room table, Ktor remote source
│   └── build.gradle.kts             # Organisations module build (Room, Ktor, publish)
├── Network/                         # Shared Infomaniak API client (HttpClient factory, ApiResponse)
│   └── build.gradle.kts             # Network module build (publish)
├── build.gradle.kts                 # Root aggregator (no sources)
├── buildRelease                     # Script to build & zip MultiplatformCalendar.xcframework for iOS/macOS release
└── buildRust                        # Script for standalone Rust compilation (optional, Gradle handles it)
```

### Modules

| Module               | Purpose                                                                                       |
|----------------------|-----------------------------------------------------------------------------------------------|
| **Account**          | Shared identity types (`AccountId`, `AccessToken`) and `InMemoryAccountTokens`, used by CalendarCore, Contacts, Meet, BookableResources and Organisations |
| **CalendarCore**     | Public API: domain models, Room database, DAOs, repositories, managers, Apple `CalendarSDK`   |
| **CalendarKmpDav**   | Internal bridge: Rust/UniFFI CalDAV bridge, remote CalDAV models/client, `CaldavClientModule` |
| **CalendarResources** | Compose Multiplatform strings and plurals used by CalendarCore recurrence localization |
| **Contacts**         | Standalone contacts module: server address book merged with device contacts, ETag sync        |
| **Meet**             | kMeet room URL generation for events (`X-INFOMANIAK-MEET-ROOM-URL`)                           |
| **BookableResources** | Meeting rooms the account can book (`X-INFOMANIAK-BOOKABLE` uuid): details, availability, bookings |
| **Organisations**    | Infomaniak organisations the account belongs to: id and name                                  |
| **Network**          | Shared Infomaniak API client: `createHttpClient`, `ApiResponse`, `ApiErrorException`          |

### Recurrence localization

`CalendarCore` provides two suspend extensions for RRULE descriptions:

```kotlin
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toLocalizedRecurrenceString
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.toLocalizedString

val description: String? = event.toLocalizedRecurrenceString()
val ruleDescription: String = rule.toLocalizedString(start = dtStart, timeZone = startTimeZone)
```

`Event.toLocalizedRecurrenceString()` automatically supplies the DTSTART context required to
describe the event's RRULE. Master events use their own `timing.bounds`, while materialized
occurrences use `recurrenceMasterBounds` so implicit RRULE selectors are always resolved against
the original master's DTSTART rather than the occurrence's start.

Overrides are enriched in the domain model with the master's RRULE and original bounds, so the same
API can describe the recurrence of both generated occurrences and overridden occurrences.

The function returns `null` when there is no RRULE, including RDATE-only events. It describes the
RRULE itself and does not include RDATE/EXDATE exceptions.

`RecurrenceRule.toLocalizedString()` requires DTSTART because omitted weekday, day, or month
selectors may inherit values from it. Its optional time zone converts a UTC UNTIL to the event's
start time zone for display; without one, UTC is used. DATE and floating UNTIL values keep their
own calendar date.

Put recurrence strings and translations in `CalendarResources/src/commonMain/composeResources/values*/strings.xml`.
`CalendarCore` depends on this module and aggregates its resources into its Apple framework.
This is distinct from the Android app's `CalendarComponents:Resources` module in the consuming repository.
The submodule's `.github/workflows/translations-validation.yml` checks the Compose resource translations.

### XCFramework

The `MultiplatformCalendar.xcframework` is produced by the **CalendarCore module**. `:CalendarKmpDav` is a plain `implementation` dependency
(not exported): the public Apple API only exposes Core-owned types — e.g. credentials are passed as the Core
`DavCredentials` (mapped to the internal `:CalendarKmpDav` `DavAccount` at the repository boundary). The only `:CalendarKmpDav` symbol
left in the generated header is an **empty** `CaldavClientModule` marker protocol that `CalendarSDK` must conform to for
DI (see the DI note below); no `:CalendarKmpDav` data type is exposed.

`:Account` and `:Contacts` are `api` dependencies of CalendarCore and **are exported**: `AccountId`, `AccessToken`,
`ContactsManager` and its types (`Contact`, …) are part of the public Swift API. `:Account` and `:Contacts` use the
generic `com.infomaniak.multiplatform_core.*` package root, as they are meant to move to a shared KMP core.
`:Meet` and `:BookableResources` are exported the same way (`MeetException`, `BookableResourcesManager` and their types) but, being
specific to the calendar, use the `com.infomaniak.multiplatform_calendar.meet` and `…bookable_resources` packages.
`:Organisations` is exported too (`OrganisationsManager` and its types), under `com.infomaniak.multiplatform_core.organisations`.

`:Network` is an `implementation` dependency of CalendarCore, Contacts, Meet, BookableResources and Organisations, and is **not** exported. Like `:Account`, it uses the
`com.infomaniak.multiplatform_core.*` package root and has no DI, so it can move to a shared KMP core.

CalendarCore's `CalendarDatabase` hosts the tables of every module. Contacts, BookableResources and Organisations only declare their entities and
DAO, public but `@HiddenFromObjC`, behind an interface (`ContactsDatabase`, `BookableResourcesDatabase`, `OrganisationsDatabase`) that `CalendarDatabase` implements.
On Apple, the contacts live in their own database instead (`StandaloneContactsDatabase`, opened at `ContactsSettings.databasePath`).
Room generates `ContactDao_Impl` in Contacts only: `CalendarDatabase` gets it through `hostedContactDao` instead of
declaring `contactDao()` abstract, which would generate a duplicate class and break the Android app's dex merge.

Apple consumers import `MultiplatformCalendar` and access the SDK through:

```swift
import MultiplatformCalendar

let sdk = CalendarSDKProvider.shared.sdk
sdk.accountManager.initAccount(...)
sdk.calendarManager.observeCalendars(...)
```

### Date and time types

`kotlinx-datetime` is an `api` dependency **exported** into the XCFramework, because its types (`LocalDate`,
`LocalDateTime`, `TimeZone`, `YearMonth`, …) cross the public API — `calendarManager.observeDaySlices(start:end:timeZone:)` takes
instants and keys its result by `LocalDate`. Exporting it gives Swift those types under their plain names
(`LocalDate` instead of an opaque `Kotlinx_datetimeLocalDate`), lets SKIE generate its Swift refinements for them,
and above all ships the Foundation converters Apple callers need to build their own values.

Kotlin → Foundation, as instance methods:

```swift
let date: Date = instant.toNSDate()
let comps: DateComponents = localDate.toNSDateComponents()     // also on LocalDateTime and YearMonth
let tz: Foundation.TimeZone = kotlinTimeZone.toNSTimeZone()
```

Foundation → Kotlin, through `ConvertersKt`:

```swift
let kotlinTz: MultiplatformCalendar.TimeZone = ConvertersKt.toKotlinTimeZone(timeZone)
```

Two rough edges are worth knowing about, both coming from upstream rather than from this build:

- **`TimeZone` is ambiguous.** The exported `kotlinx.datetime.TimeZone` collides with `Foundation.TimeZone`, so Swift
  asks for a qualified name on either side (`Foundation.TimeZone`, `MultiplatformCalendar.TimeZone`) wherever both are
  in scope.
- **`Date` → `Instant` has no usable converter.** `ConvertersKt.toKotlinInstant(_:youShallNotPass:)` takes an
  `OverloadMarker`, a marker `kotlinx-datetime` 0.8 carries while `Instant` moves from `kotlinx.datetime` to
  `kotlin.time`, and that type exposes no initialiser to Objective-C. Go through the epoch instead:

  ```swift
  let instant = KotlinInstant.companion.fromEpochMilliseconds(
      epochMilliseconds: Int64(date.timeIntervalSince1970 * 1000))
  ```

### DI (Metro)

- **Android**: `AppGraph` (in the Android app) is the `@DependencyGraph`. Core contributes shared graph accessors
  (`CalendarCoreGraph`) plus `AndroidDatabaseModule` and `DatabaseModule`. The `:CalendarKmpDav` module contributes `CaldavClientModule`.
  The `:Contacts` module is wired by Core (`ContactsModule` + platform `DeviceContactsProvider` bindings); its tables live in
  `CalendarDatabase`. `AccountManager.initAccount` / `removeAccount` also init / remove
  the account in `ContactsManager`. Likewise, `:BookableResources` is wired by Core (`BookableResourcesModule`), its tables live in
  `CalendarDatabase`, and `AccountManager` initializes / removes the account in `BookableResourcesManager` too. `:Organisations` is wired the same
  way (`OrganisationsModule`, `OrganisationsManager`). `:Meet` is
  wired by Core (`MeetModule`) and has no table; `AccountManager` initializes / removes the account in `MeetManager` too.
- **Apple**: `CalendarSDK` (in `CalendarCore/appleMain`) is the public `@DependencyGraph`. It provides the Apple Room database,
  inherits `CalendarCoreGraph` explicitly to export `accountManager` / `calendarManager` / `contactsManager` / `bookableResourcesManager` / `organisationsManager`, and inherits
  `:CalendarKmpDav`'s `CaldavClientModule` explicitly to obtain the CalDAV bridge binding. It is accessed via
  `CalendarSDKProvider.shared.sdk(calendar:crashReport:contacts:)`, `contacts` giving the path of the dedicated contacts database.

### Bookable resources

`BookableResourcesManager` reads the bookable resources API on `calendar.infomaniak.com` and keeps only the `meeting_room`
ones. `sync` replaces the local resources of each account (the API sends no ETag); `observeResource(accountId, uuid)` resolves the
`X-INFOMANIAK-BOOKABLE` uuid of an event and fetches a resource missing locally, staying `null` when that fails.
`availability` and `isAvailable` always hit the network, and `busyPeriods` does unless it is given no uuid. Booking itself is not an API call: it is the
`X-INFOMANIAK-BOOKABLE` property of the CalDAV event.

### Organisations

`OrganisationsManager` reads `GET /1/accounts` on `api.infomaniak.com` and keeps every organisation, whatever its access.
`sync` replaces the local organisations of each account; `getOrganisation(accountId, id)` reads them locally only.

### kMeet

`CalendarManager.generateMeetRoom(calendarId)` creates a new kMeet room on each call, for the account of that calendar:
`MeetManager` calls `GET /api/pim/calendar/meet-room` on `calendar.infomaniak.com`; failures are thrown as
`CalendarSdkException`, with the `MeetException` as `cause`. The returned `MeetRoom` goes in `EventEditData.meetRoom`: saving the edit writes the `X-INFOMANIAK-MEET-ROOM-URL` property
of the CalDAV event, and `null` removes it. Like the web, the room also gets a localized block (`CalendarResources`) in
`DESCRIPTION`, between the `/*---…---*/` markers the edit already hides: saving a new room appends its block in place of
the previous room's, and removing the room removes it.

### Invitation replies

`CalendarManager.updateParticipationStatus(target, reply)` answers an invitation with a `ParticipationReply` (`Accepted`,
`Declined`, `Tentative`). Only the user's `PARTSTAT` changes and `SEQUENCE` is left untouched (RFC 6638 §3.2.2.1); the server
then sends the iTIP `REPLY` to the organizer. `AllOccurrences` updates the master and every override listing the user,
`ThisOccurrence` updates the occurrence's override and creates it if needed, and `ThisAndFollowing` is rejected.

### Contacts permissions

`:Contacts` reads the device contacts but never requests the permission itself: until access is granted, only the
server address book is searched.

- **Android**: `READ_CONTACTS` is declared by the module and merged into the app manifest; the app requests it at runtime.
- **Apple**: the app must add `NSContactsUsageDescription` to its `Info.plist` and request access
  (`CNContactStore.requestAccess(for: .contacts)`).

Request it where the suggestions are first needed, e.g. when the user starts typing an attendee. Nothing else is needed
once it is granted: the next search reads the device contacts.

## Prerequisites

Before you begin, ensure you have met the following requirements:

- You are using a Linux, macOS, or Windows machine.
- You have installed Java Development Kit (JDK) 21 or later.
- You have Android Studio installed.
- **NDK 30.0.14904198 or newer**: required for the 16 KB page size alignment Android 15+ mandates. Nothing to do manually:
  the `ensure-ndk-version` convention plugin reuses any newer installed NDK and otherwise downloads this one through
  `sdkmanager`. See [NDK handling](#ndk-handling).
- You have [Rust](https://rustup.rs/) installed with cross-compilation targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android \
                     aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin
  ```
- You have an active internet connection to download project dependencies.

## Rust CalDAV Bridge

The `CalendarKmpDav/rust/caldav_bridge` crate provides CalDAV operations (discover calendars, CRUD events)
via [fast-dav-rs](https://github.com/Goopil/fast-dav-rs).
iCalendar data is parsed into typed fields using the [icalendar](https://docs.rs/icalendar) crate (see also
*Extra CalDAV properties* below).

The Rust → Kotlin/Swift bridge is handled automatically
by [uniffi-kotlin-multiplatform-bindings](https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings)
+ [UniFFI](https://mozilla.github.io/uniffi-rs/) — no manual JNI, cinterop, or JSON serialization needed.

```
Kotlin/Swift  ←──UniFFI bindings──→  Rust lib.rs  →  fast-dav-rs (CalDAV)  →  icalendar (parsing)
```

Rust compilation and binding generation are integrated into the Gradle build via the single `ch.ubique.uniffi.plugin`
plugin. No separate build step is required — just run `./gradlew assembleAndroidMain`.

> ℹ️ The Android ABIs shipped are `arm64-v8a`, `armeabi-v7a` and `x86_64`. The 32-bit `x86` ABI is **not** supported by the
> plugin; it only concerns long-obsolete 32-bit emulators.

### NDK handling

Android 15+ requires shared libraries to be aligned on 16 KB pages, which needs NDK r28 (30.x) or newer.

`com.android.kotlin.multiplatform.library` exposes no `ndkVersion` of its own — AGP 9 removed it from `CommonExtension` —
so the NDK used to cross-compile Rust is the one given to `cargo { ndkVersion = ... }`. That plugin does **not** fail when
the requested version is missing: it silently falls back to the newest installed NDK, so a machine without a 30.x NDK
would build unaligned libraries and still report a successful build.

The `ensure-ndk-version` convention plugin (`build-logic/plugins/.../ndk`) closes that hole:

```kotlin
ensureNdkVersion {
    minimumVersion = "30.0.14904198"
}

cargo {
    ndkVersion = ensureNdkVersion.resolvedVersion
}
```

`minimumVersion` is a **minimum**, not a pin: a newer installed NDK satisfies it and is reused, and only when none
qualifies is the declared version downloaded via `sdkmanager`. Wiring `resolvedVersion` into `cargo` is what makes the
check effective — repeating the version as a literal there would reintroduce the silent fallback.

Run `./gradlew :CalendarKmpDav:ensureNdkVersion` to check or provision the NDK on its own. Automatic installation needs
the *Android SDK Command-line Tools* (*Settings > Android SDK > SDK Tools*); without them the build explains how to
install the NDK manually.

### Async & cancellation

All network exports are `async fn` on the Rust side, exported with
`#[uniffi::export(async_runtime = "tokio")]`. UniFFI generates Kotlin `suspend fun`
bindings that carry `@Throws(CaldavException, CancellationException)`, so cancelling
the calling coroutine drops the Rust future, which drops the underlying reqwest
request.

- **Reads** (`discover`, `getEvents`, `getEventsInRange`, `syncCollection`,
  `getEventsByUrls`) are fully cancellable and safe to cancel at any time.
- **Writes** (`updateCalendar`, `createEvent`, `updateEvent`, `deleteEvent`) are best-effort cancellable:
  cancelling after the request has been dispatched to the server leaves the outcome
  **undefined** (the server may or may not have applied the change). Callers must
  re-sync to reconcile.
- **Pure-CPU exports** (`patchEventIcs`, `buildEventIcs`, `setParticipationStatus`) stay synchronous and are
  wrapped in `withContext(Dispatchers.Default)` inside `RustCaldavBridge` so they
  never block the caller's thread.

### Extra CalDAV properties

`list_calendars` does not request the `current-user-privilege-set` (RFC 3744), mapped to a `CalendarAccessLevel`: the
crate issues its own `Depth: 1` PROPFIND on the home-set and reads it with fast-dav's multistatus parser (see
`rust/caldav_bridge/src/props.rs`). This request is **best-effort**: when it fails, the calendars default to read-write.

Discovery also reads the principal's `calendar-user-address-set` (RFC 6638) through fast-dav's
`discover_schedule_endpoints` (`rust/caldav_bridge/src/principal.rs`). Only its `mailto:` entries are kept, without their
scheme: they are the user's emails, matched against the attendees of an event. This request is best-effort: when it fails,
discovery goes on without the emails.

### Rust build profiles & binary size

The Rust artifacts are **huge in debug and small in release** — always compare like-for-like:

| Artifact                         | Debug     | Release        |
|----------------------------------|-----------|----------------|
| Android `.so` (per ABI, shipped) | ~66–77 MB | **~3.4–4.9 MB** |
| Apple `.a` (per slice)           | ~140 MB   | **~14 MB**     |

The `.a` static archive is **never shipped**: only the linked, stripped `.so` (Android) or the framework binary (Apple) goes
into the app. The release profile (`lto`, `opt-level = "s"`, `strip`) is configured in
`CalendarKmpDav/rust/caldav_bridge/Cargo.toml`.

> ⚠️ Do **not** set `panic = "abort"`: UniFFI relies on catching Rust panics to convert them into FFI errors; aborting would
> crash the app instead.

**Profile selection:**

The Cargo profile is chosen by a single Gradle property, `releaseBuild`, which applies to every target (Android and
Apple alike). It defaults to `true` in `gradle.properties`, because a debug build weighs ~77 MB per Android ABI and
~140 MB for the Apple static lib, against ~4 MB / ~14 MB in release. When iterating on the crate itself, pass
`-PreleaseBuild=false` for much faster Rust rebuilds and native debug symbols.

```bash
# Fast local Rust rebuilds
./gradlew :CalendarCore:assembleMultiplatformCalendarDebugXCFramework -PreleaseBuild=false
```

#### Stripping

`strip = true` in the Cargo release profile only pays off on **Android**, where the shipped artifact is the `cdylib`
itself: it drops the `.so` symbol table while keeping the dynamic export table, so UniFFI's symbol lookups still resolve
(AAR: 8.0 MB → 6.5 MB). On **Apple** it changes nothing, because the shipped artifact is not the crate's own output —
the `.a` must keep its symbols to be linked into the framework. This is safe for binding generation too: the Ubique
plugin builds a *separate host library* (`buildLibraryForBindings`) that uniffi-bindgen reads, so the per-target
artifacts never need to be introspected.

The Apple framework is **dynamic** (`isStatic` not set), and the Rust `.a` is linked *into* it. Once that link is done
the ~34k local symbols coming from the static archive are dead weight — they inflate `__LINKEDIT` to ~5 MB. `buildRelease`
therefore runs `strip -x` on each XCFramework slice before zipping, which drops the local symbols while preserving the
global ones needed for linking and for dyld at runtime:

| Per slice (iosArm64) | Before      | After      |
|----------------------|-------------|------------|
| Binary               | 19.3 MB     | **15.0 MB** |
| `__LINKEDIT`         | 5.0 MB      | **737 KB**  |
| Symbols              | 58,713      | **6,115**   |

> ℹ️ `strip -S` (drop DWARF) is deliberately **not** used: Kotlin/Native already sequesters the debug info into the
> companion `.dSYM`, so it is a no-op here. The `.dSYM` bundles are kept in the zip and their UUIDs still match the
> stripped binaries, so crashes remain symbolicable.

#### Packaging

`buildRelease` archives with `zip -r --symlinks`. The flag is **required**: a macOS framework bundle exposes its binary
as `Versions/A/<name>` plus two symlinks (`Versions/Current` and the top-level one), and without `--symlinks` `zip`
dereferences them and stores the 14 MB binary three times (zip: 45 MB → 32 MB).

> ⚠️ The zip published by CI is **not** produced by `buildRelease` but by the reusable workflow
> `Infomaniak/.github/.github/workflows/kmp-build-xcframework.yml`, so any packaging change must be mirrored there.

## Build Commands

```bash
# Build the MultiplatformCalendar XCFramework (iOS/macOS) — release Rust (see "Rust build profiles")
./gradlew :CalendarCore:assembleMultiplatformCalendarReleaseXCFramework -PreleaseBuild=true

# Build & zip for iOS release (updates `Package.swift` checksums when the file exists)
./buildRelease <version>

# Build the Android library (single variant AAR)
./gradlew assembleAndroidMain

# Run unit tests
./gradlew :CalendarCore:allTests

# Targeted recurrence tests: Android host (Robolectric), macOS (including real Compose resources), iOS Simulator
./gradlew :CalendarCore:testAndroidHostTest :CalendarCore:macosArm64Test :CalendarCore:iosSimulatorArm64Test

# Clean
./gradlew clean
```

## Contributing

If you see a bug or an enhancement point, feel free to create an issue, so that we can discuss it. Once approved, we or you (
depending on the priority of the bug/improvement) will take care of the issue and apply a merge request. Please, don't do a merge
request before creating an issue.

## License

This project is under GPLv3 license. See the LICENSE file for more details.
