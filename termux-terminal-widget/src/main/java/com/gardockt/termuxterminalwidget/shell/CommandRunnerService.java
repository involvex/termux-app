package com.gardockt.termuxterminalwidget.shell;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.gardockt.termuxterminalwidget.R;
import com.gardockt.termuxterminalwidget.util.TriConsumer;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

// Rationale:
// As a result of battery optimization in Android 8+, without this service running in foreground,
// the app runs in background mode, and after a few minutes of running it is impossible to start new
// services, and therefore run Termux commands.
// https://developer.android.com/about/versions/oreo/background

@RequiresApi(api = Build.VERSION_CODES.O)
public class CommandRunnerService extends Service {

    private final CommandRunnerServiceBinder binder = new CommandRunnerServiceBinder();

    private final String NOTIFICATION_CHANNEL_ID = "command_runner_service";
    private final int NOTIFICATION_ID = 1;

    private static boolean running = false;

    /**
     * Emits an object after the service has started.
     */
    private static final BehaviorSubject<Object> serviceStarted = BehaviorSubject.create();

    public void runCommand(@NonNull String command, @NonNull TriConsumer<Integer, String, String> onCommandFinished) {
        CommandRunner.runCommand(this, command, onCommandFinished);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.command_runner_service_notification),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(notificationChannel);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_is_running))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        serviceStarted.onNext(new Object());

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * @return Observable which emits an object after the service has started.
     */
    public static Observable<Object> getServiceStartedObservable() {
        return serviceStarted;
    }

    public class CommandRunnerServiceBinder extends Binder {
        public CommandRunnerService getService() {
            return CommandRunnerService.this;
        }
    }

}
