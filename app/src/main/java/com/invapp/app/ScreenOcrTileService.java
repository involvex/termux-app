package com.invapp.app;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.invapp.R;
import com.invapp.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Quick settings tile that runs a delayed {@code td-screen-ocr} capture.
 *
 * The tap only schedules the capture: the shell waits
 * ({@link #CAPTURE_DELAY_MS}, override via {@code SCREEN_OCR_DELAY}
 * seconds env) so the user can close the shade and switch apps, then the
 * API-side {@code SCREENSHOT_DELAY_MS} margin lets the capture land on the
 * target app (MediaProjection consent must already be granted, otherwise the
 * consent dialog steals foreground back to Termux and the wrong screen is
 * captured).
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class ScreenOcrTileService extends TileService {

    /** Shell-side wait before requesting the capture, in ms. */
    @VisibleForTesting
    static final long CAPTURE_DELAY_MS = 10_000;

    /** API-side settle margin after consent, in ms (ScreenshotAPI clamps to 15000). */
    @VisibleForTesting
    static final long SCREENSHOT_DELAY_MS = 3_000;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onClick() {
        super.onClick();

        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
        toast(getString(R.string.tile_screen_ocr_start, CAPTURE_DELAY_MS / 1000), false);

        Thread worker = new Thread(() -> {
            String result = runDelayedOcr();
            mMainHandler.post(() -> {
                toast(result != null ? result : getString(R.string.tile_screen_ocr_missing), true);
                Tile t = getQsTile();
                if (t != null) {
                    t.setState(Tile.STATE_INACTIVE);
                    t.updateTile();
                }
            });
        }, "invapp-screen-ocr-tile");
        worker.setDaemon(true);
        worker.start();
    }

    private void toast(@NonNull String message, boolean longDuration) {
        Toast.makeText(this, message,
            longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    /**
     * Sleeps {@link #CAPTURE_DELAY_MS}, then runs {@code td-screen-ocr} under
     * the Termux prefix. Returns the last stdout line (the script echoes
     * {@code OCR → clipboard (N chars) ...} on success), or null when the
     * helper is missing.
     */
    @Nullable
    private String runDelayedOcr() {
        try {
            Thread.sleep(CAPTURE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        File bash = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash");
        File helper = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "td-screen-ocr");
        if (!bash.isFile() || !helper.isFile()) {
            return null;
        }

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                bash.getAbsolutePath(), "-c", buildCaptureScript());
            pb.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
            pb.environment().put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
            pb.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
            pb.environment().put("SCREENSHOT_DELAY_MS", Long.toString(SCREENSHOT_DELAY_MS));
            pb.redirectErrorStream(true);
            process = pb.start();
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            String output = readFully(process.getInputStream());
            if (!finished) {
                process.destroy();
            }
            return lastNonEmptyLine(output);
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * Shell script run under Termux bash. Kept in one place so unit tests can
     * assert on it without spawning processes.
     */
    @VisibleForTesting
    @NonNull
    static String buildCaptureScript() {
        return "td-screen-ocr";
    }

    @Nullable
    private static String lastNonEmptyLine(@Nullable String output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        String[] lines = output.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line.length() > 120 ? line.substring(0, 120) : line;
            }
        }
        return null;
    }

    private static String readFully(@NonNull InputStream in) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
