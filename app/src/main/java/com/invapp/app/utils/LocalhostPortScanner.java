package com.invapp.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lists TCP listen ports visible via {@code /proc/net/tcp{,6}}.
 * Used by Localhost Preview for one-tap open of Vite / Expo / OpenCode servers
 * and to detect wildcard binds eligible for opt-in LAN URL copy.
 */
public final class LocalhostPortScanner {

    /** Common local-dev ports floated to the front of the chip list. */
    private static final int[] PREFERRED = {
        3000, 3001, 4173, 5000, 5173, 8000, 8080, 8081, 8888, 4096, 19000, 19006
    };

    /** TCP state LISTEN in /proc/net/tcp. */
    private static final int TCP_LISTEN = 0x0A;

    private LocalhostPortScanner() {}

    /**
     * @return sorted unique ports that appear to be listening (preferring
     *         loopback / wildcard binds that are reachable via 127.0.0.1)
     */
    @NonNull
    public static List<Integer> scanListeningPorts() {
        Set<Integer> ports = new LinkedHashSet<>();
        collectFromProc("/proc/net/tcp", ports, null, false);
        collectFromProc("/proc/net/tcp6", ports, null, true);
        List<Integer> list = new ArrayList<>(ports);
        Collections.sort(list, preferredFirstComparator());
        return list;
    }

    /**
     * Whether any LISTEN socket for {@code port} is bound to a wildcard address
     * ({@code 0.0.0.0} / {@code ::}), so LAN clients can reach it when the
     * process is otherwise reachable on Wi‑Fi.
     */
    public static boolean isWildcardListen(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        Set<Integer> wild = new LinkedHashSet<>();
        collectFromProc("/proc/net/tcp", null, wild, false);
        if (wild.contains(port)) {
            return true;
        }
        collectFromProc("/proc/net/tcp6", null, wild, true);
        return wild.contains(port);
    }

    private static Comparator<Integer> preferredFirstComparator() {
        return (a, b) -> {
            int ia = indexOfPreferred(a);
            int ib = indexOfPreferred(b);
            boolean pa = ia >= 0;
            boolean pb = ib >= 0;
            if (pa && pb) {
                return Integer.compare(ia, ib);
            }
            if (pa) {
                return -1;
            }
            if (pb) {
                return 1;
            }
            return Integer.compare(a, b);
        };
    }

    private static int indexOfPreferred(int port) {
        for (int i = 0; i < PREFERRED.length; i++) {
            if (PREFERRED[i] == port) {
                return i;
            }
        }
        return -1;
    }

    /** Whether this port is a known local-dev default (Vite/Expo/OpenCode/…). */
    public static boolean isPreferredPort(int port) {
        return indexOfPreferred(port) >= 0;
    }

    private static void collectFromProc(@NonNull String path,
                                        @Nullable Set<Integer> reachableOut,
                                        @Nullable Set<Integer> wildcardOut,
                                        boolean ipv6) {
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
                // sl local_address rem_address st ...
                String[] cols = line.split("\\s+");
                if (cols.length < 4) {
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
                int port = parseLocalPort(cols[1], ipv6);
                if (port < 1 || port > 65535) {
                    continue;
                }
                // Skip privileged / noise unless preferred (e.g. never show 22 from weird binds)
                if (port < 1024 && indexOfPreferred(port) < 0) {
                    continue;
                }
                boolean wildcard = isWildcardBind(cols[1], ipv6);
                if (wildcard && wildcardOut != null) {
                    wildcardOut.add(port);
                }
                if (reachableOut != null && isReachableViaLoopback(cols[1], ipv6)) {
                    reachableOut.add(port);
                }
            }
        } catch (Exception ignored) {
            // No /proc access or parse issues — Preview still works with manual port.
        }
    }

    /**
     * local_address is {@code IP:PORT} in hex. IPv4 IP is little-endian hex;
     * IPv6 is 32 hex chars. We only need the port (after the last ':').
     */
    private static int parseLocalPort(@NonNull String localAddress, boolean ipv6) {
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

    /** {@code 0.0.0.0} or {@code ::} — reachable from LAN if firewall allows. */
    static boolean isWildcardBind(@NonNull String localAddress, boolean ipv6) {
        int colon = localAddress.lastIndexOf(':');
        if (colon <= 0) {
            return false;
        }
        String ipHex = localAddress.substring(0, colon).toUpperCase(Locale.US);
        if (!ipv6) {
            return "00000000".equals(ipHex);
        }
        for (int i = 0; i < ipHex.length(); i++) {
            if (ipHex.charAt(i) != '0') {
                return false;
            }
        }
        return ipHex.length() > 0;
    }

    /**
     * Accept wildcard and loopback binds (reachable as http://127.0.0.1:port).
     */
    private static boolean isReachableViaLoopback(@NonNull String localAddress, boolean ipv6) {
        int colon = localAddress.lastIndexOf(':');
        if (colon <= 0) {
            return false;
        }
        String ipHex = localAddress.substring(0, colon).toUpperCase(Locale.US);
        if (!ipv6) {
            // 00000000 = 0.0.0.0, 0100007F = 127.0.0.1 (LE)
            return "00000000".equals(ipHex) || "0100007F".equals(ipHex);
        }
        if (isWildcardBind(localAddress, true)) {
            return true;
        }
        // ::1 is 0000...0001 (31 zeros + 1) in network order in /proc
        return ipHex.matches("0{31}1") || "00000000000000000000000000000001".equals(ipHex);
    }
}
