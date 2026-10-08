package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LaunchPrefsTest {

    @Test
    public void fromStored_mapsOrdinalsAndFallsBackToTerminal() {
        assertEquals(LaunchPrefs.LaunchPage.OVERVIEW, LaunchPrefs.LaunchPage.fromStored(0));
        assertEquals(LaunchPrefs.LaunchPage.TERMINAL, LaunchPrefs.LaunchPage.fromStored(1));
        assertEquals(LaunchPrefs.LaunchPage.TERMINAL, LaunchPrefs.LaunchPage.fromStored(-1));
        assertEquals(LaunchPrefs.LaunchPage.TERMINAL, LaunchPrefs.LaunchPage.fromStored(99));
    }

    @Test
    public void effectiveLaunchPage_autoStartLocksToOverview() {
        assertEquals(LaunchPrefs.LaunchPage.OVERVIEW,
            LaunchPrefs.effectiveLaunchPage(1, true));
        assertEquals(LaunchPrefs.LaunchPage.OVERVIEW,
            LaunchPrefs.effectiveLaunchPage(0, true));
        assertEquals(LaunchPrefs.LaunchPage.TERMINAL,
            LaunchPrefs.effectiveLaunchPage(1, false));
        assertEquals(LaunchPrefs.LaunchPage.OVERVIEW,
            LaunchPrefs.effectiveLaunchPage(0, false));
    }

    @Test
    public void shouldForceNewSession_onlyColdStartWithToggleAndSessions() {
        assertTrue(LaunchPrefs.shouldForceNewSession(false, true, true, false));
        assertFalse(LaunchPrefs.shouldForceNewSession(true, true, true, false));
        assertFalse(LaunchPrefs.shouldForceNewSession(false, false, true, false));
        assertFalse(LaunchPrefs.shouldForceNewSession(false, true, false, false));
        assertFalse(LaunchPrefs.shouldForceNewSession(false, true, true, true));
    }
}
