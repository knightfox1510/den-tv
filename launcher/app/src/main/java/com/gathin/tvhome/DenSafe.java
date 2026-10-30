package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Safe mode. den-tv is the TV's only home screen, so a crash on start would crash again at every press of Home. If
 * Home crashes twice within ten minutes, den-tv starts as the plain launcher (the wallpapers rotating, no look, no
 * watcher, effects or app-opening animations: cleanyourtv's well-worn path) until a new version arrives or someone
 * picks a look, which tries den-tv's looks again.
 */
final class DenSafe {
    private static final String PREFS = "den_safe";
    private static final long WINDOW_MS = 10 * 60_000L;
    private static boolean installed;

    private DenSafe() {}

    /** Notes every crash of the launcher's process (then Android's own handler runs as before). */
    static void install(Context ctx) {
        if (installed) return;
        installed = true;
        Context app = ctx.getApplicationContext();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { crashed(app, System.currentTimeMillis()); } catch (Throwable ignored) { }
            if (before != null) before.uncaughtException(t, e);
        });
    }

    /** A crash at now: the second within ten minutes puts this version in safe mode. */
    static void crashed(Context ctx, long now) {
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor e = p.edit().putLong("last_crash", now);
        if (now - p.getLong("last_crash", 0) < WINDOW_MS) e.putLong("safe_version", version(ctx));
        e.commit();   // the process is about to end: written now, not later
    }

    /** In safe mode: this version crashed twice in a row. A new version starts normally. */
    static boolean on(Context ctx) {
        return prefs(ctx).getLong("safe_version", -1) == version(ctx);
    }

    /** Someone picked a look: try den-tv's looks again. */
    static void off(Context ctx) {
        if (prefs(ctx).contains("safe_version") || prefs(ctx).contains("last_crash")) prefs(ctx).edit().remove("safe_version").remove("last_crash").apply();
    }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static long version = -1;

    /** This launcher's version code (asked once: it can't change while the process runs). */
    @SuppressWarnings("deprecation")
    private static long version(Context ctx) {
        if (version >= 0) return version;
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return version = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }
}
