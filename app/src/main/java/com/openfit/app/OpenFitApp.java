package com.openfit.app;

import android.app.Application;
import android.content.Context;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Records the last uncaught exception to a small local file so a crash can be reported from the
 * phone without adb. Nothing leaves the device: the file is only read by the diagnostics dialog.
 */
public final class OpenFitApp extends Application {
    static final String CRASH_FILE = "last-crash.txt";
    private static final int MAX_CRASH_BYTES = 32 * 1024;

    @Override public void onCreate() {
        super.onCreate();
        final Context context = this;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            record(context, "uncaught", thread.getName(), throwable);
            if (previous != null) previous.uncaughtException(thread, throwable);
            else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    /** Records an exception that was caught and handled, such as a recoverable QR launch failure. */
    static void recordCaught(Context context, String where, Throwable throwable) {
        record(context, "caught", where, throwable);
    }

    private static synchronized void record(Context context, String kind, String where, Throwable throwable) {
        StringWriter trace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(trace));
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String entry = stamp + "  " + kind + "  " + where + "\n" + trace + "\n";
        FileOutputStream out = null;
        try {
            File file = new File(context.getFilesDir(), CRASH_FILE);
            // Keep the newest report only; older entries are not needed for a bug report.
            if (file.length() > MAX_CRASH_BYTES && !file.delete()) return;
            out = new FileOutputStream(file, true);
            out.write(entry.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // Crash reporting must never throw while the process is already failing.
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
        }
    }

    static String readLastCrash(Context context) {
        File file = new File(context.getFilesDir(), CRASH_FILE);
        if (!file.exists()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            String text = bytes.toString("UTF-8").trim();
            return text.isEmpty() ? null : text;
        } catch (Throwable e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    static void clearLastCrash(Context context) {
        File file = new File(context.getFilesDir(), CRASH_FILE);
        if (file.exists()) file.delete();
    }
}
