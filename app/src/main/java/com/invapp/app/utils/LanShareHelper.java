package com.invapp.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Opt-in LAN share helpers for Preview: site-local Wi‑Fi IPv4 and URL building.
 * Does not create tunnels — only copies {@code http://&lt;lan-ip&gt;:&lt;port&gt;}
 * when the user asks and the server is bound for LAN.
 */
public final class LanShareHelper {

    private LanShareHelper() {}

    /**
     * Best-effort IPv4 for sharing on the local network (prefers {@code wlan*} /
     * {@code eth*}, skips loopback and link-local).
     *
     * @return dotted IPv4 or {@code null} if none found
     */
    @Nullable
    public static String getSiteLocalIpv4() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) {
                return null;
            }
            for (NetworkInterface nif : Collections.list(ifaces)) {
                try {
                    if (!nif.isUp() || nif.isLoopback()) {
                        continue;
                    }
                } catch (Exception e) {
                    continue;
                }
                String name = nif.getName() != null
                    ? nif.getName().toLowerCase(Locale.US) : "";
                boolean preferred = name.startsWith("wlan")
                    || name.startsWith("wifi")
                    || name.startsWith("eth")
                    || name.startsWith("ap")
                    || name.startsWith("rmnet"); // some devices expose Wi‑Fi here

                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                        continue;
                    }
                    String host = addr.getHostAddress();
                    if (host == null || host.startsWith("169.254.")) {
                        continue;
                    }
                    if (preferred) {
                        return host;
                    }
                    if (fallback == null) {
                        fallback = host;
                    }
                }
            }
        } catch (Exception ignored) {
            // No interfaces — caller shows a toast.
        }
        return fallback;
    }

    /**
     * @return {@code http://ip:port/} or {@code null} if {@code ip} is null/blank
     */
    @Nullable
    public static String buildLanHttpUrl(@Nullable String ipv4, int port) {
        if (ipv4 == null || ipv4.trim().isEmpty()) {
            return null;
        }
        if (port < 1 || port > 65535) {
            return null;
        }
        return "http://" + ipv4.trim() + ":" + port + "/";
    }

    /** True when {@code host} looks like a private LAN IPv4 (not loopback). */
    public static boolean isPrivateLanIpv4(@NonNull String host) {
        String h = host.trim();
        if (h.startsWith("10.")) {
            return true;
        }
        if (h.startsWith("192.168.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] p = h.split("\\.");
            if (p.length >= 2) {
                try {
                    int second = Integer.parseInt(p[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }
}
