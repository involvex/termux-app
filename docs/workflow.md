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
# or: git pull in an existing repo
bun install
td-dev                    # or: bun run dev
```

Then: drawer → **Preview** → **Scan** → tap the port chip.

## Helpers

| Command | Purpose |
|---|---|
| `td-scaffold [name] [template]` | Vite app under `~/repos` (`0.0.0.0:5173`); templates include `react`, `pwa`, `pwa-react`, … |
| `td-dev [script]` | `bun run` with Preview / LAN hints |
| `td-clone <url> [name] [--bun-i]` | Clone into `~/repos` |

Drawer quick bar (customizable via **⋯**): pull / bun i / bun run dev / Repos /
Clone… / New… / AI.

## LAN share

Preview **Copy LAN** / long-press a port chip copies
`http://<wifi-ip>:<port>/` when the server listens on `0.0.0.0` / `::`.
No public tunnels by default.

## Bun

Use the **bundled** Android Bun. Do **not** run the official `bun.sh` install
script inside the app (glibc binary → signal 31 / missing interpreter).
