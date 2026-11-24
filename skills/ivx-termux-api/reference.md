# RUN_COMMAND extras (InVxTermux)

Prefix every extra with `com.involvex.termux_app.` (shown as `…` below).

| Extra | Type | Required | Notes |
|---|---|---|---|
| `…RUN_COMMAND_PATH` | String | yes | Absolute executable, e.g. `{PREFIX}/bin/bash` |
| `…RUN_COMMAND_ARGUMENTS` | String[] | no | Argv; `am --esa` splits on `,` |
| `…RUN_COMMAND_WORKDIR` | String | no | Absolute cwd; prefer `{HOME}/repos` |
| `…RUN_COMMAND_STDIN` | String | no | Stdin for background / `sh` |
| `…RUN_COMMAND_BACKGROUND` | boolean | no | Deprecated — use `RUNNER` |
| `…RUN_COMMAND_RUNNER` | String | no | `app-shell` or `terminal-session` |
| `…RUN_COMMAND_SESSION_ACTION` | String | no | `"0"`–`"3"` (terminal runner) |
| `…RUN_COMMAND_SHELL_NAME` | String | no | Named shell/session |
| `…RUN_COMMAND_SHELL_CREATE_MODE` | String | no | `always` \| `no-shell-with-name` |
| `…RUN_COMMAND_COMMAND_LABEL` | String | no | Notification / UI label |
| `…RUN_COMMAND_PENDING_INTENT` | PendingIntent | no | Plugin result callback |
| `…RUN_COMMAND_RESULT_DIRECTORY` | String | no | Background result files |
| `…RUN_COMMAND_RESULT_SINGLE_FILE` | boolean | no | One file vs split outputs |
| `…RUN_COMMAND_RESULT_FILE_BASENAME` | String | no | When single-file |
| `…RUN_COMMAND_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS` | boolean | no | For `am` comma escaping |
| `…RUN_COMMAND_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS` | String | no | Default alt comma: `‚` (U+201A) |

## Paths

```text
FILES=/data/data/com.involvex.termux_app/files
PREFIX=$FILES/usr
HOME=$FILES/home
BIN=$PREFIX/bin
REPOS=$HOME/repos
```

## Upstream vs fork

Intent *behavior* matches upstream Termux. Names and paths do **not**.
Upstream wiki (stock ids only):
https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
