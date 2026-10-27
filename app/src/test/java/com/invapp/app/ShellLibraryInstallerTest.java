package com.invapp.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ShellLibraryInstallerTest {

    @Test
    public void libraryVersion_isStableSemver() {
        assertEquals("1.7.0", ShellLibraryInstaller.libraryVersion());
    }

    @Test
    public void libraryConstants_areAccessible() {
        assertNotNull(ShellLibraryInstaller.class);
        assertEquals("1.7.0", ShellLibraryInstaller.LIBRARY_VERSION);
    }
}