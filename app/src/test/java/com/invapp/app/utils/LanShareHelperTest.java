package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LanShareHelperTest {

    @Test
    public void buildLanHttpUrl_formats() {
        assertEquals("http://192.168.1.10:3000/",
            LanShareHelper.buildLanHttpUrl("192.168.1.10", 3000));
        assertNull(LanShareHelper.buildLanHttpUrl(null, 3000));
        assertNull(LanShareHelper.buildLanHttpUrl("  ", 3000));
        assertNull(LanShareHelper.buildLanHttpUrl("192.168.1.10", 0));
    }

    @Test
    public void isPrivateLanIpv4_commonRanges() {
        assertTrue(LanShareHelper.isPrivateLanIpv4("192.168.0.1"));
        assertTrue(LanShareHelper.isPrivateLanIpv4("10.0.0.5"));
        assertTrue(LanShareHelper.isPrivateLanIpv4("172.16.1.1"));
        assertTrue(LanShareHelper.isPrivateLanIpv4("172.31.255.255"));
        assertFalse(LanShareHelper.isPrivateLanIpv4("172.15.0.1"));
        assertFalse(LanShareHelper.isPrivateLanIpv4("8.8.8.8"));
        assertFalse(LanShareHelper.isPrivateLanIpv4("127.0.0.1"));
    }

    @Test
    public void wildcardBind_ipv4AndIpv6() {
        assertTrue(LocalhostPortScanner.isWildcardBind("00000000:0BB8", false));
        assertFalse(LocalhostPortScanner.isWildcardBind("0100007F:0BB8", false));
        assertTrue(LocalhostPortScanner.isWildcardBind(
            "00000000000000000000000000000000:1F90", true));
        assertFalse(LocalhostPortScanner.isWildcardBind(
            "00000000000000000000000000000001:1F90", true));
    }
}
