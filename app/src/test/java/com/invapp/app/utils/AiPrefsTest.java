package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pure-logic coverage for AI Doctor command builders (no Android Context).
 * Host/version formatting is exercised via package-visible shell quoting in
 * {@link AiPrefs} through the public command builders with a fake Context —
 * those need Robolectric. Here we only assert WorkflowHelper command constants
 * that the builders compose.
 */
public class AiPrefsTest {

    @Test
    public void workflowCommands_endWithNewline() {
        assertTrue(WorkflowHelper.CMD_TD_AI.endsWith("\n"));
        assertTrue(WorkflowHelper.CMD_OPENCODE_SETUP.endsWith("\n"));
        assertEquals("td-ai\n", WorkflowHelper.CMD_TD_AI);
        assertEquals("opencode-setup\n", WorkflowHelper.CMD_OPENCODE_SETUP);
    }

    @Test
    public void hostConstants_areExpected() {
        assertEquals("0.0.0.0", AiPrefs.HOST_WILDCARD);
        assertEquals("127.0.0.1", AiPrefs.HOST_LOOPBACK);
    }
}
