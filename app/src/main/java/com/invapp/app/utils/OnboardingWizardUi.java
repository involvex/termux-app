package com.invapp.app.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.invapp.R;
import com.invapp.app.TermuxActivity;
import com.invapp.app.activities.HelpActivity;
import com.invapp.app.activities.LocalhostPreviewActivity;
import com.invapp.app.activities.SettingsActivity;

/**
 * 4-step first-launch checklist (storage → repos → Preview → AI).
 * Never auto-downloads AI or scaffolds projects; only explains + optional actions.
 */
public final class OnboardingWizardUi {

    private OnboardingWizardUi() {}

    public static void show(@NonNull Activity activity) {
        showAtStep(activity, OnboardingPrefs.getStep(activity));
    }

    /** Reset prefs and show from step 0 (Help / Settings re-run). */
    public static void showFromStart(@NonNull Activity activity) {
        OnboardingPrefs.reset(activity);
        showAtStep(activity, OnboardingPrefs.STEP_STORAGE);
    }

    private static void showAtStep(@NonNull Activity activity, int step) {
        if (activity.isFinishing()) {
            return;
        }
        final int current = Math.max(0, Math.min(OnboardingPrefs.STEP_COUNT - 1, step));
        OnboardingPrefs.setStep(activity, current);

        String title = activity.getString(R.string.title_onboarding,
            current + 1, OnboardingPrefs.STEP_COUNT);
        String message;
        String positiveLabel;
        Runnable positiveAction;

        switch (current) {
            case OnboardingPrefs.STEP_STORAGE:
                message = activity.getString(R.string.msg_onboarding_storage);
                positiveLabel = activity.getString(R.string.action_onboarding_grant_storage);
                positiveAction = () -> {
                    if (activity instanceof TermuxActivity) {
                        ((TermuxActivity) activity).requestStoragePermission(false);
                    }
                    advance(activity, current);
                };
                break;
            case OnboardingPrefs.STEP_REPOS:
                message = activity.getString(R.string.msg_onboarding_repos);
                positiveLabel = activity.getString(R.string.action_onboarding_open_workflow_help);
                positiveAction = () -> {
                    activity.startActivity(HelpActivity.createIntent(activity, "workflow"));
                    advance(activity, current);
                };
                break;
            case OnboardingPrefs.STEP_PREVIEW:
                message = activity.getString(R.string.msg_onboarding_preview);
                positiveLabel = activity.getString(R.string.action_onboarding_open_preview);
                positiveAction = () -> {
                    activity.startActivity(new Intent(activity, LocalhostPreviewActivity.class));
                    advance(activity, current);
                };
                break;
            case OnboardingPrefs.STEP_AI:
            default:
                message = activity.getString(R.string.msg_onboarding_ai);
                positiveLabel = activity.getString(R.string.action_onboarding_open_ai_doctor);
                positiveAction = () -> {
                    activity.startActivity(SettingsActivity.createAiDoctorIntent(activity));
                    OnboardingPrefs.markCompleted(activity);
                    Toast.makeText(activity, R.string.msg_onboarding_done, Toast.LENGTH_SHORT)
                        .show();
                };
                break;
        }

        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveLabel, (d, w) -> positiveAction.run())
            .setNeutralButton(R.string.action_onboarding_next, (d, w) -> {
                if (current >= OnboardingPrefs.STEP_AI) {
                    OnboardingPrefs.markCompleted(activity);
                    Toast.makeText(activity, R.string.msg_onboarding_done, Toast.LENGTH_SHORT)
                        .show();
                } else {
                    advance(activity, current);
                }
            })
            .setNegativeButton(R.string.action_onboarding_skip, (d, w) -> {
                OnboardingPrefs.markSkipped(activity);
                Toast.makeText(activity, R.string.msg_onboarding_skipped, Toast.LENGTH_SHORT)
                    .show();
            })
            .setCancelable(true)
            .show();
    }

    private static void advance(@NonNull Activity activity, int current) {
        if (current >= OnboardingPrefs.STEP_AI) {
            OnboardingPrefs.markCompleted(activity);
            Toast.makeText(activity, R.string.msg_onboarding_done, Toast.LENGTH_SHORT).show();
            return;
        }
        int next = current + 1;
        OnboardingPrefs.setStep(activity, next);
        showAtStep(activity, next);
    }
}
