package com.invapp.app.fragments.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.invapp.R;
import com.invapp.app.TermuxActivity;
import com.invapp.app.utils.BackupHelper;
import com.invapp.app.utils.SettingsSearchHelper;
import com.invapp.app.utils.WorkflowHelper;
import com.invapp.shared.activity.ActivityUtils;
import com.invapp.shared.android.PermissionUtils;
import com.invapp.app.TermuxInstaller;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings → Backup: export/restore repos manifest, {@code ~/.termux},
 * {@code ~/.shortcuts}, and workflow/extra-keys prefs.
 */
@Keep
public class BackupPreferencesFragment extends PreferenceFragmentCompat {

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_backup_preferences, rootKey);

        SwitchPreferenceCompat secrets = findPreference("backup_include_secrets");
        if (secrets != null) {
            secrets.setChecked(false);
        }

        Preference export = findPreference("backup_export");
        if (export != null) {
            export.setOnPreferenceClickListener(p -> {
                runExport();
                return true;
            });
        }

        Preference restore = findPreference("backup_restore");
        if (restore != null) {
            restore.setOnPreferenceClickListener(p -> {
                confirmRestore();
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
        refreshStatus();
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private boolean includeSecrets() {
        SwitchPreferenceCompat secrets = findPreference("backup_include_secrets");
        return secrets != null && secrets.isChecked();
    }

    private void refreshStatus() {
        Preference status = findPreference("backup_status");
        if (status == null) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (!BackupHelper.isSharedStorageReady()) {
            status.setSummary(R.string.summary_backup_storage_missing);
            return;
        }
        File latest = BackupHelper.findLatestBackupZip();
        if (latest == null) {
            status.setSummary(context.getString(R.string.summary_backup_status_ready,
                BackupHelper.backupDirectory().getAbsolutePath()));
        } else {
            status.setSummary(context.getString(R.string.summary_backup_status_latest,
                latest.getName()));
        }
    }

    private void runExport() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (!ensureStorage(context)) {
            return;
        }
        Preference export = findPreference("backup_export");
        if (export != null) {
            export.setEnabled(false);
            export.setSummary(R.string.summary_backup_working);
        }
        final boolean secrets = includeSecrets();
        final Context app = context.getApplicationContext();
        mExecutor.execute(() -> {
            BackupHelper.ExportResult result = BackupHelper.exportBackup(app, secrets);
            mMainHandler.post(() -> onExportDone(result));
        });
    }

    private void onExportDone(@NonNull BackupHelper.ExportResult result) {
        Context context = getContext();
        Preference export = findPreference("backup_export");
        if (export != null) {
            export.setEnabled(true);
            export.setSummary(R.string.summary_backup_export);
        }
        if (context == null || !isAdded()) {
            return;
        }
        if (!result.ok || result.zipFile == null) {
            int msg = "storage_missing".equals(result.error)
                ? R.string.msg_backup_storage_missing
                : R.string.msg_backup_export_failed;
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }
        Toast.makeText(context,
            context.getString(R.string.msg_backup_export_ok,
                result.zipFile.getName(), result.repoCount),
            Toast.LENGTH_LONG).show();
        refreshStatus();
    }

    private void confirmRestore() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (!ensureStorage(context)) {
            return;
        }
        File latest = BackupHelper.findLatestBackupZip();
        if (latest == null) {
            Toast.makeText(context, R.string.msg_backup_no_zip, Toast.LENGTH_LONG).show();
            return;
        }
        final boolean secrets = includeSecrets();
        String message = secrets
            ? context.getString(R.string.msg_backup_restore_confirm_secrets, latest.getName())
            : context.getString(R.string.msg_backup_restore_confirm, latest.getName());
        new AlertDialog.Builder(context)
            .setTitle(R.string.title_backup_restore)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, (d, w) -> runRestore(latest, secrets))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void runRestore(@NonNull File zipFile, boolean secrets) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Preference restore = findPreference("backup_restore");
        if (restore != null) {
            restore.setEnabled(false);
            restore.setSummary(R.string.summary_backup_working);
        }
        final Context app = context.getApplicationContext();
        mExecutor.execute(() -> {
            BackupHelper.RestoreResult result =
                BackupHelper.restoreBackup(app, zipFile, secrets);
            mMainHandler.post(() -> onRestoreDone(result));
        });
    }

    private void onRestoreDone(@NonNull BackupHelper.RestoreResult result) {
        Context context = getContext();
        Preference restore = findPreference("backup_restore");
        if (restore != null) {
            restore.setEnabled(true);
            restore.setSummary(R.string.summary_backup_restore);
        }
        if (context == null || !isAdded()) {
            return;
        }
        if (!result.ok) {
            Toast.makeText(context, R.string.msg_backup_restore_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(context,
            context.getString(R.string.msg_backup_restore_ok, result.filesRestored),
            Toast.LENGTH_LONG).show();
        if (!result.missingRepos.isEmpty()) {
            offerCloneMissing(result.missingRepos);
        }
        refreshStatus();
    }

    private void offerCloneMissing(@NonNull List<BackupHelper.RepoEntry> missing) {
        Context context = getContext();
        if (context == null || missing.isEmpty()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(missing.get(i).name);
        }
        new AlertDialog.Builder(context)
            .setTitle(R.string.title_backup_clone_missing)
            .setMessage(context.getString(R.string.msg_backup_clone_missing, names.toString()))
            .setPositiveButton(R.string.action_backup_clone_missing, (d, w) -> {
                StringBuilder cmd = new StringBuilder();
                for (BackupHelper.RepoEntry repo : missing) {
                    if (repo.url == null || repo.url.isEmpty()) {
                        continue;
                    }
                    cmd.append(WorkflowHelper.tdCloneCommand(repo.url, repo.name, false));
                }
                if (cmd.length() == 0) {
                    return;
                }
                Intent intent = new Intent(context, TermuxActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(TermuxActivity.EXTRA_INJECT_SHELL_COMMAND, cmd.toString());
                ActivityUtils.startActivity(context, intent);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private boolean ensureStorage(@NonNull Context context) {
        if (BackupHelper.isSharedStorageReady()) {
            return true;
        }
        // Mirror TermuxActivity: request permission then create ~/storage symlinks.
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
        if (!BackupHelper.isSharedStorageReady()) {
            Toast.makeText(context, R.string.msg_backup_storage_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
}
