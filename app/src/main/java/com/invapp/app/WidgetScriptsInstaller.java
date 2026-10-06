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
    public static final String ID_CAMERA_PHOTO = "camera-photo";
    public static final String ID_WIFI_INFO = "wifi-info";
    public static final String ID_WIFI_SCAN = "wifi-scan";
    public static final String ID_BATTERY_STATUS = "battery-status";
    public static final String ID_TORCH_TOGGLE = "torch-toggle";
    public static final String ID_SHARE_CLIPBOARD = "share-clipboard";
    public static final String ID_OPEN_SETTINGS = "open-settings";
    public static final String ID_VIBRATE = "vibrate";
    public static final String ID_VOLUME_INFO = "volume-info";
    public static final String ID_LOCATION = "location";
    public static final String ID_TELEPHONY_INFO = "telephony-info";
    public static final String ID_STOP_AI = "stop-ai";
    public static final String ID_TD_UPGRADE = "td-upgrade";
    public static final String ID_STOP_AGENT_WORKER = "stop-agent-worker";

    /** Background task scripts under {@code ~/.shortcuts/tasks/}. */
    public static final String ID_TD_AI = "td-ai";
    public static final String ID_AGENT_WORKER = "agent-worker";

    public static final String[] DEFAULT_FOREGROUND_IDS = {
        ID_CLIPBOARD_SPEAK, ID_CLIPBOARD_TO_FILE, ID_GIT_PULL_REPOS, ID_SCREEN_OCR
    };

    public static final String[] DEFAULT_TASK_IDS = {
        ID_TD_AI
    };

    /** Optional catalog entries (install via picker; not auto-seeded). */
    public static final String[] OPTIONAL_FOREGROUND_IDS = {
        ID_CAMERA_PHOTO, ID_WIFI_INFO, ID_WIFI_SCAN, ID_BATTERY_STATUS,
        ID_TORCH_TOGGLE, ID_SHARE_CLIPBOARD, ID_OPEN_SETTINGS,
        ID_VIBRATE, ID_VOLUME_INFO, ID_LOCATION, ID_TELEPHONY_INFO, ID_STOP_AI,
        ID_TD_UPGRADE, ID_STOP_AGENT_WORKER
    };

    /** Display labels parallel to {@link #allCatalogIds()}. */
    public static final String[] CATALOG_LABELS = {
        "clipboard-speak (TTS)",
        "clipboard-to-file",
        "git-pull-repos",
        "screen-ocr (clipboard)",
        "camera-photo",
        "wifi-info",
        "wifi-scan (nearby networks)",
        "battery-status",
        "torch-toggle",
        "share-clipboard",
        "open-settings",
        "vibrate",
        "volume-info",
        "location",
        "telephony-info",
        "stop-ai",
        "td-upgrade",
        "stop-agent-worker",
        "td-ai (background task)",
        "agent-worker (background task)"
    };

    private WidgetScriptsInstaller() {}

    @NonNull
    public static String[] allCatalogIds() {
        return new String[] {
            ID_CLIPBOARD_SPEAK, ID_CLIPBOARD_TO_FILE, ID_GIT_PULL_REPOS,
            ID_SCREEN_OCR,
            ID_CAMERA_PHOTO, ID_WIFI_INFO, ID_WIFI_SCAN, ID_BATTERY_STATUS,
            ID_TORCH_TOGGLE, ID_SHARE_CLIPBOARD, ID_OPEN_SETTINGS,
            ID_VIBRATE, ID_VOLUME_INFO, ID_LOCATION, ID_TELEPHONY_INFO, ID_STOP_AI,
            ID_TD_UPGRADE, ID_STOP_AGENT_WORKER,
            ID_TD_AI, ID_AGENT_WORKER
        };
    }

    private static boolean isTaskId(@NonNull String id) {
        return ID_TD_AI.equals(id) || ID_AGENT_WORKER.equals(id);
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
            boolean task = isTaskId(id);
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
            case ID_CAMERA_PHOTO: return 0xFFE91E63; // pink
            case ID_WIFI_INFO: return 0xFF03A9F4; // light blue
            case ID_WIFI_SCAN: return 0xFF00BCD4; // cyan
            case ID_BATTERY_STATUS: return 0xFF8BC34A; // light green
            case ID_TORCH_TOGGLE: return 0xFFFFEB3B; // yellow
            case ID_SHARE_CLIPBOARD: return 0xFF9C27B0; // purple-ish
            case ID_OPEN_SETTINGS: return 0xFF607D8B; // blue grey
            case ID_VIBRATE: return 0xFFFF5722; // deep orange
            case ID_VOLUME_INFO: return 0xFF3F51B5; // indigo
            case ID_LOCATION: return 0xFF009688; // teal
            case ID_TELEPHONY_INFO: return 0xFF795548; // brown
            case ID_STOP_AI: return 0xFFF44336; // red
            case ID_TD_UPGRADE: return 0xFF00897B; // teal dark
            case ID_STOP_AGENT_WORKER: return 0xFFE53935; // red
            case ID_TD_AI: return 0xFFAA00FF; // purple
            case ID_AGENT_WORKER: return 0xFF1E88E5; // blue
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
            boolean task = isTaskId(ids[i]);
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
        if (isTaskId(id)) {
            return scriptFile(id, true);
        }
        for (String known : allCatalogIds()) {
            if (isTaskId(known)) {
                continue;
            }
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
            + "delay=\"${SCREEN_OCR_DELAY:-10}\"\n"
            + "toast \"Capture in ${delay}s — switch screens…\"\n"
            + "sleep \"$delay\"\n"
            + "SCREENSHOT_DELAY_MS=\"${SCREENSHOT_DELAY_MS:-3000}\" td-screen-ocr\n");
        String needApi = ""
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "need_api() {\n"
            + "  if ! command -v \"$1\" >/dev/null 2>&1; then\n"
            + "    msg='Need Termux:API APK + pkg install termux-api'\n"
            + "    toast \"$msg\"; echo \"$msg\" >&2\n"
            + "    exit 1\n"
            + "  fi\n"
            + "}\n";
        m.put(ID_CAMERA_PHOTO, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: camera-photo\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "HOME=\"" + home + "\"\n"
            + needApi
            + "need_api termux-camera-photo\n"
            + "mkdir -p \"$HOME/repos\"\n"
            + "OUT=\"$HOME/repos/camera-last.jpg\"\n"
            + "toast 'Taking photo…'\n"
            + "termux-camera-photo \"$OUT\"\n"
            + "toast \"Saved → $OUT\"\n"
            + "echo \"$OUT\"\n");
        m.put(ID_WIFI_INFO, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: wifi-info\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + needApi
            + "need_api termux-wifi-connectioninfo\n"
            + "need_api termux-clipboard-set\n"
            + "toast 'Wi‑Fi info…'\n"
            + "json=$(termux-wifi-connectioninfo || true)\n"
            + "if [ -z \"$json\" ]; then toast 'No Wi‑Fi info'; exit 1; fi\n"
            + "printf '%s\\n' \"$json\" | termux-clipboard-set\n"
            + "ssid=$(printf '%s' \"$json\" | tr ',' '\\n' | sed -n 's/.*\"ssid\":[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' | head -1)\n"
            + "ip=$(printf '%s' \"$json\" | tr ',' '\\n' | sed -n 's/.*\"ip\":[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' | head -1)\n"
            + "if [ -n \"$ssid\" ] || [ -n \"$ip\" ]; then\n"
            + "  toast \"${ssid:-wifi} ${ip}\"\n"
            + "else\n"
            + "  toast 'Wi‑Fi JSON → clipboard'\n"
            + "fi\n"
            + "printf '%s\\n' \"$json\"\n");
        m.put(ID_WIFI_SCAN, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: wifi-scan\n"
            + "# Passive nearby Wi-Fi scanner (no root, no monitor mode, no capture/crack).\n"
            + "# Uses termux-wifi-scaninfo + termux-wifi-connectioninfo. Audit own/authorized nets only.\n"
            + "# Needs: Termux:API APK + pkg install termux-api + Location ON + location permission.\n"
            + "# Android throttles scans (4 per 2 min); results may be cached - this is normal.\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "HOME=\"" + home + "\"\n"
            + needApi
            + "need_api termux-wifi-scaninfo\n"
            + "need_api termux-wifi-connectioninfo\n"
            + "OUTDIR=\"$HOME/repos/wifi-scan\"\n"
            + "CACHE=\"$HOME/.cache/invx-wifi-scan\"\n"
            + "mkdir -p \"$OUTDIR\" \"$CACHE\"\n"
            + "TS=\"$(date +%Y%m%d-%H%M%S)\"\n"
            + "SCAN_F=\"$CACHE/scan-last.json\"\n"
            + "CONN_F=\"$CACHE/conn-last.json\"\n"
            + "SUM_F=\"$CACHE/last-summary.txt\"\n"
            + "toast 'Scanning Wi-Fi...'\n"
            + "termux-wifi-scaninfo > \"$SCAN_F\" 2>/dev/null || true\n"
            + "termux-wifi-connectioninfo > \"$CONN_F\" 2>/dev/null || true\n"
            + "if [ ! -s \"$SCAN_F\" ]; then toast 'No scan data (enable Location)'; echo 'No scan data - enable Location + grant permission' >&2; exit 1; fi\n"
            + "if grep -q 'API_ERROR' \"$SCAN_F\"; then toast 'Enable Location + grant permission'; cat \"$SCAN_F\" >&2; exit 1; fi\n"
            + "if ! command -v python3 >/dev/null 2>&1; then cp \"$SCAN_F\" \"$OUTDIR/scan-$TS.json\"; toast 'Saved raw JSON (pkg install python for table)'; cat \"$SCAN_F\"; exit 0; fi\n"
            + "python3 - \"$SCAN_F\" \"$CONN_F\" \"$OUTDIR\" \"$CACHE\" \"$TS\" <<'PYEOF'\n"
            + "import sys, json, os, html, csv, collections\n"
            + "scan_f, conn_f, outdir, cache, ts = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]\n"
            + "def load_json(p, default):\n"
            + "    try:\n"
            + "        with open(p, 'r', encoding='utf-8') as f:\n"
            + "            return json.load(f)\n"
            + "    except Exception:\n"
            + "        return default\n"
            + "scans = load_json(scan_f, [])\n"
            + "conn = load_json(conn_f, {})\n"
            + "if isinstance(scans, dict):\n"
            + "    scans = []\n"
            + "connected = ''\n"
            + "try:\n"
            + "    connected = str((conn or {}).get('bssid') or '').upper()\n"
            + "except Exception:\n"
            + "    connected = ''\n"
            + "OUI = {'00:1A:11': 'Google', '3C:22:FB': 'Apple', 'F0:18:98': 'Apple', '8C:85:90': 'Apple', 'D8:A0:1D': 'Apple', 'AC:DE:48': 'Apple', 'DC:A6:32': 'Raspberry Pi', 'B8:27:EB': 'Raspberry Pi', '50:C7:BF': 'TP-Link', '14:CC:20': 'TP-Link', '30:B5:C2': 'TP-Link', '9C:C7:A6': 'TP-Link', 'C0:4A:00': 'Netgear', '28:C6:8E': 'Netgear', 'A0:04:60': 'Netgear', '00:18:39': 'Cisco', '00:1B:2A': 'Cisco', '58:8D:09': 'Cisco', '78:8A:20': 'Ubiquiti', '74:83:C2': 'Ubiquiti', '24:A4:3C': 'Ubiquiti', 'E0:63:E5': 'Samsung', '78:D6:F0': 'Samsung', 'C8:14:79': 'Xiaomi', '64:CC:2E': 'Xiaomi', '34:CE:00': 'Xiaomi', '28:6C:07': 'Huawei', '48:49:5C': 'Huawei', '3C:7C:3F': 'Huawei', '70:A8:E3': 'Amazon', '44:65:0D': 'Amazon', '18:B4:30': 'Google/Nest', 'F4:F5:D8': 'Google/Nest', '00:0F:B5': 'Netgear', '10:0D:7F': 'Netgear', '2C:30:33': 'Ruckus', '38:FF:36': 'Ubiquiti', '04:18:D6': 'TP-Link', '60:E7:01': 'TP-Link'}\n"
            + "def vendor(bssid):\n"
            + "    try:\n"
            + "        return OUI.get(str(bssid).upper()[0:8], '?')\n"
            + "    except Exception:\n"
            + "        return '?'\n"
            + "def classify(cap):\n"
            + "    c = str(cap or '').upper()\n"
            + "    if 'SAE' in c or 'WPA3' in c:\n"
            + "        return ('WPA3', 'OK')\n"
            + "    if 'OWE' in c and 'WPA' not in c:\n"
            + "        return ('OWE', 'MEDIUM')\n"
            + "    if 'WEP' in c:\n"
            + "        return ('WEP', 'HIGH')\n"
            + "    if 'WPA2' in c:\n"
            + "        if 'WPS' in c:\n"
            + "            return ('WPA2+WPS', 'MEDIUM')\n"
            + "        return ('WPA2', 'OK')\n"
            + "    if 'WPA' in c:\n"
            + "        return ('WPA', 'MEDIUM')\n"
            + "    if c.strip() == '[ESS]' or c.strip() == 'ESS':\n"
            + "        return ('OPEN', 'HIGH')\n"
            + "    if 'ESS' in c and 'PRIVACY' not in c:\n"
            + "        return ('OPEN', 'HIGH')\n"
            + "    if not c.strip():\n"
            + "        return ('OPEN?', 'HIGH')\n"
            + "    return ('UNKNOWN', 'MEDIUM')\n"
            + "def freq_to_ch(f):\n"
            + "    try:\n"
            + "        f = int(f)\n"
            + "    except Exception:\n"
            + "        return ('?', '?')\n"
            + "    if f == 2484:\n"
            + "        return (14, '2.4')\n"
            + "    if 2412 <= f <= 2472:\n"
            + "        return ((f - 2412) // 5 + 1, '2.4')\n"
            + "    if 5170 <= f <= 5825:\n"
            + "        return ((f - 5000) // 5, '5')\n"
            + "    if 5955 <= f <= 7115:\n"
            + "        return ((f - 5950) // 5, '6')\n"
            + "    if 2400 <= f < 2500:\n"
            + "        return ('?', '2.4')\n"
            + "    if 5000 <= f < 6000:\n"
            + "        return ('?', '5')\n"
            + "    return ('?', '?')\n"
            + "def pct_of(r):\n"
            + "    try:\n"
            + "        r = int(r)\n"
            + "    except Exception:\n"
            + "        return 0\n"
            + "    if r >= -50:\n"
            + "        return 100\n"
            + "    if r <= -100:\n"
            + "        return 0\n"
            + "    return 2 * (r + 100)\n"
            + "def bars(p):\n"
            + "    if p >= 75:\n"
            + "        return '####'\n"
            + "    if p >= 50:\n"
            + "        return '###'\n"
            + "    if p >= 25:\n"
            + "        return '##'\n"
            + "    return '#'\n"
            + "rows = []\n"
            + "for s in scans:\n"
            + "    try:\n"
            + "        ssid = str(s.get('ssid') or '<hidden>')\n"
            + "        bssid = str(s.get('bssid') or '?')\n"
            + "        rssi = int(s.get('rssi', -100))\n"
            + "    except Exception:\n"
            + "        continue\n"
            + "    freq = s.get('frequency_mhz', 0)\n"
            + "    ch, band = freq_to_ch(freq)\n"
            + "    sec, risk = classify(s.get('capabilities', ''))\n"
            + "    p = pct_of(rssi)\n"
            + "    is_conn = (bssid.upper() == connected and connected != '')\n"
            + "    rows.append({'ssid': ssid, 'bssid': bssid, 'rssi': rssi, 'pct': p, 'bars': bars(p), 'freq': freq, 'ch': ch, 'band': band, 'sec': sec, 'risk': risk, 'vendor': vendor(bssid), 'conn': is_conn, 'caps': str(s.get('capabilities') or '')})\n"
            + "rows.sort(key=lambda r: r['rssi'], reverse=True)\n"
            + "print('Wi-Fi scan: %d networks (sorted by signal)' % len(rows))\n"
            + "print('------------------------------------------------------------')\n"
            + "print('%-24s %-17s %7s %-7s %-9s %-12s %s' % ('SSID', 'BSSID', 'dBm/%', 'Ch', 'SEC', 'VENDOR', 'FLAGS'))\n"
            + "for r in rows[:40]:\n"
            + "    ss = r['ssid'][:24]\n"
            + "    flag = ('*CONNECTED ' if r['conn'] else '') + r['risk']\n"
            + "    print('%-24s %-17s %4d/%3d %-7s %-9s %-12s %s %s' % (ss, r['bssid'], r['rssi'], r['pct'], ('%s/%s' % (r['ch'], r['band'])), r['sec'], r['vendor'][:12], r['bars'], flag))\n"
            + "if len(rows) > 40:\n"
            + "    print('... +%d more (see JSON/CSV report)' % (len(rows) - 40))\n"
            + "ch_count = collections.Counter(str(r['ch']) + '/' + str(r['band']) for r in rows)\n"
            + "open_n = sum(1 for r in rows if r['risk'] == 'HIGH')\n"
            + "med_n = sum(1 for r in rows if r['risk'] == 'MEDIUM')\n"
            + "busiest = ch_count.most_common(1)[0] if ch_count else ('-', 0)\n"
            + "ssid_groups = collections.defaultdict(set)\n"
            + "for r in rows:\n"
            + "    ssid_groups[r['ssid']].add(r['bssid'].upper())\n"
            + "dupes = sorted([k for k, v in ssid_groups.items() if len(v) > 1 and k != '<hidden>'])\n"
            + "print('------------------------------------------------------------')\n"
            + "top = rows[0] if rows else None\n"
            + "tops = ('%s %ddBm' % (top['ssid'][:20], top['rssi'])) if top else 'none'\n"
            + "print('Summary: %d nets, strongest: %s, HIGH-risk: %d, MEDIUM: %d, busiest: Ch %s (%d nets)' % (len(rows), tops, open_n, med_n, busiest[0], busiest[1]))\n"
            + "if dupes:\n"
            + "    print('Note: duplicate SSID on multiple BSSIDs (mesh or check): ' + ', '.join(dupes[:5]))\n"
            + "print('Advice: 2.4GHz use Ch 1/6/11; prefer WPA2/WPA3; avoid OPEN/WEP for anything sensitive.')\n"
            + "hist = os.path.join(cache, 'history.jsonl')\n"
            + "prev = None\n"
            + "try:\n"
            + "    with open(hist, 'r', encoding='utf-8') as f:\n"
            + "        lines = [l for l in f if l.strip()]\n"
            + "        if lines:\n"
            + "            prev = json.loads(lines[-1])\n"
            + "except Exception:\n"
            + "    prev = None\n"
            + "rank = {'OK': 0, 'MEDIUM': 1, 'HIGH': 2}\n"
            + "cur_map = {r['bssid'].upper(): r for r in rows}\n"
            + "if isinstance(prev, dict) and isinstance(prev.get('networks'), list):\n"
            + "    prev_map = {}\n"
            + "    for n in prev['networks']:\n"
            + "        try:\n"
            + "            prev_map[str(n.get('bssid', '')).upper()] = n\n"
            + "        except Exception:\n"
            + "            pass\n"
            + "    new = [b for b in cur_map if b not in prev_map]\n"
            + "    gone = [b for b in prev_map if b not in cur_map]\n"
            + "    weaker, stronger = [], []\n"
            + "    for b, r in cur_map.items():\n"
            + "        if b in prev_map:\n"
            + "            try:\n"
            + "                pr = rank.get(str(prev_map[b].get('risk', 'OK')), 0)\n"
            + "                cr = rank.get(r['risk'], 0)\n"
            + "                if cr > pr:\n"
            + "                    weaker.append(b)\n"
            + "                elif cr < pr:\n"
            + "                    stronger.append(b)\n"
            + "            except Exception:\n"
            + "                pass\n"
            + "    if new or gone or weaker or stronger:\n"
            + "        print('--- vs previous scan (%s) ---' % str(prev.get('ts', '?')))\n"
            + "        for b in new[:10]:\n"
            + "            print('[NEW] %s (%s)' % (cur_map[b]['ssid'][:24], b))\n"
            + "        for b in gone[:10]:\n"
            + "            print('[GONE] %s (%s)' % (str(prev_map[b].get('ssid', '?'))[:24], b))\n"
            + "        for b in weaker[:10]:\n"
            + "            print('[WEAKER] %s (%s)' % (cur_map[b]['ssid'][:24], b))\n"
            + "        for b in stronger[:10]:\n"
            + "            print('[STRONGER] %s (%s)' % (cur_map[b]['ssid'][:24], b))\n"
            + "base = os.path.join(outdir, 'scan-' + ts)\n"
            + "enriched = [{'ssid': r['ssid'], 'bssid': r['bssid'], 'rssi_dbm': r['rssi'], 'signal_pct': r['pct'], 'frequency_mhz': r['freq'], 'channel': r['ch'], 'band_ghz': r['band'], 'security': r['sec'], 'risk': r['risk'], 'vendor': r['vendor'], 'connected': r['conn'], 'capabilities': r['caps']} for r in rows]\n"
            + "with open(base + '.json', 'w', encoding='utf-8') as f:\n"
            + "    json.dump({'ts': ts, 'count': len(enriched), 'networks': enriched}, f, indent=2)\n"
            + "with open(base + '.csv', 'w', encoding='utf-8', newline='') as f:\n"
            + "    w = csv.writer(f)\n"
            + "    w.writerow(['ssid', 'bssid', 'rssi_dbm', 'signal_pct', 'frequency_mhz', 'channel', 'band_ghz', 'security', 'risk', 'vendor', 'connected'])\n"
            + "    for r in enriched:\n"
            + "        w.writerow([r['ssid'], r['bssid'], r['rssi_dbm'], r['signal_pct'], r['frequency_mhz'], r['channel'], r['band_ghz'], r['security'], r['risk'], r['vendor'], r['connected']])\n"
            + "with open(base + '.html', 'w', encoding='utf-8') as f:\n"
            + "    f.write('<!doctype html><html><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">')\n"
            + "    f.write('<title>Wi-Fi scan ' + html.escape(ts) + '</title>')\n"
            + "    f.write('<style>body{font-family:sans-serif;margin:16px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:6px;font-size:14px}th{background:#eee}.high{color:#c00;font-weight:bold}.med{color:#a60}</style></head><body>')\n"
            + "    f.write('<h2>Wi-Fi scan ' + html.escape(ts) + ' (' + str(len(enriched)) + ' nets)</h2>')\n"
            + "    f.write('<p>Passive scan via termux-wifi-scaninfo. Open with Preview. Avoid OPEN/WEP for sensitive use.</p><table><tr><th>SSID</th><th>BSSID</th><th>dBm/%</th><th>Ch</th><th>SEC</th><th>Vendor</th><th>Flags</th></tr>')\n"
            + "    for r in enriched:\n"
            + "        cls = 'high' if r['risk'] == 'HIGH' else ('med' if r['risk'] == 'MEDIUM' else '')\n"
            + "        f.write('<tr><td>' + html.escape(str(r['ssid'])) + '</td><td>' + html.escape(str(r['bssid'])) + '</td><td>' + str(r['rssi_dbm']) + '/' + str(r['signal_pct']) + '</td><td>' + html.escape(str(r['channel']) + '/' + str(r['band_ghz'])) + '</td><td class=' + chr(34) + cls + chr(34) + '>' + html.escape(str(r['security'])) + '</td><td>' + html.escape(str(r['vendor'])) + '</td><td>' + ('CONNECTED ' if r['connected'] else '') + html.escape(str(r['risk'])) + '</td></tr>')\n"
            + "    f.write('</table></body></html>')\n"
            + "try:\n"
            + "    with open(hist, 'a', encoding='utf-8') as f:\n"
            + "        f.write(json.dumps({'ts': ts, 'networks': [{'bssid': r['bssid'], 'ssid': r['ssid'], 'sec': r['sec'], 'risk': r['risk'], 'rssi': r['rssi']} for r in rows]}) + chr(10))\n"
            + "except Exception:\n"
            + "    pass\n"
            + "summ = '%d nets, %s, %d HIGH, busiest Ch %s' % (len(rows), tops, open_n, busiest[0])\n"
            + "with open(os.path.join(cache, 'last-summary.txt'), 'w', encoding='utf-8') as f:\n"
            + "    f.write(summ)\n"
            + "PYEOF\n"
            + "STATUS=$?\n"
            + "if [ -f \"$SUM_F\" ]; then toast \"$(cat \"$SUM_F\")\"; else toast 'Wi-Fi scan done'; fi\n"
            + "echo \"Reports -> $OUTDIR\"\n"
            + "exit $STATUS\n");
        m.put(ID_BATTERY_STATUS, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: battery-status\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + needApi
            + "need_api termux-battery-status\n"
            + "need_api termux-clipboard-set\n"
            + "json=$(termux-battery-status || true)\n"
            + "if [ -z \"$json\" ]; then toast 'No battery info'; exit 1; fi\n"
            + "printf '%s\\n' \"$json\" | termux-clipboard-set\n"
            + "pct=$(printf '%s' \"$json\" | tr ',' '\\n' | sed -n 's/.*\"percentage\":[[:space:]]*\\([0-9]*\\).*/\\1/p' | head -1)\n"
            + "st=$(printf '%s' \"$json\" | tr ',' '\\n' | sed -n 's/.*\"status\":[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' | head -1)\n"
            + "toast \"Battery ${pct:-?}% ${st}\"\n"
            + "printf '%s\\n' \"$json\"\n");
        m.put(ID_TORCH_TOGGLE, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: torch-toggle\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "HOME=\"" + home + "\"\n"
            + needApi
            + "need_api termux-torch\n"
            + "STATE=\"$HOME/.cache/invapp-torch.on\"\n"
            + "mkdir -p \"$HOME/.cache\"\n"
            + "if [ -f \"$STATE\" ]; then\n"
            + "  termux-torch off\n"
            + "  rm -f \"$STATE\"\n"
            + "  toast 'Torch off'\n"
            + "else\n"
            + "  termux-torch on\n"
            + "  touch \"$STATE\"\n"
            + "  toast 'Torch on'\n"
            + "fi\n");
        m.put(ID_SHARE_CLIPBOARD, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: share-clipboard\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + needApi
            + "need_api termux-clipboard-get\n"
            + "need_api termux-share\n"
            + "text=$(termux-clipboard-get || true)\n"
            + "if [ -z \"$text\" ]; then toast 'Clipboard empty'; exit 0; fi\n"
            + "toast 'Sharing clipboard…'\n"
            + "printf '%s' \"$text\" | termux-share -a send\n");
        m.put(ID_OPEN_SETTINGS, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: open-settings\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "toast 'Opening Settings…'\n"
            + "am start -a android.settings.SETTINGS >/dev/null 2>&1 \\\n"
            + "  || am start -n com.android.settings/.Settings >/dev/null 2>&1 \\\n"
            + "  || { toast 'Could not open Settings'; exit 1; }\n");
        m.put(ID_VIBRATE, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: vibrate\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + needApi
            + "need_api termux-vibrate\n"
            + "toast 'Vibrate'\n"
            + "termux-vibrate -d 300\n");
        m.put(ID_VOLUME_INFO, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: volume-info\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + needApi
            + "need_api termux-volume\n"
            + "need_api termux-clipboard-set\n"
            + "json=$(termux-volume || true)\n"
            + "if [ -z \"$json\" ]; then toast 'No volume info'; exit 1; fi\n"
            + "printf '%s\\n' \"$json\" | termux-clipboard-set\n"
            + "toast 'Volume JSON → clipboard'\n"
            + "printf '%s\\n' \"$json\"\n");
        m.put(ID_LOCATION, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: location\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + needApi
            + "need_api termux-location\n"
            + "need_api termux-clipboard-set\n"
            + "toast 'Getting location…'\n"
            + "json=$(termux-location -p network 2>/dev/null || termux-location || true)\n"
            + "if [ -z \"$json\" ]; then toast 'No location (grant permission)'; exit 1; fi\n"
            + "printf '%s\\n' \"$json\" | termux-clipboard-set\n"
            + "lat=$(printf '%s' \"$json\" | tr ',' '\\n' | sed -n 's/.*\"latitude\":[[:space:]]*\\([-0-9.]*\\).*/\\1/p' | head -1)\n"
            + "lon=$(printf '%s' \"$json\" | tr ',' '\\n' | sed -n 's/.*\"longitude\":[[:space:]]*\\([-0-9.]*\\).*/\\1/p' | head -1)\n"
            + "if [ -n \"$lat\" ] && [ -n \"$lon\" ]; then\n"
            + "  toast \"${lat}, ${lon}\"\n"
            + "else\n"
            + "  toast 'Location → clipboard'\n"
            + "fi\n"
            + "printf '%s\\n' \"$json\"\n");
        m.put(ID_TELEPHONY_INFO, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: telephony-info\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + needApi
            + "need_api termux-telephony-deviceinfo\n"
            + "need_api termux-clipboard-set\n"
            + "json=$(termux-telephony-deviceinfo || true)\n"
            + "if [ -z \"$json\" ]; then toast 'No telephony info'; exit 1; fi\n"
            + "printf '%s\\n' \"$json\" | termux-clipboard-set\n"
            + "toast 'Telephony JSON → clipboard'\n"
            + "printf '%s\\n' \"$json\"\n");
        m.put(ID_STOP_AI, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: stop-ai\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "PORT=4096\n"
            + "toast \"Stopping OpenCode :$PORT…\"\n"
            + "if command -v fuser >/dev/null 2>&1; then\n"
            + "  fuser -k \"${PORT}/tcp\" 2>/dev/null || true\n"
            + "fi\n"
            + "pkill -f '[o]pencode web' 2>/dev/null || true\n"
            + "pkill -f '[o]pencode serve' 2>/dev/null || true\n"
            + "sleep 0.3\n"
            + "if curl -fsS --connect-timeout 1 --max-time 1 \\\n"
            + "    \"http://127.0.0.1:${PORT}/global/health\" 2>/dev/null | grep -qi healthy; then\n"
            + "  toast 'Still healthy — try drawer Stop AI'\n"
            + "  exit 1\n"
            + "fi\n"
            + "toast 'OpenCode stopped'\n");
        m.put(ID_TD_UPGRADE, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: td-upgrade\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "toast 'td-upgrade running…'\n"
            + "if ! command -v td-upgrade >/dev/null 2>&1; then\n"
            + "  msg='td-upgrade missing — open InVxTermux once'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "td-upgrade\n"
            + "toast 'td-upgrade done'\n");
        m.put(ID_STOP_AGENT_WORKER, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: stop-agent-worker\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "toast 'Stopping Cursor agent worker…'\n"
            + "if command -v td-agent-worker >/dev/null 2>&1; then\n"
            + "  td-agent-worker stop\n"
            + "else\n"
            + "  pkill -f '[a]gent worker' 2>/dev/null || true\n"
            + "  command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock || true\n"
            + "fi\n"
            + "toast 'Agent worker stopped'\n");
        m.put(ID_TD_AI, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: td-ai (background task)\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "_oc_auth() {\n"
            + "  _sj=\"$HOME/.config/opencode/service.json\"\n"
            + "  if [ -f \"$_sj\" ]; then\n"
            + "    _pw=$(sed -n 's/.*\"password\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' \"$_sj\" | head -1)\n"
            + "    if [ -n \"$_pw\" ]; then echo \"-u opencode:$_pw\"; fi\n"
            + "  fi\n"
            + "}\n"
            + "_oc_ready() {\n"
            // V2 first (enveloped /api/info), V1 fallback.
            + "  if curl -fsS $(_oc_auth) --connect-timeout 1 --max-time 2 \\\n"
            + "      http://127.0.0.1:4096/api/info 2>/dev/null | grep -qi '\"data\"\\|version'; then return 0; fi\n"
            + "  if curl -fsS --connect-timeout 1 --max-time 2 \\\n"
            + "      http://127.0.0.1:4096/global/health 2>/dev/null | grep -qi healthy; then return 0; fi\n"
            + "  return 1\n"
            + "}\n"
            + "if _oc_ready; then\n"
            + "  toast 'OpenCode already ready :4096'\n"
            + "  exit 0\n"
            + "fi\n"
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
            + "  if _oc_ready; then\n"
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
        m.put(ID_AGENT_WORKER, ""
            + "#!" + bash + "\n"
            + "# invapp-widget: agent-worker (background task)\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast \"$1\" || true; }\n"
            + "if pgrep -f '[a]gent worker' >/dev/null 2>&1; then\n"
            + "  toast 'Agent worker already running'\n"
            + "  exit 0\n"
            + "fi\n"
            + "toast 'Starting Cursor agent worker…'\n"
            + "if ! command -v td-agent-worker >/dev/null 2>&1; then\n"
            + "  msg='td-agent-worker missing — open InVxTermux once'\n"
            + "  toast \"$msg\"; echo \"$msg\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true\n"
            + "td-agent-worker start &\n"
            + "sleep 2\n"
            + "if pgrep -f '[a]gent worker' >/dev/null 2>&1; then\n"
            + "  toast 'Agent worker started — cursor.com/agents'\n"
            + "  exit 0\n"
            + "fi\n"
            + "toast 'Worker may need: agent login (check session)'\n"
            + "exit 0\n");
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
