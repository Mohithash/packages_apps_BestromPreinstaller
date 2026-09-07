/*
 * SPDX-FileCopyrightText: BestROM
 * SPDX-License-Identifier: Apache-2.0
 */
package org.bestrom.preinstaller;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plain (non-foreground) Service that runs the preinstall scan on a
 * background thread and then stops itself. Started once per boot from
 * {@link BootCompletedReceiver}. There is no need for a JobIntentService /
 * WorkManager here: the work is a short, bounded scan of a handful of local
 * files, run once, with no requirement to survive process death - if the
 * system kills us mid-scan, the per-APK "done_<pkg>" marker is only set after
 * a package is confirmed installed (or confirmed already present), so the
 * next boot simply resumes where this one left off.
 */
public class PreinstallService extends Service {

    private static final String TAG = "BestromPreinstaller";

    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        executor.execute(() -> {
            // The default-keyboard seed is not here: it runs synchronously in
            // BootCompletedReceiver, before this service is even started, so
            // the setup wizard is never up without a keyboard.
            try {
                new Preinstaller(getApplicationContext()).run();
            } catch (Exception e) {
                // Belt and suspenders: run() already catches per-APK, this
                // guards against anything unexpected escaping it.
                Log.e(TAG, "Preinstall pass failed", e);
            } finally {
                stopSelf(startId);
            }
        });
        // Not sticky: if we're killed, BootCompletedReceiver will start us
        // again on the next boot rather than the system respawning us with a
        // null intent.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
