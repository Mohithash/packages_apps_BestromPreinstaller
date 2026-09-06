/*
 * SPDX-FileCopyrightText: BestROM
 * SPDX-License-Identifier: Apache-2.0
 */
package org.bestrom.preinstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/**
 * Target of the {@link PackageInstaller.Session#commit(android.content.IntentSender)}
 * PendingIntent. Not exported - only the system, replying to a session we
 * committed, ever delivers this. Purely informational: the "done" marker in
 * {@link Preinstaller} is already set at commit time, so nothing here needs
 * to (or should) retry the install.
 */
public class InstallStatusReceiver extends BroadcastReceiver {

    private static final String TAG = "BestromPreinstaller";

    static final String EXTRA_PACKAGE_NAME = "org.bestrom.preinstaller.extra.PACKAGE_NAME";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String ourPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i(TAG, "Install succeeded for " + ourPackageName);
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Should not happen for a privileged, silent install, but handle
            // it defensively rather than crash: nothing to do, the system
            // already surfaced whatever action it needed.
            Log.w(TAG, "Install pending user action for " + ourPackageName);
        } else {
            Log.e(TAG, "Install failed for " + ourPackageName
                    + " status=" + status + " message=" + message);
        }
    }
}
