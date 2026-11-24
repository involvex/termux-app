package com.invapp.app.utils;

import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline fork Help pages under {@code assets/help/*.md}.
 * Page ids are basename-only (no path separators / traversal).
 */
public final class HelpIndex {

    public static final String ASSET_DIR = "help";
    public static final String LINK_SCHEME = "invapp://help/";
    public static final String PAGE_TROUBLESHOOTING = "troubleshooting";

    /** Canonical page ids shipped in the APK (order = index order). */
    public static final String[] PAGES = {
        "install",
        "workflow",
        "ai",
        "troubleshooting",
        "configure",
        "openjdk",
        "cursor-agent",
        "run-command",
    };

    private static final Map<String, String> TITLES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("install", "Install");
        m.put("workflow", "Phone + PC workflow");
        m.put("ai", "AI (OpenCode)");
        m.put("troubleshooting", "Troubleshooting");
        m.put("configure", "ivx-configure");
        m.put("openjdk", "OpenJDK 17");
        m.put("cursor-agent", "Cursor Agent");
        m.put("run-command", "RUN_COMMAND API");
        TITLES = Collections.unmodifiableMap(m);
    }

    private HelpIndex() {}

    @NonNull
    public static List<String> pageIds() {
        List<String> out = new ArrayList<>(PAGES.length);
        Collections.addAll(out, PAGES);
        return out;
    }

    @NonNull
    public static String titleFor(@Nullable String pageId) {
        String id = resolvePageId(pageId);
        if (id == null) {
            return "Help";
        }
        String title = TITLES.get(id);
        return title != null ? title : id;
    }

    /**
     * Normalize a page id from intents / deep links.
     * Returns null for unknown pages or path traversal.
     */
    @Nullable
    public static String resolvePageId(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        // Reject NUL before trim — String.trim() removes chars <= U+0020,
        // which would turn "install\0" into a valid "install".
        if (raw.indexOf('\0') >= 0) {
            return null;
        }
        String id = raw.trim().toLowerCase(Locale.US);
        if (id.isEmpty()) {
            return null;
        }
        if (id.startsWith(LINK_SCHEME)) {
            id = id.substring(LINK_SCHEME.length());
        }
        int q = id.indexOf('?');
        if (q >= 0) {
            id = id.substring(0, q);
        }
        int hash = id.indexOf('#');
        if (hash >= 0) {
            id = id.substring(0, hash);
        }
        if (id.endsWith(".md")) {
            id = id.substring(0, id.length() - 3);
        }
        if (!isSafePageToken(id)) {
            return null;
        }
        for (String page : PAGES) {
            if (page.equals(id)) {
                return id;
            }
        }
        return null;
    }

    /** Reject path separators, dots, null bytes — basename tokens only. */
    @VisibleForTesting
    public static boolean isSafePageToken(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (id.contains("..") || id.contains("/") || id.contains("\\")
            || id.indexOf('\0') >= 0 || id.contains(":")) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                && !(c >= '0' && c <= '9')
                && c != '-') {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static String assetPath(@NonNull String pageId) {
        return ASSET_DIR + "/" + pageId + ".md";
    }

    @NonNull
    public static String readAsset(@NonNull AssetManager assets, @NonNull String pageId)
        throws IOException {
        String id = resolvePageId(pageId);
        if (id == null) {
            throw new IOException("unknown help page");
        }
        try (InputStream in = assets.open(assetPath(id));
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /** Index markdown with in-app links to each page. */
    @NonNull
    public static String buildIndexMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# InVxTermux Help\n\n");
        sb.append("Offline docs for this fork (Bun, Preview, `td-ai`, `~/repos`).\n\n");
        for (String id : PAGES) {
            sb.append("- [").append(titleFor(id)).append("](")
                .append(LINK_SCHEME).append(id).append(")\n");
        }
        sb.append("\nFilter the list with the search box above.\n");
        return sb.toString();
    }

    /**
     * True when {@code query} is empty or appears (case-insensitive) in
     * {@code title} or {@code body}.
     */
    public static boolean matchesFilter(@Nullable String title, @Nullable String body,
                                        @Nullable String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.US);
        if (title != null && title.toLowerCase(Locale.US).contains(q)) {
            return true;
        }
        return body != null && body.toLowerCase(Locale.US).contains(q);
    }

    /**
     * Filtered index markdown: only pages whose title or asset body match.
     * When {@code bodies} is null, title-only filter.
     */
    @NonNull
    public static String buildFilteredIndexMarkdown(@Nullable String query,
                                                    @Nullable Map<String, String> bodies) {
        if (query == null || query.trim().isEmpty()) {
            return buildIndexMarkdown();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# InVxTermux Help\n\n");
        sb.append("Matches for `").append(query.trim()).append("`:\n\n");
        boolean any = false;
        for (String id : PAGES) {
            String title = titleFor(id);
            String body = bodies != null ? bodies.get(id) : null;
            if (matchesFilter(title, body, query)) {
                sb.append("- [").append(title).append("](")
                    .append(LINK_SCHEME).append(id).append(")\n");
                any = true;
            }
        }
        if (!any) {
            sb.append("_No pages matched._\n");
        }
        return sb.toString();
    }
}
