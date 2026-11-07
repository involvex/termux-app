package com.invapp.app.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExtraKeysBarHelperTest {

    @Test
    public void buildExtraKeysJsonIsMultiPage() {
        String json = ExtraKeysBarHelper.buildExtraKeysJson(
            Arrays.asList(ExtraKeysBarHelper.ID_PULL, ExtraKeysBarHelper.ID_KEYBOARD));
        assertTrue(json.startsWith("[["));
        assertTrue(json.contains("PULL"));
        assertTrue(json.contains("KEYBOARD"));
    }

    @Test
    public void upsertPropertyReplacesExisting() {
        String text = "night-mode=true\nextra-keys=[[ESC]]\n";
        String out = ExtraKeysBarHelper.upsertProperty(text, "extra-keys", "[[['TAB']]]");
        assertTrue(out.contains("extra-keys = '[[['TAB']]]'"));
        assertFalse(out.contains("extra-keys=[[ESC]]"));
        assertTrue(out.contains("night-mode=true"));
    }

    @Test
    public void upsertPropertyAppendsMissing() {
        String out = ExtraKeysBarHelper.upsertProperty("night-mode=true\n", "extra-keys", "[]");
        assertTrue(out.contains("extra-keys = '[]'"));
    }

    @Test
    public void quoteAndUnquoteRoundTrip() {
        String json = ExtraKeysBarHelper.buildExtraKeysJson(
            Arrays.asList(ExtraKeysBarHelper.ID_PULL, ExtraKeysBarHelper.ID_AI));
        String quoted = ExtraKeysBarHelper.quotePropertyValue(json);
        assertTrue(quoted.startsWith("'"));
        assertTrue(quoted.endsWith("'"));
        assertEquals(json, ExtraKeysBarHelper.unquotePropertyValue(quoted));
        assertEquals(json, ExtraKeysBarHelper.unquotePropertyValue("\"" + json + "\""));
    }

    @Test
    public void upsertPropertyQuotesMultiPageJson() {
        String json = ExtraKeysBarHelper.buildExtraKeysJson(
            Arrays.asList(ExtraKeysBarHelper.ID_PULL));
        String out = ExtraKeysBarHelper.upsertProperty("", "extra-keys", json);
        assertTrue(out.contains("extra-keys = '"));
        assertTrue(out.contains("PULL"));
        String extracted = out.substring(out.indexOf("'") + 1, out.lastIndexOf("'"));
        assertEquals(json, extracted);
    }

    @Test
    public void filterToCatalogOrderRespectsFlags() {
        boolean[] checked = new boolean[ExtraKeysBarHelper.ALL_PAGE2_IDS.length];
        checked[0] = true;
        checked[3] = true;
        List<String> ids = ExtraKeysBarHelper.filterToCatalogOrder(checked);
        assertEquals(Arrays.asList(ExtraKeysBarHelper.ID_PULL, ExtraKeysBarHelper.ID_REPOS), ids);
    }

    @Test
    public void emptyPage2StillEmitsKeyboard() {
        String json = ExtraKeysBarHelper.buildExtraKeysJson(Collections.emptyList());
        assertTrue(json.contains("KEYBOARD"));
        assertTrue(json.contains("display: 'kbd'"));
    }

    @Test
    public void page0UsesTextDisplays() {
        String json = ExtraKeysBarHelper.buildExtraKeysJson(
            Arrays.asList(ExtraKeysBarHelper.ID_PULL));
        assertTrue(json.contains("display: 'HOME'"));
        assertTrue(json.contains("display: 'CTRL'"));
        assertTrue(json.contains("display: 'END'"));
    }

    @Test
    public void upsertRawPropertySetsStyleNone() {
        String text = "extra-keys-style = all\nnight-mode=true\n";
        String out = ExtraKeysBarHelper.upsertRawProperty(text,
            "extra-keys-style", ExtraKeysBarHelper.STYLE_TEXT);
        assertTrue(out.contains("extra-keys-style = none"));
        assertFalse(out.contains("extra-keys-style = all"));
    }

    @Test
    public void defaultPropertyConstantLooksMultiPage() {
        String def = com.invapp.shared.termux.settings.properties.TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;
        // Multi-page JSON starts with [[[ (page → row → key)
        assertTrue(def.startsWith("[[[") || def.contains("]],[["));
        assertTrue(def.contains("PULL"));
        assertTrue(def.contains("DRAWER_RIGHT"));
        assertTrue(def.contains("display: 'HOME'"));
        assertEquals("none",
            com.invapp.shared.termux.settings.properties.TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE);
    }
}
