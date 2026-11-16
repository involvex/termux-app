package com.invapp.app.utils;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BunVersionHelperTest {

    @Test
    public void isMismatch_detectsStampVsBundled() {
        assertTrue(BunVersionHelper.isMismatch("1.4.3", "1.4.3", "1.4.4"));
        assertFalse(BunVersionHelper.isMismatch("1.4.4", "1.4.4", "1.4.4"));
    }

    @Test
    public void isMismatch_detectsRunningVsStamp() {
        assertTrue(BunVersionHelper.isMismatch("1.4.4", "1.3.0", "1.4.4"));
        assertFalse(BunVersionHelper.isMismatch(null, null, "1.4.4"));
    }

    @Test
    public void collect_readsStampFile() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
            "bun-version-test-" + System.nanoTime());
        File stampDir = new File(dir, "var/lib/invapp");
        assertTrue(stampDir.mkdirs());
        File stamp = new File(stampDir, "bun.version");
        try (FileOutputStream out = new FileOutputStream(stamp)) {
            out.write("1.4.4".getBytes(StandardCharsets.UTF_8));
        }
        File libexecDir = new File(dir, "libexec");
        assertTrue(libexecDir.mkdirs());
        File bun = new File(libexecDir, "bun");
        assertTrue(bun.createNewFile());
        bun.setExecutable(true);

        BunVersionHelper.Report report = BunVersionHelper.collect(dir.getAbsolutePath(), "1.4.4");
        assertEquals("1.4.4", report.bundledExpected);
        assertEquals("1.4.4", report.stamped);
        assertTrue(report.libexecPresent);
        assertFalse(report.mismatch);

        // cleanup
        //noinspection ResultOfMethodCallIgnored
        bun.delete();
        //noinspection ResultOfMethodCallIgnored
        stamp.delete();
        //noinspection ResultOfMethodCallIgnored
        stampDir.delete();
        //noinspection ResultOfMethodCallIgnored
        libexecDir.delete();
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    @Test
    public void readStamp_missingFile() {
        assertNull(BunVersionHelper.readStamp(new File("/no/such/bun.version")));
    }
}
