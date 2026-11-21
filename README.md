# Better Watch Calendar

A Wear OS complication that answers one question: **what is my next meeting?** The phone reads the
Android calendar database, and the watch shows a snapshot pushed over the Data Layer. See
[SPEC.md](SPEC.md) for the full design.

## Setup

Only **Nix** (with flakes) and **direnv** need to be installed. [nix-direnv] is recommended because
it caches the shell.

```sh
cd better-watch-calendar
direnv allow                       # loads JDK 25, Android SDK, Studio, ktfmt, just
cp .envrc.local.example .envrc.local   # fill in device serials (see `just devices`)
just keystore                      # shared debug signing key, kept outside the repo
```

`.envrc` rewrites `local.properties` on every load, so `sdk.dir` always points at the current Nix
SDK.

### Devices

Both devices use wireless debugging (SPEC §7.2):

```sh
just pair <ip:port>       # enter the pairing code shown on the device
just connect <ip:port>
just devices
```

## Run

```sh
just install          # build and install on phone + watch
just log-phone        # logcat for the app (also log-watch)
just test             # unit tests
just fmt / just lint  # ktfmt, detekt, Android Lint
just studio           # Android Studio, launched with the shell's JDK and SDK
```

Launch Studio from the shell (`just studio`) so it sees `JAVA_HOME` and `ANDROID_HOME`. The
project's Gradle JDK is set to `JAVA_HOME` in `.idea/gradle.xml`.

## Architecture

```
:shared   Pure domain logic: snapshot schema, select(), filtering, cleaning, formatting
:mobile   Phone: CalendarContract reader → SyncWorker → Data Layer sender, settings UI
:wear     Watch: Data Layer receiver → DataStore → timeline complication + detail view
```

The phone does all reading and cleaning. The watch only picks which meeting to show at the current
time, so it keeps working from the last snapshot when the phone is out of range.

`:shared` is plain Kotlin/JVM and holds nearly all logic as pure functions, each with its own test
spec: `buildReport` (skip rules, dedup, room, attendees), `cleanDescription`, `select`,
`complicationContent` / `complicationTransitions`, the detail-view formatters, and `fitToBudget`.
The apps only map Android types in and out.

On the phone, `CalendarReader` maps `CalendarContract` cursors to `:shared` types, and `SyncWorker`
(WorkManager: app open, toggle changes, *Sync now*, every 15 minutes, and a content-URI trigger on
the calendar database) runs `sync()`. The content-URI trigger fires once per request, so each run
arms the next one, alternating between two unique work names so changes made during a sync are not
missed. *Sync now* also calls `ContentResolver.requestSync` for each Google account the user has
picked in the account chooser (Android ignores it for accounts the app can't see). That reads
the included calendars, builds the snapshot, sends it, and appends to the debug log (DataStore, last
100 attempts). Calendar toggles are stored only for calendars the user changed, so new calendars get
the default: on for each account's own calendar, and only if it is synced.
`fitToBudget` keeps the snapshot under 80 KB by stripping detail from the latest meetings first.

On the watch, `ScheduleListenerService` decodes each pushed snapshot into `SnapshotStore`
(DataStore) and asks the complication to update. Opening the watch app also reads the current
DataItem in case a push was missed. `NextMeetingComplicationService` returns a 24-hour timeline
built by `complicationTimeline` (one entry per state change: starts, the end of each 10-minute "now"
phase, midnights, and the snapshot's window end), so the watch face changes state on time without
waking the app. Time zone, clock, and locale changes rebuild the timeline, and an hourly update acts
as a safety net. Tapping the complication opens `MainActivity`, the detail view: `detailContent`
picks the same meeting plus the one after it, and `DetailScreen` lays them out in a rotary-scrollable
`TransformingLazyColumn` with a sync footer that warns when the snapshot is over an hour old. To use
it, long-press the watch face → *Edit* → pick a complication slot → *Better
Watch Calendar* → *Next meeting*.

[nix-direnv]: https://github.com/nix-community/nix-direnv
