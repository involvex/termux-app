package com.invapp.app;

import android.content.Context;
import android.system.Os;

import androidx.annotation.NonNull;

import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Seeds {@code ~/bin/termux-file-editor} for {@link com.invapp.app.api.file.FileReceiverActivity}.
 */
public final class TermuxFileEditorInstaller {

    private static final String LOG_TAG = "TermuxFileEditorInstaller";

    public static final String EDITOR_BASENAME = "termux-file-editor";

    private TermuxFileEditorInstaller() {}

    /** Absolute path of the editor script under {@code $HOME/bin}. */
    @NonNull
    public static File editorFile() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/bin", EDITOR_BASENAME);
    }

    /** Install default editor script if missing. */
    public static void installDefaultsIfMissing(@NonNull Context context) {
        File dest = editorFile();
        if (dest.isFile()) {
            return;
        }
        if (writeExec(dest, scriptBody())) {
            Logger.logInfo(LOG_TAG, "Installed " + dest);
        }
    }

    @NonNull
    static String scriptBody() {
        String bash = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        return ""
            + "#!" + bash + "\n"
            + "# invapp: termux-file-editor (FileReceiver Edit)\n"
            + "set -e\n"
            + "export PATH=\"" + prefix + "/bin:$PATH\"\n"
            + "file=\"${1:?usage: termux-file-editor <path>}\"\n"
            + "if [ ! -e \"$file\" ]; then\n"
            + "  msg=\"File not found: $file\"\n"
            + "  command -v termux-toast >/dev/null 2>&1 && termux-toast \"$msg\" || echo \"$msg\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "for ed in nvim vim nano less; do\n"
            + "  if command -v \"$ed\" >/dev/null 2>&1; then\n"
            + "    exec \"$ed\" -- \"$file\"\n"
            + "  fi\n"
            + "done\n"
            + "msg='No editor found — pkg install neovim, vim, or nano'\n"
            + "command -v termux-toast >/dev/null 2>&1 && termux-toast \"$msg\" || echo \"$msg\" >&2\n"
            + "exit 127\n";
    }

    private static boolean writeExec(@NonNull File dest, @NonNull String contents) {
        try {
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            File tmp = new File(dest.getAbsolutePath() + ".new");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(contents.getBytes(StandardCharsets.UTF_8));
            }
            //noinspection OctalInteger
            Os.chmod(tmp.getAbsolutePath(), 0700);
            if (dest.exists() && !dest.delete()) {
                try {
                    Os.remove(dest.getAbsolutePath());
                } catch (Exception e) {
                    Logger.logWarn(LOG_TAG, "replace " + dest + ": " + e.getMessage());
                }
            }
            if (!tmp.renameTo(dest)) {
                Logger.logWarn(LOG_TAG, "Could not install " + dest);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "write " + dest + ": " + e.getMessage());
            return false;
        }
    }
}
