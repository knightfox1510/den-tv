package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * den-tv's own Home choices, set in the first-time setup (the Fan Hub's SetupActivity and the phone page, through
 * DenLooksProvider): the name in the greeting, whether the greeting, the match panel and the watcher show, and
 * whether setup has run. Config.load applies the first three over the theme's settings.
 */
final class DenSettings {
    static final String NAME = "greeting_name", GREETING = "show_greeting", PANEL = "fan_panel", WATCHER = "watcher",
            SETUP_DONE = "setup_done";
    private static final String SETUP_OFFERED = "setup_offered";
    static final ComponentName SETUP = new ComponentName("tv.den.fanhub", "tv.den.fanhub.SetupActivity");

    private DenSettings() {}

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(DenLooks.PREFS, Context.MODE_PRIVATE); }

    static String name(Context ctx, String fallback) { return prefs(ctx).getString(NAME, fallback); }
    static boolean greeting(Context ctx, boolean fallback) { return prefs(ctx).getBoolean(GREETING, fallback); }
    static boolean panel(Context ctx) { return prefs(ctx).getBoolean(PANEL, true); }
    static boolean watcher(Context ctx) { return prefs(ctx).getBoolean(WATCHER, true); }

    /** Opens the setup on the TV; false when the Fan Hub, which runs it, isn't installed. */
    static boolean openSetup(Activity activity) {
        try {
            activity.startActivity(new Intent().setComponent(SETUP));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** The first time Home opens after install, start the setup once; Back from it never brings it round again. */
    static void offerSetupOnce(Activity activity) {
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(SETUP_DONE, false) || p.getBoolean(SETUP_OFFERED, false)) return;
        if (openSetup(activity)) p.edit().putBoolean(SETUP_OFFERED, true).apply();
    }
}
