package com.invapp.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScreenOcrTileServiceTest {

    @Test
    public void delays_haveExpectedDefaults() {
        assertEquals(10_000, ScreenOcrTileService.CAPTURE_DELAY_MS);
        assertEquals(3_000, ScreenOcrTileService.SCREENSHOT_DELAY_MS);
    }

    @Test
    public void captureScript_runsOcrHelper() {
        String script = ScreenOcrTileService.buildCaptureScript();
        assertTrue(script.contains("td-screen-ocr"));
    }
}
