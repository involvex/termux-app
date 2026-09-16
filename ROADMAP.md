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

### 5. LAN share (next, opt-in)

- Copy `http://<wifi-ip>:<port>` when server binds `0.0.0.0`
- No public tunnels by default

## Non-goals (for now)

- Full in-app IDE / multi-tab browser
- Reverse tunnels / public URLs by default
- Shipping every npm CLI as an app-managed wrapper

## Day-to-day usage

```bash
cd ~/repos
git clone <url> myapp && cd myapp
bun install
bun run dev
# Drawer → Preview → Scan → tap 3000
```

```bash
# AI web UI in Preview
td-ai          # or: td-ai 4096
# Drawer → Preview → tap 4096
```

On desktop: same remote, normal git + bun. Pull on the phone to continue.
