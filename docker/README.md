# Docker / Podman container

The seed tree lives in [`app/src/main/assets/invapp/`](../../app/src/main/assets/invapp)
— the same assets the APK bundles — so there is one on-disk copy of the helpers,
config and shortcuts, not two. It is the container twin of the app's runtime
environment: the same tools, the same `$PREFIX/bin` helpers, the same configs, so
a project moves between phone and container without relearning anything.

The container installs these assets via `seed.sh`, which rewrites the
`#!@BASH@` shebangs at install time — easier to lint and test in CI than Java
string literals. On device, the same asset files are installed with
`writeExecutableAsset` (`ShellLibraryInstaller`, `ResourceScriptsInstaller`,
`TermuxHackerThemeInstaller`).

Note: the device `bin/bun`, `bin/bunx`, `bin/bun-doctor`, `bin/node` and
`opencode-setup` wrappers are still emitted from Java string literals in
`TermuxBunInstaller.java` (they bake in device-only preloading of
`libinvapp-bun-seccomp.so` / the path redirector). Migrating those to assets, so
there is one copy of *every* helper, is the remaining step and is gated on an
on-device validation pass — the glibc/`ld-linux` glue in `opencode-setup` cannot
be runtime-checked here.

## What is in here

```text
docker/
  README.md              this file
  docker-compose.yml     ready-to-use dev container definition
  invapp/
    seed.sh              runs once inside the image build (as the `system` user)
    env.sh               shared environment + arch capability table
    packages.list        base packages installed into every image
    packages-extra.list  optional/heavy packages (td-install-extras)
    bin/                 helpers installed into $PREFIX/bin
    libexec/             helpers used by bin/ (vite PWA writer)
    config/              ~/.termux files, profile.d snippet, completions
    shortcuts/           ~/.shortcuts templates (run by hand, no widget launcher)
```

## Build

The heavy lifting (bootstrap rootfs generation) lives in
[`involvex/termux-docker`](https://github.com/involvex/termux-docker), a fork of
upstream `termux/termux-docker`. This repo only owns the seed tree.

```sh
git clone https://github.com/involvex/termux-docker
git clone https://github.com/involvex/termux-app

cd termux-docker
INVAPP_SRC=../termux-app/app/src/main/assets/invapp ./generate.sh --invapp
```

`./generate.sh` without `--invapp` still produces a stock termux-docker image, so
tracking upstream stays easy.

Build knobs (all optional):

| Variable | Default | Effect |
|---|---|---|
| `TERMUX_ARCH` | host arch | `aarch64`, `arm`, `x86_64`, `i686` |
| `TERMUX_DOCKER__IMAGE_NAME` | `termux/termux-docker` | tag to build |
| `TERMUX_DOCKER__BOOTSTRAP_VERSION` | `2026.06.07-r1%2Bapt.android-7` | pin to the APK's `2026.02.12-r1%2Bapt.android-7` for exact parity |
| `TERMUX_DOCKER__INVAPP_BUN_VERSION` | `1.4.2` | bun release (matches the APK bundle) |
| `TERMUX_DOCKER__INVAPP_BUN_BASELINE` | `0` | `1` for CPUs without AVX2 |
| `TERMUX_DOCKER__INVAPP_WITH_AI` | `0` | `1` bakes OpenCode + Cursor Agent into the image |
| `INVAPP_SRC` | `../termux-app/app/src/main/assets/invapp` | seed tree location |

## Run

```sh
docker run -it ghcr.io/involvex/termux-docker:aarch64
docker run -it --rm -p 5173:5173 -p 4096:4096 ghcr.io/involvex/termux-docker:x86_64
td-info          # inside: what this image can and cannot do
```

Or with compose, working on the same repo as the phone:

```sh
docker compose up -it
```

Ports are published on the host side (`docker run -p`) — the app's Preview
WebView has no container equivalent. `td-dev` and `td-scaffold` already bind
`0.0.0.0` for exactly this reason.

## Parity, and where it ends

| Feature | Container | Notes |
|---|---|---|
| bun / bunx | aarch64 + x86_64 | bun publishes no armv7/386 build — the APK has the same limit (`TermuxBunInstaller.java:88`) |
| node | all | `nodejs-lts` in the base set; also the fallback on 32-bit |
| `td-dev` `td-scaffold` `td-clone` | yes | `td-scaffold` adds `vite-plugin-pwa` for `pwa` / `pwa-react` |
| `td-ai` `opencode-setup` `cursor-agent-setup` `td-upgrade` | yes | see below |
| `bun-doctor`, `td-info` | yes | diagnostics |
| `~/.termux/*`, completions, `~/.shortcuts` | yes | byte-identical colors asset |
| `termux-api` CLIs | installed, non-functional | intents need Android |
| `termux-screenshot`, `td-screen-ocr` | stubs | say why, and suggest X11/`tesseract` |
| `td-resource-linux` (proot) | dropped | the container already is a Linux rootfs |
| `td-jdk-setup`, `jdk-doctor` | `pkg install openjdk-17` | no SIGSYS/`libandroid-shmem` dance needed in a container |

### What changes inside a container, and why

The container is a Termux prefix (bionic, `com.termux` paths) *without* Android.
That makes three of the app's workarounds unnecessary and keeps the rest:

* **Dropped** — `libinvapp-redirector.so` (nothing to remap: the prefix already
  *is* `/data/data/com.termux`), `libinvapp-bun-seccomp.so` (Android's seccomp
  traps `openat2`; Docker has its own profile — see below), and
  `libinvapp-opencode-shim.so` (Docker provides `/etc/resolv.conf` and `/sys`).
* **Kept** — the APK's **android bun build**. `bun-linux-x64.zip` is a glibc
  binary whose ELF interpreter is `/lib64/ld-linux-x86-64.so.2`; that path does
  not exist in this rootfs, so it dies with `required file not found`. The
  container installs the same `bun-linux-*-android.zip` bytes the APK bundles,
  checksum-pinned against `app/build.gradle`, and the ELF interpreter
  `/system/bin/linker64` does exist (aosp-libs).
* **Kept** — `npm_config_platform=android` / bun `--os=android`. Termux packages
  are bionic-linked even in a container, so Linux/glibc natives cannot load.
  Dropping this is the classic "bun installs, then everything segfaults" trap.
* **Kept** — Termux `glibc` + `ld-linux --library-path` wrappers for OpenCode
  and Cursor Agent. Those are real glibc binaries; a bionic prefix cannot run
  them without the Termux glibc package.

### Bun + Docker seccomp

If bun reports `Bad system call` or an unknown syscall, Docker's default seccomp
profile is blocking `openat2`/`fchmodat2`:

```sh
docker run -it --rm --security-opt seccomp=unconfined ghcr.io/involvex/termux-docker:x86_64
```

This relaxes the host policy only. `bun-doctor` prints the same hint.

### `td-ai` server password

OpenCode V2 serves `/api/info` behind Basic auth and prints a **session**
password on start; it persists it nowhere. So the container pins one:

1. `OPENCODE_SERVER_PASSWORD` if you set it
2. else `$PREFIX/var/lib/invapp/opencode-password` (generated once, mode 0600)
3. else the device's `~/.config/opencode/service.json`, for parity

`td-ai` exports it before exec'ing `opencode serve`, prints the matching
`curl -u opencode:<password> …` line, and detects an already-running server on
later invocations. Without this, a second `td-ai` could not authenticate and
would report your own healthy server as a foreign listener on `:4096`.

## Keeping this in sync with the app

The seed tree now lives in `app/src/main/assets/invapp/` and is the single
on-disk copy consumed by both `seed.sh` (container) and the app's asset
installers (`ShellLibraryInstaller`, `ResourceScriptsInstaller`,
`TermuxHackerThemeInstaller`). When you edit a helper/config/shortcut, edit it
there and both the image and the APK pick it up on the next build.

The remaining divergence — device `bin/bun`/`bunx`/`bun-doctor`/`node` and
`opencode-setup`, which are still Java string literals in
`TermuxBunInstaller.java` — is the only place two copies of a helper still
exist (see "Note" above).

Parity checks worth running after a change:

```sh
docker run --rm ghcr.io/involvex/termux-docker:x86_64 td-info
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bun-doctor
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bash -lc 'cd ~/repos && td-scaffold smoke react-ts --no-install'
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bash -lc 'td-ai 4096 & sleep 8; curl -fsS http://127.0.0.1:4096/global/health || curl -fsS http://127.0.0.1:4096/api/info'
```
