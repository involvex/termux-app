package com.gardockt.termuxterminalwidget.mainwidget.refreshers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.gardockt.termuxterminalwidget.mainwidget.MainWidget;
import com.gardockt.termuxterminalwidget.mainwidget.MainWidgetUpdateWorker;
import com.gardockt.termuxterminalwidget.widgetrefresher.WidgetRefresher;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class WorkMainWidgetRefresher implements WidgetRefresher {

    private final WorkManager workManager;

    public WorkMainWidgetRefresher(@NonNull Context context) {
        this.workManager = WorkManager.getInstance(context);
    }

    @Override
    public void add(int widgetId, long intervalSecs) {
        String updateJobTag = getUpdateJobTag(widgetId);

        // check if widget is already configured, if it is then return
        try {
            List<WorkInfo> workInfoList = workManager.getWorkInfosByTag(updateJobTag).get();
            if (workInfoList.stream().anyMatch(work -> !work.getState().isFinished())) {
                return;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        Data workData = new Data.Builder()
                .putInt(MainWidget.EXTRA_WIDGET_ID, widgetId)
                .build();
        WorkRequest workRequest = new PeriodicWorkRequest.Builder(MainWidgetUpdateWorker.class, intervalSecs, TimeUnit.SECONDS)
                .setInputData(workData)
                .addTag(updateJobTag)
                .build();
        workManager.enqueue(workRequest);
    }

    @Override
    public void remove(int widgetId) {
        workManager.cancelAllWorkByTag(getUpdateJobTag(widgetId));
    }

    @NonNull
    public static String getUpdateJobTag(int widgetId) {
        return Integer.toString(widgetId);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
