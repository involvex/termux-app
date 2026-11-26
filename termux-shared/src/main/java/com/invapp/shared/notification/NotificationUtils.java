package com.invapp.shared.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.Nullable;

import com.invapp.shared.logger.Logger;

public class NotificationUtils {

    /** Do not show notification */
    public static final int NOTIFICATION_MODE_NONE = 0;
    /** Show notification without sound, vibration or lights */
    public static final int NOTIFICATION_MODE_SILENT = 1;
    /** Show notification with sound */
    public static final int NOTIFICATION_MODE_SOUND = 2;
    /** Show notification with vibration */
    public static final int NOTIFICATION_MODE_VIBRATE = 3;
    /** Show notification with lights */
    public static final int NOTIFICATION_MODE_LIGHTS = 4;
    /** Show notification with sound and vibration */
    public static final int NOTIFICATION_MODE_SOUND_AND_VIBRATE = 5;
    /** Show notification with sound and lights */
    public static final int NOTIFICATION_MODE_SOUND_AND_LIGHTS = 6;
    /** Show notification with vibration and lights */
    public static final int NOTIFICATION_MODE_VIBRATE_AND_LIGHTS = 7;
    /** Show notification with sound, vibration and lights */
    public static final int NOTIFICATION_MODE_ALL = 8;

    private static final String LOG_TAG = "NotificationUtils";

    /**
     * Get the {@link NotificationManager}.
     *
     * @param context The {@link Context} for operations.
     * @return Returns the {@link NotificationManager}.
     */
    @Nullable
    public static NotificationManager getNotificationManager(final Context context) {
        if (context == null) return null;
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Get {@link Notification.Builder}.
     *
     * @param context The {@link Context} for operations.
     * @param title The title for the notification.
     * @param channelId The channel id for the notification.
     * @param priority The priority for the notification.
     * @param notificationText The second line text of the notification.
     * @param notificationBigText The full text of the notification that may optionally be styled.
     * @param contentIntent The {@link PendingIntent} which should be sent when notification is clicked.
     * @param deleteIntent The {@link PendingIntent} which should be sent when notification is deleted.
     * @param notificationMode The notification mode. It must be one of {@code NotificationUtils.NOTIFICATION_MODE_*}.
     *                         The builder returned will be {@code null} if {@link #NOTIFICATION_MODE_NONE}
     *                         is passed. That case should ideally be handled before calling this function.
     * @return Returns the {@link Notification.Builder}.
     */
    @Nullable
    public static Notification.Builder geNotificationBuilder(
        final Context context, final String channelId, final int priority, final CharSequence title,
        final CharSequence notificationText, final CharSequence notificationBigText,
        final PendingIntent contentIntent, final PendingIntent deleteIntent, final int notificationMode) {
        if (context == null) return null;
        Notification.Builder builder = new Notification.Builder(context);
        builder.setContentTitle(title);
        builder.setContentText(notificationText);
        if (notificationBigText != null) {
            builder.setStyle(new Notification.BigTextStyle().bigText(notificationBigText));
        }
        builder.setContentIntent(contentIntent);
        builder.setDeleteIntent(deleteIntent);

        builder.setPriority(priority);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setChannelId(channelId);

        builder = setNotificationDefaults(builder, notificationMode);

        return builder;
    }

    /**
     * Setup the notification channel if Android version is greater than or equal to
     * {@link Build.VERSION_CODES#O}. Sound / vibrate / lights stay silent (foreground-service style).
     *
     * @param context The {@link Context} for operations.
     * @param channelId The id of the channel. Must be unique per package.
     * @param channelName The user visible name of the channel.
     * @param importance The importance of the channel. This controls how interruptive notifications
     *                   posted to this channel are.
     */
    public static void setupNotificationChannel(final Context context, final String channelId, final CharSequence channelName, final int importance) {
        setupNotificationChannel(context, channelId, channelName, importance, NOTIFICATION_MODE_SILENT);
    }

    /**
     * Setup the notification channel if Android version is greater than or equal to
     * {@link Build.VERSION_CODES#O}, applying {@code notificationMode} as channel sound / vibrate /
     * lights (API 26+ ignores {@link Notification.Builder#setDefaults(int)}).
     *
     * @param context The {@link Context} for operations.
     * @param channelId The id of the channel. Must be unique per package.
     * @param channelName The user visible name of the channel.
     * @param importance The importance of the channel. This controls how interruptive notifications
     *                   posted to this channel are.
     * @param notificationMode The notification mode. It must be one of {@code NotificationUtils.NOTIFICATION_MODE_*}.
     */
    public static void setupNotificationChannel(final Context context, final String channelId,
                                                final CharSequence channelName, final int importance,
                                                final int notificationMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
        applyNotificationModeToChannel(channel, notificationMode);

        NotificationManager notificationManager = getNotificationManager(context);
        if (notificationManager != null)
            notificationManager.createNotificationChannel(channel);
    }

    /**
     * Apply sound / vibrate / lights from {@code notificationMode} onto a channel.
     * No-op for {@link #NOTIFICATION_MODE_NONE} (caller should not create a channel for that mode).
     */
    private static void applyNotificationModeToChannel(final NotificationChannel channel,
                                                       final int notificationMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        boolean sound = false;
        boolean vibrate = false;
        boolean lights = false;

        switch (notificationMode) {
            case NOTIFICATION_MODE_NONE:
            case NOTIFICATION_MODE_SILENT:
                break;
            case NOTIFICATION_MODE_SOUND:
                sound = true;
                break;
            case NOTIFICATION_MODE_VIBRATE:
                vibrate = true;
                break;
            case NOTIFICATION_MODE_LIGHTS:
                lights = true;
                break;
            case NOTIFICATION_MODE_SOUND_AND_VIBRATE:
                sound = true;
                vibrate = true;
                break;
            case NOTIFICATION_MODE_SOUND_AND_LIGHTS:
                sound = true;
                lights = true;
                break;
            case NOTIFICATION_MODE_VIBRATE_AND_LIGHTS:
                vibrate = true;
                lights = true;
                break;
            case NOTIFICATION_MODE_ALL:
                sound = true;
                vibrate = true;
                lights = true;
                break;
            default:
                Logger.logError(LOG_TAG, "Invalid notificationMode: \"" + notificationMode
                    + "\" passed to applyNotificationModeToChannel()");
                break;
        }

        if (sound) {
            channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } else {
            channel.setSound(null, null);
        }
        channel.enableVibration(vibrate);
        channel.enableLights(lights);
    }

    /**
     * Apply {@code notificationMode} to a pre-O {@link Notification.Builder} via
     * {@link Notification.Builder#setDefaults(int)}. On API 26+ the channel owns sound / vibrate /
     * lights (see {@link #setupNotificationChannel}), so this only handles {@link #NOTIFICATION_MODE_NONE}.
     */
    @SuppressWarnings("deprecation")
    public static Notification.Builder setNotificationDefaults(Notification.Builder builder, final int notificationMode) {

        if (notificationMode == NOTIFICATION_MODE_NONE) {
            Logger.logWarn(LOG_TAG, "The NOTIFICATION_MODE_NONE passed to setNotificationDefaults(), force setting builder to null.");
            return null; // return null since notification is not supposed to be shown
        }

        // On Oreo+, NotificationChannel controls sound / vibrate / lights; setDefaults is ignored.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return builder;
        }

        switch (notificationMode) {
            case NOTIFICATION_MODE_SILENT:
                break;
            case NOTIFICATION_MODE_SOUND:
                builder.setDefaults(Notification.DEFAULT_SOUND);
                break;
            case NOTIFICATION_MODE_VIBRATE:
                builder.setDefaults(Notification.DEFAULT_VIBRATE);
                break;
            case NOTIFICATION_MODE_LIGHTS:
                builder.setDefaults(Notification.DEFAULT_LIGHTS);
                break;
            case NOTIFICATION_MODE_SOUND_AND_VIBRATE:
                builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
                break;
            case NOTIFICATION_MODE_SOUND_AND_LIGHTS:
                builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS);
                break;
            case NOTIFICATION_MODE_VIBRATE_AND_LIGHTS:
                builder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
                break;
            case NOTIFICATION_MODE_ALL:
                builder.setDefaults(Notification.DEFAULT_ALL);
                break;
            default:
                Logger.logError(LOG_TAG, "Invalid notificationMode: \"" + notificationMode + "\" passed to setNotificationDefaults()");
                break;
        }

        return builder;
    }

}
