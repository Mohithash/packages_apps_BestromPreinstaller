/*
 * SPDX-FileCopyrightText: BestROM
 * SPDX-License-Identifier: Apache-2.0
 */
package org.bestrom.preinstaller;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/**
 * Picks the shipped keyboard the first time the framework fails to.
 *
 * <p>BestROM ships LeanType instead of AOSP LatinIME. LeanType is a HeliBoard
 * fork, and those manage languages inside the app rather than publishing them
 * to the framework: its input-method meta-data declares one subtype with
 * {@code imeSubtypeMode="keyboard"} and no {@code imeSubtypeLocale}, and its
 * {@code bool/im_is_default} is false outside a handful of locales.
 *
 * <p>Every pass of
 * {@code InputMethodInfoUtils.getMinimumKeyboardSetWithSystemLocale} ends in
 * {@code SubtypeUtils.containsSubtypeOf}, which needs a subtype whose locale
 * language matches the system or fallback locale, and returns false outright
 * for a null locale. With LatinIME gone nothing matches, so
 * {@code getDefaultEnabledImes} returns an empty list, no IME is enabled, none
 * is selected, and the first boot reaches the setup wizard with no on-screen
 * keyboard - no way to type a Wi-Fi password.
 *
 * <p>So write the two settings the framework would have written.
 * {@code InputMethodManagerService} registers a
 * {@code SecureSettingsChangeCallback} on DEFAULT_INPUT_METHOD,
 * ENABLED_INPUT_METHODS and SELECTED_INPUT_METHOD_SUBTYPE, which lands in
 * {@code updateInputMethodsFromSettingsLocked}, so this takes effect during the
 * same boot rather than the next one. It persists, so from the second boot on
 * {@code onUserReadyLocked} sees a non-empty default and never runs the
 * auto-selection that has nothing to pick.
 *
 * <p>This only ever fires when no keyboard is selected. A user who switches to
 * a keyboard they installed themselves is never overridden, and neither is one
 * who is already on LeanType.
 *
 * <p>It runs from {@link BootCompletedReceiver#onReceive} rather than from
 * {@link PreinstallService}, synchronously and before the service is started:
 * the setup wizard is already the foreground activity when BOOT_COMPLETED
 * fires, so every hop between the broadcast and the write is time the wizard
 * is up with no keyboard. Two {@code putString} calls are cheap enough for a
 * receiver.
 */
class DefaultImeSeeder {

    private static final String TAG = "BestromPreinstaller";

    /**
     * The IME component, not just the package: this is the exact string
     * {@code InputMethodInfo#getId()} produces, and
     * {@code InputMethodSettings} looks it up in the method map verbatim.
     */
    private static final String LEANTYPE_IME_ID =
            "com.leanbitlab.leantype/helium314.keyboard.latin.LatinIME";

    /** {@code InputMethodUtils.NOT_A_SUBTYPE_ID} - let the IME pick. */
    private static final String NOT_A_SUBTYPE_ID = "-1";

    private final ContentResolver resolver;

    DefaultImeSeeder(Context context) {
        this.resolver = context.getContentResolver();
    }

    void run() {
        final String current =
                Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD);
        if (!TextUtils.isEmpty(current)) {
            Log.i(TAG, "Default input method already set, not seeding: " + current);
            return;
        }

        try {
            // Enable first. The DEFAULT_INPUT_METHOD write below is what
            // triggers the bind, and updateInputMethodsFromSettingsLocked
            // assumes whoever writes these keeps them in sync - a default that
            // is not in the enabled list is not a state it handles.
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_INPUT_METHODS,
                    LEANTYPE_IME_ID);
            Settings.Secure.putString(resolver, Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                    NOT_A_SUBTYPE_ID);
            Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD,
                    LEANTYPE_IME_ID);
        } catch (Exception e) {
            // A missing WRITE_SECURE_SETTINGS grant must not take out the
            // preinstall pass that runs after this.
            Log.e(TAG, "Failed to seed default input method", e);
            return;
        }

        // Read back rather than assume. A silent no-op here means a boot with
        // no keyboard, and this line is the difference between diagnosing that
        // from a bugreport and having to reproduce it on a wiped device.
        final String seeded =
                Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD);
        if (LEANTYPE_IME_ID.equals(seeded)) {
            Log.i(TAG, "Seeded default input method: " + LEANTYPE_IME_ID);
        } else {
            Log.e(TAG, "Default input method seed did not stick, device has no keyboard: "
                    + "wrote " + LEANTYPE_IME_ID + ", read back " + seeded);
        }
    }
}
