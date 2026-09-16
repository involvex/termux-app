# AI helper (OpenCode)

InVxTermux can attach [OpenCode](https://github.com/anomalyco/opencode) as a web
UI and open it in Preview.

## Setup + start

```bash
opencode-setup          # once — downloads official linux tarball + glibc wrapper
td-ai                   # starts web UI on :4096 (default)
```

Drawer → **AI** probes missing / installed / ready; starts only when needed.
Long-press **AI** or drawer **Stop AI** stops listeners on `:4096`.

Optional pin: `OPENCODE_VERSION=v1.18.31 opencode-setup`

## Rules of thumb

- Setup downloads the GitHub `opencode-linux-*.tar.gz` — **not**
  `bun install -g opencode-ai` and **not** `curl … opencode.ai/install`
- Wrapper clears `LD_PRELOAD=` (empty) so the path redirector does not reinject
  into the glibc binary
- OpenCode is **not** baked into the APK; it installs into `$PREFIX` on demand

If you see a leftover “postinstall script was not run” stub:

```bash
rm -f $PREFIX/bin/opencode
opencode-setup
```
