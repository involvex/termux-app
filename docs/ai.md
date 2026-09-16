# AI helper (OpenCode)

InVxTermux can attach [OpenCode](https://github.com/anomalyco/opencode) as a web
UI and open it in Preview. Server API docs:
[opencode.ai/docs/server](https://opencode.ai/docs/server/).

## Setup + start

```bash
opencode-setup          # once — downloads official linux tarball + glibc wrapper
td-ai                   # starts web UI on http://127.0.0.1:4096/ (default)
```

Drawer → **AI** probes missing / installed / ready (`GET /global/health`);
starts only when needed; opens Preview **after** health succeeds.
Long-press **AI** or drawer **Stop AI** stops listeners on `:4096`.

Optional pin: `OPENCODE_VERSION=v1.18.31 opencode-setup`

## Endpoints (do not use `:5000` or `/api`)

| URL | Purpose |
|-----|---------|
| `http://127.0.0.1:4096/` | Web UI (Preview) |
| `http://127.0.0.1:4096/global/health` | `{ healthy: true, version }` |
| `http://127.0.0.1:4096/doc` | OpenAPI 3.1 |

There is **no** `/api` mount — sessions live under `/session`, etc.

## Rules of thumb

- Setup downloads the GitHub `opencode-linux-*.tar.gz` — **not**
  `bun install -g opencode-ai` and **not** `curl … opencode.ai/install`
- Wrapper clears `LD_PRELOAD=` (empty) so termux-exec does **not** reinject
  Bionic `libinvapp-redirector.so` (that causes `version \`LIBC' not found`
  under glibc). The DNS shim is loaded with `ld-linux --preload` instead.
- **Never** `export PATH=$PREFIX/glibc/bin:$PATH` — those ELFs break the shell
  (`Permission denied` on `grep`/`ls`/`gcc`).
- Wrapper sets `SSL_CERT_FILE` / `NODE_EXTRA_CA_CERTS` to Termux’s CA bundle
- Bind **`127.0.0.1`** only — never `--mdns` / `0.0.0.0` on stock Android
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

Android 13+ blocks netlink. Use loopback (what `td-ai` does):

```bash
opencode web --port 4096 --hostname 127.0.0.1 --print-logs
```
