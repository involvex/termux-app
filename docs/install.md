# Install

Install **only** from this fork’s
[GitHub Releases](https://github.com/involvex/termux-app/releases)
(first release: **v0.200.0**).

## Which APK?

For Android **7+**, pick an `apt-android-7` build:

| File fragment | Use when |
|---|---|
| `arm64-v8a` | Most modern phones |
| `armeabi-v7a` | Older 32-bit ARM |
| `x86_64` / `x86` | Emulators |
| `universal` | One APK for all ABIs (larger) |

Ignore `apt-android-5` unless you are on Android 5/6 (no package updates).

## Signing note

CI attaches **debug** APKs signed with a **public test key**. Anyone can forge
updates with that key — only install from
[`involvex/termux-app`](https://github.com/involvex/termux-app) if you trust that
source.

Before switching from another Termux / InVxTermux build:

1. Back up if needed ([Termux wiki: Backing up](https://wiki.termux.com/wiki/Backing_up_Termux))
2. Uninstall **all** related apps that share the same `sharedUserId` / signature set
3. Install the new APK

## Not on F-Droid / Play

This fork is **not** published on F-Droid or Google Play. Official Termux from
other stores is a different package id and will not share data with InVxTermux.

## Plugins (API / Widget)

InVxTermux uses `sharedUserId` / package family `com.involvex.termux_app*`.
Plugins must be built for **this** package id and signed with the **same** key
as the main app.

| Plugin | Package id | In this repo |
|---|---|---|
| Termux:API | `com.involvex.termux_app.api` | `:termux-api` module (build/ship with releases) |
| Termux:Widget | `com.involvex.termux_app.widget` | `:termux-widget` — classic `~/.shortcuts` one-tap scripts |
| InVx Terminal Widget | `com.involvex.termux_app.terminalwidget` | `:termux-terminal-widget` — command-output home widget |

Clipboard / TTS from the shell needs both:

1. Termux:API APK installed (same signature)
2. `pkg install termux-api` (CLI scripts under `$PREFIX/bin`)

### Widget one-tap scripts

On first launch the app seeds templates under `~/.shortcuts` (and
`~/.shortcuts/tasks/`):

| Script | Role |
|---|---|
| `clipboard-speak` | `termux-clipboard-get` → `termux-tts-speak` |
| `clipboard-to-file` | Append clipboard to `~/repos/clipboard.txt` |
| `git-pull-repos` | `git pull --ff-only` in each `~/repos/*` git repo |
| `tasks/td-ai` | Background: start OpenCode via `td-ai` |

Manage them from **Settings → InVxTermux → Widget scripts** (install / reset +
API/Widget/`termux-api` status) or the right-drawer **Widget scripts** button
(install + **Run once** in the current session).

Install the **Termux:Widget** APK from `:termux-widget` (same signature /
`sharedUserId` as the main app). Stock F-Droid `com.termux.widget` will not
install. After install, add the home-screen widget and tap a script; open
Termux:Widget once and refresh if new files do not appear.

Build locally: `./gradlew :termux-widget:assembleDebug`

### InVx Terminal Widget (command output)

Separate APK from `:termux-terminal-widget` (based on gardockt’s Termux Terminal
Widget). Install beside InVxTermux, grant **RUN_COMMAND**, open the app once to
start the foreground service, then add the widget and set a shell command.
This does **not** replace classic Termux:Widget / `~/.shortcuts`.

Build locally: `./gradlew :termux-terminal-widget:assembleDebug`

## Build from source

JDK 17, Android SDK (compile 36), NDK `29.0.14206865`:

```bash
./gradlew assembleDebug
```

See [README](https://github.com/involvex/termux-app#building) and `AGENTS.md` in
the repo.
