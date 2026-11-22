# InVxTermux — Feature Suggestions Report

> Generated: 2026-09-22 · Updated: 2026-11-22 (Phase 22) · Scope: `com.involvex.termux_app`
> Pillars: Terminal-first · PC ↔ phone (`~/repos`) · Preview (`127.0.0.1`) · AI CLI (`td-ai` → OpenCode `:4000`)
> Non-goals respected: no full in-app IDE / multi-tab browser, no public tunnels by default, no per-npm wrappers.
> Sources: `README.md`, `ROADMAP.md`, `AGENTS.md`, `docs/*.md`, `app/build.gradle`, `app/src/main/java/com/invapp/...`, `terminal-emulator`, `termux-shared`, `termux-api`.

## Feature Suggestions Report

### Done (Phase 17–20 / earlier)

| ID | Status | Notes |
|----|--------|-------|
| FEAT-002 | **done** | Recent/starred ports, 3s auto-scan, swipe-refresh — `PreviewPortPrefs` / `LocalhostPreviewActivity` |
| FEAT-003 | **done (Phase 18)** | Error page + Scan/Reload/LAN/QR actions + error detail |
| FEAT-004 | **done (Phase 17)** | QR LAN via ZXing encoder; live Wi-Fi IP watcher still optional |
| FEAT-005 | **done (Phase 18)** | Settings AI Doctor (status, repair, host, version pin) |
| FEAT-006 | **done (Phase 19)** | Settings Bun card + `bun-version` / `bun-upgrade --check` (APK-only update) |
| FEAT-007 | **done (Phase 17)** | Git badges + branch sheet + safe pull + `td-sync` |
| FEAT-008 | **done (Phase 20, narrowed)** | `SessionSnapshot` cwd/title + Restore snackbar (fresh shells; no pty replay) |
| FEAT-009 | **done (Phase 21)** | Settings → Backup zip (repos manifest, ~/.termux, shortcuts, prefs; secrets opt-in) |
| FEAT-010 | **done (Phase 20, narrowed)** | Loopback default + Copy LAN/QR confirm; biometric/auto-stop deferred |
| FEAT-011 | **done** | Copy/export last error in workflow More menu |
| FEAT-012 | **done (Phase 19)** | Preferred-ports Settings + background notification + deep-link Preview |
| FEAT-013 | **done (Phase 7)** | `td-scaffold` already seeds README/.gitignore, `react-ts` default, `--here` / `--no-install` |
| FEAT-014 | **done (Phase 22)** | Console sheet + logcat (gated) + desktop UA; Clear data already shipped |
| FEAT-017 | **done (Phase 19)** | Widget Run… args/env dialog + optional `td-dev` catalog |
| FEAT-023 | **done (Phase 22)** | Settings → Debugging → Export diagnostics zip (no secrets) |
| FEAT-024 | **done (Phase 22, narrowed)** | Offline Markwon Help + Preview error Help → troubleshooting |

### High Priority Suggestions

| ID | Category | Description | Impact | Effort |
|----|----------|-------------|--------|--------|
| FEAT-001 | Platform / Maintainability | Bump `targetSdkVersion` 28 → 34/35 + edge-to-edge | High | High |

### Medium Priority Suggestions

| ID | Category | Description | Impact | Effort |
|----|----------|-------------|--------|--------|
| FEAT-015 | Terminal UX | Theme picker (beyond hacker green) + font size gesture | Medium | Medium |
| FEAT-016 | Keys | Visual extra-keys editor (drag reorder, import/export) | Medium | Medium |
| FEAT-018 | Onboarding | First-launch checklist wizard | Medium | Medium |
| FEAT-019 | Deps | Dependabot + AGP/JDK audit, `commons-io:2.5` cap review | Medium | Low |

### Low Priority Suggestions

| ID | Category | Description | Impact | Effort |
|----|----------|-------------|--------|--------|
| FEAT-020 | Emulator | xterm conformance pass (reverse-wrap, ECH attrs, DECOM rect) | Medium | High |
| FEAT-021 | Maintainability | Fix deprecated APIs (`NotificationUtils`, `KeyboardUtils`, back dispatcher) | Low | Low |
| FEAT-022 | Accessibility/i18n | TalkBack labels, font-scale, RTL, string audit | Medium | Medium |
| FEAT-025 | Tunnels (opt-in) | Explicit opt-in LAN-only helpers (Tailscale/Cloudflared guard-railed) | Low | High |

---

## Details

### FEAT-001 — Target SDK 28 → 34/35 + edge-to-edge (Confidence: 95%)
- **Files:** `app/build.gradle:45-46`, `gradle.properties:18-21`, `app/src/main/AndroidManifest.xml`, `termux-shared/.../view/KeyboardUtils.java:108`, `app/.../terminal/io/FullScreenWorkAround.java`, `app/.../TermuxService.java`.
- **Current:** `minSdk 21`, `targetSdk 28`, `compileSdk 36`. AGP `9.4.0`, NDK `29.0.14206865`. `TODO: flag deprecated for API 30, use WindowInset API`.
- **Problem:** Play requires target ~34+; Android 12+ phantom-killer / foreground-service types, 13+ photo picker / `getifaddrs` noise, 15 edge-to-edge enforcement all hit a target-28 app. Back/nav + keyboard insets are fragile.
- **Suggestion:**
  1. Bump in a branch: `targetSdk 34` (then 35), test `TermuxService` foreground types (`dataSync`/`connectedDevice` as needed), `RUN_COMMAND` + Boot receiver `RECEIVER_EXPORTED` flags, `POST_NOTIFICATIONS` runtime ask.
  2. Migrate `KeyboardUtils` + `FullScreenWorkAround` to `WindowInsetsCompat` / `WindowInsetsControllerCompat`; enable edge-to-edge (`enableEdgeToEdge()`) in `TermuxActivity` + `LocalhostPreviewActivity` + `SettingsActivity`.
  3. Migrate `onBackPressed` deprecation in `LocalhostPreviewActivity:297` to `OnBackPressedDispatcher`.
  4. Full matrix: `assembleDebug` × 4 ABIs + `:app:assembleDebug`, `./gradlew test lint`, install on Android 8 / 12 / 14 / 15, check Preview, AI `:4000`, Widget, API screenshot.
- **Security/perf:** Reduces background-kill + permission regressions; no hot-path cost.
- **Keep:** `minSdk 21` path still tested; keep `apt-android-5` deprecated note.

### FEAT-002 — Preview history + favorites + auto-scan (Confidence: 90%)
- **Files:** `app/.../activities/LocalhostPreviewActivity.java:137-184`, `app/.../utils/LocalhostPortScanner.java`, `app/.../utils/PreferredPortWatcher.java`, `res/layout/activity_localhost_preview.xml`.
- **Current:** Manual port field + Scan button + one-shot chips; `onResume` rescans once. No memory of last ports.
- **Suggestion:**
  - Persist `recent_ports` (max 8) + `starred_ports` in `SharedPreferences`; render starred first with ★ toggle on long-press (keep long-press-Copy-LAN via double-action sheet: Copy LAN / Star / Forget).
  - Poll `scanListeningPorts()` every 3s while resumed (existing `mScanExecutor`), diff + animate only on change; add swipe-to-refresh + auto-load lowest new port with snackbar (reuse `PreferredPortWatcher` logic).
  - Remember per-repo default port: read `vite.config.*` / `package.json dev --port` when launched from drawer Repo context.
- **Effort:** Low — all helpers exist.

### FEAT-003 — Preview error page with fix hints (Confidence: 88%)
- **Files:** `LocalhostPreviewActivity.java:91-104,269-272`.
- **Current:** `WebView.loadUrl("http://127.0.0.1:"+port+"/")`; external URLs just toast `msg_preview_only_localhost`. Blank page on refused.
- **Suggestion:** Override `onReceivedError` / `onReceivedHttpError` → local `preview_error.html` asset explaining: (1) is `bun run dev` running? (2) bound on `127.0.0.1` vs `0.0.0.0`? (3) try Scan / Reload / Copy LAN check; buttons: Scan, Reload, Copy LAN, Open `td-dev` cheat-sheet. Preserve path+query when switching ports (`/foo?bar=1` shouldn't reset to `/`).
- **Example:**
  ```java
  @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
      if (r.isForMainFrame()) v.loadDataWithBaseURL(null, renderErrorHtml(port, e), "text/html", "utf-8", null);
  }
  ```

### FEAT-004 — QR-code LAN share (Confidence: 85%)
- **Files:** `app/.../utils/LanShareHelper.java`, `LocalhostPreviewActivity.java:194-236`, `com.invapp.shared.interact.ShareUtils`.
- **Current:** Copy LAN clipboard only when `isWildcardListen(port)`; `mLanHint` shows IP.
- **Suggestion:** Add QR dialog (ZXing core, no new permission) rendering `http://<wifi-ip>:<port>/` + IP + port + wildcard status. Live-watch Wi-Fi IP (NetworkCallback) and invalidate QR on change. Keep loopback-only WebView — QR is for *other devices*. Never auto-expose; keep firewall/VPN warning from `docs/ai.md:42`.
- **Security:** Show explicit "Only same Wi-Fi, no public tunnel" caption; hide QR when bind is loopback-only.

### FEAT-005 — AI setup wizard (Confidence: 92%)
- **Files:** `app/.../utils/AiSessionHelper.java`, `app/.../TermuxBunInstaller.java` (seeds `opencode-setup`, `td-ai`, `opencode-fix-net`), `docs/ai.md`, `WidgetScriptsInstaller.java`.
- **Current:** Drawer AI probes MISSING/INSTALLED/READY via `GET /global/health`; shell scripts handle glibc + `ld-linux --preload` shim + CA/DNS seeding. Users still hit LIBC/DNS/postinstall stub issues.
- **Suggestion:** Settings → AI screen with: (1) Status card (binary path, version, health, shim present, CA present), (2) `OPENCODE_VERSION` pin field + `OPENCODE_HOST` toggle (`0.0.0.0` vs `127.0.0.1`), (3) Run checks button (`getent hosts models.opencode.ai` under glibc, `cert.pem` link, stub detection via `isLikelyOpenCodeStub`), (4) one-tap Repair (re-run `opencode-setup` without wiping `~/.config/opencode`). Surface `extractLastErrorSnippet` output here.
- **Why high:** Biggest support burden in `docs/ai.md:59-99` + `troubleshooting.md`.

### FEAT-006 — Bun update checker (Confidence: 87%)
- **Files:** `app/build.gradle:264-315` (`downloadBunBootstraps`, pinned `1.4.4`), `app/.../TermuxBunInstaller.java`, `docs/troubleshooting.md:5`.
- **Current:** Bun baked via `.incbin` zips, SHA-verified at build; no in-app update path. Users must not use `bun.sh/install` (glibc SIGSYS).
- **Suggestion:** `bun-version` + `bun-upgrade --check` shell helper: query `oven-sh/bun` releases, compare to `$PREFIX/libexec/bun --version`, warn if glibc binary detected, point to next APK release (don't self-replace `.incbin`). In-app Settings card shows bundled vs running version + "Update via APK" link. Prevents signal-31 regressions from stray installs.
- **Maintainability:** Keep checksums in one `bun-versions.json` consumed by Gradle + helper.

### FEAT-007 — Git status + branch picker + safe pull (Confidence: 84%)
- **Files:** `app/.../utils/WorkflowHelper.java:25-27`, `WorkflowBarHelper.java`, widget `git-pull-repos`.
- **Current:** Quick bar injects `git pull\n`, `bun install\n`, `bun run dev\n`; repo picker lists `~/repos`; `package.json` script sheet exists.
- **Suggestion:**
  - Repo row shows `git -c color.ui=false status --porcelain=v1 -b` badge (dirty count, ahead/behind, branch).
  - Branch sheet (`git branch -vv`, filter input) + safe pull (`--ff-only`, stash prompt if dirty) reusing toast/error snippet pipeline.
  - `td-sync` helper: loop `~/repos/*` → fetch + ff-only pull, summary for widget `git-pull-repos`.
- **Perf:** Run git off UI thread (already pattern in `mScanExecutor`); cache status 10s.

### FEAT-008 — Session persist + crash recovery (Confidence: 80%) — **done Phase 20 (narrowed)**
- **Shipped:** `SessionSnapshot` (`invapp_session_snapshot`) persists cwd+title; cold-start snackbar **Restore N** → fresh shells via `createTermuxSession`. No pty/scrollback replay. Skips RUN/failsafe/`auto_start_console`.

### FEAT-009 — One-tap backup / restore (Confidence: 86%) — **done Phase 21**
- **Shipped:** Settings → Backup (`BackupHelper` / `BackupPreferencesFragment`); zip under `~/storage/shared/InVxTermux/`; secrets opt-in; widget Auto Backup rules tightened. Main app stays `allowBackup="false"`.

### FEAT-010 — LAN exposure guard + lock (Confidence: 83%) — **done Phase 20 (narrowed)**
- **Shipped:** `AiPrefs` / `opencode-server` default `127.0.0.1`; Copy LAN / QR LAN first-use confirm + don’t-ask-again. **Deferred:** biometric/app lock, auto-Stop-AI on background.

### FEAT-011 — Copy-last-error + transcript export (Confidence: 90%)
- **Files:** `AiSessionHelper.java:370-422` (`extractLastErrorSnippet`, `ERROR_LINE` regex), `PreferredPortWatcher`.
- **Current:** Regex + snackbar exists but buried.
- **Suggestion:** Promote to drawer overflow "Copy last error" + "Export transcript" (`~/repos/.td/last-error.md` with cwd, cmd, port, health). Add provider-error patterns (`AI_APICallError`, `Failed to fetch models.dev`, `EADDRINUSE`, `getifaddrs`) to regex. One-tap paste target = OpenCode Preview.
- **Effort:** Low — helper already tested.

### FEAT-012 — Configurable preferred ports (Confidence: 82%)
- **Files:** `PreferredPortWatcher.java`, `WorkflowHelper.java:34` (`VITE_DEFAULT_PORT=5173`), `LocalhostPortScanner.java`.
- **Current:** Hardcoded preferred set (5173, 4096, …).
- **Suggestion:** Settings multi-input (e.g. `5173,3000,8080,4096`) + per-template defaults (Vite 5173, Expo 8081, Astro 4321). Notify via notification channel (not only snackbar) when a watched port appears while app backgrounded — with Preview deep-link.
- **Perf:** Keep scan off UI thread; debounce 2s.

### FEAT-013 — Scaffold hardening (Confidence: 85%)
- **Files:** `WorkflowHelper.java:48-52` (`VITE_TEMPLATES`), `TermuxBunInstaller.java` (`td-scaffold`), drawer New… sheet.
- **Current:** `vanilla/react/vue/vanilla-ts + pwa/pwa-react`, `0.0.0.0:5173`.
- **Suggestion:** Default `react-ts` (not `vanilla`) for real apps; seed `.gitignore` (node_modules/dist), `README.md` with Preview/LAN steps, `vite.config` host/port pinned; cache `create-vite` tarball under `$PREFIX/var/td-cache` for offline retry; add `--here` (scaffold in cwd) + `--no-install` flags. Validate name (no spaces, no `/storage` noexec path).
- **Non-goal guard:** No native/Capacitor packaging — PWA build stays the path.

### FEAT-014 — WebView diagnostics (Confidence: 78%) — **done Phase 22**
- **Shipped:** Settings → Show Preview console + Desktop site; console sheet + Debug logcat when on; Clear data already present.

### FEAT-015 — Theme + font controls (Confidence: 75%)
- **Files:** `TermuxHackerThemeInstaller.java`, `~/.termux/colors.properties`, `TerminalViewPreferencesFragment.java`.
- **Current:** Hacker green-on-black v1/v2; green-bar `ls` clash fixed in v2 but still one theme.
- **Suggestion:** Theme picker (Hacker / Matcha / Paper / System) writing `colors.properties` + `font.ttf` size stepper + pinch-to-zoom in terminal (scale `TerminalView` text, persist). Preview theme before apply; "Reset to Hacker v2" escape hatch.
- **A11y:** Honor system font-scale minimum 12sp.

### FEAT-016 — Visual extra-keys editor (Confidence: 72%)
- **Files:** `ExtraKeysBarHelper.java`, `TermuxTerminalExtraKeys.java`, `~/.termux/termux.properties` (`extra-keys` writer).
- **Current:** 2-row nav + swipe workflow page; Customize via list.
- **Suggestion:** Drag-reorder grid + live preview + import/export JSON (`~/storage/shared/InVxTermux/keys/*.json`). Keep quoted `extra-keys` writer; validate syntax before save; preset: Minimal / Bun Dev / AI.
- **Maintainability:** Isolate writer for upstream merge ease (per `AGENTS.md §4`).

### FEAT-017 — Widget run-with-args (Confidence: 76%)
- **Files:** `WidgetScriptsInstaller.java`, `WidgetScriptsUi.java`, `~/.shortcuts/*`, `docs/install.md:55-106`.
- **Current:** Seed + picker + Run once; tap shows Running toast.
- **Suggestion:** Run-once dialog with args field (`$1…`) + env (`PORT=…`) for `td-dev`/`td-ai` tasks; add `stop-ai` + `preview-5173` to optional catalog; surface missing-prereq hints (Widget APK / API APK / `pkg termux-api` / `tesseract`) inline, not just toast.
- **Security:** Quote args via existing `shellSingleQuote`; block `;|&$()` injection in args field.

### FEAT-018 — First-launch wizard (Confidence: 81%)
- **Files:** `TermuxInstaller.java`, `TermuxApplication.java`, `docs/install.md`, `docs/workflow.md`.
- **Current:** Direct boot into `~/repos`; helpers discovered via drawer.
- **Suggestion:** 4-step checklist: (1) Storage (`termux-setup-storage` explainer, noexec warning), (2) Init `~/repos` + sample `td-scaffold demo react`, (3) Preview tour (Scan → 5173 → Copy LAN guard), (4) AI opt-in (`opencode-setup` size warning, glibc ~200MB). Skip-able, re-runnable from Help. Reduces "Permission denied bins" + "Preview blank" issues.
- **Effort:** Medium — mostly intents + prefs.

### FEAT-019 — Dependency + NDK hygiene (Confidence: 88%)
- **Files:** `app/build.gradle:25-37`, `termux-shared/build.gradle`, `gradle.properties`, `.github/workflows/*`, `dependabot.yml`.
- **Current:** Pinned `material:1.12.0`, `guava:24.1-jre`, `markwon:4.6.2`, `commons-io:2.5` (capped — `Path` missing < API 26), `desugar:1.1.5`.
- **Suggestion:** Enable Dependabot grouped minors; quarterly `AGP 9.4 → latest`, `compileSdk 36` check, NDK `29.x` bump with all-ABI bootstrap rebuild test; document why `commons-io` stays ≤2.5; add `dependency-submission` + OSV scan to CI gate.
- **Security:** Audit before any major bump (per `AGENTS.md §4`).

### FEAT-020 — Emulator conformance (Confidence: 70%)
- **Files:** `terminal-emulator/.../TerminalEmulator.java:814,849,967,1826,2287,3426`, `TerminalRow.java:218`, `src/test/...`.
- **Current:** Known `FIXME/XXX/TODO`: reverse wraparound (mode 45) unimplemented, ECH attr clear, DECOM rect, cursor thread sync.
- **Suggestion:** Parameterized tests vs https://invisible-island.net/xterm/ctlseqs/ctlseqs.html vectors; fix one per PR (start with ECH + DECOM, lowest risk); add fuzz for CSI parser. Keep `unitTests.returnDefaultValues` + hermetic fast tests.
- **Effort high, risk low** if incremental.

### FEAT-021 — Deprecated API sweep (Confidence: 90%)
- **Files:** `NotificationUtils.java:112` (`setDefaults`), `KeyboardUtils.java:108`, `LocalhostPreviewActivity:296` (`onBackPressed`), `MainWidget.java:274` (TODO).
- **Suggestion:** Notification channels per type (Preview/AI/Widget) with importance picker; `WindowInsets` migration (see FEAT-001); `OnBackPressedDispatcher`; document `MainWidget` TODO or file issue. `./gradlew lint` must stay clean.
- **Effort:** Low.

### FEAT-022 — Accessibility + i18n (Confidence: 74%)
- **Files:** `res/values/strings.xml`, drawer layouts, `TerminalToolbarViewPager.java`, `KeyboardShortcut.java`.
- **Current:** English-only, small touch targets on quick bar (recently taller — good), no TalkBack pass noted.
- **Suggestion:** Content-descriptions for Preview chips/Scan/Copy LAN/AI/Stop AI; min 48dp targets; honor font-scale in dialogs; extract all hardcoded strings; add `de`/`zh` starter bundle if contributors exist; haptic on extra-keys long-press.
- **Test:** TalkBack walkthrough + font-size 200%.

### FEAT-023 — One-tap diagnostics bundle (Confidence: 87%) — **done Phase 22**
- **Shipped:** Settings → Debugging → Export diagnostics zip under `InVxTermux/diag-*.zip` (report + versions + AI stamps; consent; no secrets).

### FEAT-024 — Offline help + search (Confidence: 79%) — **done Phase 22 (narrowed)**
- **Shipped:** Markwon `HelpActivity` + `assets/help/` subset; filter; Preview error Help → troubleshooting.

### FEAT-025 — Opt-in tunnels (explicit non-goal, guard-railed) (Confidence: 60%)
- **Files:** `LanShareHelper.java`, `ROADMAP.md:151-155` (Non-goals).
- **Current:** No public tunnels by default (correct).
- **Suggestion:** *Only if demanded:* settings-gated (`Enable public share — I understand risks`) that shells to user-installed `cloudflared`/`tailscale` with loopback guard + auto-stop on app exit + prominent banner. Never baked default. Prefer Tailscale (identity) over random public URLs.
- **Security:** Requires explicit opt-in + warning + audit log; default stays LAN-only.

---

## Security, Performance, Maintainability — Cross-cutting Notes

- **Security:** Keep Preview loopback-only (`isAllowedLoopbackUrl`), keep `LD_PRELOAD=` empty for glibc wrappers (never `unset`, never shim in `LD_PRELOAD`), keep `sharedUserId com.invapp` + same-signature plugin set, keep `RUN_COMMAND` allowlist + FQCN KeepAlive (`termux-api-start`), redact `Verbose` PII, validate `td-clone`/`td-scaffold` names + widget args quoting.
- **Performance:** Port scan + git status + AI health off UI thread (existing `mScanExecutor` pattern); reuse buffers in draw/input hot paths; `StringBuilder` over concat; `-Werror`-clean native; `LOAD_NO_CACHE` only in Preview, not globally.
- **Maintainability:** All shared constants via `TermuxConstants` (no hardcoded `com.termux`/`com.invapp` paths — PRs rejected per `AGENTS.md`); keep Preview/Bun/bootstrap isolated for upstream merges; small focused classes; Robolectric for Android logic; `./gradlew test lint` clean; Conventional Commits (`Added|Changed|Deprecated|Removed|Fixed|Security`) + semver validation.

## Suggested Next Steps (for maintainers)

> Phase 22 shipped FEAT-014 / FEAT-023 / FEAT-024 (narrowed) — cut as v0.210.0 when ready.
> Phase 21 shipped FEAT-009 → v0.209.0.
> Phase 20 shipped FEAT-008 (narrowed) + FEAT-010 (narrowed) → v0.208.0.
> Phase 19 shipped FEAT-006 / FEAT-012 / FEAT-017 + AI script asset unification.

1. Next platform: FEAT-001 targetSdk 34/35 + edge-to-edge on its own branch + device matrix.
2. Optional polish: FEAT-015 themes / FEAT-016 drag extra-keys (chrome only).
3. File issues for FEAT-020 emulator FIXMEs individually with xterm vectors.

## Appendix — Where to Implement

| Area | Entry files |
|------|-------------|
| Preview | `app/.../activities/LocalhostPreviewActivity.java`, `utils/LocalhostPortScanner.java`, `utils/LanShareHelper.java`, `utils/PreferredPortWatcher.java` |
| AI | `utils/AiSessionHelper.java`, `utils/WorkflowHelper.java` (`AI_PREVIEW_PORT`, `aiHealthUrl`), `TermuxBunInstaller.java` (`opencode-setup`, `td-ai`, `td-screen-ocr`) |
| Workflow | `utils/WorkflowHelper.java`, `utils/WorkflowBarHelper.java`, `utils/ExtraKeysBarHelper.java`, drawer in `TermuxActivity.java` |
| Widgets | `WidgetScriptsInstaller.java`, `WidgetScriptsUi.java`, `~/.shortcuts` templates, `:termux-widget`, `:termux-terminal-widget` |
| Terminal | `terminal-view/`, `terminal-emulator/.../TerminalEmulator.java`, `TermuxTerminalExtraKeys.java` |
| Platform | `app/build.gradle`, `gradle.properties`, `AndroidManifest.xml`, `termux-shared/.../TermuxConstants.java` |
| Docs | `docs/workflow.md`, `docs/ai.md`, `docs/troubleshooting.md`, `docs/install.md`, `ROADMAP.md` |
