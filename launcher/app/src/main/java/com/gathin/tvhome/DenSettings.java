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
            SETUP_DONE = "setup_done", WAKE = "wake", REDUCED_MOTION = "reduced_motion", IDLE_CLOCK = "idle_clock",
            LABELS = "labels", PHOTO = "photo";
    /** The photo on Home, copied here from photos/ while it is the wallpaper (WallpaperRotation reads wallpapers/). */
    static final String PHOTO_FILE = "zz-den-photo.jpg";
    private static final String SETUP_OFFERED = "setup_offered";
    static final ComponentName SETUP = new ComponentName("tv.den.fanhub", "tv.den.fanhub.SetupActivity");

    private DenSettings() {}

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(DenLooks.PREFS, Context.MODE_PRIVATE); }

    /** The greeting's name: the person watching (DenPeople), else the setup's name. */
    static String name(Context ctx, String fallback) {
        DenPeople.Person p = DenPeople.current(ctx);
        if (p != null && !p.guest && !p.name.isEmpty()) return p.name;
        if (p != null && p.guest) return "";
        return prefs(ctx).getString(NAME, fallback);
    }
    static boolean greeting(Context ctx, boolean fallback) { return prefs(ctx).getBoolean(GREETING, fallback); }
    /** The match panel; off for a guest, whose games it isn't. */
    static boolean panel(Context ctx) {
        DenPeople.Person p = DenPeople.current(ctx);
        return prefs(ctx).getBoolean(PANEL, true) && (p == null || !p.guest);
    }
    static boolean stats(Context ctx, boolean fallback) { return prefs(ctx).getBoolean("stats", fallback); }
    static boolean facts(Context ctx) { return prefs(ctx).getBoolean("facts", false); }
    /** The HUD panels: on for the Geek and Coder looks (and any look marked hud) unless switched off; the phone can turn them on anywhere. */
    static boolean hud(Context ctx, DenLooks.Look look) {
        boolean own = look.hud || "geek".equals(look.id) || "coder".equals(look.id);
        return prefs(ctx).getBoolean("hud", false) || (own && prefs(ctx).getBoolean("hud_own", true));
    }
    static boolean watcher(Context ctx) { return prefs(ctx).getBoolean(WATCHER, true); }
    static boolean wake(Context ctx) { return prefs(ctx).getBoolean(WAKE, true); }
    /** Motion level: "full", "calm" or unset (calm on a TV with under 1.3 GB of memory, full otherwise). Off is reduced motion. */
    static final String MOTION = "motion";

    /**
     * Calm: watchers still follow the app, but no extras, no focus effects, no idle movement, the wake and the big
     * moments as a short card, and only the plain zoom and fade for opening apps and coming back.
     */
    static boolean calm(Context ctx) {
        String m = prefs(ctx).getString(MOTION, "");
        if ("calm".equals(m)) return true;
        if ("full".equals(m)) return false;
        android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem > 0 && mi.totalMem < 1_300_000_000L;
    }

    static boolean reducedMotion(Context ctx, boolean fallback) { return prefs(ctx).getBoolean(REDUCED_MOTION, fallback); }
    /** Minutes on Home before the idle clock; the theme's when not set here. */
    static int idleClock(Context ctx, int fallback) { return prefs(ctx).getInt(IDLE_CLOCK, fallback); }

    /** Names given to apps from the phone, as "package=Name" entries in the launcher's app_labels format. */
    static java.util.List<String> labels(Context ctx) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            org.json.JSONObject j = new org.json.JSONObject(prefs(ctx).getString(LABELS, "{}"));
            for (java.util.Iterator<String> it = j.keys(); it.hasNext(); ) {
                String pkg = it.next(), name = j.optString(pkg).trim();
                if (!name.isEmpty()) out.add(pkg + "=" + name);
            }
        } catch (org.json.JSONException ignored) { }
        return out;
    }

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
