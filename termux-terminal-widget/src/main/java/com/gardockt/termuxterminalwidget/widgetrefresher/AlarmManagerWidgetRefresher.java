package com.gardockt.termuxterminalwidget.widgetrefresher;

import android.app.AlarmManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AlarmManagerWidgetRefresher implements WidgetRefresher {

    private static final String TAG = AlarmManagerWidgetRefresher.class.getSimpleName();

    private final AlarmManager alarmManager;
    private final Consumer<Integer> widgetRefreshFn;

    private final Map<Integer, AlarmManager.OnAlarmListener> widgetIdToOnAlarmListener = new HashMap<>();

    public AlarmManagerWidgetRefresher(@NonNull Context context, @NonNull Consumer<Integer> widgetRefreshFn) {
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.widgetRefreshFn = widgetRefreshFn;
    }

    @Override
    public void add(int widgetId, long intervalSecs) {
        Log.d(TAG, String.format("Registering widget %d with interval %ds", widgetId, intervalSecs));

        if (widgetIdToOnAlarmListener.containsKey(widgetId)) {
            Log.w(TAG, String.format("Widget %d already registered, ignoring", widgetId));
            return;
        }

        AlarmManager.OnAlarmListener onAlarmListener = () -> {
            AlarmManager.OnAlarmListener existingListener = widgetIdToOnAlarmListener.get(widgetId);
            if (existingListener == null) {
                Log.w(TAG, String.format("No OnAlarmListener found for widget ID %d, breaking loop", widgetId));
                return;
            }

            widgetRefreshFn.accept(widgetId);

            long nextFireTime = getNextFireTime(System.currentTimeMillis(), intervalSecs);
            setAlarm(existingListener, nextFireTime, widgetId);
        };

        widgetIdToOnAlarmListener.put(widgetId, onAlarmListener);
        setAlarm(onAlarmListener, 0, widgetId);
    }

    private void setAlarm(@NonNull AlarmManager.OnAlarmListener onAlarmListener, long fireTime, int widgetId) {
        try {
            alarmManager.setExact(AlarmManager.RTC, fireTime, null, onAlarmListener, null);
            Log.v(TAG, "Exact alarm set for widget " + widgetId);
        } catch (SecurityException ex) {
            alarmManager.set(AlarmManager.RTC, fireTime, null, onAlarmListener, null);
            Log.v(TAG, "Non-exact alarm set for widget " + widgetId);
        }
    }

    /**
     * Calculates fire time of the next alarm, so that the alarms are fired with given interval,
     * starting from given start time.
     * @param startTimeMillis Timestamp in milliseconds, at which the counting started.
     * @param intervalSecs Expected interval between alarms in seconds.
     * @return Timestamp in milliseconds, at which next alarm should fire.
     */
    private long getNextFireTime(long startTimeMillis, long intervalSecs) {
        long intervalMillis = intervalSecs * 1000;
        long currentTimeMillis = System.currentTimeMillis();

        // Fire happens when (currentTime - startTime) % interval == 0.

        long millisSinceLastInterval = (currentTimeMillis - startTimeMillis) % intervalMillis;
        long millisToNextInterval = intervalMillis - millisSinceLastInterval;
        return currentTimeMillis + millisToNextInterval;
    }

    @Override
    public void remove(int widgetId) {
        Log.d(TAG, String.format("Unregistering widget %d", widgetId));

        AlarmManager.OnAlarmListener onAlarmListener = widgetIdToOnAlarmListener.remove(widgetId);
        if (onAlarmListener != null) {
            alarmManager.cancel(onAlarmListener);
        }
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}
