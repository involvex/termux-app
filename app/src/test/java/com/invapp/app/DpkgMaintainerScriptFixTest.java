package com.invapp.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.invapp.shared.termux.shell.command.environment.TermuxShellEnvironment;

import org.junit.Test;

public class DpkgMaintainerScriptFixTest {

    private static final String PREFIX =
        "/data/data/com.involvex.termux_app/files/usr";

    @Test
    public void rewritesComTermuxShebang() {
        String in = "#!/data/data/com.termux/files/usr/bin/bash\n"
            + "echo ok\n";
        String out = TermuxShellEnvironment.patchDpkgMaintainerScriptText(in, PREFIX);
        assertTrue(out.startsWith("#!/data/data/com.involvex.termux_app/files/usr/bin/bash\n"));
        assertTrue(out.contains("echo ok"));
    }

    @Test
    public void prependsShebangWhenMissing() {
        String in = "if [ \"$#\" = \"3\" ]; then\n"
            + "  exit 0\n"
            + "fi\n";
        String out = TermuxShellEnvironment.patchDpkgMaintainerScriptText(in, PREFIX, "preinst");
        assertTrue(out.startsWith("#!" + PREFIX + "/bin/sh\nif ["));
    }

    @Test
    public void rewritesBinShShebang() {
        String in = "#!/bin/sh\n"
            + "exit 0\n";
        String out = TermuxShellEnvironment.patchDpkgMaintainerScriptText(in, PREFIX);
        assertEquals("#!" + PREFIX + "/bin/sh\n"
            + "exit 0\n", out);
    }

    @Test
    public void format_healsCorruptedShebang() {
        String corrupted = "#!" + PREFIX + "/bin/sh\n1\n";
        String out = TermuxShellEnvironment.patchDpkgMaintainerScriptText(
            corrupted, PREFIX, "format");
        assertEquals("1\n", out);
    }

    @Test
    public void format_leavesValidAlone() {
        assertEquals("1\n",
            TermuxShellEnvironment.patchDpkgMaintainerScriptText("1\n", PREFIX, "format"));
        assertTrue(TermuxShellEnvironment.isValidDpkgInfoFormat("1"));
        assertTrue(TermuxShellEnvironment.isValidDpkgInfoFormat("1\n"));
        assertFalse(TermuxShellEnvironment.isValidDpkgInfoFormat("2\n"));
        assertFalse(TermuxShellEnvironment.isValidDpkgInfoFormat(
            "#!" + PREFIX + "/bin/sh\n1\n"));
    }

    @Test
    public void format_isNotMaintainerScript() {
        assertFalse(TermuxShellEnvironment.isDpkgMaintainerScriptFileName("format"));
        assertFalse(TermuxShellEnvironment.isDpkgMaintainerScriptFileName("foo.list"));
        assertFalse(TermuxShellEnvironment.isDpkgMaintainerScriptFileName("foo.md5sums"));
        assertTrue(TermuxShellEnvironment.isDpkgMaintainerScriptFileName("preinst"));
        assertTrue(TermuxShellEnvironment.isDpkgMaintainerScriptFileName("openjdk-17.preinst"));
    }

    @Test
    public void nonScript_stripsAccidentalShebang() {
        String damaged = "#!" + PREFIX + "/bin/sh\n"
            + "/data/data/com.termux/files/usr/bin/foo\n";
        String out = TermuxShellEnvironment.patchDpkgMaintainerScriptText(
            damaged, PREFIX, "foo.list");
        assertFalse(out.startsWith("#!"));
        assertTrue(out.startsWith("/data/data/com.involvex.termux_app/files/usr/bin/foo\n"));
    }

    @Test
    public void stripAccidentalDpkgShebang_onlyPrefixSh() {
        String marker = "#!" + PREFIX + "/bin/sh\n1\n";
        assertEquals("1\n",
            TermuxShellEnvironment.stripAccidentalDpkgShebang(marker, PREFIX));
        assertEquals("hello",
            TermuxShellEnvironment.stripAccidentalDpkgShebang("hello", PREFIX));
    }
}
