package com.invapp.app.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.invapp.shared.termux.TermuxConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * One-tap backup / restore of repos manifest, {@code ~/.termux}, {@code ~/.shortcuts},
 * and workflow/extra-keys prefs into {@code ~/storage/shared/InVxTermux/}.
 *
 * <p>Secrets ({@code .npmrc}, {@code .git-credentials}, OpenCode auth) are omitted
 * unless {@code includeSecrets} is true. Does not zip {@code ~/repos} contents —
 * only remotes/branches for {@code td-clone} on restore.
 */
public final class BackupHelper {

    public static final String BACKUP_DIR_NAME = "InVxTermux";
    public static final String MANIFEST_ENTRY = "repos-manifest.json";
    public static final String PREFS_WORKFLOW_ENTRY = "prefs/workflow_bar.json";
    public static final String PREFS_EXTRA_KEYS_ENTRY = "prefs/extra_keys_bar.json";
    public static final String TERMUX_PREFIX = "home/.termux/";
    public static final String SHORTCUTS_PREFIX = "home/.shortcuts/";
    public static final String SECRETS_PREFIX = "home/";

    /** Relative to {@code $HOME}; skipped unless includeSecrets. */
    public static final String[] SECRET_RELATIVE_PATHS = {
        ".npmrc",
        ".git-credentials",
        ".config/opencode/service.json",
        ".config/opencode/auth.json",
    };

    private BackupHelper() {}

    /** One repo row in {@link #MANIFEST_ENTRY}. */
    public static final class RepoEntry {
        @NonNull
        public final String name;
        @Nullable
        public final String url;
        @Nullable
        public final String branch;

        public RepoEntry(@NonNull String name, @Nullable String url, @Nullable String branch) {
            this.name = name;
            this.url = url;
            this.branch = branch;
        }
    }

    public static final class ExportResult {
        public final boolean ok;
        @Nullable
        public final File zipFile;
        public final int repoCount;
        @Nullable
        public final String error;

        public ExportResult(boolean ok, @Nullable File zipFile, int repoCount,
                            @Nullable String error) {
            this.ok = ok;
            this.zipFile = zipFile;
            this.repoCount = repoCount;
            this.error = error;
        }
    }

    public static final class RestoreResult {
        public final boolean ok;
        public final int filesRestored;
        @NonNull
        public final List<RepoEntry> missingRepos;
        @Nullable
        public final String error;

        public RestoreResult(boolean ok, int filesRestored,
                             @NonNull List<RepoEntry> missingRepos,
                             @Nullable String error) {
            this.ok = ok;
            this.filesRestored = filesRestored;
            this.missingRepos = missingRepos;
            this.error = error;
        }
    }

    /** True if relative path (posix, under home) is a known secret. */
    public static boolean isSecretRelativePath(@Nullable String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        String norm = stripLeadingSlash(relativePath.replace('\\', '/'));
        for (String secret : SECRET_RELATIVE_PATHS) {
            if (norm.equals(secret)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a zip entry under {@code home/} should be written on export.
     * Secrets require {@code includeSecrets}; traversal is always rejected.
     */
    public static boolean shouldIncludeHomeRelative(@Nullable String relativeUnderHome,
                                                    boolean includeSecrets) {
        if (relativeUnderHome == null || relativeUnderHome.isEmpty()) {
            return false;
        }
        String norm = stripLeadingSlash(relativeUnderHome.replace('\\', '/'));
        if (norm.contains("..") || norm.contains("\0")) {
            return false;
        }
        if (isSecretRelativePath(norm)) {
            return includeSecrets;
        }
        return true;
    }

    @NonNull
    public static File backupDirectory() {
        return new File(TermuxConstants.TERMUX_STORAGE_HOME_DIR, "shared/" + BACKUP_DIR_NAME);
    }

    public static boolean isSharedStorageReady() {
        File shared = new File(TermuxConstants.TERMUX_STORAGE_HOME_DIR, "shared");
        return shared.exists();
    }

    @NonNull
    public static String backupFileName(@NonNull Date date) {
        return "backup-" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(date) + ".zip";
    }

    @NonNull
    public static File defaultBackupZipFile(@NonNull Date date) {
        return new File(backupDirectory(), backupFileName(date));
    }

    /** Newest {@code backup-*.zip} under the backup dir, or null. */
    @Nullable
    public static File findLatestBackupZip() {
        File dir = backupDirectory();
        File[] files = dir.listFiles((d, name) ->
            name != null && name.startsWith("backup-") && name.endsWith(".zip"));
        if (files == null || files.length == 0) {
            return null;
        }
        File latest = files[0];
        for (int i = 1; i < files.length; i++) {
            if (files[i].lastModified() > latest.lastModified()) {
                latest = files[i];
            }
        }
        return latest;
    }

    @NonNull
    public static List<RepoEntry> collectReposManifest() {
        List<RepoEntry> out = new ArrayList<>();
        for (File dir : WorkflowHelper.listRepos()) {
            WorkflowHelper.GitRepoStatus status = WorkflowHelper.readGitStatus(dir);
            String url = null;
            String branch = null;
            if (status.isGitRepo) {
                branch = status.branch;
                url = readGitRemoteOrigin(dir);
            }
            out.add(new RepoEntry(dir.getName(), url, branch));
        }
        return out;
    }

    @Nullable
    static String readGitRemoteOrigin(@NonNull File projectDir) {
        File gitBin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "git");
        if (!gitBin.isFile() || !new File(projectDir, ".git").exists()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                gitBin.getAbsolutePath(), "remote", "get-url", "origin");
            pb.directory(projectDir);
            // Renamed-fork apt binaries need $PREFIX/lib (libpcre2-8.so etc.).
            pb.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
            pb.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
            pb.environment().put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
            pb.environment().put("LD_LIBRARY_PATH",
                TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line.trim());
                }
            }
            p.waitFor();
            String url = out.toString().trim();
            return url.isEmpty() ? null : url;
        } catch (Exception e) {
            return null;
        }
    }

    @VisibleForTesting
    @NonNull
    public static String reposManifestToJson(@NonNull List<RepoEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (RepoEntry e : entries) {
            if (e == null || e.name == null || e.name.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{');
            sb.append("\"name\":");
            appendJsonString(sb, e.name);
            if (e.url != null && !e.url.isEmpty()) {
                sb.append(",\"url\":");
                appendJsonString(sb, e.url);
            }
            if (e.branch != null && !e.branch.isEmpty()) {
                sb.append(",\"branch\":");
                appendJsonString(sb, e.branch);
            }
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    @VisibleForTesting
    @NonNull
    public static List<RepoEntry> parseReposManifest(@Nullable String json) {
        if (json == null) {
            return Collections.emptyList();
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '[') {
            return Collections.emptyList();
        }
        List<RepoEntry> out = new ArrayList<>();
        int i = 1;
        int n = trimmed.length();
        while (i < n) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',') {
                i++;
                continue;
            }
            if (c == ']') {
                break;
            }
            if (c != '{') {
                return Collections.emptyList();
            }
            int end = findMatchingBrace(trimmed, i);
            if (end < 0) {
                return Collections.emptyList();
            }
            String obj = trimmed.substring(i, end + 1);
            String name = readJsonStringField(obj, "name");
            if (name != null && !name.isEmpty() && WorkflowHelper.isValidRepoName(name)) {
                out.add(new RepoEntry(
                    name,
                    emptyToNull(readJsonStringField(obj, "url")),
                    emptyToNull(readJsonStringField(obj, "branch"))));
            }
            i = end + 1;
        }
        return out;
    }

    /**
     * Export backup zip. Call off the UI thread.
     */
    @NonNull
    public static ExportResult exportBackup(@NonNull Context context, boolean includeSecrets) {
        if (!isSharedStorageReady()) {
            return new ExportResult(false, null, 0, "storage_missing");
        }
        File outDir = backupDirectory();
        if (!outDir.exists() && !outDir.mkdirs()) {
            return new ExportResult(false, null, 0, "mkdir_failed");
        }
        File zipFile = defaultBackupZipFile(new Date());
        List<RepoEntry> repos = collectReposManifest();
        try (ZipOutputStream zos = new ZipOutputStream(
            new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            putStringEntry(zos, MANIFEST_ENTRY, reposManifestToJson(repos));

            String workflowOrder = joinCsv(WorkflowBarHelper.getVisibleIds(context));
            putStringEntry(zos, PREFS_WORKFLOW_ENTRY,
                "{\"visible_order\":" + jsonQuote(workflowOrder) + "}");
            String keysOrder = joinCsv(ExtraKeysBarHelper.getPage2Ids(context));
            putStringEntry(zos, PREFS_EXTRA_KEYS_ENTRY,
                "{\"page2_order\":" + jsonQuote(keysOrder) + "}");

            addDirectoryToZip(zos, TermuxConstants.TERMUX_DATA_HOME_DIR, TERMUX_PREFIX,
                includeSecrets);
            addDirectoryToZip(zos, TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR, SHORTCUTS_PREFIX,
                includeSecrets);

            if (includeSecrets) {
                for (String rel : SECRET_RELATIVE_PATHS) {
                    File secret = new File(TermuxConstants.TERMUX_HOME_DIR, rel);
                    if (secret.isFile()) {
                        addFileToZip(zos, secret, SECRETS_PREFIX + rel);
                    }
                }
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
            return new ExportResult(false, null, 0,
                e.getMessage() != null ? e.getMessage() : "io_error");
        }
        return new ExportResult(true, zipFile, repos.size(), null);
    }

    /**
     * Restore from zip. Call off the UI thread. Prefs are applied when
     * {@code context} is non-null.
     */
    @NonNull
    public static RestoreResult restoreBackup(@NonNull Context context,
                                              @NonNull File zipFile,
                                              boolean includeSecrets) {
        if (!zipFile.isFile()) {
            return new RestoreResult(false, 0, Collections.emptyList(), "missing_zip");
        }
        int restored = 0;
        List<RepoEntry> manifest = Collections.emptyList();
        File home = TermuxConstants.TERMUX_HOME_DIR;
        try (ZipInputStream zis = new ZipInputStream(
            new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || entry.isDirectory()) {
                    continue;
                }
                name = stripLeadingSlash(name.replace('\\', '/'));
                if (name.contains("..") || name.contains("\0")) {
                    throw new IOException("unsafe entry: " + name);
                }

                if (MANIFEST_ENTRY.equals(name)) {
                    manifest = parseReposManifest(readFullyAsString(zis, buf));
                    continue;
                }
                if (PREFS_WORKFLOW_ENTRY.equals(name)) {
                    applyWorkflowPrefs(context, readFullyAsString(zis, buf));
                    restored++;
                    continue;
                }
                if (PREFS_EXTRA_KEYS_ENTRY.equals(name)) {
                    applyExtraKeysPrefs(context, readFullyAsString(zis, buf));
                    restored++;
                    continue;
                }
                if (name.startsWith(SECRETS_PREFIX)
                    && !name.startsWith(TERMUX_PREFIX)
                    && !name.startsWith(SHORTCUTS_PREFIX)) {
                    String rel = name.substring(SECRETS_PREFIX.length());
                    if (!shouldIncludeHomeRelative(rel, includeSecrets)) {
                        continue;
                    }
                    File dest = resolveUnder(home, rel);
                    writeStreamToFile(zis, dest, buf);
                    restored++;
                    continue;
                }
                if (name.startsWith(TERMUX_PREFIX)) {
                    String rel = name.substring(TERMUX_PREFIX.length());
                    if (!shouldIncludeHomeRelative(".termux/" + rel, includeSecrets)
                        && isSecretRelativePath(".termux/" + rel)) {
                        continue;
                    }
                    File dest = resolveUnder(TermuxConstants.TERMUX_DATA_HOME_DIR, rel);
                    writeStreamToFile(zis, dest, buf);
                    restored++;
                    continue;
                }
                if (name.startsWith(SHORTCUTS_PREFIX)) {
                    String rel = name.substring(SHORTCUTS_PREFIX.length());
                    File dest = resolveUnder(TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR, rel);
                    writeStreamToFile(zis, dest, buf);
                    restored++;
                }
            }
        } catch (IOException e) {
            return new RestoreResult(false, restored, Collections.emptyList(),
                e.getMessage() != null ? e.getMessage() : "io_error");
        }

        List<RepoEntry> missing = new ArrayList<>();
        for (RepoEntry repo : manifest) {
            if (repo.url == null || repo.url.isEmpty()) {
                continue;
            }
            File existing = new File(WorkflowHelper.reposDir(), repo.name);
            if (!existing.isDirectory()) {
                missing.add(repo);
            }
        }
        return new RestoreResult(true, restored, missing, null);
    }

    @VisibleForTesting
    @NonNull
    static File resolveUnder(@NonNull File baseDir, @NonNull String relative)
        throws IOException {
        String rel = stripLeadingSlash(relative.replace('\\', '/'));
        if (rel.contains("..") || rel.contains("\0")) {
            throw new IOException("path escapes: " + relative);
        }
        File target = new File(baseDir, rel);
        String baseCanon = baseDir.getCanonicalPath();
        String targetCanon = target.getCanonicalPath();
        if (!targetCanon.equals(baseCanon)
            && !targetCanon.startsWith(baseCanon + File.separator)) {
            throw new IOException("path escapes: " + relative);
        }
        return target;
    }

    private static void applyWorkflowPrefs(@NonNull Context context, @NonNull String json) {
        String order = readJsonStringField(json, "visible_order");
        if (order == null) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String part : order.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        WorkflowBarHelper.setVisibleIds(context, ids);
    }

    private static void applyExtraKeysPrefs(@NonNull Context context, @NonNull String json) {
        String order = readJsonStringField(json, "page2_order");
        if (order == null) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String part : order.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        ExtraKeysBarHelper.setPage2Ids(context, ids);
        ExtraKeysBarHelper.writeExtraKeysProperty(ids);
    }

    private static void addDirectoryToZip(@NonNull ZipOutputStream zos,
                                          @NonNull File dir,
                                          @NonNull String zipPrefix,
                                          boolean includeSecrets) throws IOException {
        if (!dir.isDirectory()) {
            return;
        }
        addDirectoryRecursive(zos, dir, dir, zipPrefix, includeSecrets);
    }

    private static void addDirectoryRecursive(@NonNull ZipOutputStream zos,
                                              @NonNull File root,
                                              @NonNull File current,
                                              @NonNull String zipPrefix,
                                              boolean includeSecrets) throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (isSymlink(child)) {
                continue;
            }
            String rel = relativePath(root, child);
            if (rel == null) {
                continue;
            }
            // Secrets live under $HOME, not under .termux/.shortcuts normally;
            // still gate by relative-under-home when prefix is home/.
            String homeRel = zipPrefix.startsWith("home/")
                ? zipPrefix.substring("home/".length()) + rel
                : rel;
            if (isSecretRelativePath(homeRel) && !includeSecrets) {
                continue;
            }
            if (child.isDirectory()) {
                addDirectoryRecursive(zos, root, child, zipPrefix, includeSecrets);
            } else if (child.isFile()) {
                addFileToZip(zos, child, zipPrefix + rel.replace('\\', '/'));
            }
        }
    }

    private static void addFileToZip(@NonNull ZipOutputStream zos,
                                     @NonNull File file,
                                     @NonNull String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName.replace('\\', '/'));
        zos.putNextEntry(entry);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                zos.write(buf, 0, n);
            }
        }
        zos.closeEntry();
    }

    private static void putStringEntry(@NonNull ZipOutputStream zos,
                                       @NonNull String name,
                                       @NonNull String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();
    }

    private static void writeStreamToFile(@NonNull InputStream in,
                                          @NonNull File dest,
                                          @NonNull byte[] buf) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdir failed: " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(dest)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        }
    }

    @NonNull
    private static String readFullyAsString(@NonNull InputStream in, @NonNull byte[] buf)
        throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Nullable
    private static String relativePath(@NonNull File root, @NonNull File child) {
        try {
            String rootPath = root.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            if (!childPath.startsWith(rootPath)) {
                return null;
            }
            if (childPath.equals(rootPath)) {
                return "";
            }
            String rel = childPath.substring(rootPath.length());
            if (rel.startsWith(File.separator)) {
                rel = rel.substring(1);
            }
            return rel.replace(File.separatorChar, '/');
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isSymlink(@NonNull File file) {
        try {
            File canon = file.getCanonicalFile();
            File abs = file.getAbsoluteFile();
            File parent = abs.getParentFile();
            if (parent == null) {
                return false;
            }
            File parentCanon = parent.getCanonicalFile();
            return !new File(parentCanon, abs.getName()).equals(canon);
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    private static String joinCsv(@NonNull List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    @NonNull
    private static String jsonQuote(@NonNull String value) {
        StringBuilder sb = new StringBuilder();
        appendJsonString(sb, value);
        return sb.toString();
    }

    private static void appendJsonString(@NonNull StringBuilder sb, @NonNull String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
    }

    private static int findMatchingBrace(@NonNull String s, int openIdx) {
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Nullable
    private static String readJsonStringField(@NonNull String obj, @NonNull String key) {
        String needle = "\"" + key + "\"";
        int keyIdx = obj.indexOf(needle);
        if (keyIdx < 0) {
            return null;
        }
        int colon = obj.indexOf(':', keyIdx + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) {
            i++;
        }
        if (i >= obj.length() || obj.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder value = new StringBuilder();
        boolean escape = false;
        while (i < obj.length()) {
            char c = obj.charAt(i);
            if (escape) {
                if (c == 'n') {
                    value.append('\n');
                } else if (c == 't') {
                    value.append('\t');
                } else if (c == 'r') {
                    value.append('\r');
                } else if (c == '"' || c == '\\' || c == '/') {
                    value.append(c);
                } else {
                    value.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
            i++;
        }
        return null;
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    @NonNull
    private static String stripLeadingSlash(@NonNull String path) {
        int i = 0;
        while (i < path.length() && path.charAt(i) == '/') {
            i++;
        }
        return i == 0 ? path : path.substring(i);
    }
}
