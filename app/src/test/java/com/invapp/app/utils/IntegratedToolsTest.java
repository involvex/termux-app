package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntegratedToolsTest {

    @Test
    public void catalog_hasApiAndWidgetOnly() {
        IntegratedTools.Tool[] tools = IntegratedTools.allTools();
        assertEquals(2, tools.length);
        assertEquals(IntegratedTools.Tool.API, tools[0]);
        assertEquals(IntegratedTools.Tool.WIDGET, tools[1]);
        assertEquals("tool_termux_api", IntegratedTools.Tool.API.prefKey());
        assertEquals("tool_termux_widget", IntegratedTools.Tool.WIDGET.prefKey());
    }

    @Test
    public void packageNames_useSharedConstants() {
        assertTrue(IntegratedTools.Tool.API.packageName().endsWith(".api"));
        assertTrue(IntegratedTools.Tool.WIDGET.packageName().endsWith(".widget"));
        assertFalse(IntegratedTools.Tool.API.packageName().contains("com.termux.api"));
    }

    @Test
    public void statusFor_disabledWinsOverInstallState() {
        assertEquals(IntegratedTools.Status.DISABLED,
            IntegratedTools.statusFor(false, true, true));
        assertEquals(IntegratedTools.Status.DISABLED,
            IntegratedTools.statusFor(false, false, false));
    }

    @Test
    public void statusFor_mapsInstallStates() {
        assertEquals(IntegratedTools.Status.MISSING_APK,
            IntegratedTools.statusFor(true, false, true));
        assertEquals(IntegratedTools.Status.MISSING_CLI,
            IntegratedTools.statusFor(true, true, false));
        assertEquals(IntegratedTools.Status.READY,
            IntegratedTools.statusFor(true, true, true));
    }
}
