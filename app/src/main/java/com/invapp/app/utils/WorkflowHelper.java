package com.invapp.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.invapp.shared.termux.TermuxConstants;
import com.invapp.terminal.TerminalSession;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Terminal Dev workflow helpers: inject shell commands, list {@code ~/repos},
 * read {@code package.json} scripts, git status for the Repos picker.
 */
public final class WorkflowHelper {

    /** Fast-forward only — avoids merge commits / dirty surprises from the quick bar. */
    public static final String CMD_GIT_PULL = "git pull --ff-only\n";
    /** Fetch + ff-only pull every git repo under {@code ~/repos}. */
    public static final String CMD_TD_SYNC = "td-sync\n";
    public static final String CMD_BUN_INSTALL = "bun install\n";
    public static final String CMD_BUN_RUN_DEV = "bun run dev\n";
    public static final String CMD_TD_AI = "td-ai\n";
    /** Re-install / repair OpenCode V2 wrapper (does not wipe {@code ~/.config/opencode}). */
    public static final String CMD_OPENCODE_SETUP = "opencode-setup\n";
    private static final long GIT_STATUS_CACHE_MS = 10_000L;
    private static final Map<String, CachedGitStatus> sGitStatusCache = new ConcurrentHashMap<>();
    /** First-run setup: banner / completions / packages / doctor (pure bash). */
    public static final String CMD_IVX_CONFIGURE = "ivx-configure\n";
    /** OpenCode service default ({@code http://127.0.0.1:4000/}). */
    public static final int AI_PREVIEW_PORT = 4000;
    /** OpenCode V2 API probe — see https://opencode.ai/v2/docs/api */
    public static final String AI_INFO_PATH = "/api/info";
    /** OpenCode V1 health probe (kept as fallback) — see https://opencode.ai/docs/server/#apis */
    public static final String AI_HEALTH_PATH = "/global/health";
    public static final String AI_DOC_PATH = "/doc";
    public static final int VITE_DEFAULT_PORT = 5173;

    /** Loopback base URL for OpenCode Preview / API (no trailing slash). */
    @NonNull
    public static String aiBaseUrl(int port) {
        int p = port > 0 ? port : AI_PREVIEW_PORT;
        return "http://127.0.0.1:" + p;
    }

    @NonNull
    public static String aiHealthUrl(int port) {
        return aiBaseUrl(port) + AI_HEALTH_PATH;
    }

    @NonNull
    public static String aiInfoUrl(int port) {
        return aiBaseUrl(port) + AI_INFO_PATH;
    }

    /**
     * Parse Preview input that may be a bare port ({@code 4000}),
     * {@code host:port}, or a full URL
     * ({@code http://127.0.0.1:4000/path?q=1}, {@code https://example.com:443/}).
     *
     * @return parsed target, or null when invalid
     */
    @Nullable
    public static ParsedPreviewUrl parsePreviewInput(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Bare port.
        try {
            int port = Integer.parseInt(s);
            if (port >= 1 && port <= 65535) {
                return new ParsedPreviewUrl(port, "/");
            }
            return null;
        } catch (NumberFormatException ignored) {
            // fall through to URL forms
        }
        String lower = s.toLowerCase(java.util.Locale.US);
        String work = s;
        String scheme = "http";
        // Strip scheme; preserve https when present.
        int schemeSep = lower.indexOf("://");
        if (schemeSep >= 0) {
            String schemePart = lower.substring(0, schemeSep);
            if ("https".equals(schemePart)) {
                scheme = "https";
            } else if (!"http".equals(schemePart)) {
                return null;
            }
            work = s.substring(schemeSep + 3);
        }
        // Split host:port + path.
        String hostPort = work;
        String path = "/";
        int slash = work.indexOf('/');
        if (slash >= 0) {
            hostPort = work.substring(0, slash);
            path = work.substring(slash);
            if (path.isEmpty()) {
                path = "/";
            }
        } else {
            int q = work.indexOf('?');
            if (q >= 0) {
                hostPort = work.substring(0, q);
                path = "/" + work.substring(q);
            }
        }
        // Drop userinfo if pasted from an authed URL.
        int at = hostPort.lastIndexOf('@');
        if (at >= 0) {
            hostPort = hostPort.substring(at + 1);
        }
        String host = hostPort;
        String portStr = null;
        int colon = hostPort.lastIndexOf(':');
        // IPv6 bracket form [::1]:port.
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            if (close < 0) {
                return null;
            }
            host = hostPort.substring(0, close + 1);
            if (close + 1 < hostPort.length() && hostPort.charAt(close + 1) == ':') {
                portStr = hostPort.substring(close + 2);
            }
        } else if (colon >= 0) {
            host = hostPort.substring(0, colon);
            portStr = hostPort.substring(colon + 1);
        }
        if (portStr == null || portStr.isEmpty()) {
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (port < 1 || port > 65535) {
            return null;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String hostLower = host.trim().toLowerCase(java.util.Locale.US);
        if (!hostLower.isEmpty()
            && !"127.0.0.1".equals(hostLower)
            && !"localhost".equals(hostLower)
            && !"[::1]".equals(hostLower)
            && !"::1".equals(hostLower)) {
            return new ParsedPreviewUrl(port, path, host.trim(), scheme);
        }
        return new ParsedPreviewUrl(port, path, null, scheme);
    }

    /**
     * Whether Preview WebView may navigate to {@code url}.
     * Allows {@code http}/{@code https} plus inert {@code about}/{@code data} pages.
     */
    public static boolean isAllowedPreviewUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.startsWith("http://")
            || lower.startsWith("https://")
            || lower.startsWith("about:")
            || lower.startsWith("data:");
    }

    /** Parsed Preview target: port + path, optional host, and http/https scheme. */
    public static final class ParsedPreviewUrl {
        public final int port;
        @NonNull
        public final String pathAndQuery;
        @Nullable
        public final String nonLoopbackHost;
        /** {@code http} or {@code https}. */
        @NonNull
        public final String scheme;

        public ParsedPreviewUrl(int port, @NonNull String pathAndQuery) {
            this(port, pathAndQuery, null, "http");
        }

        public ParsedPreviewUrl(int port, @NonNull String pathAndQuery,
                                 @Nullable String nonLoopbackHost) {
            this(port, pathAndQuery, nonLoopbackHost, "http");
        }

        public ParsedPreviewUrl(int port, @NonNull String pathAndQuery,
                                 @Nullable String nonLoopbackHost,
                                 @NonNull String scheme) {
            this.port = port;
            this.pathAndQuery = pathAndQuery;
            this.nonLoopbackHost = nonLoopbackHost;
            this.scheme = "https".equalsIgnoreCase(scheme) ? "https" : "http";
        }

        public boolean isLoopback() {
            return nonLoopbackHost == null;
        }

        /** Host used for loading: non-loopback when set, otherwise {@code 127.0.0.1}. */
        @NonNull
        public String loadHost() {
            return nonLoopbackHost != null ? nonLoopbackHost : "127.0.0.1";
        }
    }

    /** create-vite (+ pwa overlays) templates we expose in the New… sheet. */
    public static final String[] VITE_TEMPLATES = {
        "react-ts", "react", "vue", "vue-ts", "vanilla-ts", "vanilla",
        "pwa", "pwa-react"
    };

    /** Default template for {@code td-scaffold} when omitted. */
    public static final String DEFAULT_VITE_TEMPLATE = "react-ts";

    private WorkflowHelper() {}

    @NonNull
    public static String tdScaffoldCommand(@NonNull String name, @NonNull String template) {
        return "td-scaffold " + shellSingleQuote(name) + " " + shellSingleQuote(template) + "\n";
    }

    /**
     * {@code td-clone <url> [name] [--bun-i]} — clone into {@code ~/repos}.
     */
    @NonNull
    public static String tdCloneCommand(@NonNull String url, @NonNull String name,
                                       boolean bunInstall) {
        StringBuilder sb = new StringBuilder("td-clone ");
        sb.append(shellSingleQuote(url)).append(' ').append(shellSingleQuote(name));
        if (bunInstall) {
            sb.append(" --bun-i");
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Derive a directory name from a git URL ({@code …/foo.git} → {@code foo}).
     */
    @NonNull
    public static String suggestRepoNameFromUrl(@Nullable String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        if (s.isEmpty()) {
            return "";
        }
        // git@host:path/repo.git
        int colon = s.lastIndexOf(':');
        int slash = s.lastIndexOf('/');
        String last;
        if (s.startsWith("git@") && colon > 0 && colon > s.indexOf('@')) {
            last = s.substring(colon + 1);
            int slashIn = last.lastIndexOf('/');
            if (slashIn >= 0) {
                last = last.substring(slashIn + 1);
            }
        } else if (slash >= 0 && slash < s.length() - 1) {
            last = s.substring(slash + 1);
        } else {
            last = s;
        }
        if (last.endsWith(".git")) {
            last = last.substring(0, last.length() - 4);
        }
        // Strip query/fragment noise
        int q = last.indexOf('?');
        if (q >= 0) {
            last = last.substring(0, q);
        }
        int hash = last.indexOf('#');
        if (hash >= 0) {
            last = last.substring(0, hash);
        }
        return last;
    }

    /** Loose check: https/http/git/ssh-style remote. */
    public static boolean isPlausibleGitUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }
        String s = url.trim();
        if (s.length() < 4) {
            return false;
        }
        String lower = s.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("https://") || lower.startsWith("http://")
            || lower.startsWith("git://") || lower.startsWith("ssh://")) {
            return s.contains("/") || s.contains(":");
        }
        // git@host:path
        return s.startsWith("git@") && s.contains(":");
    }

    /** Same rules as {@code td-scaffold} / {@code td-clone} directory names. */
    public static boolean isValidRepoName(@Nullable String name) {
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    public static boolean writeToSession(@Nullable TerminalSession session, @NonNull String text) {
        if (session == null || !session.isRunning() || text.isEmpty()) {
            return false;
        }
        session.write(text);
        return true;
    }

    @NonNull
    public static File reposDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "repos");
    }

    /**
     * Immediate children of {@code ~/repos} that are directories (sorted).
     */
    @NonNull
    public static List<File> listRepos() {
        File root = reposDir();
        File[] children = root.listFiles();
        if (children == null || children.length == 0) {
            return Collections.emptyList();
        }
        List<File> dirs = new ArrayList<>();
        for (File f : children) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                dirs.add(f);
            }
        }
        Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return dirs;
    }

    /**
     * Script names from {@code package.json} {@code scripts} object, sorted.
     */
    @NonNull
    public static List<String> listPackageScripts(@NonNull File projectDir) {
        File pkg = new File(projectDir, "package.json");
        if (!pkg.isFile()) {
            return Collections.emptyList();
        }
        try {
            byte[] buf = new byte[(int) Math.min(pkg.length(), 512 * 1024)];
            int n;
            try (FileInputStream in = new FileInputStream(pkg)) {
                n = in.read(buf);
            }
            if (n <= 0) {
                return Collections.emptyList();
            }
            JSONObject root = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));
            if (!root.has("scripts")) {
                return Collections.emptyList();
            }
            JSONObject scripts = root.getJSONObject("scripts");
            List<String> names = new ArrayList<>();
            Iterator<String> keys = scripts.keys();
            while (keys.hasNext()) {
                names.add(keys.next());
            }
            Collections.sort(names, String::compareToIgnoreCase);
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @NonNull
    public static String cdAndRunScript(@NonNull File projectDir, @NonNull String script) {
        return "cd " + shellSingleQuote(projectDir.getAbsolutePath())
            + " && bun run " + shellSingleQuote(script) + "\n";
    }

    @NonNull
    public static String cdToRepo(@NonNull File projectDir) {
        return "cd " + shellSingleQuote(projectDir.getAbsolutePath()) + "\n";
    }

    /** Prefer {@code ~/repos/name} style when under home. */
    @NonNull
    public static String displayRepoName(@NonNull File projectDir) {
        return projectDir.getName();
    }

    /**
     * {@code cd} into {@code projectDir} and {@code git pull --ff-only}.
     */
    @NonNull
    public static String safePullInRepo(@NonNull File projectDir) {
        return "cd " + shellSingleQuote(projectDir.getAbsolutePath())
            + " && git pull --ff-only\n";
    }

    /**
     * {@code cd} into {@code projectDir} and check out {@code branch} (local name).
     */
    @NonNull
    public static String checkoutBranch(@NonNull File projectDir, @NonNull String branch) {
        return "cd " + shellSingleQuote(projectDir.getAbsolutePath())
            + " && git checkout " + shellSingleQuote(branch) + "\n";
    }

    /**
     * Label for the Repos picker: {@code name · branch · dirty[+N] · ↑a↓b}.
     */
    @NonNull
    public static String formatRepoLabel(@NonNull File projectDir,
                                         @Nullable GitRepoStatus status) {
        String name = displayRepoName(projectDir);
        if (status == null || !status.isGitRepo) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        if (status.branch != null && !status.branch.isEmpty()) {
            sb.append(" · ").append(status.branch);
        }
        if (status.dirtyCount > 0) {
            sb.append(" · dirty+").append(status.dirtyCount);
        }
        if (status.ahead > 0 || status.behind > 0) {
            sb.append(" ·");
            if (status.ahead > 0) {
                sb.append(" ↑").append(status.ahead);
            }
            if (status.behind > 0) {
                sb.append(" ↓").append(status.behind);
            }
        }
        return sb.toString();
    }

    /**
     * Read {@code git status --porcelain=v1 -b} for {@code projectDir} (cached ~10s).
     * Safe to call off the UI thread.
     */
    @NonNull
    public static GitRepoStatus readGitStatus(@NonNull File projectDir) {
        String key = projectDir.getAbsolutePath();
        CachedGitStatus cached = sGitStatusCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < GIT_STATUS_CACHE_MS) {
            return cached.status;
        }
        GitRepoStatus status = readGitStatusUncached(projectDir);
        sGitStatusCache.put(key, new CachedGitStatus(status, now));
        return status;
    }

    /** Drop cached status (e.g. after a pull / checkout command is queued). */
    public static void invalidateGitStatus(@NonNull File projectDir) {
        sGitStatusCache.remove(projectDir.getAbsolutePath());
    }

    @NonNull
    static GitRepoStatus readGitStatusUncached(@NonNull File projectDir) {
        File gitDir = new File(projectDir, ".git");
        if (!gitDir.exists()) {
            return GitRepoStatus.notGit();
        }
        File gitBin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "git");
        if (!gitBin.isFile()) {
            return GitRepoStatus.notGit();
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                gitBin.getAbsolutePath(),
                "-c", "color.ui=false",
                "status", "--porcelain=v1", "-b"
            );
            pb.directory(projectDir);
            pb.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
            pb.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
            pb.environment().put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(line);
                }
            }
            waitBriefly(p, 4);
            return parseGitStatusPorcelain(out.toString(), true);
        } catch (Exception e) {
            return GitRepoStatus.notGit();
        }
    }

    /**
     * Local branch names ({@code git branch --format=%(refname:short)}), sorted.
     */
    @NonNull
    public static List<String> listLocalBranches(@NonNull File projectDir) {
        File gitBin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "git");
        if (!gitBin.isFile() || !new File(projectDir, ".git").exists()) {
            return Collections.emptyList();
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                gitBin.getAbsolutePath(),
                "branch", "--format=%(refname:short)"
            );
            pb.directory(projectDir);
            pb.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
            pb.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            List<String> names = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        names.add(t);
                    }
                }
            }
            waitBriefly(p, 4);
            Collections.sort(names, String::compareToIgnoreCase);
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Parse {@code git status --porcelain=v1 -b} stdout.
     * Visible for unit tests.
     */
    @NonNull
    public static GitRepoStatus parseGitStatusPorcelain(@Nullable String output,
                                                        boolean isGitRepo) {
        if (!isGitRepo) {
            return GitRepoStatus.notGit();
        }
        if (output == null || output.isEmpty()) {
            return new GitRepoStatus(true, "HEAD", 0, 0, 0);
        }
        String branch = "HEAD";
        int ahead = 0;
        int behind = 0;
        int dirty = 0;
        String[] lines = output.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == 0 && line.startsWith("## ")) {
                String rest = line.substring(3).trim();
                int dots = rest.indexOf("...");
                String left = dots >= 0 ? rest.substring(0, dots) : rest;
                int bracket = left.indexOf(" [");
                if (bracket >= 0) {
                    left = left.substring(0, bracket);
                }
                // Detached: "## HEAD (no branch)"
                if (left.startsWith("HEAD")) {
                    branch = "HEAD";
                } else {
                    branch = left.trim();
                }
                int open = rest.lastIndexOf('[');
                int close = rest.lastIndexOf(']');
                if (open >= 0 && close > open) {
                    String meta = rest.substring(open + 1, close).toLowerCase(Locale.US);
                    ahead = parseAheadBehindToken(meta, "ahead");
                    behind = parseAheadBehindToken(meta, "behind");
                }
                continue;
            }
            if (!line.isEmpty()) {
                dirty++;
            }
        }
        return new GitRepoStatus(true, branch, dirty, ahead, behind);
    }

    private static int parseAheadBehindToken(@NonNull String meta, @NonNull String key) {
        // "ahead 2, behind 1" or "ahead 2"
        int idx = meta.indexOf(key);
        if (idx < 0) {
            return 0;
        }
        int i = idx + key.length();
        while (i < meta.length() && meta.charAt(i) == ' ') {
            i++;
        }
        int start = i;
        while (i < meta.length() && Character.isDigit(meta.charAt(i))) {
            i++;
        }
        if (start == i) {
            return 0;
        }
        try {
            return Integer.parseInt(meta.substring(start, i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void waitBriefly(@NonNull Process p, int seconds) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
                    p.destroy();
                }
            } else {
                p.waitFor();
            }
        } catch (Exception ignored) {
            try {
                p.destroy();
            } catch (Exception ignored2) {
            }
        }
    }

    @NonNull
    private static String shellSingleQuote(@NonNull String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** Snapshot of {@code git status --porcelain=v1 -b} for one repo. */
    public static final class GitRepoStatus {
        public final boolean isGitRepo;
        @Nullable
        public final String branch;
        public final int dirtyCount;
        public final int ahead;
        public final int behind;

        public GitRepoStatus(boolean isGitRepo, @Nullable String branch,
                             int dirtyCount, int ahead, int behind) {
            this.isGitRepo = isGitRepo;
            this.branch = branch;
            this.dirtyCount = Math.max(0, dirtyCount);
            this.ahead = Math.max(0, ahead);
            this.behind = Math.max(0, behind);
        }

        @NonNull
        public static GitRepoStatus notGit() {
            return new GitRepoStatus(false, null, 0, 0, 0);
        }

        public boolean isDirty() {
            return dirtyCount > 0;
        }
    }

    private static final class CachedGitStatus {
        @NonNull
        final GitRepoStatus status;
        final long atMs;

        CachedGitStatus(@NonNull GitRepoStatus status, long atMs) {
            this.status = status;
            this.atMs = atMs;
        }
    }
}
