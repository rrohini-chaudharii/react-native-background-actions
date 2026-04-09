package com.asterinet.react.bgactions;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

@SuppressWarnings("WeakerAccess")
public class BackgroundActionsModule extends ReactContextBaseJavaModule {

    private static final String TAG = "RNBackgroundActions";

    private final ReactContext reactContext;

    private Intent currentServiceIntent;

    public BackgroundActionsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @NonNull
    @Override
    public String getName() {
        return TAG;
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void start(@NonNull final ReadableMap options, @NonNull final Promise promise) {
        try {
            // ✅ SECURITY: Validate options input
            if (options == null) {
                promise.reject("INVALID_OPTIONS", "Options cannot be null");
                return;
            }

            // Stop any other intent
            if (currentServiceIntent != null) {
                reactContext.stopService(currentServiceIntent);
            }

            // ✅ SECURITY: Create EXPLICIT service intent
            currentServiceIntent = new Intent(reactContext, RNBackgroundActionsTask.class);
            
            // Get the task info from the options
            final BackgroundTaskOptions bgOptions = new BackgroundTaskOptions(reactContext, options);
            
            // ✅ SECURITY: Validate background options
            if (bgOptions.getExtras() == null) {
                promise.reject("INVALID_TASK_OPTIONS", "Invalid background task options");
                return;
            }

            currentServiceIntent.putExtras(bgOptions.getExtras());

            // ✅ SECURITY: Ensure intent remains explicit to this package
            currentServiceIntent.setPackage(reactContext.getPackageName());

            // Start the task
            // Using ContextCompat is the cleanest way to handle the API 26+ foreground requirement
            ContextCompat.startForegroundService(reactContext, currentServiceIntent);
            
            promise.resolve(null);
        } catch (SecurityException e) {
            // ✅ SECURITY: Handle security exceptions explicitly
            promise.reject("SECURITY_ERROR", "Security error starting background service: " + e.getMessage());
        } catch (Exception e) {
            // ✅ SECURITY: Log but don't expose sensitive internal details in production
            promise.reject("START_ERROR", "Failed to start background service");
        }
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void stop(@NonNull final Promise promise) {
        try {
            if (currentServiceIntent != null) {
                reactContext.stopService(currentServiceIntent);
                currentServiceIntent = null; // ✅ SECURITY: Clear reference
            }
            promise.resolve(null);
        } catch (SecurityException e) {
            promise.reject("SECURITY_ERROR", "Security error stopping background service: " + e.getMessage());
        } catch (Exception e) {
            promise.reject("STOP_ERROR", "Failed to stop background service");
        }
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void updateNotification(@NonNull final ReadableMap options, @NonNull final Promise promise) {
        try {
            // ✅ SECURITY: Validate input
            if (options == null) {
                promise.reject("INVALID_OPTIONS", "Options cannot be null");
                return;
            }

            // Get the task info from the options
            final BackgroundTaskOptions bgOptions = new BackgroundTaskOptions(reactContext, options);
            
            // ✅ SECURITY: Validate notification manager access
            final NotificationManager notificationManager = (NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) {
                promise.reject("NOTIFICATION_ERROR", "Cannot access notification manager");
                return;
            }

            final Notification notification = RNBackgroundActionsTask.buildNotification(reactContext, bgOptions);
            
            // ✅ SECURITY: Validate notification was created successfully
            if (notification == null) {
                promise.reject("NOTIFICATION_ERROR", "Failed to create secure notification");
                return;
            }

            notificationManager.notify(RNBackgroundActionsTask.SERVICE_NOTIFICATION_ID, notification);
            promise.resolve(null);
        } catch (SecurityException e) {
            promise.reject("SECURITY_ERROR", "Security error updating notification: " + e.getMessage());
        } catch (Exception e) {
            promise.reject("NOTIFICATION_ERROR", "Failed to update notification");
        }
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void addListener(String eventName) {
        // Keep: Required for RN built in Event Emitter Calls.
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void removeListeners(Integer count) {
        // Keep: Required for RN built in Event Emitter Calls.
    }
}
