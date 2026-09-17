package com.invapp.app;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;
import com.invapp.shared.termux.TermuxUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs one-tap scripts under {@code ~/.shortcuts} (and {@code tasks/}) for
 * Termux:Widget. Templates that need clipboard/TTS require the Termux:API APK
 * plus {@code pkg install termux-api}.
 */
public final class WidgetScriptsInstaller {

    private static final String LOG_TAG = "WidgetScriptsInstaller";

    /** Foreground widget scripts (shown on the widget list). */
    public static final String ID_CLIPBOARD_SPEAK = "clipboard-speak";
    public static final String ID_CLIPBOARD_TO_FILE = "clipboard-to-file";
    public static final String ID_GIT_PULL_REPOS = "git-pull-repos";
    public static final String ID_SCREEN_OCR = "screen-ocr";

    /** Background task scripts under {@code ~/.shortcuts/tasks/}. */
    public static final String ID_TD_AI = "td-ai";

    public static final String[] DEFAULT_FOREGROUND_IDS = {
        ID_CLIPBOARD_SPEAK, ID_CLIPBOARD_TO_FILE, ID_GIT_PULL_REPOS, ID_SCREEN_OCR
    };

    public static final String[] DEFAULT_TASK_IDS = {
        ID_TD_AI
    };

    /** Display labels parallel to {@link #allCatalogIds()}. */
    public static final String[] CATALOG_LABELS = {
        "clipboard-speak (TTS)",
        "clipboard-to-file",
        "git-pull-repos",
        "screen-ocr (clipboard)",
        "td-ai (background task)"
    };

    private WidgetScriptsInstaller() {}

    @NonNull
    public static String[] allCatalogIds() {
        return new String[] {
            ID_CLIPBOARD_SPEAK, ID_CLIPBOARD_TO_FILE, ID_GIT_PULL_REPOS,
            ID_SCREEN_OCR, ID_TD_AI
        };
    }

    /** Install default templates if the shortcuts dirs are empty of our files. */
    public static void installDefaultsIfMissing(@NonNull Context context) {
        ensureDirs();
        List<String> missing = new ArrayList<>();
        for (String id : DEFAULT_FOREGROUND_IDS) {
            if (!scriptFile(id, false).isFile()) {
                missing.add(id);
            }
        }
        for (String id : DEFAULT_TASK_IDS) {
            if (!scriptFile(id, true).isFile()) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            // Still seed icons / refresh if scripts already present but icons missing.
            installDefaultIcons(Arrays.asList(allCatalogIds()), false);
            return;
        }
        int n = installSelected(context, missing);
        Logger.logInfo(LOG_TAG, "Installed widget script templates: " + missing + " (" + n + ")");
    }

    /** Overwrite selected templates (or all defaults if {@code ids} empty). */
    public static int installSelected(@Nullable List<String> ids) {
        return installSelected(null, ids);
    }

    /**
     * Install selected templates, seed matching PNG icons under
     * {@code ~/.shortcuts/icons/}, and optionally refresh Termux:Widget.
     */
    public static int installSelected(@Nullable Context context, @Nullable List<String> ids) {
        ensureDirs();
        List<String> toInstall = ids == null || ids.isEmpty()
            ? Arrays.asList(allCatalogIds())
            : ids;
        int n = 0;
        for (String id : toInstall) {
            boolean task = ID_TD_AI.equals(id);
            String body = scriptBody(id);
            if (body == null) {
                continue;
            }
            if (writeExec(scriptFile(id, task), body)) {
                n++;
            }
        }
        installDefaultIcons(toInstall, true);
        if (context != null && n > 0) {
            requestWidgetRefresh(context);
        }
        return n;
    }

    public static void resetToDefaults() {
        resetToDefaults(null);
    }

    public static void resetToDefaults(@Nullable Context context) {
        installSelected(context, Arrays.asList(allCatalogIds()));
    }

    /**
     * Ask Termux:Widget to reload {@code ~/.shortcuts} list (no-op if plugin
     * missing). Safe to call from the main app across packages.
     */
    public static void requestWidgetRefresh(@NonNull Context context) {
        if (!isWidgetAppInstalled(context)) {
            return;
        }
        try {
            Intent intent = new Intent(
                TermuxConstants.TERMUX_WIDGET_APP.TERMUX_WIDGET_PROVIDER.ACTION_REFRESH_WIDGET);
            intent.setComponent(new ComponentName(
                TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME,
                "com.invapp.widget.TermuxWidgetProvider"));
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
            context.sendBroadcast(intent);
            Logger.logDebug(LOG_TAG, "Sent Termux:Widget refresh broadcast");
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Widget refresh broadcast failed: " + e.getMessage());
        }
    }

    /** Write simple colored PNG icons for known templates (idempotent unless force). */
    static void installDefaultIcons(@NonNull List<String> ids, boolean overwrite) {
        ensureDirs();
        for (String id : ids) {
            File icon = new File(TermuxConstants.TERMUX_SHORTCUT_SCRIPT_ICONS_DIR, id + ".png");
            if (icon.isFile() && !overwrite) {
                continue;
            }
            Integer color = iconColor(id);
            if (color == null) {
                continue;
            }
            writePngIcon(icon, color);
        }
    }

    @Nullable
    private static Integer iconColor(@NonNull String id) {
        switch (id) {
            case ID_CLIPBOARD_SPEAK: return 0xFF00C853; // green
            case ID_CLIPBOARD_TO_FILE: return 0xFF2979FF; // blue
            case ID_GIT_PULL_REPOS: return 0xFFFF6D00; // orange
            case ID_SCREEN_OCR: return 0xFF00BFA5; // teal
            case ID_TD_AI: return 0xFFAA00FF; // purple
            default: return null;
        }
    }

    private static void writePngIcon(@NonNull File dest, int colorArgb) {
        try {
            int size = 96;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFF121212);
            canvas.drawRoundRect(0, 0, size, size, 16, 16, paint);
            paint.setColor(colorArgb);
            canvas.drawCircle(size / 2f, size / 2f, size * 0.32f, paint);
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bmp.recycle();
            if (dest.exists() && !dest.delete()) {
                try {
                    Os.remove(dest.getAbsolutePath());
                } catch (Exception ignored) {
                }
            }
            if (!tmp.renameTo(dest)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "icon " + dest + ": " + e.getMessage());
        }
    }

    @NonNull
    public static boolean[] installedFlags() {
        String[] ids = allCatalogIds();
        boolean[] flags = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) {
            boolean task = ID_TD_AI.equals(ids[i]);
            flags[i] = scriptFile(ids[i], task).isFile();
        }
        return flags;
    }

    @NonNull
    public static List<String> filterCatalogOrder(@NonNull boolean[] checked) {
        String[] ids = allCatalogIds();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ids.length && i < checked.length; i++) {
            if (checked[i]) {
                out.add(ids[i]);
            }
        }
        return out;
    }

    public static boolean isWidgetAppInstalled(@NonNull Context context) {
        return TermuxUtils.getTermuxWidgetPackageContext(context) != null;
    }

    public static boolean isApiAppInstalled(@NonNull Context context) {
        return TermuxUtils.getTermuxAPIPackageContext(context) != null;
    }

    public static boolean isTermuxApiCliPresent() {
        File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "termux-clipboard-get");
        return bin.isFile();
    }

    @NonNull
    public static String statusSummary(@NonNull Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Widget APK: ").append(isWidgetAppInstalled(context) ? "installed" : "missing");
        sb.append('\n');
        sb.append("API APK: ").append(isApiAppInstalled(context) ? "installed" : "missing");
        sb.append('\n');
        sb.append("pkg termux-api: ").append(isTermuxApiCliPresent() ? "present" : "missing (pkg install termux-api)");
        sb.append('\n');
        sb.append("Scripts: ").append(TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH);
        return sb.toString();
    }

    @NonNull
    public static File shortcutsDir() {
        return TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR;
    }

    /** Absolute path of an installed template script, or {@code null} if unknown id. */
    @Nullable
    public static File scriptPath(@NonNull String id) {
        if (ID_TD_AI.equals(id)) {
            return scriptFile(id, true);
        }
        for (String known : DEFAULT_FOREGROUND_IDS) {
            if (known.equals(id)) {
                return scriptFile(id, false);
            }
        }
        return null;
    }

    /**
     * Shell command to run a template once in the current session
     * (quotes the script path).
     */
    @Nullable
    public static String runOnceCommand(@NonNull String id) {
        File f = scriptPath(id);
        if (f == null) {
            return null;
        }
        String path = f.getAbsolutePath().replace("'", "'\\''");
        return "bash '" + path + "'\n";
    }

    @Nullable
    static String scriptBody(@NonNull String id) {
        Map<String, String> map = bodies();
        return map.get(id);
    }

    @NonNull
    static Map<String, String> bodies() {
        String bash = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put(ID_CLIPBOARD_SPEAK, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: clipboard-speak\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "toast 'Speaking clipboard…'\n"
            + "if ! command -v termux-clipboard-get >/dev/null 2>&1 \\\n"
            + "  || ! command -v termux-tts-speak >/dev/null 2>&1; then\n"
            + "  msg='Need Termux:API APK + pkg install termux-api'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "text=$(termux-clipboard-get || true)\n"
            + "if [ -z \"$text\" ]; then\n"
            + "  toast 'Clipboard empty'\n"
            + "  exit 0\n"
            + "fi\n"
            + "printf '%s' \"$text\" | termux-tts-speak\n"
            + "toast 'Done speaking'\n");
        m.put(ID_CLIPBOARD_TO_FILE, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: clipboard-to-file\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "HOME=\"" + home + "\"\n"
            + "OUT=\"$HOME/repos/clipboard.txt\"\n"
            + "mkdir -p \"$HOME/repos\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "if ! command -v termux-clipboard-get >/dev/null 2>&1; then\n"
            + "  msg='Need Termux:API APK + pkg install termux-api'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "text=$(termux-clipboard-get || true)\n"
            + "if [ -z \"$text\" ]; then\n"
            + "  toast 'Clipboard empty'\n"
            + "  exit 0\n"
            + "fi\n"
            + "printf '%s\\n' \"$text\" >> \"$OUT\"\n"
            + "toast \"Appended → $OUT\"\n"
            + "echo \"Appended → $OUT\"\n");
        m.put(ID_GIT_PULL_REPOS, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: git-pull-repos\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "HOME=\"" + home + "\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "toast 'git pull ~/repos…'\n"
            + "cd \"$HOME/repos\" 2>/dev/null || { toast 'No ~/repos'; echo 'No ~/repos' >&2; exit 1; }\n"
            + "ok=0\n"
            + "for d in */; do\n"
            + "  [ -d \"$d/.git\" ] || continue\n"
            + "  echo \"=== $d ===\"\n"
            + "  (cd \"$d\" && git pull --ff-only) && ok=$((ok+1)) || true\n"
            + "done\n"
            + "toast \"git pull done ($ok repos)\"\n");
        m.put(ID_SCREEN_OCR, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: screen-ocr\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "toast 'Screen OCR…'\n"
            + "if ! command -v td-screen-ocr >/dev/null 2>&1; then\n"
            + "  msg='td-screen-ocr missing — reopen InVxTermux once'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "td-screen-ocr\n");
        m.put(ID_TD_AI, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: td-ai (background task)\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "toast 'Starting OpenCode…'\n"
            + "if ! command -v td-ai >/dev/null 2>&1; then\n"
            + "  msg='td-ai missing — open InVxTermux once to install helpers'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "td-ai &\n"
            + "pid=$!\n"
            + "ready=0\n"
            + "for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 30; do\n"
            + "  if curl -fsS --connect-timeout 1 --max-time 2 \\\n"
            + "      http://127.0.0.1:4096/global/health 2>/dev/null | grep -qi healthy; then\n"
            + "    ready=1; break\n"
            + "  fi\n"
            + "  if ! kill -0 \"$pid\" 2>/dev/null; then\n"
            + "    break\n"
            + "  fi\n"
            + "  sleep 1\n"
            + "done\n"
            + "if [ \"$ready\" -eq 1 ]; then\n"
            + "  toast 'OpenCode ready :4096'\n"
            + "  wait \"$pid\" || true\n"
            + "  exit 0\n"
            + "fi\n"
            + "toast 'OpenCode failed to become ready'\n"
            + "kill \"$pid\" 2>/dev/null || true\n"
            + "wait \"$pid\" 2>/dev/null || true\n"
            + "exit 1\n");
        return m;
    }

    @NonNull
    private static File scriptFile(@NonNull String id, boolean task) {
        File dir = task
            ? TermuxConstants.TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR
            : TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR;
        return new File(dir, id);
    }

    private static void ensureDirs() {
        //noinspection ResultOfMethodCallIgnored
        TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        TermuxConstants.TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        TermuxConstants.TERMUX_SHORTCUT_SCRIPT_ICONS_DIR.mkdirs();
    }

    private static boolean writeExec(@NonNull File dest, @NonNull String contents) {
        try {
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(contents.getBytes(StandardCharsets.UTF_8));
            }
            //noinspection OctalInteger
            Os.chmod(tmp.getAbsolutePath(), 0700);
            if (dest.exists() && !dest.delete()) {
                try {
                    Os.remove(dest.getAbsolutePath());
                } catch (Exception e) {
                    Logger.logWarn(LOG_TAG, "replace " + dest + ": " + e.getMessage());
                }
            }
            if (!tmp.renameTo(dest)) {
                Logger.logWarn(LOG_TAG, "Could not install " + dest);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "write " + dest + ": " + e.getMessage());
            return false;
        }
    }
}
