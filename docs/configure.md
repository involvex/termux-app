# First-run setup + login banner

InVxTermux ships one setup surface: `ivx-configure`. It owns the login banner,
bash completions, optional packages, DNS / manual proxy / `GOPROXY`, and a
read-only health report. Everything is plain bash — no `fzf`, `gum`, `ruby`, or
`python` — so it behaves the same on the phone and in the container image.
Core commands stay offline; only `network test` and `doctor --network` probe
the network on purpose.

```bash
ivx-configure            # interactive menu
ivx-configure status     # what is installed / enabled
ivx-configure network dns fix   # fix Go lookup … on [::1]:53
ivx-doctor               # same as: ivx-configure doctor
ivx-cleanup              # remove global pkg / cargo / bun / npm / pip / uv installs
ivx-disk-analyze         # df/du report: prefix, home, guests, large files
ivx-fix-dpkg-scripts     # heal dpkg info/format + maintainer script shebangs
```

Open it from the terminal, **Settings → Setup**, or the right drawer's
**Setup** button. The quick bar can also show it: **⋯ → Customize bar**.

## Login banner

Every *interactive* login prints the header before the prompt:

```text
  █ █▄ █ █ █ █▀█ █   █ █ █▀▀ ▀▄▀
  █ █ ▀█ ▀▄▀ █▄█ █▄▄ ▀▄▀ ██▄ █ █

    InVxTermux · phone + PC terminal dev
        bun 1.4.4 · git ✓ · opencode ✓
   ivx-configure · td-dev · td-ai · td-info
```

The art is pre-rendered [pyfiglet](https://github.com/pwaller/pyfiglet) output
for “InvolveX” using the **pixelfont** font (GPL-3, by Ankur Bhagat). Only the
rendered glyphs are embedded — the `.flf` font itself is **not** shipped.

### It stays out of the way

The hook lives in `$PREFIX/etc/profile.d/invapp-banner.sh` and prints nothing
unless all of these are true:

| Guard | Why |
|---|---|
| `BASH_VERSION` set | POSIX `sh` / `dash` sessions skip it |
| `$-` contains `i` | `bash -lc`, scripts, and `RUN_COMMAND` stay silent |
| `[ -t 1 ]` | pipes, redirects, scp and widgets get clean output |
| `TERM != dumb` | no escapes in dumb terminals |
| `SSH_ORIGINAL_COMMAND` empty | `ssh host cmd` prints only the command output |

Opt out for one session with `INVAPP_NO_BANNER=1`, or permanently with
`ivx-configure banner off`. Neither `~/.bashrc` nor `~/.profile` is ever edited.

### Styles, colours, custom text

```bash
ivx-configure banner preview              # show it (even when switched off)
ivx-configure banner on | off             # enable / disable logins
ivx-configure banner style pixelfont      # pixelfont | small | mini | plain | auto
ivx-configure banner color green          # green | cyan | magenta | white | rainbow | none
ivx-configure banner text "MY PROJECT"    # render custom art with figlet
ivx-configure banner text "ACME" --font-file ~/fonts/big.flf
ivx-configure banner reset                # back to defaults
```

Narrow terminals degrade automatically (`pixelfont` → `mini` → `plain`), so the
block art never wraps on a phone in portrait. `NO_COLOR=1` disables colours
everywhere.

Custom art needs `figlet` (`pkg install figlet`) and is written to
`${XDG_CONFIG_HOME:-~/.config}/invapp/art.txt`. Delete that file—or run
`banner reset`—to get the built-in header back. `--dry-run` prints the art
without writing anything.

## Shell completions

New sessions already get completions for `bun`, `pkg`, `npm`, `gh` and `git`.
`ivx-configure completions` covers tools that generate their own script:

| Command | Effect |
|---|---|
| `completions list` | table: tool present? = managed? |
| `completions all` | install every generator that exists on disk |
| `completions git gh bun …` | install for the named tools only |
| `completions clean` | remove the managed `ivx-*.bash` files |

Files land in `$PREFIX/etc/bash_completion.d/ivx-<tool>.bash` and are validated
with `bash -n` before being moved into place — a generator that emits broken
bash is discarded rather than saved. Supported generators: `git`, `gh`, `npm`,
`pnpm`, `docker`, `kubectl`, `helm`, `terraform`, `deno`, `tailscale`, `cargo`,
`rustup`, `just`, `uv`, `bun`. Tools that are not installed are reported and
skipped; nothing is installed on your behalf.

For richer `git` completions install the package: `pkg install bash-completion`.
Open a **new session** (or `exec bash -l`) after changing completions.

## Basic packages

```bash
ivx-configure packages list        # installed / missing per group
ivx-configure packages essentials  # one group
ivx-configure packages all         # every group
```

| Group | Packages |
|---|---|
| `essentials` | `bash-completion git curl wget openssh jq nano vim less unzip tar xz-utils procps ca-certificates` |
| `quality` | `fzf ripgrep fd bat eza tree htop ncdu figlet` |
| `build` | `clang make pkg-config cmake ninja python nodejs-lts` |
| `network` | `dnsutils inetutils nmap udocker termux-api` |

Already-installed packages are skipped. There is **no implicit `pkg update`**:
groups install exactly the packages listed. If a bulk install fails, each
package is retried individually so one bad name cannot abort the group. Use
`--dry-run` to see the exact `pkg install` command without running it.

## Network (DNS + manual proxy)

`$PREFIX/etc/resolv.conf` matters for many tools. **Go is special on this
fork:** Termux’s `golang` package is patched to open the *stock*
`com.termux` resolv path, and the Go runtime uses raw syscalls — so
`LD_PRELOAD` cannot rewrite it. Missing that file makes netgo fall back to
`[::1]:53` even when `$PREFIX/etc/resolv.conf` is perfect:

```text
lookup proxy.golang.org on [::1]:53: … connection refused
```

`GODEBUG=netdns=cgo` only helps when that `go` binary was built **with cgo**.
Many Termux builds are **netgo-only** (and `proot` cannot exec Android ELF
`go` on modern Android). The reliable fix is to **byte-patch** the stock
resolv path embedded in `$PREFIX/lib/go/bin/go` to a same-length file we
own (`$PREFIX/../r.conf` → `…/files/r.conf`), then wrap `$PREFIX/bin/go`:

```bash
pkg install golang python
ivx-configure network dns fix    # seeds resolv + patches go
# or explicitly:
ivx-configure network go wrap
go env GOOS
make tidy
```

Re-run `network go wrap` after `pkg upgrade golang`. The patcher runs
with `LD_PRELOAD=` empty and decodes the stock package id from base64 —
the redirector rewrites that package-id token even inside scripts
(47→60). Sets `GOTOOLCHAIN=local` so `go` does not download a linux
toolchain. A free public HTTP proxy / swapping `GOPROXY` alone will not
fix `[::1]:53`.

```bash
ivx-configure network status
ivx-configure network dns fix              # write 8.8.8.8 + 1.1.1.1
ivx-configure network dns set 1.1.1.1 8.8.8.8
ivx-configure network proxy set http://127.0.0.1:7890
ivx-configure network proxy socks socks5://127.0.0.1:1080
ivx-configure network proxy clear
ivx-configure network goproxy preset google   # or goproxyio | direct
ivx-configure network goproxy clear
eval "$(ivx-configure network apply)"      # export into the current shell
ivx-configure network test                 # opt-in DNS/curl smoke test
```

| Subcommand | Effect |
|---|---|
| `dns fix` | Seed `$PREFIX/etc/resolv.conf` (+ glibc twin + `files/r.conf`), enable `GODEBUG=netdns=cgo`, and patch/wrap `go` when golang is present. |
| `go wrap` | Patch `$PREFIX/lib/go/bin/go` → `$PREFIX/libexec/invapp/go` (stock resolv path → `files/r.conf`) and install a thin `$PREFIX/bin/go` launcher. Needs `python`. `go unwrap` restores the symlink. |
| `proxy set` / `socks` | Store a **manual** URL in config; `$PREFIX/etc/profile.d/invapp-network.sh` exports `HTTP(S)_PROXY` / `ALL_PROXY` / `NO_PROXY` on new sessions. |
| `goproxy preset` | `google` → `https://proxy.golang.org,direct`; `goproxyio` → `https://goproxy.io,direct`; `direct` → `direct`. Also runs `go env -w` when `go` is installed. DNS must work first. |

If `go env GOPROXY` is `off`, module downloads fail with `module lookup disabled
by GOPROXY=off` even when DNS and curl work. That is a Go setting (often from
`go env -w GOPROXY=off`), not a missing HTTP proxy. Fix with
`ivx-configure network goproxy preset google` or `go env -u GOPROXY`.

Free public proxy scrapers are **not** supported. Paste a proxy you control
(Clash, VPS, corporate). Open a **new session** after changing proxy /
`GOPROXY`, or `eval "$(ivx-configure network apply)"`.

## Doctor

`ivx-configure doctor` (or `ivx-doctor`) is a read-only report — it never
installs or changes anything:

- `$PREFIX` / `$HOME` / `PATH` / writable `$PREFIX/bin` / temp dir
- free space on `$PREFIX`
- path redirector presence in `LD_PRELOAD`
- bun binary, `bun` wrapper, version stamp
- `node`, `git`, `curl`
- `bash-completion` runtime, completion loader, managed file count
- banner hook, banner library, config syntax
- `termux-api`, `opencode`, `agent`, `java`
- `$PREFIX/etc/resolv.conf` usable nameservers (fails on loopback-only / empty)
- network export hook + whether proxy / `GOPROXY` keys are set

`--strict` turns warnings into a non-zero exit (useful in CI); `--network` also
probes `github.com`, optional configured proxy, `getent`, and `go env GOPROXY`.
Deeper per-topic reports stay where they are: `bun-doctor`, `jdk-doctor`,
`td-info`, `td-resource-ai`.

## Configuration

`ivx-configure` writes a small, bash-sourced file you own:

```bash
# ${XDG_CONFIG_HOME:-~/.config}/invapp/config
IVX_BANNER=1          # 0 disables the login banner
IVX_STYLE=pixelfont   # pixelfont | small | mini | plain | custom | auto
IVX_COLOR=green       # green | cyan | magenta | white | rainbow | none
IVX_TIPS=1            # hint line under the header
IVX_STATUS=1          # "bun 1.4.4 · git ✓" line
IVX_FONT=pixelfont    # default font for `banner text`
IVX_HTTP_PROXY=…      # optional; quoted URL
IVX_HTTPS_PROXY=…
IVX_ALL_PROXY=…       # socks5://…
IVX_NO_PROXY=localhost,127.0.0.1,::1,.local
IVX_GOPROXY=…         # optional Go module proxy
IVX_DNS_NS1=8.8.8.8   # preferred nameservers for `network dns fix`
IVX_DNS_NS2=1.1.1.1
IVX_GO_NETDNS=cgo     # cgo (default) | off — Go DNS on Android
```

A broken config cannot break login: the renderer ignores read/source errors and
falls back to the built-in art. `ivx-configure doctor` reports syntax errors and
`ivx-configure reset` removes the config and custom art (including proxy keys).

## Container parity

The image seeds the same files: `banner.sh` lands in `$PREFIX/lib/invapp/` and
`ivx-configure` / `ivx-doctor` in `$PREFIX/bin/`. Android-only checks (path
redirector, `termux-api`, dot-less `ssh` quirks) are downgraded to `PASS` notes
when `INVAPP_ON_DEVICE=0`, so the same report is meaningful in Docker.

## zsh

Only documented, not installed: the banner hook is `profile.d` + bash. If you
use zsh inside Termux, add the following yourself — InVxTermux will not modify
your dotfiles:

```zsh
# ~/.zshrc (your file, your choice)
[ -r "$PREFIX/lib/invapp/banner.sh" ] && . "$PREFIX/lib/invapp/banner.sh" && ivx_banner_print
```
