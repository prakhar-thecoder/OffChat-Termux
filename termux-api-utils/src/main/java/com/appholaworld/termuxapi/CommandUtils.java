package com.appholaworld.termuxapi;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for executing commands in Termux environment
 */
public class CommandUtils {
    private static final String TAG = "CommandUtils";
    private static final String PREFIX = "/data/data/com.termux/files/usr";
    private static final String TERMUX_BIN_PATH = PREFIX + "/bin";

    public static boolean executeShellCommand(Context context, String shellCommand) {
        if (shellCommand == null || shellCommand.trim().isEmpty()) {
            Log.e(TAG, "shellCommand cannot be null or empty");
            return false;
        }

        // Prefer termux's bash if present, otherwise fallback to /system/bin/sh
        String shell = TERMUX_BIN_PATH + "/bash"; // termux usually has /data/data/.../usr/bin/bash
        File shellFile = new File(shell);
        if (!shellFile.exists() || !shellFile.canExecute()) {
            // fallback to system sh
            shell = "/system/bin/sh";
            Log.w(TAG, "Termux bash not found or not executable at " + TERMUX_BIN_PATH + "/bash; falling back to " + shell);
        } else {
            Log.d(TAG, "Using shell: " + shell);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(shell);
        cmd.add("-lc");
        cmd.add(shellCommand);

        ProcessBuilder pb = new ProcessBuilder(cmd);

        // Set working directory to PREFIX (Termux root)
        File workDir = new File(PREFIX);
        if (workDir.exists() && workDir.isDirectory()) {
            pb.directory(workDir);
            Log.d(TAG, "Working directory set to: " + PREFIX);
        } else {
            Log.w(TAG, "Termux prefix not found at " + PREFIX + " — command may fail due to missing environment");
        }

        // Environment
        pb.environment().put("PREFIX", PREFIX);
        // Put TERMUX bin first so scripts pick the correct tools
        String currentPath = System.getenv("PATH");
        String newPath = TERMUX_BIN_PATH + (currentPath != null && !currentPath.isEmpty() ? ":" + currentPath : "");
        pb.environment().put("PATH", newPath);

        // LD_LIBRARY_PATH is important for native binaries that link against libs in $PREFIX/lib
        String ld = PREFIX + "/lib";
        String existingLd = pb.environment().get("LD_LIBRARY_PATH");
        pb.environment().put("LD_LIBRARY_PATH", (existingLd == null ? ld : existingLd + ":" + ld));

        pb.environment().put("HOME", "/data/data/com.termux/files/home");

        pb.redirectErrorStream(true);

        Log.i(TAG, "Executing shell command: " + shellCommand);
        Log.d(TAG, "Env PATH=" + newPath);
        Log.d(TAG, "Env LD_LIBRARY_PATH=" + pb.environment().get("LD_LIBRARY_PATH"));

        try {
            final Process process = pb.start();

            // Stream output to log as it arrives
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                Log.d(TAG, line);
            }

            int exitCode = process.waitFor();
            reader.close();

            Log.i(TAG, "Command finished; exit code=" + exitCode);
            Log.d(TAG, "Full output:\n" + output.toString());

            return exitCode == 0;
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException while starting process: " + se.getMessage(), se);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Exception while executing command: " + e.getMessage(), e);
            return false;
        }
    }
}
