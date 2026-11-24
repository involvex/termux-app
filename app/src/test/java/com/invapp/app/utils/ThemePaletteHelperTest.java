package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemePaletteHelperTest {

    @Test
    public void assetForThemeMapsIds() {
        assertEquals("colors.properties", ThemePaletteHelper.assetForTheme(ThemePaletteHelper.ID_HACKER));
        assertEquals("colors-matcha.properties",
            ThemePaletteHelper.assetForTheme(ThemePaletteHelper.ID_MATCHA));
        assertEquals("colors-paper.properties",
            ThemePaletteHelper.assetForTheme(ThemePaletteHelper.ID_PAPER));
        assertEquals("colors-paper.properties",
            ThemePaletteHelper.assetForTheme(ThemePaletteHelper.ID_SYSTEM));
    }

    @Test
    public void nightModeForTheme() {
        assertEquals("true", ThemePaletteHelper.nightModeForTheme(ThemePaletteHelper.ID_HACKER));
        assertEquals("true", ThemePaletteHelper.nightModeForTheme(ThemePaletteHelper.ID_MATCHA));
        assertEquals("false", ThemePaletteHelper.nightModeForTheme(ThemePaletteHelper.ID_PAPER));
        assertEquals("system", ThemePaletteHelper.nightModeForTheme(ThemePaletteHelper.ID_SYSTEM));
    }

    @Test
    public void hasThemeMarkDetectsPrefix() {
        assertTrue(ThemePaletteHelper.hasThemeMark("# invapp-theme: hacker\nforeground=#fff\n"));
        assertFalse(ThemePaletteHelper.hasThemeMark("# custom palette\nforeground=#fff\n"));
        assertFalse(ThemePaletteHelper.hasThemeMark(null));
    }

    @Test
    public void knownThemeIds() {
        assertTrue(ThemePaletteHelper.isKnown("hacker"));
        assertTrue(ThemePaletteHelper.isKnown("matcha"));
        assertFalse(ThemePaletteHelper.isKnown("solarized"));
        assertFalse(ThemePaletteHelper.isKnown(null));
    }
}
