package com.invapp.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.invapp.R;
import com.invapp.app.WidgetScriptsUi;
import com.invapp.app.utils.PreviewPortPrefs;
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

