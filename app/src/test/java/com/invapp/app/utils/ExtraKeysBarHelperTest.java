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
        assertTrue(out.contains("extra-keys=[[['TAB']]]"));
        assertFalse(out.contains("extra-keys=[[ESC]]"));
        assertTrue(out.contains("night-mode=true"));
    }

    @Test
    public void upsertPropertyAppendsMissing() {
        String out = ExtraKeysBarHelper.upsertProperty("night-mode=true\n", "extra-keys", "[]");
        assertTrue(out.contains("extra-keys=[]"));
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
    }

    @Test
    public void defaultPropertyConstantLooksMultiPage() {
        String def = com.invapp.shared.termux.settings.properties.TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;
        // Multi-page JSON starts with [[[ (page → row → key)
        assertTrue(def.startsWith("[[['") || def.startsWith("[[[\"") || def.contains("]],[["));
        assertTrue(def.contains("PULL"));
        assertTrue(def.contains("DRAWER_RIGHT"));
    }
}
