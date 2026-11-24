package com.invapp.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.invapp.R;
import com.invapp.app.TermuxActivity;
import com.invapp.app.utils.ThemePaletteHelper;
import com.invapp.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TerminalViewPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalViewPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey);

        ListPreference themePref = findPreference("terminal_theme");
        if (themePref != null) {
            themePref.setValue(ThemePaletteHelper.getSelectedThemeId(context));
            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String id = String.valueOf(newValue);
                applyThemeWithConfirm(context, id);
                return true;
            });
        }

        Preference resetPref = findPreference("terminal_theme_reset");
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(preference -> {
                String err = ThemePaletteHelper.resetToHacker(context);
                if (err != null) {
                    Toast.makeText(context, getString(R.string.error_theme_apply, err),
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, R.string.msg_theme_applied, Toast.LENGTH_SHORT).show();
                    if (themePref != null) {
                        themePref.setValue(ThemePaletteHelper.ID_HACKER);
                    }
                }
                return true;
            });
        }

        Preference fontPref = findPreference("terminal_font_size");
        if (fontPref != null) {
            refreshFontSummary(fontPref, context);
            fontPref.setOnPreferenceClickListener(preference -> {
                showFontSizeDialog(context, fontPref);
                return true;
            });
        }
    }

    private void applyThemeWithConfirm(@NonNull Context context, @NonNull String themeId) {
        String err = ThemePaletteHelper.applyTheme(context, themeId, false);
        if (err != null && err.contains("no InvApp mark")) {
            new AlertDialog.Builder(context)
                .setMessage(R.string.msg_theme_custom_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String forced = ThemePaletteHelper.applyTheme(context, themeId, true);
                    toastThemeResult(context, forced);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
        toastThemeResult(context, err);
    }

    private void toastThemeResult(@NonNull Context context, @Nullable String err) {
        if (err != null) {
            Toast.makeText(context, getString(R.string.error_theme_apply, err),
                Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, R.string.msg_theme_applied, Toast.LENGTH_SHORT).show();
        }
    }

    private void showFontSizeDialog(@NonNull Context context, @NonNull Preference fontPref) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, true);
        if (prefs == null) {
            return;
        }
        new AlertDialog.Builder(context)
            .setTitle(R.string.termux_terminal_view_font_size_title)
            .setMessage(getString(R.string.termux_terminal_view_font_size_summary,
                prefs.getFontSize()))
            .setPositiveButton(R.string.termux_terminal_view_font_larger, (d, w) -> {
                prefs.changeFontSize(true);
                refreshFontSummary(fontPref, context);
                TermuxActivity.updateTermuxActivityStyling(context, false);
            })
            .setNeutralButton(R.string.termux_terminal_view_font_smaller, (d, w) -> {
                prefs.changeFontSize(false);
                refreshFontSummary(fontPref, context);
                TermuxActivity.updateTermuxActivityStyling(context, false);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void refreshFontSummary(@NonNull Preference fontPref, @NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, true);
        int size = prefs != null ? prefs.getFontSize() : 0;
        fontPref.setSummary(getString(R.string.termux_terminal_view_font_size_summary, size));
    }

}

class TerminalViewPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private final TermuxAppSharedPreferences mPreferences;

    private static TerminalViewPreferencesDataStore mInstance;

    private TerminalViewPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TerminalViewPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalViewPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null) return;
        if (key == null) return;

        switch (key) {
            case "terminal_margin_adjustment":
                    mPreferences.setTerminalMarginAdjustment(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null) return false;

        switch (key) {
            case "terminal_margin_adjustment":
                return mPreferences.isTerminalMarginAdjustmentEnabled();
            default:
                return false;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (key == null || value == null) {
            return;
        }
        if ("terminal_theme".equals(key)) {
            mContext.getSharedPreferences("invapp_theme_palette", Context.MODE_PRIVATE)
                .edit()
                .putString("theme_id", value)
                .apply();
        }
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        if ("terminal_theme".equals(key)) {
            return ThemePaletteHelper.getSelectedThemeId(mContext);
        }
        return defValue;
    }

}
