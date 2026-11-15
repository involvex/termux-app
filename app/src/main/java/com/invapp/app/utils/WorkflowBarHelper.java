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
    /** First-run setup menu (ivx-configure); catalog-only, not in the default bar. */
    public static final String ID_SETUP = "setup";
    /** Always last in the bar — opens overflow / customize. Not removable. */
    public static final String ID_MORE = "more";

    /** Catalog order used when filtering multi-choice results. */
    public static final String[] ALL_ACTION_IDS = {
        ID_PULL, ID_BUN_I, ID_BUN_DEV, ID_REPOS, ID_RUN, ID_CLONE, ID_NEW, ID_AI, ID_AI_STOP,
        ID_SETUP
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

    /**
     * Catalog order for checked flags (legacy / tests). Prefer
     * {@link #mergeCheckedPreservingOrder} when saving customize results so the
     * user's previous bar order is kept.
     */
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

    /**
     * Keep previously visible ids in their saved order; append newly checked
     * ids in catalog order; drop unchecked.
     */
    @NonNull
    public static List<String> mergeCheckedPreservingOrder(
        @NonNull boolean[] checked, @NonNull List<String> previousOrder) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String id : previousOrder) {
            int idx = catalogIndex(id);
            if (idx >= 0 && idx < checked.length && checked[idx]) {
                out.add(id);
            }
        }
        for (int i = 0; i < ALL_ACTION_IDS.length && i < checked.length; i++) {
            if (checked[i]) {
                out.add(ALL_ACTION_IDS[i]);
            }
        }
        if (out.isEmpty()) {
            return new ArrayList<>(Arrays.asList(DEFAULT_VISIBLE_IDS));
        }
        return new ArrayList<>(out);
    }

    /**
     * Move {@code id} one slot toward the front of {@code order} (no-op if
     * missing or already first). Returns a new list.
     */
    @NonNull
    public static List<String> moveIdUp(@NonNull List<String> order, @NonNull String id) {
        ArrayList<String> out = new ArrayList<>(order);
        int idx = out.indexOf(id);
        if (idx <= 0) {
            return out;
        }
        Collections.swap(out, idx, idx - 1);
        return out;
    }

    private static int catalogIndex(@Nullable String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < ALL_ACTION_IDS.length; i++) {
            if (ALL_ACTION_IDS[i].equals(id)) {
                return i;
            }
        }
        return -1;
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
