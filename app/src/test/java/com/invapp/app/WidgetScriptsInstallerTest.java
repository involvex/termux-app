package com.invapp.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WidgetScriptsInstallerTest {

    @Test
    public void catalog_coversDefaults() {
        String[] ids = WidgetScriptsInstaller.allCatalogIds();
        assertEquals(4, ids.length);
        assertEquals(WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK, ids[0]);
        assertEquals(WidgetScriptsInstaller.ID_CLIPBOARD_TO_FILE, ids[1]);
        assertEquals(WidgetScriptsInstaller.ID_GIT_PULL_REPOS, ids[2]);
        assertEquals(WidgetScriptsInstaller.ID_TD_AI, ids[3]);
        assertEquals(ids.length, WidgetScriptsInstaller.CATALOG_LABELS.length);
        assertEquals(3, WidgetScriptsInstaller.DEFAULT_FOREGROUND_IDS.length);
        assertEquals(1, WidgetScriptsInstaller.DEFAULT_TASK_IDS.length);
    }

    @Test
    public void scriptBodies_haveShebangGuardsAndMarkers() {
        for (String id : WidgetScriptsInstaller.allCatalogIds()) {
            String body = WidgetScriptsInstaller.scriptBody(id);
            assertNotNull(id, body);
            assertTrue(id, body.startsWith("#!"));
            assertTrue(id, body.contains("# invapp-widget:"));
            assertTrue(id, body.contains("export PATH="));
        }

        String speak = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK);
        assertNotNull(speak);
        assertTrue(speak.contains("termux-clipboard-get"));
        assertTrue(speak.contains("termux-tts-speak"));
        assertTrue(speak.contains("pkg install termux-api"));
        assertTrue(speak.contains("Clipboard empty"));

        String toFile = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_CLIPBOARD_TO_FILE);
        assertNotNull(toFile);
        assertTrue(toFile.contains("clipboard.txt"));
        assertTrue(toFile.contains("mkdir -p"));
        assertTrue(toFile.contains("termux-clipboard-get"));

        String pull = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_GIT_PULL_REPOS);
        assertNotNull(pull);
        assertTrue(pull.contains("git pull --ff-only"));
        assertTrue(pull.contains("/repos"));

        String tdAi = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_TD_AI);
        assertNotNull(tdAi);
        assertTrue(tdAi.contains("exec td-ai"));
        assertTrue(tdAi.contains("command -v td-ai"));
    }

    @Test
    public void filterCatalogOrder_respectsChecks() {
        boolean[] none = {false, false, false, false};
        assertTrue(WidgetScriptsInstaller.filterCatalogOrder(none).isEmpty());

        boolean[] speakAndAi = {true, false, false, true};
        List<String> ids = WidgetScriptsInstaller.filterCatalogOrder(speakAndAi);
        assertEquals(Arrays.asList(
            WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK,
            WidgetScriptsInstaller.ID_TD_AI), ids);

        boolean[] shortArr = {true};
        assertEquals(Arrays.asList(WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK),
            WidgetScriptsInstaller.filterCatalogOrder(shortArr));
    }

    @Test
    public void runOnceCommand_quotesKnownIds() {
        String cmd = WidgetScriptsInstaller.runOnceCommand(
            WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK);
        assertNotNull(cmd);
        assertTrue(cmd.startsWith("bash '"));
        assertTrue(cmd.endsWith("'\n"));
        assertTrue(cmd.contains(".shortcuts"));
        assertTrue(cmd.contains(WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK));

        String task = WidgetScriptsInstaller.runOnceCommand(
            WidgetScriptsInstaller.ID_TD_AI);
        assertNotNull(task);
        assertTrue(task.contains("tasks"));

        assertNull(WidgetScriptsInstaller.runOnceCommand("not-a-template"));
    }

    @Test
    public void scriptPath_taskVsForeground() {
        assertNotNull(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_GIT_PULL_REPOS));
        assertTrue(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_TD_AI).getPath().contains("tasks"));
        assertFalse(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK).getPath().replace('\\', '/')
            .contains("/tasks/"));
        assertNull(WidgetScriptsInstaller.scriptPath("nope"));
    }

    @Test
    public void catalog_hasFourIconSlots() {
        assertEquals(4, WidgetScriptsInstaller.allCatalogIds().length);
        for (String id : WidgetScriptsInstaller.allCatalogIds()) {
            assertTrue(id, id.length() > 0);
        }
    }
}
