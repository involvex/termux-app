package com.invapp.app.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreviewPortPrefsTest {

    @Test
    public void parsePortsCsv_acceptsValidAndSkipsJunk() {
        List<Integer> ports = PreviewPortPrefs.parsePortsCsv("5173, 3000;abc 8080 0 70000");
        assertEquals(Arrays.asList(5173, 3000, 8080), ports);
        assertTrue(PreviewPortPrefs.parsePortsCsv(null).isEmpty());
        assertTrue(PreviewPortPrefs.parsePortsCsv("  ").isEmpty());
    }

    @Test
    public void portsToCsv_roundTrip() {
        List<Integer> ports = Arrays.asList(4096, 5173, 3000);
        assertEquals("4096,5173,3000", PreviewPortPrefs.portsToCsv(ports));
        assertEquals(ports, PreviewPortPrefs.parsePortsCsv(
            PreviewPortPrefs.portsToCsv(ports)));
    }

    @Test
    public void defaultPreferred_includesViteAndOpenCode() {
        List<Integer> defaults = PreviewPortPrefs.defaultPreferredList();
        assertTrue(defaults.contains(5173));
        assertTrue(defaults.contains(4000));
        assertTrue(defaults.contains(4096));
        assertEquals(PreviewPortPrefs.DEFAULT_PREFERRED.length, defaults.size());
    }

    @Test
    public void preferredFirstComparator_ordersPreferredAhead() {
        List<Integer> preferred = Arrays.asList(4096, 5173, 3000);
        List<Integer> ports = Arrays.asList(9000, 5173, 3000, 4096);
        Collections.sort(ports, LocalhostPortScanner.preferredFirstComparator(preferred));
        assertEquals(Arrays.asList(4096, 5173, 3000, 9000), ports);
    }

    @Test
    public void isPreferredPort_defaults() {
        assertTrue(LocalhostPortScanner.isPreferredPort(5173));
        assertTrue(LocalhostPortScanner.isPreferredPort(4096));
        assertFalse(LocalhostPortScanner.isPreferredPort(12345));
    }

    @Test
    public void shouldRecordConsole_gatesSink() {
        assertFalse(PreviewPortPrefs.shouldRecordConsole(false));
        assertTrue(PreviewPortPrefs.shouldRecordConsole(true));
    }

    @Test
    public void desktopUserAgent_isNonEmptyChrome() {
        assertTrue(PreviewPortPrefs.DESKTOP_USER_AGENT.contains("Chrome"));
        assertTrue(PreviewPortPrefs.DESKTOP_USER_AGENT.contains("Linux"));
        assertTrue(PreviewPortPrefs.MAX_CONSOLE_LINES > 0);
    }
}
