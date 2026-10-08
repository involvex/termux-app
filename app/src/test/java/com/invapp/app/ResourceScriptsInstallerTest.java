package com.invapp.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResourceScriptsInstallerTest {

    @Test
    public void catalog_hasSixHelpers() {
        String[] ids = ResourceScriptsInstaller.allScriptIds();
        assertEquals(6, ids.length);
        assertEquals(ResourceScriptsInstaller.ID_LINUX, ids[0]);
        assertEquals(ResourceScriptsInstaller.ID_CATALOG, ids[5]);
    }

    @Test
    public void scriptBodies_haveShebangMarkerPathAndRedirector() {
        for (String id : ResourceScriptsInstaller.allScriptIds()) {
            String body = ResourceScriptsInstaller.scriptBody(id);
            assertNotNull(id, body);
            assertTrue(id, body.startsWith("#!"));
            assertTrue(id, body.contains("# invapp-resource: " + id));
            assertTrue(id, body.contains("export PATH="));
            assertTrue(id, body.contains("libinvapp-redirector.so"));
            assertFalse(id, body.contains("/data/data/com.termux"));
            assertFalse(id, body.contains("tmux new"));
            assertFalse(id, body.contains("pkg install tmux"));
        }
        assertNull(ResourceScriptsInstaller.scriptBody("not-a-resource"));
    }

    @Test
    public void linuxBody_prootContainerWithoutTopLevelUnset() {
        String body = ResourceScriptsInstaller.scriptBody(
            ResourceScriptsInstaller.ID_LINUX);
        assertNotNull(body);
        assertTrue(body.contains("proot"));
        assertTrue(body.contains("images.linuxcontainers.org"));
        assertTrue(body.contains("debian-container"));
        assertTrue(body.contains("xz -t"));
        assertTrue(body.contains("--link2symlink"));
        // Redirector preserved at top level; unset only inside generated run.sh heredoc.
        assertTrue(body.contains("REDIRECTOR_SO"));
        assertFalse(body.contains("com.termux.ultra"));
    }

    @Test
    public void pythonBody_pkgPythonAndVenv() {
        String body = ResourceScriptsInstaller.scriptBody(
            ResourceScriptsInstaller.ID_PYTHON);
        assertNotNull(body);
        assertTrue(body.contains("pkg install -y python"));
        assertTrue(body.contains("python -m pip"));
        assertTrue(body.contains("--venv"));
    }

    @Test
    public void qemuBody_nativePkgsOnlyNoImageDownload() {
        String body = ResourceScriptsInstaller.scriptBody(
            ResourceScriptsInstaller.ID_QEMU);
        assertNotNull(body);
        assertTrue(body.contains("qemu-system-aarch64"));
        assertTrue(body.contains("qemu-utils"));
        assertFalse(body.contains("debian-12-genericcloud"));
        assertFalse(body.contains("cloud.debian.org"));
    }

    @Test
    public void bunBody_androidFilters() {
        String body = ResourceScriptsInstaller.scriptBody(
            ResourceScriptsInstaller.ID_BUN);
        assertNotNull(body);
        assertTrue(body.contains("libexec/bun"));
        assertTrue(body.contains("--version"));
        assertTrue(body.contains("--os=android"));
        assertTrue(body.contains("bun-doctor"));
    }

    @Test
    public void aiBody_healthHints() {
        String body = ResourceScriptsInstaller.scriptBody(
            ResourceScriptsInstaller.ID_AI);
        assertNotNull(body);
        assertTrue(body.contains("opencode-setup"));
        assertTrue(body.contains("opencode-fix-net"));
        assertTrue(body.contains("td-ai"));
        assertTrue(body.contains("4096"));
        assertTrue(body.contains("cursor-agent"));
    }

    @Test
    public void scriptFile_isUnderPrefixBin() {
        String path = ResourceScriptsInstaller.scriptFile(
            ResourceScriptsInstaller.ID_LINUX).getAbsolutePath().replace('\\', '/');
        assertTrue(path.contains("/bin/"));
        assertTrue(path.endsWith(ResourceScriptsInstaller.ID_LINUX));
    }
}
