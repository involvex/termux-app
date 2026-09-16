package com.invapp.app;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.invapp.R;

import java.util.List;

/**
 * Shared install/reset/status picker for Termux:Widget {@code ~/.shortcuts} templates.
 */
public final class WidgetScriptsUi {

    /** Writes a shell command into the current terminal session. */
    public interface RunOnceHandler {
        /** @return {@code true} if the command was sent */
        boolean run(@NonNull String command);
    }

    private WidgetScriptsUi() {}

    /** Settings: Install / Reset / Cancel (no live session). */
    public static void showSettingsPicker(@NonNull Context context) {
        showPicker(context, null);
    }

    /**
     * @param runOnce when non-null, neutral button is “Run once” (first checked
     *                script) instead of “Reset”; use Settings for reset.
     */
    public static void showPicker(@NonNull Context context,
                                  @Nullable RunOnceHandler runOnce) {
        final boolean[] checked = WidgetScriptsInstaller.installedFlags();
        boolean anyInstalled = false;
        for (boolean b : checked) {
            if (b) {
                anyInstalled = true;
                break;
            }
        }
        if (!anyInstalled) {
            for (int i = 0; i < checked.length; i++) {
                checked[i] = true;
            }
        }

        String status = WidgetScriptsInstaller.statusSummary(context);
        String help = context.getString(R.string.msg_widget_scripts_help);
        if (runOnce != null) {
            help = help + "\n\n" + context.getString(R.string.msg_widget_scripts_run_hint);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle(R.string.title_widget_scripts)
            .setMessage(status + "\n\n" + help)
            .setMultiChoiceItems(WidgetScriptsInstaller.CATALOG_LABELS, checked,
                (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton(R.string.action_widget_scripts_install, (dialog, which) -> {
                List<String> ids = WidgetScriptsInstaller.filterCatalogOrder(checked);
                int n = WidgetScriptsInstaller.installSelected(ids);
                Toast.makeText(context,
                    context.getString(R.string.msg_widget_scripts_installed, n),
                    Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(android.R.string.cancel, null);

        if (runOnce != null) {
            builder.setNeutralButton(R.string.action_widget_scripts_run_once, (dialog, which) -> {
                List<String> ids = WidgetScriptsInstaller.filterCatalogOrder(checked);
                if (ids.isEmpty()) {
                    Toast.makeText(context, R.string.msg_widget_scripts_none_selected,
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                WidgetScriptsInstaller.installSelected(ids);
                String cmd = WidgetScriptsInstaller.runOnceCommand(ids.get(0));
                if (cmd == null) {
                    return;
                }
                if (!runOnce.run(cmd)) {
                    Toast.makeText(context, R.string.msg_workflow_no_session,
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(context,
                    context.getString(R.string.msg_widget_scripts_running, ids.get(0)),
                    Toast.LENGTH_SHORT).show();
            });
        } else {
            builder.setNeutralButton(R.string.action_widget_scripts_reset, (dialog, which) -> {
                WidgetScriptsInstaller.resetToDefaults();
                Toast.makeText(context, R.string.msg_widget_scripts_reset,
                    Toast.LENGTH_SHORT).show();
            });
        }

        builder.show();
    }
}
