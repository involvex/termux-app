package com.invapp.app.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DiagnosticsHelperTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void isAllowedEntry_allowlistOnly() {
        assertTrue(DiagnosticsHelper.isAllowedEntry(DiagnosticsHelper.ENTRY_REPORT));
        assertTrue(DiagnosticsHelper.isAllowedEntry(DiagnosticsHelper.ENTRY_VERSIONS));
        assertTrue(DiagnosticsHelper.isAllowedEntry(DiagnosticsHelper.ENTRY_AI_STATUS));
        assertFalse(DiagnosticsHelper.isAllowedEntry(null));
        assertFalse(DiagnosticsHelper.isAllowedEntry(""));
        assertFalse(DiagnosticsHelper.isAllowedEntry("report.md/../.npmrc"));
        assertFalse(DiagnosticsHelper.isAllowedEntry("home/.npmrc"));
        assertFalse(DiagnosticsHelper.isAllowedEntry(".git-credentials"));
        assertFalse(DiagnosticsHelper.isAllowedEntry("secrets.txt"));
    }

    @Test
    public void isForbiddenEntry_blocksSecretsAndNonAllowlist() {
        assertTrue(DiagnosticsHelper.isForbiddenEntry(".npmrc"));
        assertTrue(DiagnosticsHelper.isForbiddenEntry("home/.git-credentials"));
        assertTrue(DiagnosticsHelper.isForbiddenEntry(".config/opencode/auth.json"));
        assertTrue(DiagnosticsHelper.isForbiddenEntry("foo/../bar"));
        assertTrue(DiagnosticsHelper.isForbiddenEntry("extra.txt"));
        assertFalse(DiagnosticsHelper.isForbiddenEntry(DiagnosticsHelper.ENTRY_REPORT));
        assertFalse(DiagnosticsHelper.isForbiddenEntry(DiagnosticsHelper.ENTRY_VERSIONS));
        assertFalse(DiagnosticsHelper.isForbiddenEntry(DiagnosticsHelper.ENTRY_AI_STATUS));
    }

    @Test
    public void diagFileName_usesYyyyMmDdHHmm() {
        @SuppressWarnings("deprecation")
        Date d = new Date(126, 10, 22, 15, 4); // 2026-11-22 15:04
        assertEquals("diag-20261122-1504.zip", DiagnosticsHelper.diagFileName(d));
        assertTrue(DiagnosticsHelper.diagFileName(new Date())
            .matches("diag-\\d{8}-\\d{4}\\.zip"));
    }

    @Test
    public void writeZip_onlyAllowlistedEntries() throws Exception {
        File zip = tempFolder.newFile("diag-test.zip");
        DiagnosticsHelper.writeZip(zip,
            "# report\n",
            "app.versionName=0.210.0\n",
            "opencode.installed=false\n");

        Set<String> names = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                assertTrue("unexpected entry " + entry.getName(),
                    DiagnosticsHelper.isAllowedEntry(entry.getName()));
                assertFalse(DiagnosticsHelper.isForbiddenEntry(entry.getName()));
            }
        }
        assertEquals(DiagnosticsHelper.allowedEntries(), names);
        assertFalse(names.contains(".npmrc"));
        assertFalse(names.contains(".git-credentials"));
        assertFalse(names.contains(".config/opencode/auth.json"));
    }

    @Test
    public void writeZip_refusesForbiddenName_viaAllowlistGate() {
        // putEntry is private; isAllowedEntry/isForbiddenEntry are the public gates
        // used before any zip write.
        assertTrue(DiagnosticsHelper.isForbiddenEntry("home/.npmrc"));
        assertFalse(DiagnosticsHelper.isAllowedEntry("home/.npmrc"));
        try {
            // Sanity: allowlist size stays three.
            assertEquals(3, DiagnosticsHelper.allowedEntries().size());
        } catch (AssertionError e) {
            fail(e.getMessage());
        }
    }
}
