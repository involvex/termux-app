package com.invapp.app.fragments.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
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
import com.invapp.app.activities.LocalhostPreviewActivity;
import com.invapp.app.utils.AiPrefs;
import com.invapp.app.utils.AiSessionHelper;
import com.invapp.app.utils.SettingsSearchHelper;
import com.invapp.app.utils.WorkflowHelper;
import com.invapp.shared.activity.ActivityUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings → AI Doctor: OpenCode status, repair, host/version prefs.
 */
@Keep
public class AiPreferencesFragment extends PreferenceFragmentCompat {

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

        setPreferencesFromResource(R.xml.termux_ai_preferences, rootKey);

        Preference runChecks = findPreference("ai_run_checks");
        if (runChecks != null) {
            runChecks.setOnPreferenceClickListener(p -> {
                refreshStatus(true);
                return true;
            });
        }

        Preference repair = findPreference("ai_repair");
        if (repair != null) {
            repair.setOnPreferenceClickListener(p -> {
                injectIntoTerminal(AiPrefs.buildRepairCommand(context),
                    R.string.msg_ai_doctor_repair_started);
                return true;
            });
        }

        Preference start = findPreference("ai_start");
        if (start != null) {
            start.setOnPreferenceClickListener(p -> {
                injectIntoTerminal(AiPrefs.buildTdAiCommand(context),
                    R.string.msg_ai_doctor_start_sent);
                return true;
            });
        }

        Preference preview = findPreference("ai_open_preview");
        if (preview != null) {
            preview.setOnPreferenceClickListener(p -> {
                ActivityUtils.startActivity(context,
                    LocalhostPreviewActivity.createIntent(context, WorkflowHelper.AI_PREVIEW_PORT));
                return true;
            });
        }

        Preference stop = findPreference("ai_stop");
        if (stop != null) {
            stop.setOnPreferenceClickListener(p -> {
                stopAiAsync();
                return true;
            });
        }

        SwitchPreferenceCompat hostLoopback = findPreference("ai_host_loopback");
        if (hostLoopback != null) {
            hostLoopback.setChecked(AiPrefs.isLoopbackHost(context));
            hostLoopback.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean loopback = Boolean.TRUE.equals(newValue);
                AiPrefs.setOpenCodeHost(context,
                    loopback ? AiPrefs.HOST_LOOPBACK : AiPrefs.HOST_WILDCARD);
                hostLoopback.setChecked(loopback);
                Toast.makeText(context, R.string.msg_ai_doctor_host_saved, Toast.LENGTH_SHORT)
                    .show();
                return true;
            });
        }

        Preference versionPin = findPreference("ai_version_pin");
        if (versionPin != null) {
            syncVersionSummary(context, versionPin);
            versionPin.setOnPreferenceClickListener(p -> {
                showVersionPinDialog(context, versionPin);
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
        refreshStatus(false);
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private void refreshStatus(boolean toastDone) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Preference status = findPreference("ai_status");
        if (status != null) {
            status.setSummary(R.string.summary_ai_doctor_status_loading);
        }
        mExecutor.execute(() -> {
            final AiSessionHelper.DoctorReport report =
                AiSessionHelper.collectDoctorReport(WorkflowHelper.AI_PREVIEW_PORT);
            mMainHandler.post(() -> applyReport(report, toastDone));
        });
    }

    private void applyReport(@NonNull AiSessionHelper.DoctorReport report, boolean toastDone) {
        Context context = getContext();
        if (context == null || !isAdded()) {
            return;
        }
        Preference status = findPreference("ai_status");
        if (status != null) {
            status.setSummary(formatReport(context, report));
        }
        if (toastDone) {
            Toast.makeText(context, R.string.msg_ai_doctor_checks_done, Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    static String formatReport(@NonNull Context context,
                               @NonNull AiSessionHelper.DoctorReport report) {
        String statusLabel;
        switch (report.status) {
            case READY:
                statusLabel = context.getString(R.string.status_ai_doctor_ready);
                break;
            case INSTALLED:
                statusLabel = context.getString(R.string.status_ai_doctor_installed);
                break;
            case MISSING:
            default:
                statusLabel = context.getString(R.string.status_ai_doctor_missing);
                break;
        }
        String binary = report.binaryPath != null
            ? report.binaryPath
            : context.getString(R.string.status_ai_doctor_binary_none);
        return context.getString(R.string.summary_ai_doctor_status,
            statusLabel,
            binary,
            report.shimPresent
                ? context.getString(R.string.status_ai_doctor_yes)
                : context.getString(R.string.status_ai_doctor_no),
            report.certPresent
                ? context.getString(R.string.status_ai_doctor_yes)
                : context.getString(R.string.status_ai_doctor_no),
            report.stubPresent
                ? context.getString(R.string.status_ai_doctor_stub_yes)
                : context.getString(R.string.status_ai_doctor_stub_no),
            report.infoUrl);
    }

    private void stopAiAsync() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        mExecutor.execute(() -> {
            final int killed = AiSessionHelper.stopAi(WorkflowHelper.AI_PREVIEW_PORT);
            mMainHandler.post(() -> {
                Context ctx = getContext();
                if (ctx == null || !isAdded()) {
                    return;
                }
                Toast.makeText(ctx,
                    killed > 0
                        ? R.string.msg_workflow_ai_stopped
                        : R.string.msg_workflow_ai_not_running,
                    Toast.LENGTH_SHORT).show();
                refreshStatus(false);
            });
        });
    }

    private void injectIntoTerminal(@NonNull String command, int toastRes) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(TermuxActivity.EXTRA_INJECT_SHELL_COMMAND, command);
        ActivityUtils.startActivity(context, intent);
        Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show();
    }

    private void showVersionPinDialog(@NonNull Context context, @NonNull Preference preference) {
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.hint_ai_doctor_version);
        input.setText(AiPrefs.getOpenCodeVersionPin(context));
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(context)
            .setTitle(R.string.title_ai_doctor_version)
            .setMessage(R.string.summary_ai_doctor_version)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                CharSequence text = input.getText();
                AiPrefs.setOpenCodeVersionPin(context,
                    text != null ? text.toString() : "");
                syncVersionSummary(context, preference);
                Toast.makeText(context, R.string.msg_ai_doctor_version_saved, Toast.LENGTH_SHORT)
                    .show();
            })
            .setNeutralButton(R.string.action_ai_doctor_clear_version, (dialog, which) -> {
                AiPrefs.setOpenCodeVersionPin(context, "");
                syncVersionSummary(context, preference);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void syncVersionSummary(@NonNull Context context, @NonNull Preference preference) {
        String pin = AiPrefs.getOpenCodeVersionPin(context);
        if (pin.isEmpty()) {
            preference.setSummary(R.string.summary_ai_doctor_version);
        } else {
            preference.setSummary(context.getString(R.string.summary_ai_doctor_version_pinned, pin));
        }
    }
}
