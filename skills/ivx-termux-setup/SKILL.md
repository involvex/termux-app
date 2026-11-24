---
name: ivx-termux-setup
description: >-
  Wire any project to InVxTermux (com.involvex.termux_app): AGENTS.md snippet,
  allow-external-apps, fork package/paths, RUN_COMMAND preconditions. Use when
  setting up Termux automation, phone agents, involvex/termux-app, InVxTermux,
  or when a project must call Termux without stock com.termux ids.
---

# InVxTermux setup (any project)

Stock Termux docs (`com.termux`) are **wrong** for this fork. Always use
InVxTermux ids below. Full reference:
https://involvex.github.io/termux-app/run-command/

Companion skill for invoking commands: **`ivx-termux-api`**.

## Hard constants (do not invent)

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

**Never** write `com.termux` or `/data/data/com.termux/…` into project docs,
skills, Tasker, or `adb` templates.

## Workflow — wire a project

Copy and track:

```
Setup progress:
- [ ] 1. Confirm InVxTermux installed (not stock Termux)
- [ ] 2. Paste AGENTS snippet into project AGENTS.md (or skill)
- [ ] 3. Human enables allow-external-apps + RUN_COMMAND permission
- [ ] 4. Point callers at ivx-termux-api for execution
```

### 1. Confirm install

Ask the human if needed. Package must be `com.involvex.termux_app`.
Plugins must be `com.involvex.termux_app.*` with the **same signature**
(stock F-Droid Termux:API / Widget will not attach).

### 2. Paste agent brief

Append [agents-snippet.md](agents-snippet.md) into the project's `AGENTS.md`
(or a project skill). Do not paraphrase the constant table.

If the project already has a Termux section with `com.termux`, **replace** it.

### 3. Human device gates (agent does not skip these)

Inside InVxTermux shell (or instruct the human):

```bash
mkdir -p ~/.termux
grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null \
  || echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

Then reopen InVxTermux so properties reload.

Caller app / automation must hold
`com.involvex.termux_app.permission.RUN_COMMAND` (user grants in App info).

### 4. Paths for projects on device

- Runnable code → `~/repos` (`{HOME}/repos`)
- Shared storage (`~/storage/shared`, `/storage/emulated/0`) is often **noexec**
- Intent extras are **not** rewritten by the path redirector — send InVx paths

### 5. adb / install policy

Do **not** run `adb install`, `am start`, `monkey`, or `am start-foreground-service`
unless the human explicitly asked — they may be using the phone.
Generate the command; let them run it (or wait for approval).

## Done when

- Project AGENTS (or skill) contains the InVx constant table
- No `com.termux` package/path leftovers in that section
- Human confirmed `allow-external-apps=true` + permission (or documented as TODO)
- Execution work hands off to **`ivx-termux-api`**

## Links

- Docs: https://involvex.github.io/termux-app/run-command/
- Repo: https://github.com/involvex/termux-app
- Snippet: [agents-snippet.md](agents-snippet.md)
