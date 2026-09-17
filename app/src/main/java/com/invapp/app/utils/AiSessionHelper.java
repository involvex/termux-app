package com.invapp.app.utils;

import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.invapp.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * OpenCode / {@code td-ai} session status for the drawer AI controls.
 *
 * <p>Detects install presence and whether the Preview port is already listening,
 * and can SIGTERM listeners on that port (same app UID as Termux sessions).
 * Health checks use loopback; {@code td-ai} may bind {@code 0.0.0.0} for LAN.
 */
public final class AiSessionHelper {

    public enum Status {
        /** {@code opencode} binary not found under PREFIX or {@code ~/.bun/bin}. */
        MISSING,
        /** Installed but nothing listening on the AI Preview port. */
        INSTALLED,
        /** Port is accepting connections — open Preview only. */
        READY
    }

    private static final int TCP_LISTEN = 0x0A;
    private static final int CONNECT_TIMEOUT_MS = 250;
    private static final int HEALTH_CONNECT_TIMEOUT_MS = 400;
    private static final int HEALTH_READ_TIMEOUT_MS = 800;
    /** Default wait when starting {@code td-ai} before opening Preview. */
    public static final int DEFAULT_HEALTH_WAIT_MS = 45_000;
    private static final Pattern ERROR_LINE = Pattern.compile(
        "(?i).*(error|errno|exception|failed|fatal|EACCES|EPERM|SIGSYS|not found).*");

    private AiSessionHelper() {}

    @NonNull
    public static Status probe() {
        return probe(WorkflowHelper.AI_PREVIEW_PORT);
    }

    @NonNull
    public static Status probe(int port) {
        // Prefer OpenAPI health (docs: GET /global/health) over bare TCP —
        // Preview must talk to a live OpenCode server, not an unrelated :4096.
        if (isOpenCodeHealthy(port)) {
            return Status.READY;
        }
        if (isOpenCodeInstalled()) {
            return Status.INSTALLED;
        }
        return Status.MISSING;
    }

    /**
     * {@code GET http://127.0.0.1:port/global/health} — true when body contains
     * {@code healthy} (OpenCode server API).
     */
    public static boolean isOpenCodeHealthy(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(WorkflowHelper.aiHealthUrl(port));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HEALTH_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(HEALTH_READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return false;
            }
            InputStream in = conn.getInputStream();
            if (in == null) {
                return false;
            }
            StringBuilder sb = new StringBuilder(128);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[256];
                int n;
                while ((n = reader.read(buf)) >= 0 && sb.length() < 512) {
                    sb.append(buf, 0, n);
                }
            }
            String body = sb.toString().toLowerCase(Locale.US);
            return body.contains("healthy");
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Poll {@link #isOpenCodeHealthy(int)} until true or {@code timeoutMs}.
     *
     * @return true if healthy within the timeout
     */
    public static boolean waitUntilHealthy(int port, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (isOpenCodeHealthy(port)) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return isOpenCodeHealthy(port);
            }
        }
        return isOpenCodeHealthy(port);
    }

    /**
     * True if something accepts TCP on loopback {@code port}. Prefers
     * {@code /proc/net/tcp*} scan; falls back to a short connect attempt
     * when {@code /proc} is unreadable.
     */
    public static boolean isPortListening(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        try {
            if (LocalhostPortScanner.scanListeningPorts().contains(port)) {
                return true;
            }
        } catch (Exception ignored) {
            // fall through to connect probe
        }
        return canConnectLoopback(port);
    }

    private static boolean canConnectLoopback(int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * True if an {@code opencode} executable exists where {@code td-ai} looks.
     * Ignores leftover bun JS stubs that print “postinstall script was not run”.
     */
    public static boolean isOpenCodeInstalled() {
        String bin = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        String[] candidates = {
            bin + "/opencode",
            home + "/.bun/bin/opencode",
            bin + "/opencode-ai",
            home + "/.bun/bin/opencode-ai"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (isUsableOpenCodeBinary(f)) {
                return true;
            }
        }
        File libexec = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "libexec/opencode");
        if (libexec.isDirectory()) {
            File[] kids = libexec.listFiles();
            if (kids != null) {
                for (File kid : kids) {
                    if (kid == null) {
                        continue;
                    }
                    if ("opencode".equals(kid.getName()) && isUsableOpenCodeBinary(kid)) {
                        return true;
                    }
                    if (kid.isDirectory()) {
                        File nested = new File(kid, "opencode");
                        if (isUsableOpenCodeBinary(nested)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Executable and not a leftover npm/bun JS stub.
     */
    @VisibleForTesting
    static boolean isUsableOpenCodeBinary(@Nullable File f) {
        if (f == null || !f.isFile() || !f.canExecute()) {
            return false;
        }
        return !isLikelyOpenCodeStub(f);
    }

    /**
     * Detects the broken {@code opencode-ai} JS placeholder left by
     * {@code bun install -g} (prints postinstall warning; not a real CLI).
     */
    @VisibleForTesting
    static boolean isLikelyOpenCodeStub(@NonNull File f) {
        try {
            byte[] head = new byte[512];
            int n;
            try (InputStream in = new java.io.FileInputStream(f)) {
                n = in.read(head);
            }
            if (n <= 0) {
                return true;
            }
            // ELF / Mach-O / PE — real binary
            if (n >= 4
                && ((head[0] == 0x7f && head[1] == 'E' && head[2] == 'L' && head[3] == 'F')
                || (head[0] == (byte) 0xCF && head[1] == (byte) 0xFA)
                || (head[0] == 'M' && head[1] == 'Z'))) {
                return false;
            }
            String text = new String(head, 0, n, StandardCharsets.UTF_8).toLowerCase(Locale.US);
            if (text.contains("postinstall") || text.contains("opencode-ai")) {
                return true;
            }
            // Shell/ld-linux wrappers from opencode-setup are fine.
            if (text.startsWith("#!") && (text.contains("ld-linux")
                || text.contains("libexec/opencode")
                || text.contains("exec "))) {
                return false;
            }
            // Bare node/bun JS without wrapper → stub.
            return text.startsWith("#!") && (text.contains("node") || text.contains("bun"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Stop AI web on {@code port}: SIGTERM LISTEN owners, then {@code pkill}
     * OpenCode web/serve as a fallback.
     *
     * @return &gt;0 if a stop action ran or the port is no longer accepting
     */
    public static int stopAi(int port) {
        boolean wasUp = isPortListening(port);
        int n = killListenersOnPort(port);
        if (isPortListening(port)) {
            n += pkillOpenCodeWeb();
        }
        if (!isPortListening(port) && wasUp) {
            return Math.max(n, 1);
        }
        if (!wasUp) {
            return 0;
        }
        return n;
    }

    /**
     * SIGTERM (then SIGKILL) processes that own a LISTEN socket on {@code port}.
     *
     * @return number of distinct PIDs signaled
     */
    public static int killListenersOnPort(int port) {
        if (port < 1 || port > 65535) {
            return 0;
        }
        Set<Long> inodes = new HashSet<>();
        collectListenInodes("/proc/net/tcp", port, inodes);
        collectListenInodes("/proc/net/tcp6", port, inodes);
        if (inodes.isEmpty()) {
            return 0;
        }
        Set<Integer> pids = findPidsForSocketInodes(inodes);
        int signaled = 0;
        for (Integer pid : pids) {
            if (pid == null || pid <= 1) {
                continue;
            }
            try {
                Os.kill(pid, OsConstants.SIGTERM);
                signaled++;
            } catch (Exception ignored) {
                // Permission or already gone.
            }
        }
        if (signaled > 0) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (Integer pid : pids) {
                if (pid == null || pid <= 1) {
                    continue;
                }
                if (!isPortListening(port)) {
                    break;
                }
                try {
                    Os.kill(pid, OsConstants.SIGKILL);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        return signaled;
    }

    /** Best-effort {@code pkill} of OpenCode web/serve under the Termux prefix. */
    static int pkillOpenCodeWeb() {
        File bash = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash");
        if (!bash.isFile()) {
            return 0;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                bash.getAbsolutePath(), "-c",
                "pkill -f '[o]pencode web' 2>/dev/null;"
                    + " pkill -f '[o]pencode serve' 2>/dev/null;"
                    + " true"
            );
            pb.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
            pb.environment().put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                p.destroy();
                return 0;
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Pull a short trailing error snippet from terminal transcript, or null.
     */
    @Nullable
    public static String extractLastErrorSnippet(@Nullable String transcript) {
        return extractLastErrorSnippet(transcript, 12, 800);
    }

    @VisibleForTesting
    @Nullable
    static String extractLastErrorSnippet(@Nullable String transcript, int maxLines, int maxChars) {
        if (transcript == null || transcript.isEmpty()) {
            return null;
        }
        String[] lines = transcript.split("\n", -1);
        int errAt = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (ERROR_LINE.matcher(line).matches()) {
                errAt = i;
                break;
            }
        }
        if (errAt < 0) {
            return null;
        }
        int start = Math.max(0, errAt - Math.min(3, maxLines - 1));
        int end = Math.min(lines.length, start + maxLines);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() && sb.length() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
            if (sb.length() >= maxChars) {
                break;
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        if (sb.length() > maxChars) {
            return sb.substring(0, maxChars - 1) + "…";
        }
        return sb.toString();
    }

    private static void collectListenInodes(@NonNull String path, int port,
                                            @NonNull Set<Long> out) {
        String portHex = String.format(Locale.US, "%04X", port);
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine(); // header
            if (line == null) {
                return;
            }
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] cols = line.split("\\s+");
                if (cols.length < 10) {
                    continue;
                }
                int state;
                try {
                    state = Integer.parseInt(cols[3], 16);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (state != TCP_LISTEN) {
                    continue;
                }
                int localPort = parseLocalPort(cols[1]);
                if (localPort != port) {
                    continue;
                }
                int colon = cols[1].lastIndexOf(':');
                if (colon < 0) {
                    continue;
                }
                String hex = cols[1].substring(colon + 1).toUpperCase(Locale.US);
                if (!portHex.equals(hex)) {
                    continue;
                }
                try {
                    out.add(Long.parseLong(cols[9]));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        } catch (Exception ignored) {
            // No /proc access
        }
    }

    private static int parseLocalPort(@NonNull String localAddress) {
        int colon = localAddress.lastIndexOf(':');
        if (colon < 0 || colon == localAddress.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(localAddress.substring(colon + 1), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @NonNull
    private static Set<Integer> findPidsForSocketInodes(@NonNull Set<Long> inodes) {
        Set<Integer> pids = new HashSet<>();
        File proc = new File("/proc");
        File[] entries = proc.listFiles();
        if (entries == null) {
            return pids;
        }
        for (File entry : entries) {
            String name = entry.getName();
            if (!isAllDigits(name)) {
                continue;
            }
            int pid;
            try {
                pid = Integer.parseInt(name);
            } catch (NumberFormatException e) {
                continue;
            }
            File fdDir = new File(entry, "fd");
            File[] fds = fdDir.listFiles();
            if (fds == null) {
                continue;
            }
            for (File fd : fds) {
                try {
                    String link = Os.readlink(fd.getAbsolutePath());
                    if (link == null || !link.startsWith("socket:[")) {
                        continue;
                    }
                    int end = link.indexOf(']');
                    if (end < 9) {
                        continue;
                    }
                    long inode = Long.parseLong(link.substring(8, end));
                    if (inodes.contains(inode)) {
                        pids.add(pid);
                        break;
                    }
                } catch (Exception ignored) {
                    // skip unreadable fd
                }
            }
        }
        return pids;
    }

    private static boolean isAllDigits(@NonNull String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
