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
| AI CLI | e.g. `opencode serve --port 5000` → open Preview |

## Phases

### 1. Runtime (done / current)

- Bundled Android Bun (`$PREFIX/libexec/bun` + `$PREFIX/bin/bun` shim)
- Shim clears `LD_PRELOAD`, sets OPENSSL + `npm_config_platform=android`
- Default cwd `~/repos` (exec); shared storage for browse/sync only
- No per-package CLI wrappers

### 2. Preview (shipped MVP)

- Drawer **Preview** → `LocalhostPreviewActivity`
- Port field (default 5000); loopback-only navigation
- Cleartext allowed only for `localhost` / `127.0.0.1`

### 3. Workflow UX (next)

- Extra-key / snippet presets: `git pull`, `bun install`, `bun run dev`
- Optional “open Preview on port …” after detecting a listening port
- Document recommended layout: `~/repos/<project>`

### 4. AI tools

- Document `opencode serve --port 5000` (or similar) + Preview
- Optional helper script under `$PREFIX/bin` that starts the server and prints
  “open Preview → 5000”
- No cloud lock-in; keep tooling local to the device prefix

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
# Drawer → Preview → 3000 (or your app’s port)
```

On desktop: same remote, normal git + bun. Pull on the phone to continue.
