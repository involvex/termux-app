package com.invapp.app;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ShellCompletionsInstallTest {

    @Test
    public void bunCompletionRegistersComplete() {
        String s = TermuxBunInstaller.bunCompletionScript();
        assertTrue(s.contains("complete -F _invapp_bun bun"));
        assertTrue(s.contains("install"));
    }

    @Test
    public void pkgCompletionHasInstallSearch() {
        String s = TermuxBunInstaller.pkgCompletionScript();
        assertTrue(s.contains("complete -F _invapp_pkg pkg"));
        assertTrue(s.contains("install"));
        assertTrue(s.contains("search"));
    }

    @Test
    public void npmAndGitAndGhScriptsPresent() {
        assertTrue(TermuxBunInstaller.npmCompletionScript().contains("complete -F _invapp_npm npm"));
        assertTrue(TermuxBunInstaller.gitCompletionScript().contains("complete -F _invapp_git git"));
        assertTrue(TermuxBunInstaller.ghCompletionScript().contains("gh completion")
            || TermuxBunInstaller.ghCompletionScript().contains("_invapp_gh"));
    }
}
