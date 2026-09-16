package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorkflowHelperTest {

    @Test
    public void tdScaffoldCommand_quotesArgs() {
        assertEquals("td-scaffold 'my-app' 'react'\n",
            WorkflowHelper.tdScaffoldCommand("my-app", "react"));
        assertEquals("td-scaffold 'a'\\''b' 'vanilla'\n",
            WorkflowHelper.tdScaffoldCommand("a'b", "vanilla"));
    }

    @Test
    public void viteTemplates_includeVanillaReactAndPwa() {
        boolean vanilla = false;
        boolean react = false;
        boolean pwa = false;
        boolean pwaReact = false;
        for (String t : WorkflowHelper.VITE_TEMPLATES) {
            if ("vanilla".equals(t)) vanilla = true;
            if ("react".equals(t)) react = true;
            if ("pwa".equals(t)) pwa = true;
            if ("pwa-react".equals(t)) pwaReact = true;
        }
        assertTrue(vanilla);
        assertTrue(react);
        assertTrue(pwa);
        assertTrue(pwaReact);
        assertEquals(4096, WorkflowHelper.AI_PREVIEW_PORT);
        assertEquals(5173, WorkflowHelper.VITE_DEFAULT_PORT);
    }
}
