package com.invapp.app.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;
import com.invapp.R;
import com.invapp.app.activities.LocalhostPreviewActivity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches for newly opened preferred localhost ports and offers Preview.
 */
public final class PreferredPortWatcher {

    private static final long INTERVAL_MS = 4000L;

    private final Activity mActivity;
    private final View mAnchor;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Set<Integer> mKnownPorts = new HashSet<>();
    private final AtomicBoolean mStarted = new AtomicBoolean(false);
    private boolean mSeeded;

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
    }

    public void start() {
        if (!mStarted.compareAndSet(false, true)) {
            return;
        }
        mHandler.post(mTick);
    }

    public void stop() {
        mStarted.set(false);
        mHandler.removeCallbacks(mTick);
    }

    public void shutdown() {
        stop();
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
        mKnownPorts.clear();
        mKnownPorts.addAll(ports);
        if (notifyPort != null) {
            showOffer(notifyPort);
        }
    }

    private boolean isPreferred(int port) {
        return LocalhostPortScanner.isPreferredPort(mActivity, port);
    }

    private void showOffer(final int port) {
        String msg = mActivity.getString(R.string.msg_preview_port_detected, port);
        Snackbar.make(mAnchor, msg, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_open_preview, v -> {
                Intent intent = LocalhostPreviewActivity.createIntent(mActivity, port);
                mActivity.startActivity(intent);
            })
            .show();
    }
}
