# AI helper (OpenCode V2)

InVxTermux attaches [OpenCode V2](https://opencode.ai/v2/docs/) as a web
UI and opens it in Preview. Server exposure rules — see skill
`opencode-v2-serve`: one foreground server only (`opencode serve`,
never background service + serve), bind `0.0.0.0`, fixed port, stable
password from `~/.config/opencode/service.json`, verify with
`curl -u opencode:<pw> http://127.0.0.1:4096/api/info` (must return
JSON `{data:...}`, not HTML — HTML means V1 routes). No mDNS in V2,
plain HTTP for LAN/MyFritz.

## Setup + start

```bash
opencode-setup          # once — official V2 linux tarball + glibc wrapper
td-ai                   # serve 0.0.0.0:4096; Preview → http://127.0.0.1:4096/
```

Drawer → **AI** probes missing / installed / ready (`GET /api/info` with
`service.json` Basic auth, falling back to V1 `GET /global/health`);
starts only when needed; opens Preview **after** health succeeds.
Long-press **AI** or drawer **Stop AI** stops listeners on `:4096`.
`td-ai` also runs `opencode pair --url <lan-url>` when supported
so overlay clients auto-connect (V2 pairing; skipped on V1 binaries).

Optional pin: `OPENCODE_VERSION=2.0.6 opencode-setup`
(empty = live lookup via `https://opencode.ai/update/api/latest/cli/npm`,
fallback `2.0.6`; `1.x` still resolves to the old GitHub
`anomalyco/opencode` tarball for rollback).
Flags mirror upstream: `opencode-setup --version 2.0.6`,
`opencode-setup --binary /path/to/opencode`.

## Endpoints (`:4096` loopback; Preview also accepts a pasted URL)

| URL | Purpose |
|-----|---------|
| `http://127.0.0.1:4096/` | Web UI (Preview) |
| `http://127.0.0.1:4096/api/info` | V2 `{ data: ... version }` (Basic `opencode:<service.json password>`) |
| `http://127.0.0.1:4096/global/health` | V1 `{ healthy: true, version }` fallback |
| `http://127.0.0.1:4096/doc` | OpenAPI 3.1 |

Preview accepts a bare port (`4096`), `host:port`, or a full loopback URL
(`http://127.0.0.1:4096/path`). Non-loopback hosts pasted there load the same
port locally and surface **Copy LAN** for the other device.

## Rules of thumb

- Setup downloads the official V2 `opencode-linux-*.tar.gz` from
  `https://opencode.ai/files/bin/<version>/` — **not**
  `bun install -g opencode-ai` and **not** piping
  `curl … opencode.ai/v2/install | bash` directly (that script targets
  `~/.opencode/bin` without the glibc/`ld-linux --preload` wrapper;
  `opencode-setup` replicates its tarball step + wrapper)
- Wrapper clears `LD_PRELOAD=` (empty) so termux-exec does **not** reinject
  Bionic `libinvapp-redirector.so` (that causes `version \`LIBC' not found`
  under glibc). The DNS shim is loaded with `ld-linux --preload` instead.
- **Never** `export PATH=$PREFIX/glibc/bin:$PATH` — those ELFs break the shell
  (`Permission denied` on `grep`/`ls`/`gcc`).
- Wrapper sets `SSL_CERT_FILE` / `NODE_EXTRA_CA_CERTS` to Termux’s CA bundle
- Bind **`0.0.0.0`** by default so other devices on the same Wi‑Fi can open
  OpenCode (drawer **Preview → Copy LAN**). In-app Preview stays on
  `127.0.0.1:4096`. Never `--mdns`. Override: `OPENCODE_HOST=127.0.0.1 td-ai`.
  Firewall/VPN may block LAN; `getifaddrs` log noise is non-fatal for bind.
- OpenCode is **not** baked into the APK; it installs into `$PREFIX` on demand
- Contrast: [and-code](https://github.com/yugahashimoto/and-code) runs the
  **musl** OpenCode tarball inside Alpine via **proot**. We stay on glibc +
  `ld-linux --preload` DNS shim (do **not** put the shim in `LD_PRELOAD`).
- If you see `version \`LIBC' not found` from `libinvapp-redirector.so`, the
  wrapper was overwritten — reinstall the `--preload` wrapper (next APK, or
  avoid re-running an old `opencode-setup` until then).

If you see a leftover “postinstall script was not run” stub:

```bash
rm -f $PREFIX/bin/opencode
opencode-setup
```

### “Cannot connect to API” / Failed to fetch models.dev

`curl` (Bionic) can succeed while OpenCode (glibc) still fails — glibc DNS
looks at hardcoded `com.termux` paths. Fix = ship/load the shim (not more
`resolv.conf` copies alone):

```bash
ls -la $PREFIX/lib/libinvapp-opencode-shim.so   # must exist after app update
# quick probe (must print IPs):
LD_PRELOAD=$PREFIX/lib/libinvapp-opencode-shim.so \
  $PREFIX/glibc/lib/ld-linux-aarch64.so.1 --library-path $PREFIX/glibc/lib \
  $PREFIX/glibc/bin/getent hosts models.opencode.ai

mkdir -p "$PREFIX/glibc/etc/ssl/certs"
printf '%s\n' 'nameserver 8.8.8.8' 'nameserver 1.1.1.1' | tee \
  "$PREFIX/etc/resolv.conf" "$PREFIX/glibc/etc/resolv.conf"
ln -sfn "$PREFIX/etc/tls/cert.pem" \
  "$PREFIX/glibc/etc/ssl/certs/ca-certificates.crt"
export SSL_CERT_FILE=$PREFIX/etc/tls/cert.pem
export NODE_EXTRA_CA_CERTS=$PREFIX/etc/tls/cert.pem

# ensure wrapper loads the shim (re-run after APK update):
head -n 30 $PREFIX/bin/opencode | grep -F libinvapp-opencode-shim
rm -rf ~/.cache/opencode
cd ~/repos/copilot
pkill -f opencode 2>/dev/null || true
td-ai
curl -s http://127.0.0.1:4096/global/health
```

### AI_APICallError / models.dev / Kilo “Unable to connect”

Local `:4096` is fine. Provider HTTPS fails when glibc DNS fails. Confirm the
shim is loaded by the wrapper, then:

```bash
opencode-fix-net
ls -la $PREFIX/lib/libinvapp-opencode-shim.so
rm -rf ~/.cache/opencode
cd ~/repos/copilot && pkill -f opencode; td-ai
```

Stay in a project dir (not `/` or `~`) — FFF/file picker breaks on root/home.

### `getifaddrs returned an error`

Android 13+ may log this when probing interfaces. It is **non-fatal** for a
plain `0.0.0.0` bind (what `td-ai` does). Never pass `--mdns`. For loopback-only:

```bash
OPENCODE_HOST=127.0.0.1 td-ai
# or:
opencode serve --port 4096 --hostname 127.0.0.1 --print-logs
```

LAN clients: same Wi‑Fi → `http://<phone-ip>:4096/` (or Preview **Copy LAN**).
Firewall / VPN / client isolation on the AP may still block access.

## Cursor Agent CLI (separate from OpenCode)

For Cursor cloud **My Machines** (`agent worker start`), see
[Cursor Agent](cursor-agent.md). Drawer AI stays OpenCode.