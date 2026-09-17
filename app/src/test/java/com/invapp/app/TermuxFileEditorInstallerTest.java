package com.invapp.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TermuxFileEditorInstallerTest {

    @Test
    public void scriptBody_hasShebangAndEditorChain() {
        String body = TermuxFileEditorInstaller.scriptBody();
        assertNotNull(body);
        assertTrue(body.startsWith("#!"));
        assertTrue(body.contains("termux-file-editor"));
        assertTrue(body.contains("nvim"));
        assertTrue(body.contains("vim"));
        assertTrue(body.contains("nano"));
        assertTrue(body.contains("less"));
        assertTrue(body.contains("exec"));
        assertFalse(body.contains("unset LD_PRELOAD"));
    }

    @Test
    public void editorFile_isUnderHomeBin() {
        String path = TermuxFileEditorInstaller.editorFile().getAbsolutePath().replace('\\', '/');
        assertTrue(path.contains("/bin/"));
        assertTrue(path.endsWith(TermuxFileEditorInstaller.EDITOR_BASENAME));
    }
}
