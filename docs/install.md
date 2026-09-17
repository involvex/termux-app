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
| `screen-ocr` | Capture/OCR screenshot → clipboard (`td-screen-ocr`, needs `tesseract`) |
| `camera-photo` | `termux-camera-photo` → `~/repos/camera-last.jpg` (optional catalog) |
| `wifi-info` | `termux-wifi-connectioninfo` → clipboard + toast (optional) |
| `battery-status` | `termux-battery-status` → clipboard + toast (optional) |
| `torch-toggle` | `termux-torch` on/off (optional) |
| `share-clipboard` | Clipboard → `termux-share` (optional) |
| `open-settings` | `am start` Android Settings (optional) |
| `vibrate` | `termux-vibrate` short pulse (optional) |
| `volume-info` | `termux-volume` → clipboard (optional) |
| `location` | `termux-location` → clipboard + toast (optional) |
| `telephony-info` | `termux-telephony-deviceinfo` → clipboard (optional) |
| `stop-ai` | Stop OpenCode on `:4096` (optional) |
| `tasks/td-ai` | Background: start OpenCode via `td-ai` (skips if already healthy) |

Defaults seed on first launch; optional scripts install via the Widget scripts
picker.

`screen-ocr` / `td-screen-ocr` prefers `termux-screenshot` (Termux:API
MediaProjection consent dialog, then PNG under `~/repos/screen-ocr/`), then
falls back to the newest image under `~/storage/.../Screenshots` (run
`termux-setup-storage` first). Install OCR with `pkg install tesseract`
(includes **eng** traineddata). Other languages: copy
`<lang>.traineddata` into `$PREFIX/share/tessdata` (see
https://github.com/tesseract-ocr/tessdata); override with `OCR_LANG=deu`.
Toasts hint when tesseract or the lang pack is missing. Termux:X11 is **not**
required (and does not help) for this flow.

Upstream-ready CLI twin for packages:
[`contrib/termux-api-package/scripts/termux-screenshot.in`](../contrib/termux-api-package/scripts/termux-screenshot.in).

Manage them from **Settings → InVxTermux → Widget scripts** (install / reset +
API/Widget/`termux-api` status) or the right-drawer **Widget scripts** button
(install + **Run once** in the current session).

Tapping a home-screen shortcut always shows a short **Running: name** toast.
Script start/end feedback uses `termux-toast` when Termux:API APK +
`pkg install termux-api` are present.

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
