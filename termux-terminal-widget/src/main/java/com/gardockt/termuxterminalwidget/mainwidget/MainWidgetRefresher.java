package com.gardockt.termuxterminalwidget.mainwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gardockt.termuxterminalwidget.GlobalPreferences;
import com.gardockt.termuxterminalwidget.GlobalPreferencesUtils;
import com.gardockt.termuxterminalwidget.mainwidget.refreshers.WorkMainWidgetRefresher;
import com.gardockt.termuxterminalwidget.widgetrefresher.AlarmManagerWidgetRefresher;
import com.gardockt.termuxterminalwidget.widgetrefresher.WidgetRefresher;

/**
 * This class is NOT thread-safe - just don't add/remove a widget while changing
 * {@link WidgetRefresher} implementation, and you should be fine, though.
 */
public class MainWidgetRefresher {

    private final static String TAG = MainWidgetRefresher.class.getSimpleName();

    private static WidgetRefresher refresher = null;

    @NonNull
    public static WidgetRefresher getInstance(@NonNull Context context) {
        if (refresher == null) {
            refresher = getNewInstance(context);
        }
        return refresher;
    }

    /**
     * @return New instance of {@link WidgetRefresher}, with implementation depending on current
     * configuration.
     */
    @NonNull
    private static WidgetRefresher getNewInstance(@NonNull Context context) {
        GlobalPreferences preferences = GlobalPreferencesUtils.get(context);
        if (preferences.isAlarmManagerBackendEnabled()) {
            Log.v(TAG, "getNewInstance is returning AlarmManagerWidgetRefresher");
            return new AlarmManagerWidgetRefresher(context, (widgetId) -> refreshWidget(context, widgetId));
        } else {
            Log.v(TAG, "getNewInstance is returning WorkMainWidgetRefresher");
            return new WorkMainWidgetRefresher(context);
        }
    }

    private static void refreshWidget(@NonNull Context context, int widgetId) {
        MainWidget.updateWidget(context, AppWidgetManager.getInstance(context), widgetId, true);
    }

    /**
     * Changes currently used {@link WidgetRefresher} implementation, with new implementation
     * depending on current configuration. All managed widgets are moved from the old instance to
     * the new one.
     */
    public static void refreshInstance(@NonNull Context context) {
        WidgetRefresher newInstance = getNewInstance(context);
        replaceInstance(context, newInstance);
    }

    /**
     * Replaces currently used {@link WidgetRefresher} instance with the one passed as an argument,
     * moving all managed widgets from the old instance to the new one.
     */
    private static void replaceInstance(@NonNull Context context, @NonNull WidgetRefresher newInstance) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, MainWidget.class));

        for (int widgetId : widgetIds) {
            MainWidgetPreferences preferences = MainWidgetPreferencesManager.load(context, widgetId);
            newInstance.add(widgetId, preferences.getRefreshIntervalSecs());
            if (refresher != null) {
                refresher.remove(widgetId);
            }
        }

        refresher = newInstance;
    }
}
