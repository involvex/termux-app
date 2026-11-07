# Troubleshooting

| Symptom | Fix |
|---|---|
| `Unknown signal 31 (SIGSYS)` on Bun | Use bundled `$PREFIX/libexec/bun`; never `bun.sh/install`. Delete `node_modules` + lockfile if a PC lockfile pinned `linux-*` natives, then `bun i` on the phone |
| `/usr/bin/env: bad interpreter` on npm/npx | Open a **new** session after update (redirector rewrites shebangs). `pkg install nodejs` if npm is missing |
| `Permission denied` on `tsc` / bins | Project is under shared storage (**noexec**). Move to `~/repos` |
| OpenJDK install stuck / `java` dlopen / Bad system call | Run `td-jdk-setup` (installs recommends incl. `libandroid-shmem`). `jdk-doctor` for details. See [OpenJDK 17](openjdk.md) |
| `dpkg: corrupt info database format file …/info/format` | Older helpers damaged `format` (must be `1\n`). Reopen app or `ivx-fix-dpkg-scripts`, or `printf '1\n' > "$PREFIX/var/lib/dpkg/info/format"` |
| `pd run` / proot → `execve(bash)` ENOENT or guest `libc.so` / host `libtalloc` missing | Reopen app to reseed `$PREFIX/bin/proot` wrapper (host `LD_LIBRARY_PATH=$PREFIX/lib`, guest `-e LD_LIBRARY_PATH=`). If `libtalloc`/`libandroid-support` missing: `export LD_LIBRARY_PATH=$PREFIX/lib` then reopen app. Incomplete rootfs: `pd reset <distro> && pd install <distro>` |
| `td-resource-linux --run` → `bad interpreter` or `libandroid-support.so` not found | Reopen app; `--run` rewrites `run.sh` and restores `LD_LIBRARY_PATH=$PREFIX/lib` for host bash |
| Global tool clutter (pkg / cargo / bun / npm / pip / uv) | `ivx-cleanup` interactive menu |
| Low disk / find what eats space | `ivx-disk-analyze` (overview) · `ivx-disk-analyze guests` · `ivx-disk-analyze large ~/repos` |
| OpenCode `Permission denied` / LIBC / postinstall / SIGSYS | Re-run `opencode-setup` (official V2 tarball + glibc). Do not `unset LD_PRELOAD` |
| OpenCode `AI_APICallError` / “typo in the url or port” | Local `:4096` OK; Zen/provider broken. `rm -rf ~/.cache/opencode`, re-auth, seed glibc DNS/CA, `opencode-setup`, retry from `~/repos/…` (V1 only: fix `@opencode-ai/plugin@local` → `latest`) |
| OpenCode `getifaddrs returned an error` | Non-fatal for `0.0.0.0` bind; V2 has no `--mdns`. Override `OPENCODE_HOST=127.0.0.1 td-ai` if needed. Preview uses `127.0.0.1:4096` / `/api/info` |
| Preview blank / wrong port | OpenCode is `:4096` — there is no `/api` on `:5000`. Drawer **AI** opens Preview after health; tap Reload if early |
| Tab completion missing for bun/pkg/npm | Open a **new** session after app update. Optional: `pkg install bash-completion`. Completers live under `$PREFIX/etc/bash_completion.d/` |
| Banner missing on login | `ivx-configure status` — hook must exist and the shell must be interactive (`bash -lc` and `ssh host cmd` are silent by design). `IVX_FORCE`-free repro: `ivx-configure banner preview`. Reopen the app if the hook is missing |
| Banner shows garbled blocks / wraps | Terminal too narrow for `pixelfont`; it falls back automatically. Pin it: `ivx-configure banner style mini` (or `plain`) |
| Banner appears in scripts / widgets / ssh commands | It should not — the hook requires `-t 1` + interactive `$-`. Check `ivx-configure doctor` for a stray `banner.sh` edit, and use `INVAPP_NO_BANNER=1` |
| `ivx-configure: PREFIX is not set` | Run it inside Termux (`PREFIX` is exported by the shell). `bash ~/…/ivx-configure` outside the app has no prefix |
| `ivx-configure banner text` says figlet is missing | `pkg install figlet`, or pass `--font-file /path/to/font.flf`. Built-in styles need nothing |
| Custom banner art did not take effect | Style must be `custom` and `${XDG_CONFIG_HOME:-~/.config}/invapp/art.txt` must be non-empty. `ivx-configure banner show` then `banner preview` |
| Config edit broke the banner | The renderer ignores source errors; `ivx-configure doctor` names the file, `ivx-configure reset` restores defaults |
| `ivx-configure completions all` skips tools | Generators only run for installed binaries — the table marks them `no`. Install the tool first, then re-run |
| Doctor `FAIL` on a container | Android-only checks (redirector, `termux-api`) are informational when `INVAPP_ON_DEVICE=0`. `--strict` in CI should allow those |
| `ivx-doctor` says the banner hook is missing | Reopen the app (it reseeds on start) or run `td-upgrade` on device. `ivx-configure` and `ivx-doctor` are re-written every launch |
| Extra keys only show ESC/nav | Swipe the key bar **left/right** for workflow page (pull / bun i / …). **Tools → Customize keys…** or **Settings → Customize keybinds** |
| Extra keys show weird symbols (⎈ ⎋ ⇱) | `extra-keys-style=all` — set `extra-keys-style=none` or run **Customize keybinds → Reset** (writes text HOME/END/CTRL) |
| Green-bar `ls` folders | Hacker theme ANSI clash — new session or replace `~/.termux/colors.properties` |
| Phantom kill `[signal 9]` (Android 12+) | OS phantom/CPU killer — see upstream Termux issue [#2366](https://github.com/termux/termux-app/issues/2366) |
| Plugin install fail | Same signature + `sharedUserId` source only; uninstall all, reinstall set |

More agent-oriented notes live in [`AGENTS.md`](https://github.com/involvex/termux-app/blob/master/AGENTS.md)
in the repository.
