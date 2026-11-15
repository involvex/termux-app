# AI helper (OpenCode V2)

InVxTermux attaches [OpenCode V2](https://opencode.ai/v2/docs/) as a web
UI and opens it in Preview. Prefer the **background service**
(`opencode service start` via `opencode-server` / `td-ai`), bind
`0.0.0.0`, fixed port **4000**, stable password, verify with
`curl -u opencode:<pw> http://127.0.0.1:4000/api/info` (must return
JSON `{data:...}`, not HTML — HTML means V1 routes). No mDNS in V2,
plain HTTP for LAN/MyFritz.

Do **not** keep `opencode-v2` / `opencode-v2-server` shims (they point at
`~/.opencode/bin` and break under this package id). The only CLI is
`$PREFIX/bin/opencode` from `opencode-setup`.

## Setup + start

```bash
opencode-setup          # once — official V2 linux tarball + glibc wrapper
td-ai                   # alias → opencode-server (service 0.0.0.0:4000)
# or: opencode-server [port]
# Preview → http://127.0.0.1:4000/
opencode-server stop    # or: td-ai-stop / drawer → Stop AI
```

Drawer → **AI** probes missing / installed / ready (`GET /api/info` with
Basic auth, falling back to V1 `GET /global/health`); starts only when
needed; opens Preview **after** health succeeds.
Long-press **AI** or drawer **Stop AI** stops the service on `:4000`.

Optional pin: `OPENCODE_VERSION=2.0.6 opencode-setup`
(empty = live lookup via `https://opencode.ai/update/api/latest/cli/npm`,
fallback `2.0.6`; `1.x` still resolves to the old GitHub
`anomalyco/opencode` tarball for rollback).
Flags mirror upstream: `opencode-setup --version 2.0.6`,
`opencode-setup --binary /path/to/opencode`.

## Endpoints (`:4000` loopback; Preview also accepts a pasted URL)

| URL | Purpose |
|-----|---------|
| `http://127.0.0.1:4000/` | Web UI (Preview) |
| `http://127.0.0.1:4000/api/info` | V2 `{ data: ... version }` (Basic `opencode:<password>`) |
| `http://127.0.0.1:4000/global/health` | V1 `{ healthy: true, version }` fallback |
| `http://127.0.0.1:4000/doc` | OpenAPI 3.1 |

Preview accepts a bare port (`4000`), `host:port`, or a full loopback URL
(`http://127.0.0.1:4000/path`). Non-loopback hosts pasted there load the same
port locally and surface **Copy LAN** / **QR LAN** for the other device.

## Rules of thumb

- Setup downloads the official V2 `opencode-linux-*.tar.gz` from
  `https://opencode.ai/files/bin/<version>/` — **not**
  `bun install -g opencode-ai` and **not** piping
  `curl … opencode.ai/v2/install | bash` directly (that script targets
  `~/.opencode/bin` without the glibc/`ld-linux --preload` wrapper;
  `opencode-setup` replicates its tarball step + wrapper and removes
  leftover `opencode-v2*` shims)
- Wrapper clears `LD_PRELOAD=` (empty) so termux-exec does **not** reinject
  Bionic `libinvapp-redirector.so` (that causes `version \`LIBC' not found`
  under glibc). The DNS shim is loaded with `ld-linux --preload` instead.
- **Never** `export PATH=$PREFIX/glibc/bin:$PATH` — those ELFs break the shell
  (`Permission denied` on `grep`/`ls`/`gcc`).
- Wrapper sets `SSL_CERT_FILE` / `NODE_EXTRA_CA_CERTS` to Termux’s CA bundle
- Bind **`0.0.0.0`** by default so other devices on the same Wi‑Fi can open
  OpenCode (drawer **Preview → Copy LAN / QR LAN**). In-app Preview stays on
  `127.0.0.1:4000`. Never `--mdns`. Override: `OPENCODE_HOST=127.0.0.1 td-ai`.
  Firewall/VPN may block LAN; `getifaddrs` log noise is non-fatal for bind.
- OpenCode is **not** baked into the APK; it installs into `$PREFIX` on demand
- Prefer **one** server: background `opencode service` *or* foreground
  `opencode serve` — not both. `opencode-server` uses the service when
  available and falls back to foreground `serve`.

If you see a leftover “postinstall script was not run” stub:

```bash
rm -f $PREFIX/bin/opencode $PREFIX/bin/opencode-v2 $PREFIX/bin/opencode-v2-server
opencode-setup
```

### “Cannot connect to API” / Failed to fetch models.dev

If `curl` works but OpenCode cannot reach `models.dev` /
`models.opencode.ai`, glibc DNS is usually broken (stock glibc hardcodes
`/data/data/com.termux/.../glibc/etc/*`):

```bash
opencode-fix-net      # or: reopen app so setup reseeds CA/resolv + shim
ls -l $PREFIX/lib/libinvapp-opencode-shim.so
```

Ready check: `GET http://127.0.0.1:4000/api/info` (not `:5000/api`).
