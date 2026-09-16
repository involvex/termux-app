# Attribution

Based on [Termux Terminal Widget](https://codeberg.org/gardockt/termux-terminal-widget)
by gardockt (GPLv3). Adapted for InVxTermux:

- `applicationId` `com.involvex.termux_app.terminalwidget`
- Local `project(":termux-shared")` (`com.invapp.shared`) instead of JitPack
- `RUN_COMMAND` permission / package queries retargeted to `com.involvex.termux_app`
- Signed with the same public test key as the main app / Termux:API

This is **not** classic Termux:Widget (`com.involvex.termux_app.widget` /
`~/.shortcuts` one-tap scripts). That remains a separate Phase 2 module.
