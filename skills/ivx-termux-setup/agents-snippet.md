# InVxTermux RUN_COMMAND (agents)

You are talking to **InVxTermux**, not stock Termux.

**Never** use `com.termux`, `/data/data/com.termux/…`, or stock wiki extras.
Those target a different app and will fail or hit the wrong install.

| Item | Value |
|------|--------|
| Package / applicationId | `com.involvex.termux_app` |
| Java class package | `com.invapp` |
| Permission | `com.involvex.termux_app.permission.RUN_COMMAND` |
| Action | `com.involvex.termux_app.RUN_COMMAND` |
| Service | `com.involvex.termux_app/com.invapp.app.RunCommandService` |
| FILES | `/data/data/com.involvex.termux_app/files` |
| PREFIX | `{FILES}/usr` |
| HOME | `{FILES}/home` |
| Prefer cwd | `{HOME}/repos` |
| Docs | https://involvex.github.io/termux-app/run-command/ |

**Hard rules**

1. Extra keys are `{package}.RUN_COMMAND_*` with package
   `com.involvex.termux_app` (not `com.termux`, not `com.invapp`).
2. `RUN_COMMAND_PATH` must be an **absolute** executable under `{PREFIX}/bin`
   (usually `{PREFIX}/bin/bash`). Do not pass a bare command name.
3. Put projects and scripts under `{HOME}/repos`. Do not exec from
   `/storage/emulated/0` or `{HOME}/storage/shared` (noexec).
4. Caller needs the RUN_COMMAND permission **and**
   `allow-external-apps=true` in `{HOME}/.termux/termux.properties`.
5. Prefer `RUN_COMMAND_RUNNER=app-shell` for non-interactive agent work;
   use `terminal-session` only when the user should see a session.
6. For results without a PendingIntent, set `RUN_COMMAND_RESULT_DIRECTORY`
   under `{HOME}/…` and read `stdout` / `stderr` / `exit_code` files.
7. Do not run `adb install` / `am start` / `monkey` unless the human asked —
   they may be using the phone.
8. Do not invent package ids. If unsure, re-read the docs URL above.
9. Stock Termux:API / Widget F-Droid APKs will **not** attach to this fork;
   plugins must be `com.involvex.termux_app.*` with the same signature.
10. Inside the environment, helpers like `td-sync`, `td-dev`, `td-ai`,
    `ivx-doctor` exist — call them via `bash -lc '…'` after setup, still
    with InVx paths in the intent.

**Minimal adb template**

```bash
PKG=com.involvex.termux_app
FILES=/data/data/$PKG/files
adb shell am start-foreground-service \
  -n $PKG/com.invapp.app.RunCommandService \
  -a $PKG.RUN_COMMAND \
  --es $PKG.RUN_COMMAND_PATH "$FILES/usr/bin/bash" \
  --esa $PKG.RUN_COMMAND_ARGUMENTS "-lc,YOUR_COMMAND_HERE" \
  --es $PKG.RUN_COMMAND_WORKDIR "$FILES/home/repos" \
  --es $PKG.RUN_COMMAND_RUNNER "app-shell" \
  --es $PKG.RUN_COMMAND_RESULT_DIRECTORY "$FILES/home/agent-out" \
  --ez $PKG.RUN_COMMAND_RESULT_SINGLE_FILE false \
  --es $PKG.RUN_COMMAND_COMMAND_LABEL "agent"
```

Replace `YOUR_COMMAND_HERE` only; keep `PKG` / `FILES` / extra names unchanged.

For invocation details, load skill **`ivx-termux-api`**.
