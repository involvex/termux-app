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

Note: `bin/bun`, `bin/bunx`, `bin/bun-doctor`, `bin/node` and `td-dev` are now
installed from the shared `bin/` assets on both device and container. What is
still a Java string literal in `TermuxBunInstaller.java` is `opencode-setup`
(glibc/`ld-linux` wrapper plus the Android DNS shim) and the `td-*` helpers
whose device and container UX intentionally differ — they carry on-device hints
("reopen the app", "drawer → Preview") that are meaningless in a container, so
they are a reconciliation rather than a copy. Those stay gated on an on-device
validation pass: the glibc/`ld-linux` glue cannot be runtime-checked here.

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

### `bunx` / `npx` exec caveat (container only)

This is **not** a regression from the CVE surface reduction — it is how
`termux/termux-docker` runs *without* Android, and it reproduces identically on
the published image (which still ships `npm`).

**Symptom.** In the container, `bunx --bun <pkg>` (and `npx`, where present)
downloads and extracts the package into `$TMPDIR/bunx-1000-<pkg>@...` — the
install succeeds (e.g. 124 files extracted for `cowsay@1.6.0`) — but the bin
**exec fails with exit 1 and no error message**. `bun run`, `bun install`, and
invoking a package's JS directly (`bun node_modules/cowsay/cli.js hi`) all work.

**Root cause.** Ecosystem CLIs declare their interpreter with an absolute
shebang `#!/usr/bin/env node`. The Termux prefix installs `env` at
`$PREFIX/bin/env`, but the container rootfs has **no `/usr/bin` at all** — so the
kernel's literal lookup for `/usr/bin/env` returns `ENOENT` → `bad interpreter:
/usr/bin/env: No such file or directory`. On a real Android device this works
because `termux-exec`' (LD_PRELOAD) rewrites `/usr/bin/env` → `$PREFIX/bin/env`;
in the container `termux-exec`'s postinst cannot read
`android_build_version_sdk` (there is no `/system/bin/getprop`) and **skips
enabling `$LD_PRELOAD`**, so the rewrite never happens.

Evidence (x86_64 build on the stock base `termux/termux-docker:x86_64`):

* `/usr/bin/env` and `/bin/env` — both absent; `$PREFIX/bin/env` present.
* `bunx --bun cowsay@1.6.0 hi` → "Resolved, downloaded and extracted [124]" /
  "Saved lockfile", then exit 1; no `cowsay` output. Same on the published image.
* Reproducer: `PATH=$TMPDIR/bunx-*/node_modules/.bin:$PATH cowsay hi` →
  `...cowsay: /usr/bin/env: bad interpreter: No such file or directory`.
* **Fix proven:** bind-mount the `coreutils` `env` binary over `/usr/bin/env`
  and the identical command prints the cow (exit 0). Forcing `LD_PRELOAD` with
  the termux-exec lib does **not** fix it (the lib self-disables without the
  Android SDK value), so the shim, not the hook, is what's missing.

**Impact.** Does not affect the core dev workflow: `td-dev`, `td-scaffold` and
`td-clone` call `bun`/`bun run` directly (shebang-free), so they are unaffected.
It only blocks *executing* npm/PEP 508 ecosystem CLI entry points through their
`#!/usr/bin/env <tool>` shebangs. `bunx`/`npx` can still be used for packages
whose entry is invoked path-wise, or by applying the workaround below.

**Workaround / fix (belongs in `involvex/termux-docker`, not here).** Add a root
layer in the image build that exposes the interpreter, e.g.:

```sh
# in termux-docker's Dockerfile, as root, after seeding
ln -sf "$PREFIX/bin/env" /usr/bin/env
```

`seed.sh` itself cannot do this: it runs as the non-root `system` user (uid 1000)
and cannot create `/usr/bin`. This tree owns the *seed assets* only; the base
rootfs fix lives in the `involvex/termux-docker` fork. Note the remaining
`package.json` in the hardened image is `corepack` shipped inside `nodejs-lts`
(not an npm tree) and is not among the reported advisories.

## Image vulnerability surface (CVE scans)

An image scan (`docker scout cves`, Trivy, Grype, Docker Desktop's scan tab)
reports ~19 findings against this image. Every one of them comes from two Termux
packages that are installed **only because apt installs `Recommends:` by
default** (`APT::Install-Recommends "1"`) — and nothing the seed installs uses
either of them:

| Carrier | Why it is installed | Where the findings live |
|---|---|---|
| `npm` | `Recommends` of `nodejs-lts` | `/usr/lib/node_modules/npm/node_modules` — 185 `package.json` files: `undici` 6.28.0, `ip-address` 10.5.0, `brace-expansion` 5.0.9, … |
| `python-pip` | `Recommends` of `python` | `pip/_vendor/` — `urllib3` 2.7.0, `setuptools` 70.3.0, `msgpack` 1.1.2 |

`seed.sh` therefore installs the base set with `--no-install-recommends`
(see `PKG_INSTALL`), so **neither package is baked into the image**. This is
attack-surface reduction, not a CVE remediation — it removes the entire
vulnerable tree rather than the affected versions.

Why that is safe, all verified against the published image:

* `apt-get -s remove npm python-pip` removes nothing else, and no installed
  package `Depends:` on either.
* Nothing in the seed tree invokes `npm`, `npx`, `pip` or `corepack` directly.
  `bun x --bun` would run npx through bun — but in the container that only
  *installs* the package; its exec still needs `/usr/bin/env`, which the
  container lacks (see the `bunx`/`npx` exec caveat above). `opencode-setup`
  and `cursor-agent-setup` ship their own runtimes and never call pip.
* On a fresh `termux/termux-docker` rootfs, `pkg install -y
  --no-install-recommends <base list>` installs cleanly with `npm`/`python-pip`/
  `corepack` absent, and `bash git curl vim python3 node tar unzip jq tmux htop`
  all still present.

Not fixable from this repo: as of 2026-10 the newest Termux builds *are* the
installed ones (`apt-cache policy` candidate == installed for both `npm`
11.21.0 and `python-pip` 26.2.1), so the vendored flaws — CVE-2026-97689 /
-97688 (urllib3 < 2.8.0), CVE-2025-47273 (setuptools < 78.1.1), the msgpack and
undici advisories — have no fixed Termux package yet. Rebuilding does not help
until [termux-packages](https://github.com/termux/termux-packages) republishes
`python-pip` / `npm`. Everything else in the image (bootstrap, aosp-libs, and
each package in `packages.list`) is owned by termux-packages, not this repo, so
a version bump there is the only real remediation.

If you need them anyway:

```sh
pkg install npm        # or: pkg install python-pip
```

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

The remaining divergence — `opencode-setup` and the `td-*` helpers whose
device/container UX intentionally differs, still Java string literals in
`TermuxBunInstaller.java` — is the only place two copies of a helper still
exist (see "Note" above).

Parity checks worth running after a change:

```sh
docker run --rm ghcr.io/involvex/termux-docker:x86_64 td-info
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bun-doctor
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bash -lc 'cd ~/repos && td-scaffold smoke react-ts --no-install'
docker run --rm ghcr.io/involvex/termux-docker:x86_64 bash -lc 'td-ai 4096 & sleep 8; curl -fsS http://127.0.0.1:4096/global/health || curl -fsS http://127.0.0.1:4096/api/info'
```
