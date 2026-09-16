package com.invapp.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Preference-backed order of drawer workflow quick-action buttons.
 *
 * <p>Default bar is intentionally short (fat-finger friendly). Overflow actions
 * stay available via the {@code more} button; users can pick which ids appear
 * on the scroll row.
 */
public final class WorkflowBarHelper {

    public static final String ID_PULL = "pull";
    public static final String ID_BUN_I = "bun_i";
    public static final String ID_BUN_DEV = "bun_dev";
    public static final String ID_REPOS = "repos";
    public static final String ID_RUN = "run";
    public static final String ID_CLONE = "clone";
    public static final String ID_NEW = "new";
    public static final String ID_AI = "ai";
    public static final String ID_AI_STOP = "ai_stop";
    /** Always last in the bar — opens overflow / customize. Not removable. */
    public static final String ID_MORE = "more";

    /** Catalog order used when filtering multi-choice results. */
    public static final String[] ALL_ACTION_IDS = {
        ID_PULL, ID_BUN_I, ID_BUN_DEV, ID_REPOS, ID_RUN, ID_CLONE, ID_NEW, ID_AI, ID_AI_STOP
    };

    /** Roomier default: everyday git/bun + clone/new/AI. Run / Stop AI via More. */
    public static final String[] DEFAULT_VISIBLE_IDS = {
        ID_PULL, ID_BUN_I, ID_BUN_DEV, ID_REPOS, ID_CLONE, ID_NEW, ID_AI
    };

    private static final String PREFS = "invapp_workflow_bar";
    private static final String KEY_ORDER = "visible_order";
    private static final String SEP = ",";

    private WorkflowBarHelper() {}

    @NonNull
    public static List<String> getVisibleIds(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ORDER, null);
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>(Arrays.asList(DEFAULT_VISIBLE_IDS));
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : raw.split(SEP)) {
            String id = part.trim();
            if (id.isEmpty() || ID_MORE.equals(id)) {
                continue;
            }
            if (!isKnownAction(id) || !seen.add(id)) {
                continue;
            }
            out.add(id);
        }
        if (out.isEmpty()) {
            return new ArrayList<>(Arrays.asList(DEFAULT_VISIBLE_IDS));
        }
        return out;
    }

    public static void setVisibleIds(@NonNull Context context, @NonNull List<String> ids) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isEmpty() || ID_MORE.equals(id)) {
                continue;
            }
            if (isKnownAction(id)) {
                seen.add(id);
            }
        }
        if (seen.isEmpty()) {
            seen.addAll(Arrays.asList(DEFAULT_VISIBLE_IDS));
        }
        StringBuilder sb = new StringBuilder();
        for (String id : seen) {
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(id);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, sb.toString())
            .apply();
    }

    public static void resetToDefault(@NonNull Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ORDER)
            .apply();
    }

    public static boolean isKnownAction(@Nullable String id) {
        if (id == null) {
            return false;
        }
        for (String known : ALL_ACTION_IDS) {
            if (known.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static List<String> filterToCatalogOrder(@NonNull boolean[] checked) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ALL_ACTION_IDS.length && i < checked.length; i++) {
            if (checked[i]) {
                out.add(ALL_ACTION_IDS[i]);
            }
        }
        return out.isEmpty() ? Arrays.asList(DEFAULT_VISIBLE_IDS) : out;
    }

    @NonNull
    public static boolean[] checkedFlags(@NonNull List<String> visible) {
        boolean[] flags = new boolean[ALL_ACTION_IDS.length];
        for (int i = 0; i < ALL_ACTION_IDS.length; i++) {
            flags[i] = visible.contains(ALL_ACTION_IDS[i]);
        }
        return flags;
    }

    @NonNull
    public static List<String> labelKeys() {
        return Collections.unmodifiableList(Arrays.asList(ALL_ACTION_IDS));
    }
}
