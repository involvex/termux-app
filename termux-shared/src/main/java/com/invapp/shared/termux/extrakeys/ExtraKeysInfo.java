package com.invapp.shared.termux.extrakeys;

import androidx.annotation.NonNull;

import com.invapp.shared.termux.extrakeys.ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS;
import com.invapp.shared.termux.terminal.io.TerminalExtraKeys;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Info needed by {@link ExtraKeysView} to display extra keys.
 *
 * <p>Legacy format (single page): JSON array of <em>rows</em>:
 * {@code [['ESC','TAB'], ['CTRL','ALT']]}.
 *
 * <p>Multi-page format: JSON array of <em>pages</em>, each page an array of rows:
 * {@code [[['ESC','TAB'],['CTRL','ALT']], [[{key:'PULL',display:'pull'},'DRAWER']]]}.
 *
 * <p>Detection: if the first element of the outer array is a JSONArray whose first
 * element is also a JSONArray, the value is treated as multi-page.
 *
 * @see ExtraKeysView
 * @see TerminalExtraKeys
 */
public class ExtraKeysInfo {

    private final ExtraKeyButton[][][] mPages;

    public ExtraKeysInfo(@NonNull String propertiesInfo, String style,
                         @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        mPages = initPages(propertiesInfo, getCharDisplayMapForStyle(style), extraKeyAliasMap);
    }

    public ExtraKeysInfo(@NonNull String propertiesInfo,
                         @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap,
                         @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        mPages = initPages(propertiesInfo, extraKeyDisplayMap, extraKeyAliasMap);
    }

    @NonNull
    private static ExtraKeyButton[][][] initPages(@NonNull String propertiesInfo,
                                                  @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap,
                                                  @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        JSONArray arr = new JSONArray(propertiesInfo);
        if (arr.length() == 0) {
            return new ExtraKeyButton[][][] { new ExtraKeyButton[0][] };
        }
        if (isMultiPage(arr)) {
            ExtraKeyButton[][][] pages = new ExtraKeyButton[arr.length()][][];
            for (int p = 0; p < arr.length(); p++) {
                pages[p] = parseMatrix(arr.getJSONArray(p), extraKeyDisplayMap, extraKeyAliasMap);
            }
            return pages;
        }
        return new ExtraKeyButton[][][] {
            parseMatrix(arr, extraKeyDisplayMap, extraKeyAliasMap)
        };
    }

    /**
     * Multi-page when outer[0][0] is itself a JSONArray (a row inside a page).
     */
    static boolean isMultiPage(@NonNull JSONArray arr) throws JSONException {
        if (arr.length() == 0) {
            return false;
        }
        Object first = arr.get(0);
        if (!(first instanceof JSONArray)) {
            return false;
        }
        JSONArray pageOrRow = (JSONArray) first;
        if (pageOrRow.length() == 0) {
            return false;
        }
        return pageOrRow.get(0) instanceof JSONArray;
    }

    /** Visible for tests. */
    public static boolean isMultiPageJson(@NonNull String propertiesInfo) throws JSONException {
        return isMultiPage(new JSONArray(propertiesInfo));
    }

    @NonNull
    private static ExtraKeyButton[][] parseMatrix(@NonNull JSONArray arr,
                                                  @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap,
                                                  @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        Object[][] matrix = new Object[arr.length()][];
        for (int i = 0; i < arr.length(); i++) {
            JSONArray line = arr.getJSONArray(i);
            matrix[i] = new Object[line.length()];
            for (int j = 0; j < line.length(); j++) {
                matrix[i][j] = line.get(j);
            }
        }

        ExtraKeyButton[][] buttons = new ExtraKeyButton[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            buttons[i] = new ExtraKeyButton[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                JSONObject jobject = normalizeKeyConfig(matrix[i][j]);
                ExtraKeyButton button;
                if (!jobject.has(ExtraKeyButton.KEY_POPUP)) {
                    button = new ExtraKeyButton(jobject, extraKeyDisplayMap, extraKeyAliasMap);
                } else {
                    JSONObject popupJobject = normalizeKeyConfig(jobject.get(ExtraKeyButton.KEY_POPUP));
                    ExtraKeyButton popup = new ExtraKeyButton(popupJobject, extraKeyDisplayMap, extraKeyAliasMap);
                    button = new ExtraKeyButton(jobject, popup, extraKeyDisplayMap, extraKeyAliasMap);
                }
                buttons[i][j] = button;
            }
        }
        return buttons;
    }

    private static JSONObject normalizeKeyConfig(Object key) throws JSONException {
        if (key instanceof String) {
            JSONObject jobject = new JSONObject();
            jobject.put(ExtraKeyButton.KEY_KEY_NAME, key);
            return jobject;
        } else if (key instanceof JSONObject) {
            return (JSONObject) key;
        }
        throw new JSONException("An key in the extra-key matrix must be a string or an object");
    }

    /** First page matrix (backward compatible). */
    public ExtraKeyButton[][] getMatrix() {
        return getMatrix(0);
    }

    @NonNull
    public ExtraKeyButton[][] getMatrix(int page) {
        if (page < 0 || page >= mPages.length) {
            return mPages[0];
        }
        return mPages[page];
    }

    public int getPageCount() {
        return mPages.length;
    }

    /** Max row count across all pages (for toolbar height). */
    public int getMaxRowCount() {
        int max = 0;
        for (ExtraKeyButton[][] page : mPages) {
            if (page != null && page.length > max) {
                max = page.length;
            }
        }
        return max;
    }

    @NonNull
    public static ExtraKeysConstants.ExtraKeyDisplayMap getCharDisplayMapForStyle(String style) {
        switch (style) {
            case "arrows-only":
                return EXTRA_KEY_DISPLAY_MAPS.ARROWS_ONLY_CHAR_DISPLAY;
            case "arrows-all":
                return EXTRA_KEY_DISPLAY_MAPS.LOTS_OF_ARROWS_CHAR_DISPLAY;
            case "all":
                return EXTRA_KEY_DISPLAY_MAPS.FULL_ISO_CHAR_DISPLAY;
            case "none":
                return new ExtraKeysConstants.ExtraKeyDisplayMap();
            default:
                return EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY;
        }
    }

}
