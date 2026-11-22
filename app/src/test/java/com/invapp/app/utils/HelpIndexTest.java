package com.invapp.app.utils;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HelpIndexTest {

    @Test
    public void resolvePageId_acceptsKnownPages() {
        assertEquals("install", HelpIndex.resolvePageId("install"));
        assertEquals("troubleshooting", HelpIndex.resolvePageId("Troubleshooting.md"));
        assertEquals("ai", HelpIndex.resolvePageId("invapp://help/ai"));
        assertEquals("cursor-agent", HelpIndex.resolvePageId("cursor-agent?x=1#top"));
        assertEquals(HelpIndex.PAGE_TROUBLESHOOTING,
            HelpIndex.resolvePageId("troubleshooting"));
    }

    @Test
    public void resolvePageId_rejectsTraversalAndUnknown() {
        assertNull(HelpIndex.resolvePageId(null));
        assertNull(HelpIndex.resolvePageId(""));
        assertNull(HelpIndex.resolvePageId("../etc/passwd"));
        assertNull(HelpIndex.resolvePageId("help/../install"));
        assertNull(HelpIndex.resolvePageId("install/../../secret"));
        assertNull(HelpIndex.resolvePageId("nethunter"));
        assertNull(HelpIndex.resolvePageId("docker"));
        assertNull(HelpIndex.resolvePageId("foo/bar"));
        assertNull(HelpIndex.resolvePageId("install" + (char) 0));
    }

    @Test
    public void isSafePageToken_basenameOnly() {
        assertTrue(HelpIndex.isSafePageToken("install"));
        assertTrue(HelpIndex.isSafePageToken("cursor-agent"));
        assertFalse(HelpIndex.isSafePageToken(".."));
        assertFalse(HelpIndex.isSafePageToken("a/b"));
        assertFalse(HelpIndex.isSafePageToken("a\\b"));
        assertFalse(HelpIndex.isSafePageToken("a:b"));
        assertFalse(HelpIndex.isSafePageToken("install" + (char) 0));
    }

    @Test
    public void assetPath_usesHelpDir() {
        assertEquals("help/install.md", HelpIndex.assetPath("install"));
        assertEquals("help/troubleshooting.md",
            HelpIndex.assetPath(HelpIndex.PAGE_TROUBLESHOOTING));
    }

    @Test
    public void buildIndexMarkdown_listsAllPages() {
        String md = HelpIndex.buildIndexMarkdown();
        assertTrue(md.contains("# InVxTermux Help"));
        for (String id : HelpIndex.PAGES) {
            assertTrue("missing link for " + id, md.contains("invapp://help/" + id));
            assertTrue("missing title for " + id, md.contains(HelpIndex.titleFor(id)));
        }
    }

    @Test
    public void matchesFilter_andFilteredIndex() {
        assertTrue(HelpIndex.matchesFilter("Install", "body", null));
        assertTrue(HelpIndex.matchesFilter("Install", "body", "  "));
        assertTrue(HelpIndex.matchesFilter("Install", "bun preview", "bun"));
        assertFalse(HelpIndex.matchesFilter("Install", "other", "opencode"));

        Map<String, String> bodies = new HashMap<>();
        bodies.put("ai", "OpenCode td-ai service on :4000");
        bodies.put("install", "GitHub Releases APK");
        String filtered = HelpIndex.buildFilteredIndexMarkdown("opencode", bodies);
        assertTrue(filtered.contains("invapp://help/ai"));
        assertFalse(filtered.contains("invapp://help/install"));

        String empty = HelpIndex.buildFilteredIndexMarkdown("zzzz-no-match",
            Collections.emptyMap());
        assertTrue(empty.contains("No pages matched"));
        assertNotNull(HelpIndex.titleFor(null));
    }
}
