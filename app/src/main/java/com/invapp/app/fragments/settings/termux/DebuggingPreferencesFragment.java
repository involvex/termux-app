package com.invapp.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.invapp.R;
import com.invapp.app.TermuxInstaller;
import com.invapp.app.utils.DiagnosticsHelper;
import com.invapp.shared.android.PermissionUtils;
import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class DebuggingPreferencesFragment extends PreferenceFragmentCompat {

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(DebuggingPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_debugging_preferences, rootKey);

        configureLoggingPreferences(context);
        configureDiagnosticsExport();
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private void configureLoggingPreferences(@NonNull Context context) {
        PreferenceCategory loggingCategory = findPreference("logging");
        if (loggingCategory == null) return;

        ListPreference logLevelListPreference = findPreference("log_level");
        if (logLevelListPreference != null) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
            if (preferences == null) return;

            setLogLevelListPreferenceData(logLevelListPreference, context, preferences.getLogLevel());
            loggingCategory.addPreference(logLevelListPreference);
        }
    }

    private void configureDiagnosticsExport() {
        Preference export = findPreference("export_diagnostics");
        if (export == null) {
            return;
        }
        export.setOnPreferenceClickListener(p -> {
            confirmAndExport();
            return true;
        });
    }

    private void confirmAndExport() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context)
            .setTitle(R.string.title_export_diagnostics)
            .setMessage(R.string.msg_diag_export_confirm)
            .setPositiveButton(android.R.string.ok, (d, w) -> runExport())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void runExport() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (!ensureStorage(context)) {
            return;
        }
        Preference export = findPreference("export_diagnostics");
        if (export != null) {
            export.setEnabled(false);
            export.setSummary(R.string.msg_diag_working);
        }
        final Context app = context.getApplicationContext();
        mExecutor.execute(() -> {
            DiagnosticsHelper.ExportResult result = DiagnosticsHelper.exportDiagnostics(app);
            mMainHandler.post(() -> onExportDone(result));
        });
    }

    private void onExportDone(@NonNull DiagnosticsHelper.ExportResult result) {
        Context context = getContext();
        Preference export = findPreference("export_diagnostics");
        if (export != null) {
            export.setEnabled(true);
            export.setSummary(R.string.summary_export_diagnostics);
        }
        if (context == null || !isAdded()) {
            return;
        }
        if (!result.ok || result.zipFile == null) {
            int msg = "storage_missing".equals(result.error)
                ? R.string.msg_backup_storage_missing
                : R.string.msg_diag_export_failed;
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(context,
            context.getString(R.string.msg_diag_export_ok, result.zipFile.getAbsolutePath()),
            Toast.LENGTH_LONG).show();
    }

    private boolean ensureStorage(@NonNull Context context) {
        if (DiagnosticsHelper.isSharedStorageReady()) {
            return true;
        }
        android.app.Activity activity = getActivity();
        if (activity != null) {
            if (!PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    activity, PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION, true)) {
                Toast.makeText(context, R.string.msg_backup_storage_missing, Toast.LENGTH_LONG)
                    .show();
                return false;
            }
            TermuxInstaller.setupStorageSymlinks(activity);
        }
        if (!DiagnosticsHelper.isSharedStorageReady()) {
            Toast.makeText(context, R.string.msg_backup_storage_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    public static ListPreference setLogLevelListPreferenceData(ListPreference logLevelListPreference, Context context, int logLevel) {
        if (logLevelListPreference == null)
            logLevelListPreference = new ListPreference(context);

        CharSequence[] logLevels = Logger.getLogLevelsArray();
        CharSequence[] logLevelLabels = Logger.getLogLevelLabelsArray(context, logLevels, true);

        logLevelListPreference.setEntryValues(logLevels);
        logLevelListPreference.setEntries(logLevelLabels);

        logLevelListPreference.setValue(String.valueOf(logLevel));
        logLevelListPreference.setDefaultValue(Logger.DEFAULT_LOG_LEVEL);

        return logLevelListPreference;
    }

}

class DebuggingPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private final TermuxAppSharedPreferences mPreferences;

    private static DebuggingPreferencesDataStore mInstance;

    private DebuggingPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized DebuggingPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DebuggingPreferencesDataStore(context);
        }
        return mInstance;
    }



    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        if (mPreferences == null) return null;
        if (key == null) return null;

        switch (key) {
            case "log_level":
                return String.valueOf(mPreferences.getLogLevel());
            default:
                return null;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (mPreferences == null) return;
        if (key == null) return;

        switch (key) {
            case "log_level":
                if (value != null) {
                    mPreferences.setLogLevel(mContext, Integer.parseInt(value));
                }
                break;
            default:
                break;
        }
    }



    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null) return;
        if (key == null) return;

        switch (key) {
            case "terminal_view_key_logging_enabled":
                    mPreferences.setTerminalViewKeyLoggingEnabled(value);
                break;
            case "plugin_error_notifications_enabled":
                mPreferences.setPluginErrorNotificationsEnabled(value);
                break;
            case "crash_report_notifications_enabled":
                mPreferences.setCrashReportNotificationsEnabled(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null) return false;
        switch (key) {
            case "terminal_view_key_logging_enabled":
                return mPreferences.isTerminalViewKeyLoggingEnabled();
            case "plugin_error_notifications_enabled":
                return mPreferences.arePluginErrorNotificationsEnabled(false);
            case "crash_report_notifications_enabled":
                return mPreferences.areCrashReportNotificationsEnabled(false);
            default:
                return false;
        }
    }

}
