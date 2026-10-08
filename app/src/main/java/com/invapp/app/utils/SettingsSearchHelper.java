package com.invapp.app.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.invapp.R;

import java.util.Locale;

/**
 * Keyword filter for View-based {@code PreferenceFragmentCompat} screens
 * (Ultra's Compose settings search, adapted: hits stay live, no-match shows a
 * disabled row instead of an empty card).
 */
public final class SettingsSearchHelper {

    /** Marker key for the programmatic no-match row. */
    public static final String KEY_SEARCH_EMPTY = "search_empty";

    /** Tag to avoid adding the header twice across view recreation. */
    private static final String TAG_SEARCH_HEADER = "invapp_search_header";

    private SettingsSearchHelper() {}

    /** Null-safe case-insensitive match on title/summary/key. Empty query matches all. */
    public static boolean matches(@Nullable CharSequence title,
                                  @Nullable CharSequence summary,
                                  @Nullable String key,
                                  @Nullable String query) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return true;
        }
        return contains(title, q) || contains(summary, q) || contains(key, q);
    }

    /**
     * Insert a search {@code EditText} above the preference list. Safe to call
     * from {@code onViewCreated} (requires the fragment view to exist); call
     * after {@code setPreferencesFromResource} so the screen is present.
     */
    public static void attachSearchHeader(@NonNull PreferenceFragmentCompat fragment) {
        View root = fragment.getView();
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        if (group.findViewWithTag(TAG_SEARCH_HEADER) != null) {
            return;
        }
        if (fragment.getContext() == null) {
            return;
        }
        EditText search = new EditText(fragment.getContext());
        search.setTag(TAG_SEARCH_HEADER);
        search.setSingleLine(true);
        search.setHint(R.string.hint_search_settings);
        int pad = (int) (16 * fragment.getResources().getDisplayMetrics().density);
        search.setPadding(pad, pad, pad, pad);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                PreferenceScreen screen = fragment.getPreferenceScreen();
                if (screen != null) {
                    applyFilter(screen, s != null ? s.toString() : "");
                }
            }
        });
        group.addView(search, 0);
    }

    /**
     * Toggle {@code setVisible} across the screen (recursing into groups).
     *
     * @return number of visible content prefs (excluding the empty row).
     */
    public static int applyFilter(@NonNull PreferenceGroup screen, @Nullable String query) {
        ensureEmptyRow(screen);
        String q = normalize(query);
        int visible = applyFilterRecursive(screen, q);
        Preference empty = screen.findPreference(KEY_SEARCH_EMPTY);
        if (empty != null) {
            empty.setVisible(!q.isEmpty() && visible == 0);
        }
        return visible;
    }

    private static int applyFilterRecursive(@NonNull PreferenceGroup group, @NonNull String q) {
        int visible = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref == null || KEY_SEARCH_EMPTY.equals(pref.getKey())) {
                continue;
            }
            if (pref instanceof PreferenceGroup) {
                int childVisible = applyFilterRecursive((PreferenceGroup) pref, q);
                pref.setVisible(q.isEmpty() || childVisible > 0);
                if (pref.isVisible() && !(pref instanceof PreferenceScreen)) {
                    visible++;
                } else if (pref.isVisible()) {
                    visible += childVisible;
                }
            } else {
                boolean hit = matches(pref.getTitle(), pref.getSummary(), pref.getKey(), q);
                pref.setVisible(hit);
                if (hit) {
                    visible++;
                }
            }
        }
        return visible;
    }

    private static void ensureEmptyRow(@NonNull PreferenceGroup screen) {
        Preference existing = screen.findPreference(KEY_SEARCH_EMPTY);
        if (existing != null) {
            return;
        }
        Preference empty = new Preference(screen.getContext());
        empty.setKey(KEY_SEARCH_EMPTY);
        empty.setTitle(R.string.msg_settings_no_match);
        empty.setSelectable(false);
        empty.setPersistent(false);
        empty.setVisible(false);
        screen.addPreference(empty);
    }

    @NonNull
    static String normalize(@Nullable String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(@Nullable CharSequence haystack, @NonNull String needle) {
        if (haystack == null || needle.isEmpty()) {
            return false;
        }
        return haystack.toString().toLowerCase(Locale.ROOT).contains(needle);
    }
}
