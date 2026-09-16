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
    public void tdCloneCommand_quotesAndOptionalBun() {
        assertEquals("td-clone 'https://github.com/a/b.git' 'b'\n",
            WorkflowHelper.tdCloneCommand("https://github.com/a/b.git", "b", false));
        assertEquals("td-clone 'https://github.com/a/b.git' 'b' --bun-i\n",
            WorkflowHelper.tdCloneCommand("https://github.com/a/b.git", "b", true));
        assertEquals("td-clone 'x'\\''y' 'n' --bun-i\n",
            WorkflowHelper.tdCloneCommand("x'y", "n", true));
    }

    @Test
    public void suggestRepoNameFromUrl_stripsGitSuffix() {
        assertEquals("repo",
            WorkflowHelper.suggestRepoNameFromUrl("https://github.com/org/repo.git"));
        assertEquals("repo",
            WorkflowHelper.suggestRepoNameFromUrl("git@github.com:org/repo.git"));
        assertEquals("repo",
            WorkflowHelper.suggestRepoNameFromUrl("https://github.com/org/repo"));
        assertTrue(WorkflowHelper.isPlausibleGitUrl("https://github.com/a/b"));
        assertTrue(WorkflowHelper.isPlausibleGitUrl("git@github.com:a/b.git"));
        assertTrue(!WorkflowHelper.isPlausibleGitUrl("not a url"));
        assertTrue(WorkflowHelper.isValidRepoName("my-app"));
        assertTrue(!WorkflowHelper.isValidRepoName("../x"));
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
