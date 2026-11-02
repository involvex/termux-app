# Kali NetHunter (udocker + VNC)

Optional, **device-only** desktop: Termux [`udocker`](https://indigo-dc.github.io/udocker) pulls `kalilinux/kali-rolling`, then TigerVNC serves a guest desktop on **`127.0.0.1:5901`**. Connect with **AVNC**, RealVNC Viewer, or NetHunter KeX — not Preview/noVNC.

This replaced the old proot-distro + noVNC bridge (guest login often failed on this fork).

## Setup (once)

```bash
# Pull/create container `kali` (large download — free space under $PREFIX)
td-udocker-setup
# or full NetHunter profile (udocker + apt VNC/desktop + vncpasswd):
td-nethunter-setup
```

Defaults: image `kalilinux/kali-rolling`, name `kali`. Override image:

```bash
td-udocker-setup debian:bookworm          # smaller test image → name `debian`
TD_UDOCKER_NAME_OVERRIDE=mykali td-nethunter-setup kalilinux/kali-rolling
```

## Daily use

```bash
td-udocker                 # interactive root shell (alias: td-guest)
td-udocker apt update      # one-shot command
td-nethunter               # start VNC on 127.0.0.1:5901
td-nethunter --stop        # kill VNC in the guest
td-nethunter-debug         # paste-back self-test
```

Open a VNC client → host `127.0.0.1`, port `5901` (display `:1`). Change display with `TD_NETHUNTER_DISPLAY=2 td-nethunter` → `:5902`.

## What gets installed

| Where | What |
|-------|------|
| Termux | `pkg install udocker` (deps: proot, python, resolv-conf) on first setup |
| Guest | TigerVNC / tightvnc, openbox/xfce, dbus; optional `nethunter-utils` on Kali |
| State | `$PREFIX/var/td-udocker/` ready markers + logs; `~/.udocker/` |

`~/.udocker/udocker.conf` sets `proot_killonexit = False` so Xvnc keeps running after `udocker run` returns, and `proot_noseccomp = True`.

## Troubleshooting

1. Run `td-nethunter-debug` and keep the full output.
2. **`LD_PRELOAD` must stay set** for the host `udocker` process — Termux’s package hardcodes `/data/data/com.termux/.../bin/proot`; this fork’s path redirector rewrites it. Open a **new** session after an app update.
3. Rogue pip `proot` shim → `pip uninstall proot && pkg install -y -f proot`.
4. VNC not listening → `td-udocker cat /root/.td-nethunter-vnc.log` and re-run `td-nethunter-setup`.
5. Old proot-distro trees under `$PREFIX/var/lib/proot-distro/` are unused; delete if you need space.

## Not in this stack

- Preview WebView / noVNC / websockify
- Official NetHunter-Store KeX rootfs installer (`install-nethunter-termux`)
- Termux:X11 (needs a matching signed X11 APK for this `sharedUserId` — future plugin)
- Container twin (`termux-docker`) — helpers are device-only
