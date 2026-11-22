# Cursor Agent CLI (cloud worker)

Run [Cursor Agent CLI](https://cursor.com/docs/cli/installation) on-device so the
phone can appear under **My Machines** at
[cursor.com/agents](https://cursor.com/agents). The agent loop runs in Cursor’s
cloud; tool calls execute on the phone over an **outbound HTTPS** bridge (no
inbound port / Preview).

Drawer **AI** / Preview stay OpenCode (`td-ai`). This is a separate CLI path.

## Why not `curl | bash` / Bun?

The official Linux arm64 package is a **glibc Node 24** bundle
(`agent-cli-package.tar.gz`) with `*.linux-arm64-gnu.node` addons that need
`libdl.so.2`. Android Bun cannot load them (same class of failure as OpenCode
without `ld-linux`).

## Setup

```bash
cursor-agent-setup          # ~173 MiB download + glibc wrapper
agent --version
agent login                 # or: export CURSOR_API_KEY=…
```

Optional pin: `CURSOR_AGENT_VERSION=2026.09.26-dd393fe cursor-agent-setup`

After stock `agent update` (rewrites `~/.local/bin` only):

```bash
cursor-agent-setup --wrap-only
# or:
td-upgrade
```

## Start a cloud worker

```bash
td-agent-worker start
# name default: invx-phone  (override: --name my-phone)
# dirs default: ~/repos     (extra: --dir ~/repos/app)
```

Keep the process running (or use the optional Widget **tasks/agent-worker**).
Then pick the machine in the Cursor cloud UI.

```bash
td-agent-worker status
td-agent-worker stop
```

## Stack upgrade helper

```bash
td-upgrade
# pkg update/upgrade → opencode-setup → agent update + rewrap → Bun stamp check
```

Bun stays **APK-bundled** — do not run `bun upgrade` on-device (wrong binary /
SIGSYS). Install a newer InVxTermux APK to bump Bun.

Optional widgets (Settings / drawer catalog): `td-upgrade`, `tasks/agent-worker`,
`stop-agent-worker`.

## Rules of thumb

- Never `curl https://cursor.com/install | bash` under Android Bun
- Wrapper: `export LD_PRELOAD=` (empty) + `ld-linux --preload` DNS shim
  (`libinvapp-opencode-shim.so`) — same as OpenCode
- Needs Termux **glibc ≥ 2.34** for `pty.node` (`pkg upgrade glibc` if needed)
- Sandbox is disabled on setup (`agent sandbox disable`) — Linux sandbox
  primitives are unreliable on Android
- Outbound hosts: `api2.cursor.sh`, `api2direct.cursor.sh`, `downloads.cursor.com`

## Troubleshooting

### `Cannot find module 'tree-sitter'`

`index.js` loads `tree-sitter` / `tree-sitter-bash` from
`node_modules/` next to it (webpack externals). A partial extract that only
has `node` + `index.js` triggers this. `td-upgrade` used to only `--wrap-only`
after a failed `agent update`, which never repaired the tree.

```bash
ls ~/.local/share/cursor-agent/versions/*/node_modules/tree-sitter/package.json
# if missing:
rm -rf ~/.local/share/cursor-agent/versions/2026.09.26-dd393fe
cursor-agent-setup          # full re-download; do NOT use --wrap-only here
agent --version
```

Do not `npm install tree-sitter` into that tree under Bun — use the official
tarball only. After the next APK, `td-upgrade` / `--wrap-only` auto-re-extract
when `tree-sitter` is missing.
