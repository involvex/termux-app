package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsSearchHelperTest {

    @Test
    public void matches_emptyQueryMatchesAll() {
        assertTrue(SettingsSearchHelper.matches("Widget scripts", "summary", "widget_scripts", ""));
        assertTrue(SettingsSearchHelper.matches("Widget scripts", "summary", "widget_scripts", "   "));
        assertTrue(SettingsSearchHelper.matches(null, null, null, null));
    }

    @Test
    public void matches_isCaseInsensitiveAcrossTitleSummaryKey() {
        assertTrue(SettingsSearchHelper.matches("Widget scripts", "other", "widget_scripts", "widget"));
        assertTrue(SettingsSearchHelper.matches("Title", "Preferred Preview ports", "k", "preview"));
        assertTrue(SettingsSearchHelper.matches("Title", "Summary", "auto_start_console", "AUTO_START"));
        assertTrue(SettingsSearchHelper.matches("Start page", "Overview opens the drawer", "launch_page", "DRAWER"));
    }

    @Test
    public void matches_rejectsNonHitsAndHandlesNulls() {
        assertFalse(SettingsSearchHelper.matches("Widget scripts", "summary", "widget_scripts", "qemu"));
        assertFalse(SettingsSearchHelper.matches(null, null, null, "widget"));
        assertFalse(SettingsSearchHelper.matches(null, "summary", "key", "widget"));
    }
}
