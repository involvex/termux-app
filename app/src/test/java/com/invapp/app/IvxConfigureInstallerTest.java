package com.invapp.app;

import com.invapp.shared.termux.TermuxConstants;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IvxConfigureInstallerTest {

    @Test
    public void version_isSemver() {
        assertTrue(IvxConfigureInstaller.VERSION.matches("\\d+\\.\\d+\\.\\d+"));
        assertTrue(IvxConfigureInstaller.version().equals(IvxConfigureInstaller.VERSION));
    }

    @Test
    public void destinations_liveUnderTheTermuxPrefix() {
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH.replace('\\', '/');
        assertTrue(rel(IvxConfigureInstaller.configureFile()).startsWith(prefix));
        assertTrue(rel(IvxConfigureInstaller.doctorFile()).startsWith(prefix));
        assertTrue(rel(IvxConfigureInstaller.bannerFile()).startsWith(prefix));
        assertTrue(rel(IvxConfigureInstaller.hookFile()).startsWith(prefix));
        assertTrue(rel(IvxConfigureInstaller.completionFile()).startsWith(prefix));
        assertTrue(rel(IvxConfigureInstaller.stampFile()).startsWith(prefix));

        assertTrue(IvxConfigureInstaller.configureFile().getName().equals("ivx-configure"));
        assertTrue(IvxConfigureInstaller.doctorFile().getName().equals("ivx-doctor"));
        assertTrue(rel(IvxConfigureInstaller.bannerFile()).endsWith("lib/invapp/banner.sh"));
        assertTrue(rel(IvxConfigureInstaller.hookFile()).endsWith("etc/profile.d/invapp-banner.sh"));
        assertTrue(rel(IvxConfigureInstaller.completionFile())
            .endsWith("etc/bash_completion.d/invapp-ivx.bash"));
    }

    @Test
    public void execAssets_useThePortableShebangMarker() {
        assertTrue(read("invapp/bin/ivx-configure").startsWith("#!@BASH@"));
        assertTrue(read("invapp/bin/ivx-doctor").startsWith("#!@BASH@"));
    }

    @Test
    public void hook_isInteractiveAndTermuxAgnostic() {
        String hook = read("invapp/config/profile.d/invapp-banner.sh");
        assertTrue(hook.contains("[ -t 1 ]"));
        assertTrue(hook.contains("SSH_ORIGINAL_COMMAND"));
        assertTrue(hook.contains("INVAPP_NO_BANNER"));
        assertTrue(hook.contains("return 0"));
        assertTrue(hook.contains("BASH_VERSION"));
    }

    @Test
    public void banner_embedsRenderedPixelArt() {
        String banner = read("invapp/banner.sh");
        assertTrue("pixelfont art must be embedded", banner.contains("█ █▄ █"));
        assertTrue("must honour the off switch", banner.contains("IVX_BANNER"));
        assertTrue("must read the bun stamp, not run bun",
            banner.contains("bun.version"));
        assertTrue("no python dependency", !banner.contains("figlet_format"));
    }

    @Test
    public void configure_hasDoctorAndCompletionSurface() {
        String configure = read("invapp/bin/ivx-configure");
        assertTrue(configure.contains("cmd_doctor"));
        assertTrue(configure.contains("bash -n"));
        assertTrue(configure.contains("ivx-"));
        String completion = read("invapp/config/bash_completion.d/invapp-ivx.bash");
        assertTrue(completion.contains("complete -F _ivx_configure ivx-configure"));
        assertTrue(completion.contains("complete -F _ivx_doctor ivx-doctor"));
    }

    @Test
    public void assets_neverHardcodeTheStockPackageId() {
        String[] assets = {
            "invapp/bin/ivx-configure",
            "invapp/bin/ivx-doctor",
            "invapp/banner.sh",
            "invapp/config/profile.d/invapp-banner.sh",
            "invapp/config/bash_completion.d/invapp-ivx.bash",
        };
        for (String asset : assets) {
            String body = read(asset);
            assertTrue(asset + " must not hardcode /data/data/com.termux",
                !body.contains("/data/data/com.termux"));
        }
    }

    /** Path with forward slashes, so Windows JVM runs compare against Termux paths. */
    private static String rel(File file) {
        return file.getPath().replace('\\', '/');
    }

    /** Read an asset from the module dir, tolerating app/ or repo-root test cwd. */
    private static String read(String asset) {
        String[] roots = {"src/main/assets", "app/src/main/assets", "../app/src/main/assets"};
        for (String root : roots) {
            File file = new File(root, asset);
            if (file.isFile()) {
                try {
                    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    fail("cannot read " + file + ": " + e.getMessage());
                }
            }
        }
        fail("asset not found: " + asset);
        return null;
    }

    @Test
    public void readHelper_findsTheAssets() {
        assertNotNull(read("invapp/env.sh"));
    }
}
