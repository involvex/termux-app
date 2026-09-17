# InVxTermux

[![Build status](https://github.com/involvex/termux-app/actions/workflows/debug_build.yml/badge.svg)](https://github.com/involvex/termux-app/actions/workflows/debug_build.yml)
[![Unit tests](https://github.com/involvex/termux-app/actions/workflows/run_tests.yml/badge.svg)](https://github.com/involvex/termux-app/actions/workflows/run_tests.yml)
[![Docs](https://img.shields.io/badge/docs-GitHub%20Pages-00FF41?style=flat&labelColor=000000)](https://involvex.github.io/termux-app/)

**InVxTermux** is an unofficial Android terminal fork focused on a **phone + PC**
dev workflow: clone under `~/repos`, run Bun / Node projects, preview localhost
servers in-app, and optionally attach an AI CLI.

Package id: `com.involvex.termux_app` · Latest: **v0.202.0** · Docs:
[involvex.github.io/termux-app](https://involvex.github.io/termux-app/)

> **Unofficial fork.** Based on [Termux](https://termux.dev) /
> [`termux/termux-app`](https://github.com/termux/termux-app) (GPLv3).
> Not affiliated with the Termux maintainers. Packages installable *inside* the
> environment still come from
> [`termux/termux-packages`](https://github.com/termux/termux-packages).

## Features (this fork)

- **apt / OpenSSH** — path redirector maps hardcoded `/data/data/com.termux` to this package
- **Bun 1.4.2 (Android)** — embedded; `$PREFIX/libexec/bun` + `$PREFIX/bin/bun` shim (seccomp + `--os=android`)
- **Default cwd** — `~/repos` (executable). Prefer over `~/storage/shared` (**noexec**)
- **Localhost Preview** — drawer → **Preview** (port scan / chips / Copy LAN)
- **OpenCode helper** — `opencode-setup` then `td-ai` (web UI on `:4096`) → Preview
- **Workflow helpers** — `td-scaffold`, `td-dev`, `td-clone`, customizable drawer quick bar
- **Widget scripts** — `~/.shortcuts` templates (clipboard / OCR / git / API helpers / `td-ai`); Settings + drawer picker
- **Hacker theme** — matrix green on black (launcher + terminal)

Do **not** run `curl -fsSL https://bun.sh/install | bash` inside this app — that
installs a glibc Linux binary and fails with signal 31 / “required file not found”.

Product roadmap: [ROADMAP.md](ROADMAP.md) · Agent notes: [AGENTS.md](AGENTS.md)

## Installation

Install **only** from this repository’s
[GitHub Releases](https://github.com/involvex/termux-app/releases)
(first release: **v0.200.0**).

For Android 7+, use the `apt-android-7` APK matching your ABI (most phones:
`arm64-v8a` or `universal`).

**Signing note:** Release APKs from CI use the public Termux-style **test key**.
Anyone can build APKs with the same key — only install from
[`involvex/termux-app`](https://github.com/involvex/termux-app) if you trust that
source. Uninstall any other Termux / InVxTermux builds before switching sources
(`sharedUserId` + signature must match).

### Plugins (API / Widget)

- **Termux:API** (`com.involvex.termux_app.api`) is built from `:termux-api` in this
  repo. After installing that APK, run `pkg install termux-api` for shell CLIs
  (`termux-clipboard-get`, `termux-tts-speak`, …).
- **Termux:Widget** (`com.involvex.termux_app.widget`) — `:termux-widget`
  for classic `~/.shortcuts` one-tap scripts (same signature / `sharedUserId`).
  Stock F-Droid `com.termux.widget` will **not** install beside InVxTermux.
- **InVx Terminal Widget** (`com.involvex.termux_app.terminalwidget`) —
  `:termux-terminal-widget` (command-output home widget). Grant **RUN_COMMAND**,
  open the app once, then add the widget. Not the same as classic Termux:Widget.
- The app seeds `~/.shortcuts` templates on launch; manage via
  **Settings → Widget scripts** or the right-drawer **Widget scripts** button.
  Details: [docs/install.md](docs/install.md#plugins-api--widget).

This fork is **not** published on F-Droid or Google Play. Official Termux builds
from other sources are a different app id / signature and will not share data
with InVxTermux.

### After install

```bash
cd ~/repos
td-scaffold myapp react   # or: drawer → New…
td-dev                    # then drawer → Preview → Scan
```

```bash
td-ai                     # OpenCode on :4096 → Preview
```

## Building

Requires JDK 17, Android SDK (compile SDK 36), NDK `29.0.14206865`.

```bash
./gradlew assembleDebug          # Windows: gradlew.bat
./gradlew test
./gradlew :app:assembleDebug     # after Bun/bootstrap changes
```

## License

GPLv3 (with exceptions noted in [LICENSE.md](LICENSE.md)).
InVxTermux is a fork of [`termux/termux-app`](https://github.com/termux/termux-app).

Terminal emulator core components are Apache 2.0 — see `terminal-view` /
`terminal-emulator`. Shared library exceptions:
[`termux-shared/LICENSE.md`](termux-shared/LICENSE.md).

## Sponsors

Support InVxTermux development:

- [GitHub Sponsors](https://github.com/sponsors/involvex)
- [Buy Me a Coffee](https://buymeacoffee.com/involvex)
- [PayPal](https://paypal.me/involvex)
- [OpenCode](https://opencode.ai/go?ref=XS9FHCZT4C)
- [Microsoft Rewards](https://rewards.bing.com/welcome?rh=14525F68&ref=rafsrchae&form=ML2XE3&OCID=ML2XE3&PUBL=RewardsDO&CREA=ML2XE3)

## Upstream Termux

- App: https://github.com/termux/termux-app
- Packages: https://github.com/termux/termux-packages
- Wiki: https://wiki.termux.com
- Security (upstream): https://termux.dev/security

For **this fork**, open issues at
https://github.com/involvex/termux-app/issues and see [SECURITY.md](SECURITY.md).
