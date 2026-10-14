# Docker / Podman container

`docker/invapp/` is the container twin of the app's runtime environment: the
same tools, the same `$PREFIX/bin` helpers, the same configs, so a project moves
between phone and container without relearning anything.

The app itself seeds its helpers from Java string literals
(`TermuxBunInstaller.java`, `ResourceScriptsInstaller.java`,
`WidgetScriptsInstaller.java`). The container seeds real shell files from this
tree instead — easier to read, lint and test.

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
INVAPP_SRC=../termux-app/docker/invapp ./generate.sh --invapp
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
| `INVAPP_SRC` | `../termux-app/docker/invapp` | seed tree location |

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

## Keeping this in sync with the app

`docker/invapp/bin/*` are ports of the strings written by
`TermuxBunInstaller.java`. When you change a helper on the device side, port the
change here too — the two cannot share files today (one is a Java literal, one
is a shell script). The intended follow-up is to move the helpers into
`app/src/main/assets/invapp/` and have both the app and `seed.sh` install the
same files.

Parity checks worth running after a change:

```sh
docker run --rm ghcr.io/involvex/termux-docker:x86_64 td-info
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bun-doctor
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bash -lc 'cd ~/repos && td-scaffold smoke react-ts --no-install'
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bash -lc 'td-ai 4096 & sleep 8; curl -fsS http://127.0.0.1:4096/global/health || curl -fsS http://127.0.0.1:4096/api/info'
```
