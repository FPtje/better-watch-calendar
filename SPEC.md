# Better Watch Calendar — Specification

A narrowly scoped calendar for Wear OS. It answers one question at a glance: **what is my next meeting?**

- **Target devices:** Pixel Watch 2 (Wear OS) and Pixel 8 Pro (Android).
- **Distribution:** personal sideload only.
- **Status:** draft, 2026-10-19.

---

## 1. Goals and non-goals

### Goals

1. A **short-text complication** that shows the start time and name of the next meeting. For the first 10 minutes of a meeting, it shows that meeting instead.
2. Tapping the complication opens a **detail view** with two meetings: the one the complication shows, and the one after it. Each meeting shows its name, time, room, description, and (nice to have) attendees.
3. Calendar data comes **only from the phone**. The watch never talks to Google or uses the watch's own Google account.
4. The phone app supports **multiple Google accounts at the same time**.
5. A reproducible dev environment: a Nix flake with direnv, and no global installs apart from Nix and direnv.

### Non-goals

- Creating, editing, or RSVPing to events.
- Non-Google calendars (Exchange, iCloud, CalDAV), even if they appear in the phone's calendar database.
- Tiles, notifications, and reminders. Pixel already handles reminders.
- Play Store release.
- Standalone watch operation, meaning the watch fetching data without the phone.
- Talking to Google's servers directly. There is no OAuth, no Google Cloud project, and no network code.

---

## 2. System overview

```
┌──────────────────── Pixel 8 Pro ────────────────────┐        ┌──────────── Pixel Watch 2 ────────────┐
│                                                     │        │                                       │
│  Android account sync (Google, first-party)         │        │  DataLayer listener                   │
│            │ keeps up to date                       │        │        │                              │
│            ▼                                        │        │        ▼                              │
│  CalendarContract (system calendar database)        │        │  SnapshotStore (DataStore)            │
│            │ READ_CALENDAR                          │        │        │                              │
│            ▼                                        │        │        ├──► Complication service      │
│  SyncWorker (on DB change + every 15 min + manual)  │ Data   │        │    (timeline, SHORT_TEXT)    │
│   ├─ query Instances + Attendees (7 days)           │ Layer  │        │                              │
│   ├─ filter / normalize / clean descriptions        │  API   │        └──► Detail activity (Compose) │
│   └─ build ScheduleSnapshot ────────────────────────┼───────►│                                       │
│                                                     │        │                                       │
│  Phone UI: calendar toggles, sync, debug log        │        │                                       │
└─────────────────────────────────────────────────────┘        └───────────────────────────────────────┘
```

The phone reads the **Android calendar database** (`CalendarContract`). This is the same mechanism apps like Business Calendar use. Google's own sync keeps the database up to date for every Google account added in Android Settings, so the app needs only the `READ_CALENDAR` permission. It never touches tokens or Google APIs.

The phone does all reading, filtering, and cleaning. It pushes a self-contained **snapshot** of the next 7 days to the watch. The watch only decides *which* meeting to show at the current time. Because of this, the complication stays correct when the phone is out of range, until the snapshot runs out of events.

---

## 3. Functional requirements

### 3.1 Phone app (companion)

| ID | Requirement |
|----|-------------|
| P1 | Request the `READ_CALENDAR` runtime permission on first launch. Without it, show an explanation screen with a button to grant it. |
| P2 | Accounts are managed in **Android Settings → Accounts**, not in the app. The app shows every calendar whose `ACCOUNT_TYPE == "com.google"`, grouped by `ACCOUNT_NAME`. Calendars from other account types are ignored. |
| P3 | Each calendar has an include toggle. New calendars default to **on** for each account's primary calendar (`OWNER_ACCOUNT == ACCOUNT_NAME`) and **off** for all others. Calendars with `SYNC_EVENTS == 0` are shown greyed out with the hint "Not synced. Enable it in Google Calendar settings". |
| P4 | Read event instances from included calendars in the window `[now − 1 h, now + 7 d]`. |
| P5 | Sync triggers: (a) a **content-URI-triggered** WorkManager job on `CalendarContract.CONTENT_URI` (descendants included), which fires within seconds of any calendar change, even when the app isn't running, and is re-enqueued after each run; (b) a periodic job every **15 minutes** as a safety net and to roll the 7-day window forward; (c) app open and toggle changes; (d) a **manual "Sync now" button**. |
| P6 | "Sync now" also calls `ContentResolver.requestSync` (manual + expedited) for each Google account on the calendar authority. This asks Google's sync to refresh from the server on a best-effort basis. The resulting database change then triggers (a). |
| P7 | After each successful read, push a `ScheduleSnapshot` to the watch (§5). |
| P8 | A **debug log screen** shows the last 100 sync attempts: timestamp, trigger, per-account event count, payload size, and errors. |

### 3.2 Reading, filtering, and normalization (phone)

**Query.** Read `CalendarContract.Instances` for the window. This table expands recurring events into individual occurrences. Select these columns:

- `EVENT_ID`, `BEGIN`, `END`
- `TITLE`, `EVENT_LOCATION`, `DESCRIPTION`
- `ALL_DAY`, `STATUS`, `SELF_ATTENDEE_STATUS`
- `CALENDAR_ID`, `UID_2445`

Then make one batched query on `CalendarContract.Attendees` for the resulting event IDs. Cursor reading sits in a thin imperative layer that maps rows into immutable `RawInstance` / `RawAttendee` values. Everything after that point is pure functions in `:shared`.

An instance is **skipped** if any of these is true:

- `STATUS == STATUS_CANCELED`
- `ALL_DAY == 1`
- `SELF_ATTENDEE_STATUS == ATTENDEE_STATUS_DECLINED`
- it has **no attendees** other than the user and room resources. This excludes focus time, out-of-office, working-location entries, personal blocks, and solo reminders. The user is the attendee whose email equals the calendar's `OWNER_ACCOUNT`, and resources are attendees with `ATTENDEE_TYPE == TYPE_RESOURCE`.

The same meeting can appear in more than one account or calendar, for example when an invite reaches both a work and a personal account. Such instances are **deduplicated** by `UID_2445` plus `BEGIN`, keeping the first occurrence. Accounts are ordered alphabetically by default, and the order can be changed in settings later if needed.

**Room** is resolved in this order:

1. The names of attendees with `ATTENDEE_TYPE == TYPE_RESOURCE` (`ATTENDEE_NAME`, falling back to the email address), joined with `", "`.
2. Otherwise, `EVENT_LOCATION`.
3. Otherwise, a conferencing label detected in the raw description: `meet.google.com` → "Google Meet", `zoom.us` → "Zoom", `teams.microsoft.com` → "Teams".
4. Otherwise, empty.

**Description** is cleaned as follows. Google sync may store HTML in this field.

1. Convert HTML to plain text: `<br>`, `<p>`, and `<li>` become newlines, all other tags are stripped, and entities are decoded.
2. Remove conferencing boilerplate:
   - the Google Calendar `-::~:~::~:~…` delimited block
   - "Join with Google Meet" / "Join by phone" blocks
   - "Join Zoom Meeting" blocks
   - the Microsoft Teams underscore-delimited block
3. Collapse runs of blank lines and trim.
4. Cap at 2,000 characters, adding `…` when truncated.

**Attendees** keeps each attendee's `ATTENDEE_NAME`, or the email address when there is no name. Resources are excluded and the user is excluded. The list is sorted with organizer first (`ATTENDEE_RELATIONSHIP == RELATIONSHIP_ORGANIZER`), then accepted, then alphabetical, and capped at 30 entries plus a "+N more" count.

### 3.3 Watch: selection rules (pure function)

`select(events: List<Meeting>, now: Instant): Selection`. The events are filtered and sorted by start time.

- **Current meeting:** a meeting where `start ≤ now < min(start + 10 min, end)`. If several qualify, the one with the **earliest start** wins.
- **Next meeting:** if there is no current meeting, the earliest meeting with `start > now`.
- **Shown meeting:** the current meeting if there is one, otherwise the next meeting.
- **Following meeting:** the first meeting in the sorted list after the shown one (by start, then end, then id). This shows up as the second card in the detail view.
- **Lookahead:** 7 days, limited to what the snapshot contains.

### 3.4 Watch: complication

- Type: **`SHORT_TEXT`** only. This could be extended to `LONG_TEXT` later at little cost.
- Content:

  | State | `text` (top, ~7 chars) | `title` (bottom, ~7 chars) |
  |---|---|---|
  | Next meeting today | `10:30` (follows the system 12/24 h setting) | meeting name, truncated by the watch face |
  | Current meeting (first 10 min) | `now` | meeting name |
  | Next meeting on a later day | `Tue` (weekday abbreviation in the system locale) | meeting name |
  | Nothing in 7 days / empty snapshot | `—` | `free` |
  | No snapshot ever received | `—` | `sync` |

- `contentDescription` (screen reader) carries the full text, for example *"Next meeting: Design review at 10:30 in Orion"*.
- **Tap action** opens the detail activity.
- **Updates:** use a **timeline complication** (`ComplicationDataTimeline`) with an entry for every state transition (each `start` and `start + 10 min`) for the next 24 hours. The watch face then switches state on time without waking the app. The timeline is rebuilt whenever a new snapshot arrives (via `ComplicationDataSourceUpdateRequester`), on time zone or time changes, and periodically with `UPDATE_PERIOD_SECONDS = 3600` as a safety net.

### 3.5 Watch: detail view

A scrolling Compose for Wear OS screen (`TransformingLazyColumn`, rotary input supported):

1. **Shown meeting card**
   - Name (up to 3 lines).
   - Time: `10:30–11:15`, with a weekday prefix if the meeting is not today, and a "now" badge if it is current.
   - Room, if present.
   - Description: the cleaned plain text, shown in full.
   - Attendees (nice to have): a list at the bottom of the card.
2. **Following meeting card** in the same format.
3. **Footer:** `Synced 14:02`. If the snapshot is more than 1 hour old, show a warning: *"Phone not synced for 3 h"*.

Empty states: "No meetings in the next 7 days" and "Waiting for phone… open Better Watch Calendar on your phone".

The staleness indicator appears **only** in the detail view, never on the complication.

---

## 4. Data access and permissions

- **Source:** `android.provider.CalendarContract`, read through `ContentResolver`. Only `READ_CALENDAR` is required. The app never writes to the database.
- **Accounts:** any Google account added to the phone (Android Settings → Accounts) with calendar sync enabled. This includes the business account, which is in the main profile, not a work profile. Adding or removing an account needs no app changes. Its calendars appear in or disappear from the calendar list automatically.
- **Freshness:** Google's sync adapter receives push notifications from Google, so database changes usually land within seconds to minutes. The content-URI trigger (P5a) forwards them to the watch right away.
- **What the database does not provide:**
  - Google's `eventType` (focus time, out-of-office, working location). The no-other-attendees filter covers this in practice.
  - Structured conference data. Detection from the description text covers this (§3.2).
- **Typed errors** at this boundary:

  ```kotlin
  sealed interface ReadError {
      data object PermissionDenied : ReadError
      data object NoGoogleCalendars : ReadError
      data class ProviderFailure(val message: String) : ReadError  // null cursor, SecurityException
  }
  ```

---

## 5. Phone ↔ watch protocol

- **Transport:** Wearable Data Layer API, `DataClient`. Both APKs share the **same `applicationId` and signing key**, as the Data Layer requires.
- **Path:** `/schedule/v1`, a single `DataItem` that is overwritten on each sync and sent with `setUrgent()`. The Data Layer drops unchanged items by itself, so identical syncs cost nothing. The many calendar-change triggers are therefore cheap.
- **Encoding:** `kotlinx.serialization` with **ProtoBuf**. The `@Serializable` types live in the `:shared` module and are used by both apps.

```kotlin
@Serializable data class ScheduleSnapshot(
    val schemaVersion: Int,            // bump on breaking change; watch ignores unknown versions
    val syncedAt: Long,                // epoch millis
    val windowEnd: Long,               // epoch millis; selection beyond this is "unknown", not "free"
    val meetings: List<Meeting>,       // filtered, deduped, sorted by start
)

@Serializable data class Meeting(
    val id: String,                    // "<calendarId>:<eventId>:<begin>" (unique per instance)
    val title: String,
    val start: Long,                   // epoch millis
    val end: Long,
    val room: String,                  // "" when absent
    val description: String,           // cleaned plain text, ≤ 2000 chars
    val attendees: List<String>,       // ≤ 30
    val moreAttendees: Int,
)
```

- **Size budget:** DataItems are limited to 100 KB, so the target is ≤ 80 KB. If the payload is too large, drop descriptions and attendees from the **latest** meetings first until it fits. The next three meetings always keep full detail.
- **Watch side:** a `WearableListenerService` filtered on `/schedule/v1` persists the decoded snapshot to DataStore and requests a complication update. When the app starts, it also reads the current DataItem through `DataClient.getDataItems` in case it missed an event.
- **No network on either side:**
  - The watch APK **declares no `INTERNET` permission**. This enforces the "no direct Google access" requirement at the manifest level. The Data Layer runs through Play Services and does not need it.
  - The phone APK doesn't need `INTERNET` either.
- The watch manifest sets `com.google.android.wearable.standalone = false`.

---

## 6. Architecture and code conventions

### 6.1 Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.4 (K2 compiler), JDK 25 toolchain (see §6.4) |
| UI | Jetpack Compose (phone, Material 3); Compose for Wear OS (Material 3 for Wear) |
| Typed errors / FP | **Arrow** (`arrow-core`: `Either`, `Raise` DSL, `NonEmptyList`) |
| Calendar access | `ContentResolver` + `CalendarContract`, wrapped in a small cursor-to-data-class layer |
| Serialization (Data Layer) | `kotlinx.serialization` ProtoBuf |
| Persistence | Jetpack DataStore (calendar toggles, debug log, watch snapshot) |
| Background | WorkManager (`CoroutineWorker`, content-URI trigger + periodic) |
| Concurrency | Coroutines + Flow |
| DI | None. Manual wiring through a small `AppGraph` object per app. |
| Build | Gradle 9 (Kotlin DSL) + AGP 9 with built-in Kotlin + version catalog (`gradle/libs.versions.toml`) |
| Tests | Kotest 6 (JUnit Platform runner), including property tests for selection and cleaning |
| Lint/format | ktfmt (Google style) + detekt 2 + Android Lint |

### 6.2 Modules

```
:shared   Pure Kotlin/Android library. Domain types, snapshot schema, select(),
          filtering/dedup, description cleaning, formatting. No Android UI,
          no ContentResolver. Nearly all logic and nearly all tests live here.
:mobile   Phone app. CalendarContract reader, SyncWorker, Data Layer sender, UI.
:wear     Watch app. Data Layer receiver, complication service, detail activity.
```

### 6.3 Style

- **Functional core, imperative shell.** Domain logic consists of pure functions over immutable `data class` and `sealed interface` types. Side effects (cursors, Data Layer, DataStore, clock) sit at the edges and are passed in as parameters. Time comes in as a `Clock` or `Instant`, never `Instant.now()` inside logic.
- Expected failures are modeled as sealed error types (for example `ReadError`, §4) returned through `Either` / `Raise`. Exceptions are reserved for programmer errors.
- Classes are allowed where Android requires them (services, workers, activities). They stay thin and delegate to functions.
- Prefer value classes for identifiers (`@JvmInline value class CalendarId(val value: Long)`).
- **Documentation:** a one-line KDoc on every public declaration whose name doesn't say everything, plus a concise `README.md` covering setup, run, and architecture in a page. Comments explain *why*, not *what*.

### 6.4 Tool and SDK versions

Baseline as of 2026-10. Each entry is the current stable release or newer. Exact versions are pinned in `flake.lock` and `gradle/libs.versions.toml` at M0. Use the newest stable patch available then.

| Tool | Version | Notes |
|---|---|---|
| JDK | **25 LTS** (nixpkgs `jdk25`, 25.0.4.1) | Runs Gradle, Kotlin, and Studio's Gradle builds. Chosen over the newer non-LTS JDK 27 because Gradle and Kotlin support for new JDKs lags behind their release. |
| Gradle | **9.6+** via the wrapper | AGP 9.4 requires at least 9.6.0. The wrapper is set to the latest 9.x at M0. |
| Android Gradle Plugin | **9.4.x** | Uses AGP 9's built-in Kotlin support, so modules don't apply `org.jetbrains.kotlin.android`. |
| Kotlin | **2.4.20** | Plus the matching Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) and serialization plugin. |
| `compileSdk` / `targetSdk` | **37** (Android 17) | The same for both apps. |
| `minSdk` | **36** for both `:mobile` and `:wear` | Only your own devices need support: the Pixel 8 Pro on Android 16+ and the Pixel Watch 2 on Wear OS 6, which is Android 16 / API 36. This avoids compatibility code for older APIs. Check with `adb shell getprop ro.build.version.sdk`. |
| Bytecode target | The highest Java level AGP 9.4's D8 accepts | Gradle runs on `jvmToolchain(25)`. Android code compiles to the highest Java bytecode level D8 supports, confirmed at M0, falling back to 21. `:shared` uses the same target. |
| Android Studio | **2026.2.1** (nixpkgs `android-studio`) | Its Gradle JDK setting points at `JAVA_HOME` (the flake JDK 25), not the bundled JBR. |
| ktfmt | **0.63** | From nixpkgs, with a Gradle task that wraps it. |
| detekt | **2.0.0-alpha** (`dev.detekt` Gradle plugin) | detekt 1.23.x is built against Kotlin 2.0 and doesn't support Kotlin 2.4. The 2.0 alphas add Kotlin 2.4 support. Switch to 2.0 stable when it ships. If an alpha breaks the build, drop detekt and rely on Android Lint. |
| Libraries | Latest stable at M0 | Compose BOM, Compose for Wear OS Material 3, `androidx.wear.watchface:watchface-complications-data-source-ktx`, Play Services Wearable, WorkManager, DataStore, Arrow 2.x, kotlinx.serialization, Kotest 6.x. |

---

## 7. Development environment (Nix)

### 7.1 Requirements

- `flake.nix` + `flake.lock` + `.envrc` (`use flake`). Entering the directory provides everything.
- Nothing is installed globally except **Nix** (flakes enabled) and **direnv**, with nix-direnv recommended for caching.
- The devShell provides:
  - **JDK 25** (`jdk25`). This takes precedence over the system JDK (currently 27) inside the shell.
  - an **Android SDK** composed declaratively via [`android-nixpkgs`](https://github.com/tadfisher/android-nixpkgs):
    - `platform-tools` (**adb**, fastboot)
    - `build-tools` (latest 37.x)
    - `platforms;android-37`
    - `cmdline-tools;latest`
    - `emulator`
    - system images for one phone image (API 37, x86_64) and one Wear OS 6 image (API 36, x86_64)
  - **Android Studio** from nixpkgs (`android-studio`, unfree). It points at the Nix SDK through `ANDROID_HOME`.
  - `ktfmt`, `just`. detekt runs through its Gradle plugin, so it isn't in the shell.
- Environment variables: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `JAVA_HOME`.
- Gradle comes from the **Gradle wrapper** checked into the repo. The flake JDK runs it.
- `allowUnfree` and Android SDK license acceptance are scoped to the flake's `pkgs` import, not set globally.
- Real files: `.envrc`, `flake.nix`, `flake.lock`. `.direnv/`, `local.properties`, `build/`, and `.gradle/` are gitignored.

### 7.2 Device connection

The Pixel Watch 2 has no USB data connection, so both devices use **wireless debugging**.

| Device | Steps |
|---|---|
| Pixel 8 Pro | USB (`adb devices`), or Developer options → Wireless debugging → *Pair device with pairing code* → `adb pair <ip>:<port>` then `adb connect <ip>:<port>` |
| Pixel Watch 2 | Settings → System → About → tap *Build number* 7× → Developer options → *ADB debugging* + *Wireless debugging* → *Pair new device* → `adb pair <ip>:<port> <code>` → `adb connect <ip>:<port>` |

USB access on CachyOS may need udev rules (the `android-udev` package or equivalent). This is the one system-level change Nix can't make. It's not needed if both devices use wireless debugging.

### 7.3 `justfile` tasks

```
just devices          # adb devices -l
just pair <ip:port>   # adb pair
just connect <ip:port>
just install-phone    # ./gradlew :mobile:installDebug   (targets $PHONE_SERIAL)
just install-watch    # ./gradlew :wear:installDebug     (targets $WATCH_SERIAL)
just install          # both
just log-phone        # adb -s $PHONE_SERIAL logcat --pid=$(pidof app)
just log-watch
just test             # ./gradlew test
just fmt / just lint
```

Device serials go in an untracked `.envrc.local`, which `.envrc` sources if present.

### 7.4 Debug signing

A **project debug keystore** is kept outside the repo (path in `.envrc.local`). The phone and watch APKs must be signed with the same key for the Data Layer to connect them. A shared, stable keystore also lets you install from different machines or after Nix rebuilds without uninstalling first.

---

## 8. Testing strategy

- **Unit (in `:shared`, the bulk of it):**
  - `select()`: the 10-minute boundary, overlaps, meetings shorter than 10 minutes, back-to-back meetings, day rollover, and time zone/DST changes. Property tests cover invariants such as "shown meeting is never in the past unless current" and "following meeting is never the shown meeting".
  - Filtering and deduplication on hand-built `RawInstance` / `RawAttendee` fixtures (all-day, declined, solo, cancelled, resources, same `UID_2445` in two accounts).
  - Description cleaning and conference detection against real-world Meet, Zoom, and Teams invite fixtures.
  - Snapshot size trimming.
  - Complication text formatting for each state, in both 12 h and 24 h.
- **Integration (manual, on device):**
  - Personal and business calendars both appear.
  - Toggle calendars.
  - Edit a meeting in Google Calendar on the web and confirm the watch updates within a minute or so.
  - Airplane-mode the phone and confirm the watch keeps advancing through the snapshot.
  - Revoke `READ_CALENDAR` and confirm the permission screen appears.
- **Emulator:** a paired phone and Wear emulator from the flake for UI work without hardware. The emulator can add a test Google account, or you can insert local events via a debug-only seeding function.

---

## 9. Milestones

| # | Deliverable | Done when |
|---|---|---|
| M0 | Dev environment | `cd` into the repo loads the shell; `adb devices` lists phone + watch over Wi-Fi; Android Studio opens the project. |
| M1 | Skeleton | 3 modules build; phone sends a hard-coded snapshot; watch logs it. |
| M2 | Domain core | `select()`, filters, deduplication, cleaning, and formatting implemented and tested in `:shared`. |
| M3 | Calendar reading | Permission flow; calendars from both accounts listed with toggles; real events shown in the phone debug log. |
| M4 | Complication | Timeline complication on the real watch face shows correct states and transitions. |
| M5 | Detail view | Tap opens two meeting cards with room, description, attendees, and synced footer. |
| M6 | Polish | Content-URI trigger, "Sync now" with `requestSync`, size trimming, empty and stale states. |

---

## 10. Open questions and risks

1. **Short-text truncation.** The watch face decides how many title characters fit. Validate on your actual watch face; a `LONG_TEXT` variant may be worth adding later.
2. **"now" vs. start time.** While a meeting is current, the complication shows `now`. An alternative is the end time (`→11:15`). Revisit after daily use.
3. **Wear OS version.** The Pixel Watch 2 runs Wear OS 6 (Android 16, API 36), and the spec assumes `minSdk 36`. Check the actual API level on the watch during M0.
4. **Room resource names.** Check whether booked rooms appear as `TYPE_RESOURCE` attendees in the phone's calendar database for your business account. If they don't, the room falls back to `EVENT_LOCATION`, which Google usually fills with the room name anyway.
5. **Bleeding-edge toolchain.** detekt 2 is still alpha, and the Java bytecode level AGP's D8 accepts may be below 25. Both have fallbacks in §6.4.
