# Docker / Podman container

Run the same InVxTermux tooling as a container image: bun, `td-dev`,
`td-scaffold`, `td-clone`, `td-ai`, OpenCode, Cursor Agent, the hacker theme and
your `~/.shortcuts` templates.

The image is `ghcr.io/involvex/termux-docker`. It is a
[Termux](https://termux.dev) environment — same `$PREFIX`, same package
manager, same `~/repos` — without the Android runtime.

## Pull and run

```bash
docker run -it ghcr.io/involvex/termux-docker:aarch64
```

Tags follow the device ABI: `aarch64`, `arm`, `x86_64`, `i686`, plus
`latest` (multiplatform manifest). On an x86 PC, use `x86_64`; on a phone,
`aarch64`.

Verify what the image can do:

```bash
docker run --rm ghcr.io/involvex/termux-docker:x86_64 td-info
```

## Everyday use

```bash
td-clone https://github.com/involvex/termux-app myapp   # clone into ~/repos
td-scaffold myapp react-ts                              # vite project
cd ~/repos/myapp && td-dev                              # dev server
td-ai                                                    # OpenCode on :4096
```

Ports are published on the **host** side, because the app's Preview WebView has
no container equivalent:

```bash
docker run -it --rm -p 5173:5173 -p 4096:4096 ghcr.io/involvex/termux-docker:x86_64
```

`td-dev` and `td-scaffold` bind `0.0.0.0` for exactly this reason. There is also
a compose file at the **repo root** (`compose.yaml`), so no `cd docker/`:

```bash
cd termux-app && docker compose up -d && docker compose exec invtermux bash
```

## What works, what cannot

| Feature | In the container |
|---|---|
| `bun`, `bunx`, `td-dev`, `td-scaffold`, `td-clone` | ✅ on `aarch64` / `x86_64` |
| `td-ai`, `opencode-setup`, `cursor-agent-setup`, `td-upgrade` | ✅ |
| Hacker `colors.properties`, `termux.properties`, completions | ✅ |
| `~/.shortcuts` templates | ✅ seeded — run them by hand, there is no widget launcher |
| `node` | ✅ everywhere (`nodejs-lts`) — also the fallback on 32-bit ABIs |
| `termux-api` CLIs (`termux-sensor`, `termux-battery`, …) | ⚠️ installed, but every call broadcasts an Android intent, so they fail |
| `termux-screenshot`, `td-screen-ocr` | ⚠️ stubs that explain the Android-only requirement |
| OpenJDK 17 | ✅ `pkg install openjdk-17` or `td-install-extras openjdk-17` — no `libandroid-shmem` dance needed |
| `td-resource-linux` (proot) | ❌ omitted on purpose — the container already is a Linux rootfs |

!!! note "bun and 32-bit"
    Bun publishes `x64` and `aarch64` Linux builds only — no `armv7`, no `386`.
    The app has the same limit, so the `arm` and `i686` images are base-only and
    fall back to `nodejs-lts`.

!!! warning "bun + Docker seccomp"
    If bun reports `Bad system call` or an unknown syscall, Docker's default
    seccomp profile is rejecting `openat2`/`fchmodat2`:

    ```bash
    docker run -it --rm --security-opt seccomp=unconfined ghcr.io/involvex/termux-docker:x86_64
    ```

    `bun-doctor` prints the same hint.

!!! info "`td-ai` server password"
    OpenCode V2 protects `/api/info` with a session password it only prints to
    the log. The container pins one instead: `OPENCODE_SERVER_PASSWORD`, else a
    generated `$PREFIX/var/lib/invapp/opencode-password` (mode 0600), else the
    device's `~/.config/opencode/service.json`. `td-ai` prints the matching
    `curl -u opencode:<password> …` command and detects an already-running
    server, so `td-ai-stop` / re-running `td-ai` behave predictably.

## Build it yourself

The tooling source lives in this repo (`app/src/main/assets/invapp/`); the rootfs generation
lives in the [involvex/termux-docker](https://github.com/involvex/termux-docker)
fork, which keeps upstream `termux/termux-docker` mergeable.

```bash
git clone https://github.com/involvex/termux-docker
git clone https://github.com/involvex/termux-app

cd termux-docker
INVAPP_SRC=../termux-app/app/src/main/assets/invapp ./generate.sh --invapp
```

Without `--invapp` the fork still produces a stock Termux image.

| Variable | Default | Effect |
|---|---|---|
| `TERMUX_ARCH` | host arch | `aarch64`, `arm`, `x86_64`, `i686` |
| `TERMUX_DOCKER__IMAGE_NAME` | `termux/termux-docker` | image tag to build |
| `TERMUX_DOCKER__BOOTSTRAP_VERSION` | `2026.06.07-r1%2Bapt.android-7` | pin `2026.02.12-r1%2Bapt.android-7` to match the APK |
| `TERMUX_DOCKER__INVAPP_BUN_VERSION` | `1.4.2` | bun release (matches the APK bundle) |
| `TERMUX_DOCKER__INVAPP_BUN_BASELINE` | `0` | `1` for CPUs without AVX2 |
| `TERMUX_DOCKER__INVAPP_WITH_AI` | `0` | `1` bakes OpenCode + Cursor Agent into the image |

CI builds all four ABIs on native runners and pushes per-arch tags plus a
`latest` manifest to `ghcr.io`.

## Why some wrappers stay

A container is a Termux prefix *without* Android. The prefix is still
**bionic**-linked and still rooted at `/data/data/com.termux`, so:

* The container ships the APK's **android bun build** (`bun-linux-*-android.zip`,
  checksum-pinned to the same digests as the APK). The plain Linux build cannot
  run here — its ELF interpreter `/lib64/ld-linux-x86-64.so.2` does not exist in
  this rootfs.
* `--os=android` / `npm_config_platform=android` **must** stay — Linux/glibc
  natives cannot load. Dropping it is the classic "bun installs, then everything
  segfaults" trap.
* Termux `glibc` + `ld-linux --library-path` wrappers **must** stay for OpenCode
  and Cursor Agent: those are real glibc binaries.
* The path redirector, bun's seccomp shim and the OpenCode DNS shim are gone —
  they only existed to paper over Android.

See `docker/README.md` in this repo for the full parity matrix and the sync
workflow when a `td-*` helper changes.
