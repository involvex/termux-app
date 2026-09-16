package com.invapp.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiSessionHelperTest {

    @Test
    public void extractLastErrorSnippet_findsTrailingError() {
        String transcript = ""
            + "$ bun run build\n"
            + "ok line\n"
            + "error: Cannot find module 'foo'\n"
            + "    at resolve\n";
        String snip = AiSessionHelper.extractLastErrorSnippet(transcript);
        assertTrue(snip != null && snip.contains("Cannot find module"));
        assertTrue(snip.contains("at resolve"));
    }

    @Test
    public void extractLastErrorSnippet_nullWhenClean() {
        assertNull(AiSessionHelper.extractLastErrorSnippet("hello\nworld\n"));
        assertNull(AiSessionHelper.extractLastErrorSnippet(null));
        assertNull(AiSessionHelper.extractLastErrorSnippet(""));
    }

    @Test
    public void extractLastErrorSnippet_respectsMaxChars() {
        StringBuilder longErr = new StringBuilder("error: ");
        for (int i = 0; i < 200; i++) {
            longErr.append('x');
        }
        String snip = AiSessionHelper.extractLastErrorSnippet(longErr.toString(), 4, 40);
        assertTrue(snip != null && snip.length() <= 40);
        assertTrue(snip.endsWith("…") || snip.length() < 40);
    }

    @Test
    public void statusEnum_hasExpectedValues() {
        assertEquals(3, AiSessionHelper.Status.values().length);
        assertEquals(AiSessionHelper.Status.MISSING, AiSessionHelper.Status.valueOf("MISSING"));
        assertEquals(AiSessionHelper.Status.INSTALLED, AiSessionHelper.Status.valueOf("INSTALLED"));
        assertEquals(AiSessionHelper.Status.READY, AiSessionHelper.Status.valueOf("READY"));
    }
}
