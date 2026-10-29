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
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Pattern SCORE = Pattern.compile("(\\d+)\\u2013(\\d+)"), EPISODE = Pattern.compile("EPISODE (\\d+)");
    private static volatile Map<String, String> badges = new HashMap<>();

    private DenBadges() {}

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
                if (!next.equals(badges)) {
                    badges = next;
                    new Handler(Looper.getMainLooper()).post(changed);
                }
            });
        } catch (RuntimeException ignored) { }
    }
}
