package com.invapp.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResourceScriptsInstallerTest {

    @Test
    public void catalog_hasSevenHelpers() {
        String[] ids = ResourceScriptsInstaller.allScriptIds();
        assertEquals(7, ids.length);
        assertEquals(ResourceScriptsInstaller.ID_LINUX, ids[0]);
        assertEquals(ResourceScriptsInstaller.ID_CATALOG, ids[6]);
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
    public void litertBody_nativeAndroidWheelNoUvNoProot() {
        String body = ResourceScriptsInstaller.scriptBody(
            ResourceScriptsInstaller.ID_LITERT);
        assertNotNull(body);
        assertTrue(body.contains("litert-lm-api"));
        assertTrue(body.contains("android_23_arm64_v8a"));
        assertTrue(body.contains("site-packages"));
        // Engine wheel is resolved from PyPI JSON and extracted manually.
        assertTrue(body.contains("zipfile.ZipFile"));
        // No uv (glibc python breaks under the path remapper) and no proot.
        assertFalse(body.contains("uv tool"));
        assertFalse(body.contains("proot-distro"));
        // hf-xet has no Android wheel — hub must install without it.
        assertTrue(body.contains("HF_HUB_DISABLE_XET"));
        assertTrue(body.contains("--no-deps huggingface_hub"));
        // Never hardcodes the upstream package id.
        assertFalse(body.contains("/data/data/com.termux"));
        // Passthrough entrypoint exists and goes through the wrapper (which
        // preloads the GL stubs), not the bare venv script.
        assertTrue(body.contains("\"$PREFIX/bin/litert-lm\" \"$@\""));
        // Android GL stubs: engine dlopen fails without them (vendor symbol
        // android::egl_get_connection is unreachable from an app process).
        assertTrue(body.contains("GL_DIR=\"$PREFIX/lib/litert-lm-gl\""));
        assertTrue(body.contains("LD_PRELOAD="));
        assertTrue(body.contains("libGLESv3.so"));
        assertTrue(body.contains("generated by td-resource-litert-lm"));
        // CLI probe is a hard requirement again (no soft pass on GL errors).
        assertFalse(body.contains("trying run mode instead"));
        // Android python has no os.link; `litert-lm import` needs it for the
        // model hardlink, so the installer ships a shim + .pth.
        assertTrue(body.contains("invapp_android_compat"));
        assertTrue(body.contains("invapp-android.pth"));
        assertTrue(body.contains("linkat"));
        // Termux strips ensurepip's bundled wheels, and a failing ensurepip
        // aborts venv creation before --system-site-packages is recorded, so
        // both flags are mandatory together; pip arrives via pkg + the venv.
        assertTrue(body.contains("--without-pip --system-site-packages"));
        assertTrue(body.contains("-m pip --version"));
        // huggingface_hub 2.x hard-requires httpx2 (pure python).
        assertTrue(body.contains("httpx2"));
    }

    @Test
    public void scriptFile_isUnderPrefixBin() {
        String path = ResourceScriptsInstaller.scriptFile(
            ResourceScriptsInstaller.ID_LINUX).getAbsolutePath().replace('\\', '/');
        assertTrue(path.contains("/bin/"));
        assertTrue(path.endsWith(ResourceScriptsInstaller.ID_LINUX));
    }

    @Test
    public void linuxBody_resolvesBuildFromSimplestreamsIndex() {
        String body = ResourceScriptsInstaller.linuxBody();
        assertTrue(body.contains("streams/v1/images.json"));
    }

    @Test
    public void linuxBody_imageUrlContainsReleaseSegment() {
        String body = ResourceScriptsInstaller.linuxBody();
        assertTrue(body.contains("$rel/$IMG_ARCH"));
    }

    @Test
    public void linuxBody_doesNotGuessTodaysDate() {
        String body = ResourceScriptsInstaller.linuxBody();
        assertFalse(body.contains("DATE=$(date"));
        assertFalse(body.contains("$IMG_ARCH/default/$DATE"));
    }

    @Test
    public void linuxBody_aptSourcesFollowWinningRelease() {
        String body = ResourceScriptsInstaller.linuxBody();
        assertTrue(body.contains("\"deb $MIRROR $rel main universe\""));
    }

    @Test
    public void linuxBody_guardsAgainstPipProotShim() {
        String body = ResourceScriptsInstaller.linuxBody();
        assertTrue(body.contains("reinstalling proot"));
        assertTrue(body.contains("7f 45 4c 46"));
        assertTrue(body.contains("pip uninstall proot"));
    }
}
