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
 * committed, ever delivers this.
 *
 * <p>This is where the {@code done_<packageName>} marker is set for an
 * install {@link Preinstaller} started, so the marker records what actually
 * happened rather than that an attempt was made. It is set on success and on
 * the failures that would only fail the same way again; a failure that a
 * later boot could get past - no space, or a session cut short by a reboot -
 * leaves it unset, and the next boot tries once more.
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
            markDone(context, ourPackageName);
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Should not happen for a privileged, silent install, but handle
            // it defensively rather than crash: nothing to do, the system
            // already surfaced whatever action it needed. No marker - if the
            // user never completed it, a later boot may as well try again.
            Log.w(TAG, "Install pending user action for " + ourPackageName);
        } else if (isRetryable(status)) {
            // Out of space, or the session was abandoned (a reboot mid-write
            // does this). Leave the marker unset so the next boot retries.
            Log.w(TAG, "Install failed for " + ourPackageName + ", will retry next boot"
                    + " status=" + status + " message=" + message);
        } else {
            // Wrong ABI, malformed APK, conflicting signature or package: a
            // retry every boot would fail the same way and only cost time.
            Log.e(TAG, "Install failed for " + ourPackageName
                    + " status=" + status + " message=" + message);
            markDone(context, ourPackageName);
        }
    }

    private static boolean isRetryable(int status) {
        return status == PackageInstaller.STATUS_FAILURE_STORAGE
                || status == PackageInstaller.STATUS_FAILURE_ABORTED;
    }

    private static void markDone(Context context, String packageName) {
        if (packageName == null) {
            // Without the package name there is no marker to write. The
            // extra is set on every session we commit, so this means the
            // broadcast did not come from one of ours.
            Log.w(TAG, "Install status with no package name, ignoring");
            return;
        }
        Preinstaller.markDone(context, packageName);
    }
}
