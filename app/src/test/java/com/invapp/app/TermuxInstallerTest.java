package com.invapp.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TermuxInstallerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void acceptsEntryUnderDestinationDir() throws IOException {
        File dest = tempFolder.newFolder("usr-staging");
        File resolved = TermuxInstaller.resolveZipEntryTargetFile(dest, "bin/bash");
        assertEquals(new File(dest, "bin/bash").getPath(), resolved.getPath());
        assertTrue(resolved.getCanonicalPath().startsWith(dest.getCanonicalPath() + File.separator));
    }

    @Test
    public void acceptsSymlinkLocationUnderDestinationDir() throws IOException {
        File dest = tempFolder.newFolder("usr-staging");
        File resolved = TermuxInstaller.resolveZipEntryTargetFile(dest, "lib/libexample.so");
        assertEquals(new File(dest, "lib/libexample.so").getPath(), resolved.getPath());
    }

    @Test
    public void acceptsEntryThatStaysInsideViaDotDot() throws IOException {
        File dest = tempFolder.newFolder("usr-staging");
        File resolved = TermuxInstaller.resolveZipEntryTargetFile(dest, "bin/../lib/libc.so");
        assertTrue(resolved.getCanonicalPath().startsWith(dest.getCanonicalPath() + File.separator));
    }

    @Test
    public void rejectsParentTraversal() throws IOException {
        File dest = tempFolder.newFolder("usr-staging");
        try {
            TermuxInstaller.resolveZipEntryTargetFile(dest, "../evil");
            fail("Expected IOException for entry escaping destination dir");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("evil"));
        }
    }

    @Test
    public void rejectsNestedParentTraversal() throws IOException {
        File dest = tempFolder.newFolder("usr-staging");
        try {
            TermuxInstaller.resolveZipEntryTargetFile(dest, "bin/../../evil");
            fail("Expected IOException for nested entry escaping destination dir");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("evil"));
        }
    }

    @Test
    public void rejectsSymlinkLocationOutsideDestinationDir() throws IOException {
        File dest = tempFolder.newFolder("usr-staging");
        try {
            TermuxInstaller.resolveZipEntryTargetFile(dest, "../evil-link");
            fail("Expected IOException for symlink location escaping destination dir");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void rejectsNullDestinationDirAndEntryName() {
        File dest = new File("usr-staging");
        try {
            TermuxInstaller.resolveZipEntryTargetFile(null, "bin/bash");
            fail("Expected IOException for null destination dir");
        } catch (IOException expected) {
            // expected
        }
        try {
            TermuxInstaller.resolveZipEntryTargetFile(dest, null);
            fail("Expected IOException for null entry name");
        } catch (IOException expected) {
            // expected
        }
    }
}
