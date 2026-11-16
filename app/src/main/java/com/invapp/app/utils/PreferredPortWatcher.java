package com.invapp.app.utils;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.material.snackbar.Snackbar;
import com.invapp.R;
import com.invapp.app.activities.LocalhostPreviewActivity;
import com.invapp.shared.notification.NotificationUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches for newly opened preferred localhost ports and offers Preview
 * (Snackbar in foreground, notification when backgrounded).
 */
public final class PreferredPortWatcher {

    private static final long INTERVAL_MS = 4000L;
    static final String CHANNEL_ID = "invapp_preview_ports";

    private final Activity mActivity;
    private final View mAnchor;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Set<Integer> mKnownPorts = new HashSet<>();
    private final Set<Integer> mNotifiedPorts = new HashSet<>();
    private final AtomicBoolean mStarted = new AtomicBoolean(false);
    private boolean mSeeded;
    private boolean mActivityVisible;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (!mStarted.get()) {
                return;
            }
            mExecutor.execute(() -> {
                final List<Integer> ports = LocalhostPortScanner.scanListeningPorts(mActivity);
                mHandler.post(() -> onScanResult(ports));
            });
            mHandler.postDelayed(this, INTERVAL_MS);
        }
    };

    public PreferredPortWatcher(@NonNull Activity activity, @NonNull View anchor) {
        mActivity = activity;
        mAnchor = anchor;
        ensureChannel(activity.getApplicationContext());
    }

    public void start() {
        mActivityVisible = true;
        if (mStarted.compareAndSet(false, true)) {
            mHandler.post(mTick);
        }
    }

    /**
     * Keep scanning in the background for notifications; hide Snackbars.
     */
    public void enterBackground() {
        mActivityVisible = false;
        if (!PreviewPortPrefs.isNotifyPreferredPorts(mActivity)) {
            stop();
            return;
        }
        if (mStarted.compareAndSet(false, true)) {
            mHandler.post(mTick);
        }
    }

    public void stop() {
        mStarted.set(false);
        mHandler.removeCallbacks(mTick);
    }

    public void shutdown() {
        stop();
        clearAllNotifications();
        mExecutor.shutdownNow();
    }

    private void onScanResult(@NonNull List<Integer> ports) {
        if (mActivity.isFinishing()) {
            return;
        }
        if (!mSeeded) {
            mKnownPorts.addAll(ports);
            mSeeded = true;
            return;
        }
        Integer notifyPort = null;
        for (Integer port : ports) {
            if (!mKnownPorts.contains(port) && isPreferred(port)) {
                notifyPort = port;
                break;
            }
        }
        Set<Integer> previous = new HashSet<>(mKnownPorts);
        mKnownPorts.clear();
        mKnownPorts.addAll(ports);

        for (Integer was : previous) {
            if (!mKnownPorts.contains(was) && mNotifiedPorts.remove(was)) {
                cancelNotification(was);
            }
        }

        if (notifyPort != null) {
            showOffer(notifyPort);
        }
    }

    private boolean isPreferred(int port) {
        return LocalhostPortScanner.isPreferredPort(mActivity, port);
    }

    private void showOffer(final int port) {
        if (mActivityVisible) {
            String msg = mActivity.getString(R.string.msg_preview_port_detected, port);
            Snackbar.make(mAnchor, msg, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_open_preview, v -> {
                    Intent intent = LocalhostPreviewActivity.createIntent(mActivity, port);
                    mActivity.startActivity(intent);
                    cancelNotification(port);
                })
                .show();
            return;
        }
        if (!PreviewPortPrefs.isNotifyPreferredPorts(mActivity)) {
            return;
        }
        postNotification(port);
    }

    private void postNotification(int port) {
        Context app = mActivity.getApplicationContext();
        ensureChannel(app);
        Intent intent = LocalhostPreviewActivity.createIntent(app, port);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(app, notificationId(port), intent, flags);

        Notification notification = new NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(app.getString(R.string.notification_preview_port_title, port))
            .setContentText(app.getString(R.string.notification_preview_port_text, port))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build();

        NotificationManager nm = NotificationUtils.getNotificationManager(app);
        if (nm != null) {
            nm.notify(notificationId(port), notification);
            mNotifiedPorts.add(port);
        }
    }

    private void cancelNotification(int port) {
        cancelPortNotification(mActivity, port);
        mNotifiedPorts.remove(port);
    }

    /** Clear a preferred-port notification (e.g. when Preview opens that port). */
    public static void cancelPortNotification(@NonNull Context context, int port) {
        NotificationManager nm = NotificationUtils.getNotificationManager(
            context.getApplicationContext());
        if (nm != null) {
            nm.cancel(notificationId(port));
        }
    }

    private void clearAllNotifications() {
        NotificationManager nm = NotificationUtils.getNotificationManager(
            mActivity.getApplicationContext());
        if (nm == null) {
            return;
        }
        for (Integer port : new HashSet<>(mNotifiedPorts)) {
            nm.cancel(notificationId(port));
        }
        mNotifiedPorts.clear();
    }

    static int notificationId(int port) {
        return 0x1F0000 + (port & 0xFFFF);
    }

    static void ensureChannel(@NonNull Context context) {
        NotificationUtils.setupNotificationChannel(
            context,
            CHANNEL_ID,
            context.getString(R.string.notification_channel_preview_ports),
            NotificationManager.IMPORTANCE_DEFAULT);
    }
}
