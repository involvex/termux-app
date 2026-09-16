# Terminal Dev roadmap

Product direction for this Termux fork (`com.involvex.termux_app`): stay in the
terminal, develop on PC and phone against the same git remotes, preview local
servers on-device, and optionally attach AI CLIs.

## Pillars

| Pillar | Intent |
|--------|--------|
| Terminal-first | git / bun / build stay in-session |
| PC ↔ phone | Same repo: push on PC, pull on phone under `~/repos` |
| Preview | In-app view of `http://127.0.0.1:<port>` |
| AI CLI | e.g. `td-ai` → OpenCode web → Preview |

## Phases

### 1. Runtime (done)

- Bundled Android Bun (`$PREFIX/libexec/bun` + `$PREFIX/bin/bun` shim)
- Shim preloads `libinvapp-bun-seccomp.so` (Android seccomp SIGSYS→ENOSYS) and
  injects `--os=android` on install/add/create
- Redirector rewrites `#!/usr/bin/env` so stock `npm`/`npx` shebangs work
- Default cwd `~/repos` (exec); shared storage for browse/sync only

### 2. Preview (done)

- Drawer **Preview** → `LocalhostPreviewActivity`
- Port field + **Scan** → listening TCP chips (one-tap open)
- Loopback-only navigation; cleartext only for localhost

### 3. AI tools (done — bootstrap)

- `opencode-setup` — `bun install -g opencode-ai@latest`
- `td-ai [port]` — install if needed, start `opencode web` (default **4096**), print Preview hint
- Not baked into the APK; installs into the Termux prefix on demand

### 4. Workflow UX (done)

- Drawer snippets: `git pull`, `bun i`, `bun run dev`, **Repos**, **Run…**
- Default extra-keys row: pull / bun i / dev / repos (if user has not customized `extra-keys`)
- Snackbar when a preferred localhost port newly appears → open Preview
- Repo picker (`~/repos`) and `package.json` script run sheet

### 5. LAN share (done — opt-in)

- Preview **Copy LAN** / long-press port chip → clipboard `http://<wifi-ip>:<port>/`
- Only when the server listens on `0.0.0.0` / `::` (loopback-only binds explain how to fix)
- No public tunnels by default

### 6. Dev CLI reliability (done)

- `node` shim → Bun when `nodejs` pkg is absent (package bins with
  `#!/usr/bin/env node`)
- Bun preload = seccomp + redirector (SIGSYS fix **and** shebang rewrite for
  bunx children)
- `bunx` passes `--bun`; `td-dev [script]` for Preview/LAN hints; hardened `td-ai`
- [bun-termux-loader](https://github.com/kaan-escober/bun-termux-loader) is for
  glibc `bun build --compile` bundles — not needed for our official Android Bun

### 7. Vite golden path + AI session UX (done)

- `td-scaffold [name] [template]` — Vite app under `~/repos` (default
  `vanilla`; also `react`, `vue`, `vanilla-ts`, …) with `dev` on
  `0.0.0.0:5173` for Preview / Copy LAN
- Drawer **New…** → name + template → runs `td-scaffold`
- Drawer **AI** → `td-ai` in the current session + open Preview on `:4096`
- Optional later: Vite-PWA template once useful

## Non-goals (for now)

- Full in-app IDE / multi-tab browser
- Reverse tunnels / public URLs by default
- Shipping every npm CLI as an app-managed wrapper

## Day-to-day usage

```bash
cd ~/repos
td-scaffold myapp react   # or: drawer → New…
td-dev                    # vite on :5173
# Drawer → Preview → Scan → 5173
```

```bash
# AI web UI in Preview
td-ai          # or: drawer → AI
# Preview opens on :4096
```

On desktop: same remote, normal git + bun. Pull on the phone to continue.
