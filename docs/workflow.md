# Phone + PC workflow

Goal: same git remote on desktop and phone; run installs and preview on-device.

## Layout

| Path | Role |
|---|---|
| `~/repos` | Default cwd — **executable** projects |
| `~/storage/shared` | Browse / sync only (**noexec**) |

Keep runnable Node/Bun projects under `~/repos`.

## Typical loop

**On PC**

```bash
git push
```

**On phone (InVxTermux)**

```bash
cd ~/repos
td-clone https://github.com/org/app.git   # or: drawer → Clone…
td-sync                   # or: drawer → td-sync (ff-only; skips dirty)
bun install
td-dev                    # or: bun run dev
```

Then: drawer → **Preview** → **Scan** → tap the port chip.

**Repos** (drawer): each row shows branch / dirty / ahead·behind. Tap → Open /
Safe pull / Branches….

## Helpers

| Command | Purpose |
|---|---|
| `td-scaffold [name] [template] [--here] [--no-install]` | Vite app under `~/repos` (default **`react-ts`**, `0.0.0.0:5173`); also `react`, `pwa`, `pwa-react`, … Seeds `.gitignore` + README |
| `td-dev [script]` | `bun run` with Preview / LAN hints |
| `td-clone <url> [name] [--bun-i]` | Clone into `~/repos` |
| `td-sync` | Fetch + `git pull --ff-only` every clean git repo under `~/repos` |
| `ivx-configure` | Login banner, shell completions, package groups, doctor — see [First-run setup](configure.md) |
| `ivx-doctor` | Read-only health report (`--strict`, `--network`) |

Drawer quick bar (customizable / reorderable via **⋯**): td-sync / bun i /
bun run dev / Repos / Clone… / New… / AI. Add **Setup** (`ivx-configure`) from
the same menu; the right drawer also has a **Setup** button. Long-press
**td-sync** injects cwd `git pull --ff-only`.

## LAN share

Preview **Copy LAN** / **QR LAN** (or long-press a port chip) share
`http://<wifi-ip>:<port>/` when the server listens on `0.0.0.0` / `::`.
QR is same-Wi‑Fi only — no public tunnels. Settings → **Preferred Preview ports**
customizes Scan / snackbar priorities (and **Notify on preferred ports** for
background alerts). Settings → **Bun** shows the APK stamp; use
`bun-upgrade --check` — never `bun upgrade`.

## Bun

Use the **bundled** Android Bun. Do **not** run the official `bun.sh` install
script inside the app (glibc binary → signal 31 / missing interpreter).

## Widget scripts

Templates under `~/.shortcuts` for Termux:Widget one-taps (clipboard TTS, append
clipboard, git pull repos, background `td-ai`). See [Install → Plugins](install.md#plugins-api-widget)
for matching API/Widget APKs and `pkg install termux-api`.
