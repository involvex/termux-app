package com.invapp.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.invapp.shared.termux.TermuxConstants;
import com.invapp.terminal.TerminalSession;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Terminal Dev workflow helpers: inject shell commands, list {@code ~/repos},
 * read {@code package.json} scripts.
 */
public final class WorkflowHelper {

    public static final String CMD_GIT_PULL = "git pull\n";
    public static final String CMD_BUN_INSTALL = "bun install\n";
    public static final String CMD_BUN_RUN_DEV = "bun run dev\n";
    public static final String CMD_TD_AI = "td-ai\n";
    public static final int AI_PREVIEW_PORT = 4096;
    public static final int VITE_DEFAULT_PORT = 5173;

    /** create-vite (+ pwa overlays) templates we expose in the New… sheet. */
    public static final String[] VITE_TEMPLATES = {
        "vanilla", "vanilla-ts", "react", "react-ts", "vue", "vue-ts",
        "pwa", "pwa-react"
    };

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

    @NonNull
    private static String shellSingleQuote(@NonNull String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
