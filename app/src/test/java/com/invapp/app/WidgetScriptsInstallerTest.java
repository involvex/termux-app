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

    private static final int CATALOG_SIZE = 16;

    @Test
    public void catalog_coversDefaultsAndOptionals() {
        String[] ids = WidgetScriptsInstaller.allCatalogIds();
        assertEquals(CATALOG_SIZE, ids.length);
        assertEquals(WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK, ids[0]);
        assertEquals(WidgetScriptsInstaller.ID_SCREEN_OCR, ids[3]);
        assertEquals(WidgetScriptsInstaller.ID_CAMERA_PHOTO, ids[4]);
        assertEquals(WidgetScriptsInstaller.ID_OPEN_SETTINGS, ids[9]);
        assertEquals(WidgetScriptsInstaller.ID_VIBRATE, ids[10]);
        assertEquals(WidgetScriptsInstaller.ID_STOP_AI, ids[14]);
        assertEquals(WidgetScriptsInstaller.ID_TD_AI, ids[15]);
        assertEquals(ids.length, WidgetScriptsInstaller.CATALOG_LABELS.length);
        assertEquals(4, WidgetScriptsInstaller.DEFAULT_FOREGROUND_IDS.length);
        assertEquals(11, WidgetScriptsInstaller.OPTIONAL_FOREGROUND_IDS.length);
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
        assertTrue(speak.contains("termux-toast"));
        assertTrue(speak.contains("Speaking clipboard"));

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
        assertTrue(pull.contains("git pull ~/repos"));

        String ocr = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_SCREEN_OCR);
        assertNotNull(ocr);
        assertTrue(ocr.contains("td-screen-ocr"));
        assertTrue(ocr.contains("Screen OCR"));
        assertTrue(ocr.contains("command -v td-screen-ocr"));

        String cam = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_CAMERA_PHOTO);
        assertNotNull(cam);
        assertTrue(cam.contains("termux-camera-photo"));
        assertTrue(cam.contains("camera-last.jpg"));

        String wifi = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_WIFI_INFO);
        assertNotNull(wifi);
        assertTrue(wifi.contains("termux-wifi-connectioninfo"));
        assertTrue(wifi.contains("termux-clipboard-set"));

        String batt = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_BATTERY_STATUS);
        assertNotNull(batt);
        assertTrue(batt.contains("termux-battery-status"));

        String torch = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_TORCH_TOGGLE);
        assertNotNull(torch);
        assertTrue(torch.contains("termux-torch"));
        assertTrue(torch.contains("invapp-torch.on"));

        String share = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_SHARE_CLIPBOARD);
        assertNotNull(share);
        assertTrue(share.contains("termux-share"));

        String settings = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_OPEN_SETTINGS);
        assertNotNull(settings);
        assertTrue(settings.contains("android.settings.SETTINGS"));

        String vibrate = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_VIBRATE);
        assertNotNull(vibrate);
        assertTrue(vibrate.contains("termux-vibrate"));

        String volume = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_VOLUME_INFO);
        assertNotNull(volume);
        assertTrue(volume.contains("termux-volume"));

        String location = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_LOCATION);
        assertNotNull(location);
        assertTrue(location.contains("termux-location"));

        String telephony = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_TELEPHONY_INFO);
        assertNotNull(telephony);
        assertTrue(telephony.contains("termux-telephony-deviceinfo"));

        String stopAi = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_STOP_AI);
        assertNotNull(stopAi);
        assertTrue(stopAi.contains("pkill -f '[o]pencode web'"));
        assertTrue(stopAi.contains("OpenCode stopped"));

        String tdAi = WidgetScriptsInstaller.scriptBody(
            WidgetScriptsInstaller.ID_TD_AI);
        assertNotNull(tdAi);
        assertTrue(tdAi.contains("td-ai &"));
        assertTrue(tdAi.contains("command -v td-ai"));
        assertTrue(tdAi.contains("OpenCode ready"));
        assertTrue(tdAi.contains("already ready"));
        assertTrue(tdAi.contains("global/health"));
    }

    @Test
    public void filterCatalogOrder_respectsChecks() {
        boolean[] none = new boolean[CATALOG_SIZE];
        assertTrue(WidgetScriptsInstaller.filterCatalogOrder(none).isEmpty());

        boolean[] speakAndAi = new boolean[CATALOG_SIZE];
        speakAndAi[0] = true;
        speakAndAi[15] = true;
        List<String> ids = WidgetScriptsInstaller.filterCatalogOrder(speakAndAi);
        assertEquals(Arrays.asList(
            WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK,
            WidgetScriptsInstaller.ID_TD_AI), ids);

        boolean[] camOnly = new boolean[CATALOG_SIZE];
        camOnly[4] = true;
        assertEquals(Arrays.asList(WidgetScriptsInstaller.ID_CAMERA_PHOTO),
            WidgetScriptsInstaller.filterCatalogOrder(camOnly));

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

        String ocr = WidgetScriptsInstaller.runOnceCommand(
            WidgetScriptsInstaller.ID_SCREEN_OCR);
        assertNotNull(ocr);
        assertTrue(ocr.contains(WidgetScriptsInstaller.ID_SCREEN_OCR));
        assertFalse(ocr.contains("tasks"));

        assertNotNull(WidgetScriptsInstaller.runOnceCommand(
            WidgetScriptsInstaller.ID_WIFI_INFO));
        assertNotNull(WidgetScriptsInstaller.runOnceCommand(
            WidgetScriptsInstaller.ID_STOP_AI));

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
        assertNotNull(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_SCREEN_OCR));
        assertNotNull(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_TORCH_TOGGLE));
        assertNotNull(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_STOP_AI));
        assertTrue(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_TD_AI).getPath().contains("tasks"));
        assertFalse(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_CLIPBOARD_SPEAK).getPath().replace('\\', '/')
            .contains("/tasks/"));
        assertFalse(WidgetScriptsInstaller.scriptPath(
            WidgetScriptsInstaller.ID_SCREEN_OCR).getPath().replace('\\', '/')
            .contains("/tasks/"));
        assertNull(WidgetScriptsInstaller.scriptPath("nope"));
    }

    @Test
    public void catalog_hasIconSlotsForAllIds() {
        assertEquals(CATALOG_SIZE, WidgetScriptsInstaller.allCatalogIds().length);
        for (String id : WidgetScriptsInstaller.allCatalogIds()) {
            assertTrue(id, id.length() > 0);
        }
    }
}
