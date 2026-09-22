package com.invapp.app.utils;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

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
    public void extractLastErrorSnippet_matchesOpenCodePatterns() {
        assertTrue(AiSessionHelper.extractLastErrorSnippet(
            "AI_APICallError: rate limit\n") != null);
        assertTrue(AiSessionHelper.extractLastErrorSnippet(
            "Failed to fetch models.dev\n") != null);
        assertTrue(AiSessionHelper.extractLastErrorSnippet(
            "listen EADDRINUSE: address already in use :::5173\n") != null);
        assertTrue(AiSessionHelper.extractLastErrorSnippet(
            "getifaddrs returned an error\n") != null);
        assertTrue(AiSessionHelper.extractLastErrorSnippet(
            "version `LIBC' not found\n") != null);
    }

    @Test
    public void exportLastErrorMarkdown_writesFile() throws Exception {
        File repos = WorkflowHelper.reposDir();
        // May not exist on JVM unit test host — export creates .td under reposDir.
        String path = AiSessionHelper.exportLastErrorMarkdown(
            "error: boom\n", "/tmp", "unit-test");
        if (path == null) {
            // Host path may be unwritable; still exercise null path for empty
            assertTrue(AiSessionHelper.exportLastErrorMarkdown(null, null, null) == null);
            assertTrue(AiSessionHelper.exportLastErrorMarkdown("  ", null, null) == null);
            return;
        }
        File f = new File(path);
        assertTrue(f.isFile());
        StringBuilder body = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(
            new java.io.FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                body.append(line).append('\n');
            }
        }
        assertTrue(body.toString().contains("boom"));
        assertTrue(body.toString().contains("/tmp"));
        // cleanup
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        File parent = f.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.delete();
        }
    }

    @Test
    public void statusEnum_hasExpectedValues() {
        assertEquals(3, AiSessionHelper.Status.values().length);
        assertEquals(AiSessionHelper.Status.MISSING, AiSessionHelper.Status.valueOf("MISSING"));
        assertEquals(AiSessionHelper.Status.INSTALLED, AiSessionHelper.Status.valueOf("INSTALLED"));
        assertEquals(AiSessionHelper.Status.READY, AiSessionHelper.Status.valueOf("READY"));
    }

    @Test
    public void isOpenCodeHealthy_rejectsInvalidPort() {
        assertTrue(!AiSessionHelper.isOpenCodeHealthy(0));
        assertTrue(!AiSessionHelper.isOpenCodeHealthy(-1));
        assertTrue(!AiSessionHelper.isOpenCodeHealthy(70000));
    }

    @Test
    public void isLikelyOpenCodeStub_detectsPostinstallPlaceholder() throws Exception {
        File stub = File.createTempFile("opencode-stub", ".js");
        try {
            try (FileWriter w = new FileWriter(stub)) {
                w.write("#!/usr/bin/env node\n");
                w.write("console.log('postinstall script was not run');\n");
                w.write("// opencode-ai placeholder\n");
            }
            assertTrue(stub.setExecutable(true));
            assertTrue(AiSessionHelper.isLikelyOpenCodeStub(stub));
            assertTrue(!AiSessionHelper.isUsableOpenCodeBinary(stub));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            stub.delete();
        }
    }

    @Test
    public void isLikelyOpenCodeStub_allowsLdLinuxWrapper() throws Exception {
        File wrap = File.createTempFile("opencode-wrap", ".sh");
        try {
            try (FileWriter w = new FileWriter(wrap)) {
                w.write("#!/data/data/com.involvex.termux_app/files/usr/bin/bash\n");
                w.write("exec ld-linux --preload ... \"$PREFIX/libexec/opencode/opencode\" \"$@\"\n");
            }
            assertTrue(wrap.setExecutable(true));
            assertTrue(!AiSessionHelper.isLikelyOpenCodeStub(wrap));
            assertTrue(AiSessionHelper.isUsableOpenCodeBinary(wrap));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            wrap.delete();
        }
    }
}
