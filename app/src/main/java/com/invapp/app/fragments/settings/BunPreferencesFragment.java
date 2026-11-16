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
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.invapp.R;
import com.invapp.app.TermuxActivity;
import com.invapp.app.TermuxBunInstaller;
import com.invapp.app.utils.BunVersionHelper;
import com.invapp.app.utils.SettingsSearchHelper;
import com.invapp.shared.activity.ActivityUtils;
import com.invapp.shared.interact.ShareUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings → Bun: bundled stamp vs expected APK pin; update via Releases only.
 */
@Keep
public class BunPreferencesFragment extends PreferenceFragmentCompat {

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

        setPreferencesFromResource(R.xml.termux_bun_preferences, rootKey);

        Preference refresh = findPreference("bun_refresh");
        if (refresh != null) {
            refresh.setOnPreferenceClickListener(p -> {
                refreshStatus(true);
                return true;
            });
        }

        Preference doctor = findPreference("bun_doctor");
        if (doctor != null) {
            doctor.setOnPreferenceClickListener(p -> {
                injectIntoTerminal("bun-doctor\n", R.string.msg_bun_doctor_sent);
                return true;
            });
        }

        Preference upgradeCheck = findPreference("bun_upgrade_check");
        if (upgradeCheck != null) {
            upgradeCheck.setOnPreferenceClickListener(p -> {
                injectIntoTerminal("bun-upgrade --check\n", R.string.msg_bun_upgrade_check_sent);
                return true;
            });
        }

        Preference updateApk = findPreference("bun_update_apk");
        if (updateApk != null) {
            updateApk.setOnPreferenceClickListener(p -> {
                ShareUtils.openUrl(context, BunVersionHelper.RELEASES_URL);
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
        Preference status = findPreference("bun_status");
        if (status != null) {
            status.setSummary(R.string.summary_bun_status_loading);
        }
        mExecutor.execute(() -> {
            final BunVersionHelper.Report report = BunVersionHelper.collect();
            mMainHandler.post(() -> applyReport(report, toastDone));
        });
    }

    private void applyReport(@NonNull BunVersionHelper.Report report, boolean toastDone) {
        Context context = getContext();
        if (context == null || !isAdded()) {
            return;
        }
        Preference status = findPreference("bun_status");
        if (status != null) {
            status.setSummary(formatReport(context, report));
        }
        if (toastDone) {
            Toast.makeText(context, R.string.msg_bun_checks_done, Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    static String formatReport(@NonNull Context context,
                               @NonNull BunVersionHelper.Report report) {
        String stamped = report.stamped != null
            ? report.stamped
            : context.getString(R.string.status_bun_unknown);
        String running = report.running != null
            ? report.running
            : context.getString(R.string.status_bun_missing);
        String libexec = report.libexecPresent
            ? context.getString(R.string.status_ai_doctor_yes)
            : context.getString(R.string.status_ai_doctor_no);
        String match = report.mismatch
            ? context.getString(R.string.status_bun_mismatch)
            : context.getString(R.string.status_bun_ok);
        return context.getString(R.string.summary_bun_status,
            TermuxBunInstaller.BUNDLED_BUN_VERSION,
            stamped,
            running,
            libexec,
            match);
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
}
