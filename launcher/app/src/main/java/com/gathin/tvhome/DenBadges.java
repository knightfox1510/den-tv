package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Badges on app tiles, from the Fan Hub's turns: a live score on FanCode, JioHotstar or JioCinema while one of your
 * teams plays, and the episode number on Crunchyroll when a new episode is out today. Read when Home comes back.
 */
final class DenBadges {
    private static final Uri TURNS = Uri.parse("content://tv.den.fanhub.games/turns");
    /** The Fan Hub's screen time per app (StatsProvider): package, today's milliseconds, when it was last left. */
    private static final Uri STATS = Uri.parse("content://tv.den.fanhub.stats/apps");
    static final String CONTINUE = "CONTINUE";
    private static volatile Map<String, String> byApp = new HashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Pattern SCORE = Pattern.compile("(\\d+)\\u2013(\\d+)"), EPISODE = Pattern.compile("EPISODE (\\d+)");
    private static volatile Map<String, String> badges = new HashMap<>();

    private DenBadges() {}

    /**
     * A tile's badge: live score or new episode first (by the app's name), then CONTINUE on the app you left last
     * (within 3 hours), then today's time in it when "Time on tiles" is on. Null for none.
     */
    static String forApp(String pkg, String label) {
        String b = forLabel(label);
        if (b != null || pkg == null) return b;
        return byApp.get(pkg);
    }

    /** The badge for a tile by its name, or null. */
    static String forLabel(String label) {
        if (label == null) return null;
        String l = label.toLowerCase(Locale.ROOT).replace(" ", "");
        Map<String, String> b = badges;
        if (l.contains("crunchyroll")) return b.get("episode");
        if (l.contains("hotstar")) return b.get("cricket") != null ? b.get("cricket") : b.get("football");
        if (l.contains("fancode")) return b.get("football") != null ? b.get("football") : b.get("cricket");
        if (l.contains("jiocinema")) return b.get("football");
        return null;
    }

    /** Reads the turns off the main thread; changed runs on the main thread when the badges changed. */
    static void refresh(Context ctx, Runnable changed) {
        Context app = ctx.getApplicationContext();
        try {
            IO.execute(() -> {
                Map<String, String> next = new HashMap<>();
                long now = System.currentTimeMillis();
                try (Cursor c = app.getContentResolver().query(TURNS, new String[]{"top_line", "word", "start_ms", "end_ms"}, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        String top = c.getString(0), word = c.getString(1);
                        long start = c.getLong(2), end = c.getLong(3);
                        boolean live = now >= start && now < end;
                        if ("New episode".equals(word) && start - now < 20 * 3_600_000L && end > now) {
                            Matcher m = EPISODE.matcher(top);
                            next.put("episode", live ? "NEW" : m.find() ? "EP " + m.group(1) : "NEW");
                        } else if (live && "Kick-off".equals(word)) {
                            Matcher m = SCORE.matcher(top);
                            next.put("football", m.find() ? "LIVE " + m.group(1) + "–" + m.group(2) : "LIVE");
                        } else if (live && "First ball".equals(word)) {
                            next.put("cricket", "LIVE");
                        }
                    }
                } catch (RuntimeException ignored) {
                    // No Fan Hub: no badges.
                }
                Map<String, String> apps = new HashMap<>();
                android.content.SharedPreferences p = DenSettings.prefs(app);
                boolean cont = p.getBoolean(DenSettings.TILE_CONTINUE, true), time = p.getBoolean(DenSettings.TILE_TIME, false);
                if (cont || time) {
                    String lastPkg = null;
                    long lastAt = 0;
                    try (Cursor c = app.getContentResolver().query(STATS, null, null, null, null)) {
                        while (c != null && c.moveToNext()) {
                            String pkg = c.getString(0);
                            long ms = c.getLong(1), left = c.getLong(2);
                            if (pkg.equals(app.getPackageName()) || pkg.startsWith("tv.den.")) continue;
                            if (time && ms >= 5 * 60_000L) apps.put(pkg, minutes(ms));
                            if (left > lastAt) { lastAt = left; lastPkg = pkg; }
                        }
                    } catch (RuntimeException ignored) { }   // an older Fan Hub, or none
                    if (cont && lastPkg != null && now - lastAt < 3 * 3_600_000L) apps.put(lastPkg, CONTINUE);
                }
                if (!next.equals(badges) || !apps.equals(byApp)) {
                    badges = next;
                    byApp = apps;
                    new Handler(Looper.getMainLooper()).post(changed);
                }
            });
        } catch (RuntimeException ignored) { }
    }

    /** "1h 12m", "45m". */
    static String minutes(long ms) {
        long m = ms / 60_000L;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }
}
