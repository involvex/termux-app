# RUN_COMMAND API (InVxTermux)

Third-party apps, Tasker, home-screen widgets, and **AI agents** can run shell
commands inside InVxTermux via the `RUN_COMMAND` intent.

Upstream semantics:
[termux/termux-app wiki — RUN_COMMAND Intent](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).
**Do not copy stock `com.termux` package names or `/data/data/com.termux/…`
paths** — this fork uses different ids and filesystem roots.

Offline in-app: drawer **Help** → **RUN_COMMAND**, or `invapp://help/run-command`.

## Stock Termux vs this fork

| | Stock Termux | **InVxTermux (this fork)** |
|---|---|---|
| App name | Termux | InVxTermux |
| `applicationId` / package | `com.termux` | `com.involvex.termux_app` |
| Java / AGP `namespace` | `com.termux` | `com.invapp` |
| Permission | `com.termux.permission.RUN_COMMAND` | `com.involvex.termux_app.permission.RUN_COMMAND` |
| Intent action | `com.termux.RUN_COMMAND` | `com.involvex.termux_app.RUN_COMMAND` |
| Extra key prefix | `com.termux.RUN_COMMAND_*` | `com.involvex.termux_app.RUN_COMMAND_*` |
| Service component | `com.termux/com.termux.app.RunCommandService` | `com.involvex.termux_app/com.invapp.app.RunCommandService` |
| Files / `$PREFIX` | `/data/data/com.termux/files` · `…/usr` | `/data/data/com.involvex.termux_app/files` · `…/usr` |
| `$HOME` | `…/files/home` | `…/files/home` (same layout, different package) |
| Default cwd (sessions) | `$HOME` | `~/repos` (`$HOME/repos`) |
| Docs | Upstream wiki | **This page** + [GitHub Pages](https://involvex.github.io/termux-app/run-command/) |

Constants live in
`termux-shared` → `com.invapp.shared.termux.TermuxConstants`
(`TERMUX_PACKAGE_NAME`, `TERMUX_APP_JAVA_PACKAGE_NAME`,
`TERMUX_APP.RUN_COMMAND_SERVICE.*`). Prefer importing that class over hardcoding.

## Preconditions

1. InVxTermux is installed (same signature set as any plugin that calls it).
2. Caller holds `com.involvex.termux_app.permission.RUN_COMMAND`
   (`dangerous` — user must grant in App info / runtime prompt).
3. In `~/.termux/termux.properties` (inside the InVxTermux environment):

   ```properties
   allow-external-apps=true
   ```

   Then reopen the app (or trigger a style reload) so properties are reread.
   Default is `false`; without it, external `RUN_COMMAND` calls are rejected
   (forced error notification).

4. Use **absolute paths under this fork’s files tree** for
   `RUN_COMMAND_PATH` / `RUN_COMMAND_WORKDIR` / result directories.
   Relative paths and stock `com.termux` paths fail.

## Intent schema (fork values)

| Field | Value |
|---|---|
| Component package | `com.involvex.termux_app` |
| Component class | `com.invapp.app.RunCommandService` |
| Action | `com.involvex.termux_app.RUN_COMMAND` |

### Extras (all use the fork package prefix)

| Extra | Type | Required | Notes |
|---|---|---|---|
| `…RUN_COMMAND_PATH` | `String` | **yes** | Absolute executable, e.g. `$PREFIX/bin/bash` |
| `…RUN_COMMAND_ARGUMENTS` | `String[]` | no | Argv after the executable |
| `…RUN_COMMAND_WORKDIR` | `String` | no | Absolute cwd; prefer `$HOME/repos/…` |
| `…RUN_COMMAND_STDIN` | `String` | no | Stdin (useful with `sh -c` / background) |
| `…RUN_COMMAND_BACKGROUND` | `boolean` | no | **Deprecated** — prefer `RUN_COMMAND_RUNNER` |
| `…RUN_COMMAND_RUNNER` | `String` | no | `app-shell` (background) or `terminal-session` (foreground) |
| `…RUN_COMMAND_SESSION_ACTION` | `String` | no | `"0"`–`"3"` — session switch / open activity (terminal runner) |
| `…RUN_COMMAND_SHELL_NAME` | `String` | no | Named session / shell |
| `…RUN_COMMAND_SHELL_CREATE_MODE` | `String` | no | `always` or `no-shell-with-name` |
| `…RUN_COMMAND_COMMAND_LABEL` | `String` | no | Shown in notifications / UI |
| `…RUN_COMMAND_PENDING_INTENT` | `PendingIntent` | no | Result callback (plugins) |
| `…RUN_COMMAND_RESULT_DIRECTORY` | `String` | no | Dir for result files (background) |
| `…RUN_COMMAND_RESULT_SINGLE_FILE` | `boolean` | no | One file vs `stdout`/`stderr`/`exit_code`/… |
| `…RUN_COMMAND_RESULT_FILE_BASENAME` | `String` | no | When single-file |
| `…RUN_COMMAND_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS` | `boolean` | no | For `am` comma escaping |
| `…RUN_COMMAND_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS` | `String` | no | Default alternative is `‚` (U+201A) |

Full path strings (substitute into extras / argv):

```text
FILES=/data/data/com.involvex.termux_app/files
PREFIX=$FILES/usr
HOME=$FILES/home
BIN=$PREFIX/bin
REPOS=$HOME/repos
```

### Session action values (`terminal-session`)

| Value | Behavior |
|---|---|
| `0` | Switch to new session + open activity |
| `1` | Keep current session + open activity |
| `2` | Switch to new session, do not open activity |
| `3` | Keep current session, do not open activity |

## Paths and exec rules

- Runnable projects belong under **`~/repos`** (`$HOME/repos`). Shared storage
  (`~/storage/shared`, `/storage/emulated/0/…`) is often **noexec** — binaries
  and scripts there will fail with `Permission denied`.
- Prefer `$PREFIX/bin/bash` or `$PREFIX/bin/sh` as `RUN_COMMAND_PATH`, then pass
  `-c` / script paths as arguments — same pattern as the Terminal Widget.
- The path redirector maps many hardcoded `com.termux` paths **inside** the
  Termux environment; **intent extras are not rewritten**. Callers must send
  InVx paths.

## Examples

### `adb` — foreground session (`echo` in `~/repos`)

```bash
PKG=com.involvex.termux_app
FILES=/data/data/$PKG/files
adb shell am start-foreground-service \
  -n $PKG/com.invapp.app.RunCommandService \
  -a $PKG.RUN_COMMAND \
  --es $PKG.RUN_COMMAND_PATH "$FILES/usr/bin/bash" \
  --esa $PKG.RUN_COMMAND_ARGUMENTS "-lc,echo hello from RUN_COMMAND; pwd" \
  --es $PKG.RUN_COMMAND_WORKDIR "$FILES/home/repos" \
  --ez $PKG.RUN_COMMAND_BACKGROUND false \
  --es $PKG.RUN_COMMAND_SESSION_ACTION "0"
```

### `adb` — background with result directory

```bash
PKG=com.involvex.termux_app
FILES=/data/data/$PKG/files
OUT=$FILES/home/run_command_out
adb shell mkdir -p "$OUT"
adb shell am start-foreground-service \
  -n $PKG/com.invapp.app.RunCommandService \
  -a $PKG.RUN_COMMAND \
  --es $PKG.RUN_COMMAND_PATH "$FILES/usr/bin/bash" \
  --esa $PKG.RUN_COMMAND_ARGUMENTS "-lc,uname -a; date" \
  --es $PKG.RUN_COMMAND_WORKDIR "$FILES/home/repos" \
  --es $PKG.RUN_COMMAND_RUNNER "app-shell" \
  --es $PKG.RUN_COMMAND_RESULT_DIRECTORY "$OUT" \
  --ez $PKG.RUN_COMMAND_RESULT_SINGLE_FILE false \
  --es $PKG.RUN_COMMAND_COMMAND_LABEL "agent-uname"
# Then read $OUT/stdout , $OUT/stderr , $OUT/exit_code (names may include suffix)
```

### Android / Kotlin (plugin or companion app)

```kotlin
val pkg = "com.involvex.termux_app"
val files = "/data/data/$pkg/files"
val intent = Intent().apply {
    setClassName(pkg, "com.invapp.app.RunCommandService")
    action = "$pkg.RUN_COMMAND"
    putExtra("$pkg.RUN_COMMAND_PATH", "$files/usr/bin/bash")
    putExtra("$pkg.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", "td-sync"))
    putExtra("$pkg.RUN_COMMAND_WORKDIR", "$files/home/repos")
    putExtra("$pkg.RUN_COMMAND_RUNNER", "app-shell")
    putExtra("$pkg.RUN_COMMAND_COMMAND_LABEL", "td-sync")
}
context.startForegroundService(intent) // or startService on older APIs
```

Prefer `TermuxConstants` from this fork’s `termux-shared` when the caller is
built against the same tree.

### InVx Terminal Widget

Package `com.involvex.termux_app.terminalwidget` — grant **RUN_COMMAND**, open
once, then set a shell command. It already targets the fork constants. See
[Install → plugins](install.md#plugins-api--widget).

---

## Cursor / agent skills

For any project on a machine that has these skills installed:

| Skill | Use when |
|---|---|
| `ivx-termux-setup` | First-time wiring: AGENTS snippet, `allow-external-apps`, no `com.termux` leftovers |
| `ivx-termux-api` | Invoke `RUN_COMMAND` (adb templates, extras, result files) |

Canonical copies live in this repo under [`skills/`](https://github.com/involvex/termux-app/tree/master/skills)
(`skills/ivx-termux-setup`, `skills/ivx-termux-api`). Install into
`~/.agents/skills/` (or your Cursor user Agent Store `skills/`) so every
workspace can load them by name.

## For AI agents (copy into other projects)

Paste the block below into another repo’s `AGENTS.md` / skill / system prompt
when the agent must drive InVxTermux on a device (via `adb`, Tasker, or a
companion APK). Keep it verbatim unless the fork package id changes.
Prefer loading skill **`ivx-termux-setup`** (which pastes the same brief)
instead of hand-copying when the skill is available.

````markdown
### InVxTermux RUN_COMMAND (agents)

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
````

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Nothing happens / silent fail | `allow-external-apps` still `false`, or properties not reloaded |
| Permission denial | Caller missing `com.involvex.termux_app.permission.RUN_COMMAND` |
| `Unable to resolve Intent` / component not found | Wrong package or class — must be `…/com.invapp.app.RunCommandService` |
| Command not found / bad interpreter | Stock `com.termux` path in `RUN_COMMAND_PATH`, or relative path |
| `Permission denied` executing project | Project on shared storage (noexec) — move to `~/repos` |
| Results empty | Background runner without `RESULT_DIRECTORY` / PendingIntent; or wrong out path |
| Stock Tasker / Termux plugin fails | Plugin built for `com.termux` — needs fork package + same signature |

More: [Troubleshooting](troubleshooting.md) · upstream intent details (names differ):
[RUN_COMMAND-Intent wiki](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).
