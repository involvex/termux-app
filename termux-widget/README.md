# Termux:Widget (InVxTermux)

Classic home-screen / launcher shortcuts for scripts under `~/.shortcuts`
(and `tasks/`). Adapted from [involvex/termux-widget](https://github.com/involvex/termux-widget)
(upstream [termux/termux-widget](https://github.com/termux/termux-widget)).

| | |
|---|---|
| `applicationId` | `com.involvex.termux_app.widget` |
| Java namespace | `com.invapp.widget` |
| `sharedUserId` | `com.involvex.termux_app` (same cert as main app) |

Build:

```bash
./gradlew :termux-widget:assembleDebug
```

Not the same as `:termux-terminal-widget` (command-output display widget).
