package com.invapp.app.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.invapp.BuildConfig;
import com.invapp.app.TermuxBunInstaller;
import com.invapp.shared.android.AndroidUtils;
import com.invapp.shared.termux.TermuxConstants;
import com.invapp.shared.termux.TermuxUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Support diagnostics zip beside Backup under {@code ~/storage/shared/InVxTermux/}.
 *
 * <p>Allowlisted entries only — no OpenCode auth, {@code .npmrc}, or
 * {@code .git-credentials}. Does not change Log Level.
 */
public final class DiagnosticsHelper {

    public static final String ENTRY_REPORT = "report.md";
    public static final String ENTRY_VERSIONS = "versions.txt";
    public static final String ENTRY_AI_STATUS = "ai-status.txt";

    private static final Set<String> ALLOWED_ENTRIES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(ENTRY_REPORT, ENTRY_VERSIONS, ENTRY_AI_STATUS)));

    /** Paths that must never appear as zip entry names (secrets / traversal). */
    private static final String[] FORBIDDEN_SUBSTRINGS = {
        ".npmrc",
        ".git-credentials",
        "opencode/auth",
        "opencode/service",
        "..",
        "\0",
    };

    private DiagnosticsHelper() {}

    public static final class ExportResult {
        public final boolean ok;
        @Nullable
        public final File zipFile;
        @Nullable
        public final String error;

        public ExportResult(boolean ok, @Nullable File zipFile, @Nullable String error) {
            this.ok = ok;
            this.zipFile = zipFile;
            this.error = error;
        }
    }

    /** Same directory as {@link BackupHelper#backupDirectory()}. */
    @NonNull
    public static File diagDirectory() {
        return BackupHelper.backupDirectory();
    }

    public static boolean isSharedStorageReady() {
        return BackupHelper.isSharedStorageReady();
    }

    @NonNull
    public static String diagFileName(@NonNull Date date) {
        return "diag-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(date) + ".zip";
    }

    @NonNull
    public static File defaultDiagZipFile(@NonNull Date date) {
        return new File(diagDirectory(), diagFileName(date));
    }

    /** True only for the fixed allowlist of entry basenames. */
    public static boolean isAllowedEntry(@Nullable String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return false;
        }
        String norm = entryName.replace('\\', '/');
        if (norm.contains("/") || norm.contains("..") || norm.contains("\0")) {
            return false;
        }
        return ALLOWED_ENTRIES.contains(norm);
    }

    /**
     * Reject secret paths and traversal in a candidate entry name.
     * Used by tests to prove secrets stay out of the zip.
     */
    public static boolean isForbiddenEntry(@Nullable String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return true;
        }
        String norm = entryName.replace('\\', '/').toLowerCase(Locale.US);
        for (String bad : FORBIDDEN_SUBSTRINGS) {
            if (norm.contains(bad.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return !isAllowedEntry(entryName);
    }

    @VisibleForTesting
    @NonNull
    public static Set<String> allowedEntries() {
        return ALLOWED_ENTRIES;
    }

    @NonNull
    public static ExportResult exportDiagnostics(@NonNull Context context) {
        if (!isSharedStorageReady()) {
            return new ExportResult(false, null, "storage_missing");
        }
        File dir = diagDirectory();
        if (!dir.exists() && !dir.mkdirs()) {
            return new ExportResult(false, null, "mkdir_failed");
        }
        Date now = new Date();
        File zipFile = defaultDiagZipFile(now);
        try {
            String report = buildReportMarkdown(context);
            String versions = buildVersionsText(context);
            String aiStatus = buildAiStatusText();
            writeZip(zipFile, report, versions, aiStatus);
            return new ExportResult(true, zipFile, null);
        } catch (Exception e) {
            if (zipFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                zipFile.delete();
            }
            return new ExportResult(false, null, e.getMessage());
        }
    }

    @VisibleForTesting
    static void writeZip(@NonNull File zipFile,
                         @NonNull String report,
                         @NonNull String versions,
                         @NonNull String aiStatus) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
            new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            putEntry(zos, ENTRY_REPORT, report);
            putEntry(zos, ENTRY_VERSIONS, versions);
            putEntry(zos, ENTRY_AI_STATUS, aiStatus);
        }
    }

    private static void putEntry(@NonNull ZipOutputStream zos, @NonNull String name,
                                 @NonNull String body) throws IOException {
        if (!isAllowedEntry(name) || isForbiddenEntry(name)) {
            throw new IOException("refused entry: " + name);
        }
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(body.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    @NonNull
    static String buildReportMarkdown(@NonNull Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# InVxTermux diagnostics\n\n");
        sb.append("Generated: ")
            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()))
            .append("\n\n");
        String appInfo = TermuxUtils.getAppInfoMarkdownString(context,
            TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES);
        if (appInfo != null) {
            sb.append(appInfo).append("\n\n");
        }
        sb.append(AndroidUtils.getDeviceInfoMarkdownString(context, true)).append("\n\n");
        String debug = TermuxUtils.getTermuxDebugMarkdownString(context);
        if (debug != null) {
            sb.append(debug).append("\n");
        }
        return sb.toString();
    }

    @NonNull
    static String buildVersionsText(@NonNull Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("app.versionName=").append(safePackageVersion(context)).append('\n');
        sb.append("app.versionCode=").append(safePackageVersionCode(context)).append('\n');
        sb.append("app.applicationId=").append(context.getPackageName()).append('\n');
        sb.append("build.abi=");
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            sb.append(Build.SUPPORTED_ABIS[0]);
        } else {
            sb.append("unknown");
        }
        sb.append('\n');
        sb.append("build.packageVariant=").append(BuildConfig.TERMUX_PACKAGE_VARIANT).append('\n');
        sb.append("bun.bundledExpected=")
            .append(TermuxBunInstaller.BUNDLED_BUN_VERSION).append('\n');
        BunVersionHelper.Report bun = BunVersionHelper.collect();
        sb.append("bun.stamped=").append(nullToDash(bun.stamped)).append('\n');
        sb.append("bun.libexecPresent=").append(bun.libexecPresent).append('\n');
        File bootstrapMarker = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "bin/bash");
        sb.append("bootstrap.prefixBashPresent=").append(bootstrapMarker.isFile()).append('\n');
        sb.append("android.sdk=").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("android.release=").append(Build.VERSION.RELEASE).append('\n');
        return sb.toString();
    }

    @NonNull
    static String buildAiStatusText() {
        StringBuilder sb = new StringBuilder();
        String binary = AiSessionHelper.resolveOpenCodeBinary();
        sb.append("opencode.binary=").append(nullToDash(binary)).append('\n');
        sb.append("opencode.installed=").append(AiSessionHelper.isOpenCodeInstalled()).append('\n');
        sb.append("opencode.shimPresent=")
            .append(AiSessionHelper.isOpenCodeShimPresent()).append('\n');
        sb.append("opencode.tlsCertPresent=")
            .append(AiSessionHelper.isTlsCertPresent()).append('\n');
        sb.append("opencode.stubPresent=")
            .append(AiSessionHelper.isOpenCodeStubPresent()).append('\n');
        int port = WorkflowHelper.AI_PREVIEW_PORT;
        sb.append("opencode.port=").append(port).append('\n');
        sb.append("opencode.portListening=")
            .append(AiSessionHelper.isPortListening(port)).append('\n');
        sb.append("opencode.healthy=")
            .append(AiSessionHelper.isOpenCodeHealthy(port)).append('\n');
        return sb.toString();
    }

    @NonNull
    private static String nullToDash(@Nullable String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    @NonNull
    private static String safePackageVersion(@NonNull Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "-";
        } catch (Exception e) {
            return "-";
        }
    }

    private static long safePackageVersionCode(@NonNull Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (Exception e) {
            return -1;
        }
    }
}
