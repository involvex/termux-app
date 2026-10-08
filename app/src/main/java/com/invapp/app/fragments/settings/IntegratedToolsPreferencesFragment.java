package com.invapp.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.invapp.R;
import com.invapp.app.utils.IntegratedTools;
import com.invapp.app.utils.SettingsSearchHelper;

/**
 * v1 integrated-tools screen (API + Widget flags with live install status).
 * Flags are stored in isolated {@code invapp_integrated_tools} prefs and
 * wired manually (not via {@code PreferenceDataStore}).
 */
@Keep
public class IntegratedToolsPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        // Keep the default data store unused for our keys (persistent=false + manual wiring).
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.invapp_integrated_tools, rootKey);

        for (IntegratedTools.Tool tool : IntegratedTools.allTools()) {
            SwitchPreferenceCompat row = findPreference(tool.prefKey());
            if (row == null) {
                continue;
            }
            syncRow(context, row, tool);
            row.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                IntegratedTools.setEnabled(context, tool, enabled);
                syncRow(context, row, tool);
                return true;
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SettingsSearchHelper.attachSearchHeader(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context == null) return;
        for (IntegratedTools.Tool tool : IntegratedTools.allTools()) {
            SwitchPreferenceCompat row = findPreference(tool.prefKey());
            if (row != null) {
                syncRow(context, row, tool);
            }
        }
    }

    private void syncRow(@NonNull Context context,
                         @NonNull SwitchPreferenceCompat row,
                         @NonNull IntegratedTools.Tool tool) {
        row.setChecked(IntegratedTools.isEnabled(context, tool));
        row.setSummary(statusText(context, tool));
    }

    @NonNull
    static String statusText(@NonNull Context context, @NonNull IntegratedTools.Tool tool) {
        boolean apk = IntegratedTools.isPluginInstalled(context, tool);
        boolean cli = IntegratedTools.isCliPresent(tool);
        boolean enabled = IntegratedTools.isEnabled(context, tool);
        switch (IntegratedTools.statusFor(enabled, apk, cli)) {
            case READY:
                return context.getString(tool == IntegratedTools.Tool.API
                    ? R.string.status_tool_api_ready : R.string.status_tool_widget_ready);
            case MISSING_APK:
                return context.getString(R.string.status_tool_apk_missing, tool.packageName());
            case MISSING_CLI:
                return context.getString(R.string.status_tool_api_cli_missing);
            case DISABLED:
            default:
                return context.getString(tool == IntegratedTools.Tool.API
                    ? R.string.summary_tool_api : R.string.summary_tool_widget);
        }
    }
}
