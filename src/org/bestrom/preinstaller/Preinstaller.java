/*
 * SPDX-FileCopyrightText: BestROM
 * SPDX-License-Identifier: Apache-2.0
 */
package org.bestrom.preinstaller;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot scan-and-install pass.
 *
 * <p>For every *.apk under {@link #PREINSTALL_DIRS}, install it as a normal,
 * fully-removable user app via {@link PackageInstaller} - the same mechanism
 * Play Store or any installer app uses - unless it has already been handled
 * once. "Handled" is tracked per package name in a SharedPreferences marker
 * ({@code done_<packageName>}), set to true either after a successful
 * install hand-off or when the package is found to already be installed.
 * That marker is the entire mechanism that makes this "removable preload":
 * once set, this class will never reinstall the package, so a user
 * uninstall sticks across reboots.
 */
class Preinstaller {

    private static final String TAG = "BestromPreinstaller";

    private static final String PREFS_NAME = "preinstall_state";
    private static final String DONE_PREFIX = "done_";

    private static final String[] PREINSTALL_DIRS = {
            "/product/etc/bestrom/preinstall/",
            "/system/etc/bestrom/preinstall/",
    };

    private final Context context;
    private final PackageManager packageManager;
    private final SharedPreferences prefs;

    Preinstaller(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    void run() {
        List<File> apks = findApks();
        if (apks.isEmpty()) {
            Log.i(TAG, "No preinstall APKs found under " + String.join(", ", PREINSTALL_DIRS));
            return;
        }
        for (File apk : apks) {
            try {
                handleApk(apk);
            } catch (Exception e) {
                // One bad APK must never stop the rest of the batch from
                // being processed.
                Log.e(TAG, "Failed to process " + apk.getAbsolutePath(), e);
            }
        }
    }

    private List<File> findApks() {
        List<File> result = new ArrayList<>();
        for (String dirPath : PREINSTALL_DIRS) {
            File dir = new File(dirPath);
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".apk")) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    private void handleApk(File apk) throws IOException {
        PackageInfo archiveInfo = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archiveInfo == null || archiveInfo.packageName == null) {
            Log.w(TAG, "Could not read package info from " + apk.getAbsolutePath());
            return;
        }
        String packageName = archiveInfo.packageName;

        if (isDone(packageName)) {
            Log.i(TAG, packageName + " already handled, skipping");
            return;
        }

        if (isInstalled(packageName)) {
            Log.i(TAG, packageName + " is already installed, marking done without reinstalling");
            markDone(packageName);
            return;
        }

        Log.i(TAG, "Installing " + packageName + " from " + apk.getAbsolutePath());
        installApk(apk, packageName);
        // Mark done immediately once the session is handed off to the
        // system: from this point on PackageInstaller owns the install, and
        // InstallStatusReceiver only logs the outcome. We do not want to
        // retry on every subsequent boot merely because the async install
        // result hasn't landed yet, and a failed install should not be
        // retried automatically either (it may fail again for a reason that
        // needs a human, e.g. incompatible ABI) - the marker is intentionally
        // "attempted", not "succeeded".
        markDone(packageName);
    }

    private boolean isInstalled(String packageName) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isDone(String packageName) {
        return prefs.getBoolean(DONE_PREFIX + packageName, false);
    }

    private void markDone(String packageName) {
        prefs.edit().putBoolean(DONE_PREFIX + packageName, true).apply();
    }

    private void installApk(File apk, String packageName) throws IOException {
        PackageInstaller installer = packageManager.getPackageInstaller();

        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setInstallReason(PackageManager.INSTALL_REASON_DEVICE_SETUP);
        // So Settings/Play Store show "installed by BestROM" rather than
        // leaving the installer of record unset.
        params.setInstallerPackageName(context.getPackageName());

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = null;
        boolean committed = false;
        try {
            session = installer.openSession(sessionId);

            try (OutputStream out = session.openWrite(packageName, 0, apk.length());
                    InputStream in = new FileInputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            }

            Intent statusIntent = new Intent(context, InstallStatusReceiver.class);
            statusIntent.putExtra(InstallStatusReceiver.EXTRA_PACKAGE_NAME, packageName);
            PendingIntent statusReceiver = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            session.commit(statusReceiver.getIntentSender());
            committed = true;
        } finally {
            if (session != null) {
                if (!committed) {
                    session.abandon();
                }
                session.close();
            }
        }
    }
}
