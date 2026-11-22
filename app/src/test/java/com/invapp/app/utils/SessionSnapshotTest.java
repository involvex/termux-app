package com.invapp.app.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionSnapshotTest {

    private static final String HOME = "/data/data/com.involvex.termux_app/files/home";

    @Test
    public void shouldOfferRestore_onlyColdStartEmptyWithSnapshot() {
        assertTrue(SessionSnapshot.shouldOfferRestore(
            false, false, true, false, false, false));
        assertFalse(SessionSnapshot.shouldOfferRestore(
            true, false, true, false, false, false));
        assertFalse(SessionSnapshot.shouldOfferRestore(
            false, true, true, false, false, false));
        assertFalse(SessionSnapshot.shouldOfferRestore(
            false, false, false, false, false, false));
        assertFalse(SessionSnapshot.shouldOfferRestore(
            false, false, true, true, false, false));
        assertFalse(SessionSnapshot.shouldOfferRestore(
            false, false, true, false, true, false));
        assertFalse(SessionSnapshot.shouldOfferRestore(
            false, false, true, false, false, true));
    }

    @Test
    public void sanitizeCwd_acceptsHomeAndRepos() {
        assertEquals(HOME, SessionSnapshot.sanitizeCwd(HOME, HOME));
        assertEquals(HOME, SessionSnapshot.sanitizeCwd(HOME + "/", HOME));
        assertEquals(HOME + "/repos/app",
            SessionSnapshot.sanitizeCwd(HOME + "/repos/app", HOME));
        assertEquals(HOME + "/repos/app",
            SessionSnapshot.sanitizeCwd(HOME + "/repos/app/", HOME));
    }

    @Test
    public void sanitizeCwd_rejectsEmptyOutsideAndTraversal() {
        assertNull(SessionSnapshot.sanitizeCwd(null, HOME));
        assertNull(SessionSnapshot.sanitizeCwd("", HOME));
        assertNull(SessionSnapshot.sanitizeCwd("   ", HOME));
        assertNull(SessionSnapshot.sanitizeCwd("/tmp", HOME));
        assertNull(SessionSnapshot.sanitizeCwd(HOME + "/../usr", HOME));
        assertNull(SessionSnapshot.sanitizeCwd(HOME + "/repos/../..", HOME));
        assertNull(SessionSnapshot.sanitizeCwd("/data/data/other/files/home", HOME));
    }

    @Test
    public void sanitizeEntries_dedupesCapsAndDropsBad() {
        List<SessionSnapshot.Entry> raw = Arrays.asList(
            new SessionSnapshot.Entry(HOME + "/repos/a", "A"),
            new SessionSnapshot.Entry("/tmp/x", "bad"),
            new SessionSnapshot.Entry(HOME + "/repos/a", "dup"),
            new SessionSnapshot.Entry(HOME + "/repos/b", "  "),
            new SessionSnapshot.Entry(HOME + "/repos/c", "C"),
            new SessionSnapshot.Entry(HOME + "/repos/d", "D")
        );
        List<SessionSnapshot.Entry> clean =
            SessionSnapshot.sanitizeEntries(raw, HOME, 3);
        assertEquals(3, clean.size());
        assertEquals(HOME + "/repos/a", clean.get(0).cwd);
        assertEquals("A", clean.get(0).title);
        assertEquals(HOME + "/repos/b", clean.get(1).cwd);
        assertNull(clean.get(1).title);
        assertEquals(HOME + "/repos/c", clean.get(2).cwd);
    }

    @Test
    public void sanitizeEntries_emptyInput() {
        assertTrue(SessionSnapshot.sanitizeEntries(null, HOME, 8).isEmpty());
        assertTrue(SessionSnapshot.sanitizeEntries(
            Collections.emptyList(), HOME, 8).isEmpty());
        assertTrue(SessionSnapshot.sanitizeEntries(
            Collections.singletonList(new SessionSnapshot.Entry(HOME, "x")),
            HOME, 0).isEmpty());
    }

    @Test
    public void json_roundTripPreservesFields() {
        List<SessionSnapshot.Entry> entries = Arrays.asList(
            new SessionSnapshot.Entry(HOME + "/repos/a", "title"),
            new SessionSnapshot.Entry(HOME, null)
        );
        String json = SessionSnapshot.toJson(entries);
        List<SessionSnapshot.Entry> parsed = SessionSnapshot.parseJson(json);
        assertEquals(2, parsed.size());
        assertEquals(HOME + "/repos/a", parsed.get(0).cwd);
        assertEquals("title", parsed.get(0).title);
        assertEquals(HOME, parsed.get(1).cwd);
        assertNull(parsed.get(1).title);
    }

    @Test
    public void parseJson_emptyOrCorrupt() {
        assertTrue(SessionSnapshot.parseJson(null).isEmpty());
        assertTrue(SessionSnapshot.parseJson("").isEmpty());
        assertTrue(SessionSnapshot.parseJson("not-json").isEmpty());
    }
}
