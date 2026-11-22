package com.invapp.app.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BackupHelperTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void isSecretRelativePath_matchesKnownSecrets() {
        assertTrue(BackupHelper.isSecretRelativePath(".npmrc"));
        assertTrue(BackupHelper.isSecretRelativePath(".git-credentials"));
        assertTrue(BackupHelper.isSecretRelativePath(".config/opencode/service.json"));
        assertTrue(BackupHelper.isSecretRelativePath(".config/opencode/auth.json"));
        assertFalse(BackupHelper.isSecretRelativePath(".termux/termux.properties"));
        assertFalse(BackupHelper.isSecretRelativePath(".shortcuts/td-ai"));
        assertFalse(BackupHelper.isSecretRelativePath(null));
    }

    @Test
    public void shouldIncludeHomeRelative_gatesSecretsAndTraversal() {
        assertTrue(BackupHelper.shouldIncludeHomeRelative(".termux/colors.properties", false));
        assertFalse(BackupHelper.shouldIncludeHomeRelative(".npmrc", false));
        assertTrue(BackupHelper.shouldIncludeHomeRelative(".npmrc", true));
        assertFalse(BackupHelper.shouldIncludeHomeRelative("../evil", false));
        assertFalse(BackupHelper.shouldIncludeHomeRelative("foo/../../etc/passwd", true));
        assertFalse(BackupHelper.shouldIncludeHomeRelative("", false));
    }

    @Test
    public void reposManifest_roundTrip() {
        List<BackupHelper.RepoEntry> entries = Arrays.asList(
            new BackupHelper.RepoEntry("app", "https://example.com/app.git", "main"),
            new BackupHelper.RepoEntry("lib", null, "dev")
        );
        String json = BackupHelper.reposManifestToJson(entries);
        List<BackupHelper.RepoEntry> parsed = BackupHelper.parseReposManifest(json);
        assertEquals(2, parsed.size());
        assertEquals("app", parsed.get(0).name);
        assertEquals("https://example.com/app.git", parsed.get(0).url);
        assertEquals("main", parsed.get(0).branch);
        assertEquals("lib", parsed.get(1).name);
        assertNull(parsed.get(1).url);
        assertEquals("dev", parsed.get(1).branch);
    }

    @Test
    public void parseReposManifest_rejectsBadNamesAndCorrupt() {
        assertTrue(BackupHelper.parseReposManifest(null).isEmpty());
        assertTrue(BackupHelper.parseReposManifest("not-json").isEmpty());
        assertTrue(BackupHelper.parseReposManifest(
            "[{\"name\":\"bad name\",\"url\":\"https://x\"}]").isEmpty());
        assertEquals(Collections.emptyList(),
            BackupHelper.parseReposManifest("[]"));
    }

    @Test
    public void backupFileName_usesYyyyMmDd() {
        // Fixed date: 2026-11-22 (Date year = year-1900, month 0-based)
        @SuppressWarnings("deprecation")
        Date d = new Date(126, 10, 22);
        assertEquals("backup-20261122.zip", BackupHelper.backupFileName(d));
        assertTrue(BackupHelper.backupFileName(new Date()).matches("backup-\\d{8}\\.zip"));
    }

    @Test
    public void resolveUnder_rejectsTraversal() throws IOException {
        File base = tempFolder.newFolder("home");
        File ok = BackupHelper.resolveUnder(base, "repos/app");
        assertTrue(ok.getCanonicalPath().startsWith(base.getCanonicalPath()));
        try {
            BackupHelper.resolveUnder(base, "../evil");
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("escapes"));
        }
    }
}
