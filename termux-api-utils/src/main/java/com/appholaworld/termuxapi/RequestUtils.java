package com.appholaworld.termuxapi;

import android.util.Log;

import com.google.firebase.database.FirebaseDatabase;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.json.JSONObject;

public class RequestUtils {
    private static String TAG = "RequestUtils";

    public static void post(String path, Map<String, String> data) {
        FirebaseDatabase.getInstance().getReference("server_url").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                new Thread(() -> {
                    String fullUrl = task.getResult().getValue(String.class) + path;
                    Log.d(TAG, "Posting to: " + fullUrl);

                    HttpURLConnection conn = null;
                    try {
                        JSONObject json = new JSONObject(data);
                        URL url = new URL(fullUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/json");

                        byte[] out = json.toString().getBytes();
                        OutputStream os = conn.getOutputStream();
                        os.write(out);
                        os.flush();
                        os.close();

                        int code = conn.getResponseCode();
                        Log.d(TAG, "Response code: " + code);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to post", e);
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }).start();
            } else {
                Log.e(TAG, "Error reading URL from DB: " + task.getException().toString());
            }
        });
    }
}
