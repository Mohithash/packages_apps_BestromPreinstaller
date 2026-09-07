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
 * Seeds the default keyboard, then kicks off the one-time preinstall pass, on
 * every boot. The preinstall work happens in {@link PreinstallService};
 * {@link Preinstaller} makes sure a package that was already handled
 * (installed once, marker set - whether or not the user has since uninstalled
 * it) is never touched again, so this receiver firing on every boot is
 * harmless and cheap once the bundled set has been processed.
 *
 * <p>The IME seed runs here rather than on the service's executor because the
 * setup wizard is already up when BOOT_COMPLETED is delivered, and until the
 * seed lands there is no keyboard to type a Wi-Fi password with. It is two
 * {@code Settings.Secure} writes guarded by a read, so it costs nothing on the
 * boots where it does nothing. See {@link DefaultImeSeeder}.
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
            new DefaultImeSeeder(context).run();
        } catch (Exception e) {
            // Never let the keyboard seed take out the preinstall pass below.
            Log.e(TAG, "Default IME seed failed", e);
        }
        try {
            context.startService(new Intent(context, PreinstallService.class));
        } catch (Exception e) {
            // Never let a bad boot receiver take out the boot sequence.
            Log.e(TAG, "Failed to start PreinstallService", e);
        }
    }
}
