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
        boolean reactTs = false;
        boolean pwa = false;
        boolean pwaReact = false;
        for (String t : WorkflowHelper.VITE_TEMPLATES) {
            if ("vanilla".equals(t)) vanilla = true;
            if ("react".equals(t)) react = true;
            if ("react-ts".equals(t)) reactTs = true;
            if ("pwa".equals(t)) pwa = true;
            if ("pwa-react".equals(t)) pwaReact = true;
        }
        assertTrue(vanilla);
        assertTrue(react);
        assertTrue(reactTs);
        assertTrue(pwa);
        assertTrue(pwaReact);
        assertEquals("react-ts", WorkflowHelper.VITE_TEMPLATES[0]);
        assertEquals("react-ts", WorkflowHelper.DEFAULT_VITE_TEMPLATE);
        assertEquals(4096, WorkflowHelper.AI_PREVIEW_PORT);
        assertEquals(5173, WorkflowHelper.VITE_DEFAULT_PORT);
        assertEquals("/global/health", WorkflowHelper.AI_HEALTH_PATH);
        assertEquals("/api/info", WorkflowHelper.AI_INFO_PATH);
        assertEquals("http://127.0.0.1:4096/global/health",
            WorkflowHelper.aiHealthUrl(4096));
        assertEquals("http://127.0.0.1:4096/api/info",
            WorkflowHelper.aiInfoUrl(4096));
        assertEquals("http://127.0.0.1:4096", WorkflowHelper.aiBaseUrl(0));
    }

    @Test
    public void parsePreviewInput_acceptsPortHostPortAndFullUrl() {
        WorkflowHelper.ParsedPreviewUrl bare =
            WorkflowHelper.parsePreviewInput("4096");
        assertTrue(bare != null && bare.port == 4096 && bare.isLoopback());

        WorkflowHelper.ParsedPreviewUrl hostPort =
            WorkflowHelper.parsePreviewInput("127.0.0.1:5173");
        assertTrue(hostPort != null && hostPort.port == 5173 && hostPort.isLoopback());

        WorkflowHelper.ParsedPreviewUrl full =
            WorkflowHelper.parsePreviewInput("http://127.0.0.1:4096/chat?x=1");
        assertTrue(full != null && full.port == 4096 && full.isLoopback());
        assertEquals("/chat?x=1", full.pathAndQuery);

        WorkflowHelper.ParsedPreviewUrl lan =
            WorkflowHelper.parsePreviewInput("http://192.168.1.10:4096/");
        assertTrue(lan != null && lan.port == 4096 && !lan.isLoopback());
        assertEquals("192.168.1.10", lan.loadHost());
        assertEquals("http", lan.scheme);

        WorkflowHelper.ParsedPreviewUrl httpsRemote =
            WorkflowHelper.parsePreviewInput("https://example.com:8443/app");
        assertTrue(httpsRemote != null && httpsRemote.port == 8443
            && !httpsRemote.isLoopback());
        assertEquals("https", httpsRemote.scheme);
        assertEquals("example.com", httpsRemote.loadHost());
        assertEquals("/app", httpsRemote.pathAndQuery);

        assertTrue(WorkflowHelper.parsePreviewInput("ftp://127.0.0.1:21/") == null);
        assertTrue(WorkflowHelper.parsePreviewInput("") == null);
        assertTrue(WorkflowHelper.parsePreviewInput("0") == null);
        assertTrue(WorkflowHelper.parsePreviewInput("not a url") == null);
    }

    @Test
    public void isAllowedPreviewUrl_allowsHttpHttpsAndInertSchemes() {
        assertTrue(WorkflowHelper.isAllowedPreviewUrl("http://127.0.0.1:5173/"));
        assertTrue(WorkflowHelper.isAllowedPreviewUrl("https://example.com/"));
        assertTrue(WorkflowHelper.isAllowedPreviewUrl("http://192.168.1.10:4096/"));
        assertTrue(WorkflowHelper.isAllowedPreviewUrl("about:blank"));
        assertTrue(WorkflowHelper.isAllowedPreviewUrl("data:text/html,hi"));
        assertTrue(!WorkflowHelper.isAllowedPreviewUrl("file:///sdcard/x.html"));
        assertTrue(!WorkflowHelper.isAllowedPreviewUrl("javascript:alert(1)"));
        assertTrue(!WorkflowHelper.isAllowedPreviewUrl("content://com.example/x"));
        assertTrue(!WorkflowHelper.isAllowedPreviewUrl(null));
        assertTrue(!WorkflowHelper.isAllowedPreviewUrl(""));
    }
}
