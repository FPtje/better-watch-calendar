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

[nix-direnv]: https://github.com/nix-community/nix-direnv
