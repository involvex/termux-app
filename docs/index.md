# InVxTermux

Unofficial Android terminal fork for **phone + PC** development — stay in the
shell, clone under `~/repos`, run Bun projects, preview localhost servers, and
optionally attach an AI CLI.

| | |
|---|---|
| Package id | `com.involvex.termux_app` |
| Latest | **v0.200.0** |
| Repo | [involvex/termux-app](https://github.com/involvex/termux-app) |
| Releases | [GitHub Releases](https://github.com/involvex/termux-app/releases) |

!!! warning "Unofficial fork"
    InVxTermux is based on [Termux](https://termux.dev) /
    [`termux/termux-app`](https://github.com/termux/termux-app) (GPLv3).
    **Not affiliated** with the Termux maintainers. Linux packages inside the
    environment still come from
    [`termux/termux-packages`](https://github.com/termux/termux-packages).

## Quick start

1. [Install](install.md) the APK from GitHub Releases
2. Open the app — session starts in `~/repos`
3. Scaffold or clone, then run a dev server
4. Drawer → **Preview** → Scan

```bash
td-scaffold myapp react
td-dev
# Drawer → Preview → 5173
```

## What's different

- Bundled **Android Bun** + path redirector for apt/SSH
- **Localhost Preview** with port chips and Copy LAN
- Helpers: `td-scaffold`, `td-dev`, `td-clone`, `td-ai`
- Hacker theme (matrix green on black)

## Sponsors

[GitHub Sponsors](https://github.com/sponsors/involvex) ·
[Buy Me a Coffee](https://buymeacoffee.com/involvex) ·
[PayPal](https://paypal.me/involvex) ·
[OpenCode](https://opencode.ai/go?ref=XS9FHCZT4C) ·
[Microsoft Rewards](https://rewards.bing.com/welcome?rh=14525F68&ref=rafsrchae&form=ML2XE3&OCID=ML2XE3&PUBL=RewardsDO&CREA=ML2XE3)

More on [About / License](about.md).
