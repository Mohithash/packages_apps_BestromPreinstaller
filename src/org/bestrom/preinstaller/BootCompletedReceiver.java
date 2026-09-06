/*
 * SPDX-FileCopyrightText: BestROM
 * SPDX-License-Identifier: Apache-2.0
 */
package org.bestrom.preinstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Kicks off the one-time preinstall pass on every boot. The actual work
 * happens in {@link PreinstallService}; {@link Preinstaller} makes sure a
 * package that was already handled (installed once, marker set - whether or
 * not the user has since uninstalled it) is never touched again, so this
 * receiver firing on every boot is harmless and cheap once the bundled set
 * has been processed.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "BestromPreinstaller";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Log.i(TAG, "Boot completed, starting preinstall pass");
        try {
            context.startService(new Intent(context, PreinstallService.class));
        } catch (Exception e) {
            // Never let a bad boot receiver take out the boot sequence.
            Log.e(TAG, "Failed to start PreinstallService", e);
        }
    }
}
