package com.asterinet.react.bgactions;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

final public class RNBackgroundActionsTask extends HeadlessJsTaskService {

    public static final int SERVICE_NOTIFICATION_ID = 92901;
    private static final String CHANNEL_ID = "RN_BACKGROUND_ACTIONS_CHANNEL";
    private static final String TAG = "RNBackgroundActionsTask";

    @SuppressLint("UnspecifiedImmutableFlag")
    @NonNull
    public static Notification buildNotification(@NonNull Context context, @NonNull final BackgroundTaskOptions bgOptions) {
        // Get info
        final String taskTitle = bgOptions.getTaskTitle();
        final String taskDesc = bgOptions.getTaskDesc();
        final int iconInt = bgOptions.getIconInt();
        final int color = bgOptions.getColor();
        final String linkingURI = bgOptions.getLinkingURI();
        
        Intent notificationIntent;
        if (linkingURI != null) {
            notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkingURI));
            // ✅ SECURITY: Make intent explicit
            notificationIntent.setPackage(context.getPackageName());
        } else {
            notificationIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (notificationIntent == null) {
                notificationIntent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage(context.getPackageName());
            }
        }

        // ✅ SECURITY: Handle PendingIntent flags correctly for all API levels
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        final PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, pendingIntentFlags);
        
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(taskTitle)
                .setContentText(taskDesc)
                .setSmallIcon(iconInt)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setColor(color);

        // ✅ OPTIMIZATION: Ensure the notification shows immediately on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        final Bundle progressBarBundle = bgOptions.getProgressBar();
        if (progressBarBundle != null) {
            try {
                final int progressMax = (int) Math.floor(progressBarBundle.getDouble("max"));
                final int progressCurrent = (int) Math.floor(progressBarBundle.getDouble("value"));
                final boolean progressIndeterminate = progressBarBundle.getBoolean("indeterminate");
                builder.setProgress(progressMax, progressCurrent, progressIndeterminate);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set progress bar", e);
            }
        }
        return builder.build();
    }

    @Override
    protected @Nullable
    HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        if (intent == null) return null;
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            return new HeadlessJsTaskConfig(extras.getString("taskName"), Arguments.fromBundle(extras), 0, true);
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(TAG, "Service started with null extras. Stopping.");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final BackgroundTaskOptions bgOptions = new BackgroundTaskOptions(extras);
        createNotificationChannel(bgOptions.getTaskTitle(), bgOptions.getTaskDesc());

        final Notification notification = buildNotification(this, bgOptions);

        // ✅ CRASH PROTECTION: Wrap foreground start in a try-catch for Android 12+ rules
        try {
            ServiceCompat.startForeground(
                this,
                SERVICE_NOTIFICATION_ID,
                notification,
                bgOptions.getForegroundServiceType()
            );
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                e instanceof android.app.ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "Foreground service start denied by OS (App in background)");
            } else {
                Log.e(TAG, "Failed to start foreground service", e);
            }
            // Stop the service to prevent a "Context.startForegroundService() did not then call Service.startForeground()" crash
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onTimeout(int startId) {
        super.onTimeout(startId);
        stopSelf(startId);
    }

    private void createNotificationChannel(@NonNull final String taskTitle, @NonNull final String taskDesc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                // IMPORTANCE_LOW ensures the notification doesn't "pop" or make sound every update
                final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, taskTitle, NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(taskDesc);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
