package com.invapp.app.utils;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.invapp.R;

import java.util.List;

/**
 * Shared multi-choice dialog for workflow (page 2) extra keys.
 * Always rewrites nav page 1 as text labels and sets {@code extra-keys-style=none}.
 */
public final class ExtraKeysCustomizeUi {

    public interface ReloadCallback {
        /** Called on the UI thread after a successful properties write. */
        void onExtraKeysWritten();
    }

    private ExtraKeysCustomizeUi() {}

    public static void show(@NonNull Context context, @Nullable ReloadCallback reload) {
        final String[] labels = ExtraKeysBarHelper.PAGE2_LABELS;
        final boolean[] checked = ExtraKeysBarHelper.checkedFlags(
            ExtraKeysBarHelper.getPage2Ids(context));
        new AlertDialog.Builder(context)
            .setTitle(R.string.title_customize_extra_keys)
            .setMessage(R.string.msg_customize_extra_keys)
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                checked[which] = isChecked;
            })
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                List<String> ids = ExtraKeysBarHelper.filterToCatalogOrder(checked);
                ExtraKeysBarHelper.setPage2Ids(context, ids);
                applyWrite(context, ids, reload);
            })
            .setNeutralButton(R.string.action_workflow_reset_bar, (dialog, which) -> {
                ExtraKeysBarHelper.resetToDefault(context);
                List<String> ids = ExtraKeysBarHelper.getPage2Ids(context);
                applyWrite(context, ids, reload);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void applyWrite(@NonNull Context context,
                                   @NonNull List<String> ids,
                                   @Nullable ReloadCallback reload) {
        if (ExtraKeysBarHelper.writeExtraKeysProperty(ids)) {
            if (reload != null) {
                reload.onExtraKeysWritten();
            }
            Toast.makeText(context, R.string.msg_extra_keys_updated, Toast.LENGTH_LONG)
                .show();
        } else {
            Toast.makeText(context, R.string.error_extra_keys_write_failed, Toast.LENGTH_SHORT)
                .show();
        }
    }
}
