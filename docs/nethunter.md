# Kali NetHunter (udocker + VNC)

Optional, **device-only** desktop. Connect with **AVNC**, RealVNC Viewer, or NetHunter KeX to **`127.0.0.1:5901`** — not Preview/noVNC.

## Recommended setup (kali.download rootfs)

Docker Hub pulls of `kalilinux/kali-rolling` often fail on Termux/Android (`manifest not found or not authorized`) even with `--platform=linux/arm64`. Use the same official NetHunter rootfs tarballs as [KeX / install-nethunter-termux](https://gitlab.com/kalilinux/nethunter/build-scripts/kali-nethunter-rootless):

```bash
td-nethunter-setup                 # downloads nano (~170 MiB) + VNC stack
# or smaller / larger:
td-nethunter-setup --variant minimal   # ~120 MiB
td-nethunter-setup --variant full      # ~1.7 GiB
```

Then:

```bash
td-udocker            # interactive root shell
td-nethunter          # start VNC on 127.0.0.1:5901
td-nethunter --stop
td-nethunter-debug
```

## PC transfer (when you want the Docker image)

Hub must be pulled on a machine that can talk to Docker Hub; **match the phone ABI** (`linux/arm64` on most phones):

```bash
# On the PC
docker pull --platform linux/arm64 kalilinux/kali-rolling:arm64
docker save -o kali-arm64.tar kalilinux/kali-rolling:arm64
# copy kali-arm64.tar to the phone (USB / ~/storage/downloads), then:
td-udocker-setup --from-docker-save ~/storage/downloads/kali-arm64.tar
td-nethunter-setup --from-docker-save ~/storage/downloads/kali-arm64.tar
```

`docker save` (layers + metadata) → `udocker load`. Do **not** confuse with `docker export` / a raw proot backup.

## Importing a rootfs tarball

```bash
td-udocker-setup --from-tar /path/to/rootfs.tar.xz
```

The archive must have `bin/sh` (or `usr/bin/bash`) at the **filesystem root**, or a single top-level dir such as `kali-arm64/` (official NetHunter layout). Setup flattens that nesting into `$PREFIX/var/td-udocker/rootfs/<name>` and runs it with **direct `proot`** (same idea as KeX).

### Why not `udocker import` for kali.download

On this fork, `udocker import` re-extracts layers with host proot/tar and fails (`Error: while extracting image layer` / guest `bash` with no execute bit) even after a good first extract. NetHunter / `--from-tar` therefore **skip import**: install the tree once, stamp `backend=rootfs`, and `td-udocker` / `td-nethunter` call fork-safe `proot` (`env -u LD_PRELOAD` + `PROOT_LOADER` + `PROOT_TMP_DIR`). Hub / `--from-docker-save` still use udocker.

### Why extract showed `Cannot mknod` / `Cannot hard link`

Android apps cannot create device nodes or many hardlinks. Plain `tar -xJf` therefore exits non‑zero on the NetHunter rootfs. Setup extracts with **`proot --link2symlink tar …`** (same as official KeX), but on this fork that requires **`LD_PRELOAD=` (empty) + `PROOT_LOADER` + `PROOT_TMP_DIR`** — otherwise proot dies with `execve(…/bin/tar): No such file or directory` or warnings about `/data/data/com.termux/.../tmp`. The app reseeds a fork-safe `$PREFIX/bin/proot` wrapper (ELF at `$PREFIX/libexec/proot-bin`) so stock `pd` / `proot-distro` and `td-resource-linux` get the same fix. mknod/hardlink errors stay non‑fatal; success is “`bin/sh` exists”. Plain `tar` is the fallback. Runtime `/dev` comes from host binds.

If `pd run …` still reports `execve("/usr/bin/bash"): No such file or directory`, reopen the app (reseed wrapper), then `pd reset <distro> && pd install <distro>` when the rootfs is incomplete. Prefer `td-nethunter-setup` for Kali desktop.

### Why a proot backup had “missing /bin/sh”

| What you did | Result |
|--------------|--------|
| Tar of `…/kali-arm64/` **directory** without flattening | Guest root = empty / wrong tree → no `/bin/sh` |
| `udocker load` / `import` on a **flat rootfs** | Wrong verb — use `--from-tar` (rootfs) |
| Incomplete extract / wrong path | Only stubs, no shell |

Use `--from-tar` (rootfs/export) or `--from-docker-save` (`docker save`), not the wrong verb for the file type.

## Optional Hub pull

```bash
td-udocker-setup kalilinux/kali-rolling:arm64   # often fails on-device
td-nethunter-setup --docker                     # same, then VNC apt
```

## Env knobs

| Variable | Default | Meaning |
|----------|---------|---------|
| `TD_NETHUNTER_VARIANT` | `nano` | `nano` / `minimal` / `full` |
| `TD_NETHUNTER_ROOTFS_BASE` | `https://kali.download/nethunter-images/current/rootfs` | Mirror |
| `TD_UDOCKER_NAME` | `kali` | Container name |
| `TD_UDOCKER_PLATFORM` | auto from `uname -m` | e.g. `linux/arm64` |
| `TD_NETHUNTER_DISPLAY` | `1` | VNC display → port `5900+N` |

## Troubleshooting

1. `td-nethunter-debug` — paste full output.
2. Hub errors → ignore Hub; run plain `td-nethunter-setup` (kali.download).
3. `Error: while extracting image layer` / `bash` no execute bit after import → reopen the app (reseeds helpers), remove any half-broken guest (`rm -rf $PREFIX/var/td-udocker/rootfs/kali`), re-run `td-nethunter-setup` (rootfs path, not `udocker import`).
4. Keep **`LD_PRELOAD`** set for host `udocker` (path redirector). Direct-proot NetHunter clears it only inside the guest `proot` call.
5. Rogue pip `proot` → `pip uninstall proot && pkg install -y -f proot`.
6. VNC quiet → `td-udocker kali cat /root/.td-nethunter-vnc.log`.

## Not in this stack

- Preview WebView / noVNC
- Termux:X11 (needs a matching signed X11 APK)
- Container twin (`termux-docker`)
