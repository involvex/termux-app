package com.invapp.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.invapp.R;
import com.invapp.app.WidgetScriptsUi;
import com.invapp.app.utils.LaunchPrefs;
import com.invapp.app.utils.PreviewPortPrefs;
import com.invapp.app.utils.SettingsSearchHelper;
import com.invapp.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TermuxPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_preferences, rootKey);

        Preference widgetScripts = findPreference("widget_scripts");
        if (widgetScripts != null) {
            widgetScripts.setOnPreferenceClickListener(preference -> {
                WidgetScriptsUi.showSettingsPicker(context);
                return true;
            });
        }

        Preference preferredPorts = findPreference("preferred_ports");
        if (preferredPorts != null) {
            preferredPorts.setSummary(PreviewPortPrefs.getPreferredPortsCsv(context));
            preferredPorts.setOnPreferenceClickListener(preference -> {
                showPreferredPortsDialog(context, preferredPorts);
                return true;
            });
        }

        configureLaunchPrefs(context);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view,
                              @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SettingsSearchHelper.attachSearchHeader(this);
    }

    private void configureLaunchPrefs(@NonNull Context context) {
        ListPreference launchPage = findPreference(LaunchPrefs.KEY_LAUNCH_PAGE);
        SwitchPreferenceCompat autoStart =
            findPreference(LaunchPrefs.KEY_AUTO_START_CONSOLE);
        if (launchPage != null) {
            launchPage.setEntries(new CharSequence[] {
                context.getString(R.string.entry_launch_overview),
                context.getString(R.string.entry_launch_terminal)
            });
            launchPage.setEntryValues(new CharSequence[] {"0", "1"});
            syncLaunchPageRow(context, launchPage, autoStart);
            launchPage.setOnPreferenceChangeListener((preference, newValue) -> {
                int stored = LaunchPrefs.LaunchPage.TERMINAL.storedValue();
                try {
                    stored = Integer.parseInt(String.valueOf(newValue));
                } catch (NumberFormatException ignored) {
                }
                LaunchPrefs.LaunchPage page = LaunchPrefs.LaunchPage.fromStored(stored);
                LaunchPrefs.setLaunchPage(context, page);
                syncLaunchPageRow(context, launchPage, autoStart);
                return true;
            });
        }
        if (autoStart != null) {
            autoStart.setChecked(LaunchPrefs.isAutoStartConsole(context));
            autoStart.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                LaunchPrefs.setAutoStartConsole(context, enabled);
                autoStart.setChecked(enabled);
                if (launchPage != null) {
                    syncLaunchPageRow(context, launchPage, autoStart);
                }
                return true;
            });
        }
    }

    private void syncLaunchPageRow(@NonNull Context context,
                                   @NonNull ListPreference launchPage,
                                   @Nullable SwitchPreferenceCompat autoStart) {
        boolean auto = autoStart != null
            ? autoStart.isChecked()
            : LaunchPrefs.isAutoStartConsole(context);
        LaunchPrefs.LaunchPage effective = LaunchPrefs.effectiveLaunchPage(context);
        launchPage.setValue(String.valueOf(
            LaunchPrefs.getLaunchPage(context).storedValue()));
        if (auto) {
            launchPage.setEnabled(false);
            launchPage.setSummary(context.getString(R.string.summary_launch_page_locked));
        } else {
            launchPage.setEnabled(true);
            launchPage.setSummary(effective == LaunchPrefs.LaunchPage.OVERVIEW
                ? context.getString(R.string.entry_launch_overview)
                : context.getString(R.string.entry_launch_terminal));
        }
    }

    private void showPreferredPortsDialog(@NonNull Context context,
                                          @NonNull Preference preference) {
        final EditText input = new EditText(context);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setHint(R.string.hint_preferred_ports);
        input.setText(PreviewPortPrefs.getPreferredPortsCsv(context));
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(context)
            .setTitle(R.string.title_preferred_ports)
            .setMessage(R.string.summary_preferred_ports)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                CharSequence text = input.getText();
                PreviewPortPrefs.setPreferredPortsCsv(context,
                    text != null ? text.toString() : "");
                preference.setSummary(PreviewPortPrefs.getPreferredPortsCsv(context));
                Toast.makeText(context, R.string.msg_preferred_ports_saved, Toast.LENGTH_SHORT)
                    .show();
            })
            .setNeutralButton(R.string.action_workflow_reset_bar, (dialog, which) -> {
                PreviewPortPrefs.setPreferredPortsCsv(context, null);
                preference.setSummary(PreviewPortPrefs.getPreferredPortsCsv(context));
                Toast.makeText(context, R.string.msg_preferred_ports_saved, Toast.LENGTH_SHORT)
                    .show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

}

class TermuxPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private final TermuxAppSharedPreferences mPreferences;

    private static TermuxPreferencesDataStore mInstance;

    private TermuxPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxPreferencesDataStore(context);
        }
        return mInstance;
    }

}

