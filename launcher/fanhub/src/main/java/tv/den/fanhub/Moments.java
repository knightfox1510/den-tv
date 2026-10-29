package tv.den.fanhub;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import java.util.List;
import java.util.Map;

/**
 * The big moments: a goal for your team, your cricket side passing 50, 100, 150 runs and so on, and a new episode
 * coming out. Found by comparing one fetch with the last, then sent to the launcher, which plays the moment on Home
 * (or as soon as Home is back, within a few minutes).
 */
final class Moments {
    private static final String MOMENT = "content://tv.den.launcher.looks/moment";

    private Moments() {}

    /** One moment, or null: what changed between before and after for one source. */
    static String[] find(String key, List<FanHub.Turn> before, List<FanHub.Turn> after, long now) {
        if (after == null || after.isEmpty()) return null;
        FanHub.Turn b = before == null || before.isEmpty() ? null : before.get(0), a = after.get(0);
        if (key.startsWith("show:")) {
            // The episode has just come out: it is on now and wasn't at the last fetch.
            boolean outNow = now >= a.start && now < a.end, wasOut = b != null && b.start == a.start && b.live;
            if (outNow && !wasOut && now - a.start < 30 * 60_000L) return new String[]{"drop", "New episode", a.topLine};
            return null;
        }
        if (!key.startsWith("team:") || !a.live || b == null || !b.live || a.ours < 0 || b.ours < 0) return null;
        boolean cricket = "First ball".equals(a.word);
        if (!cricket && a.ours > b.ours) return new String[]{"goal", "Goal!", a.score};
        if (cricket && a.ours / 50 > b.ours / 50) return new String[]{"six", (a.ours / 50 * 50) + " up", a.score};
        return null;
    }

    static void compare(Context ctx, Map<String, List<FanHub.Turn>> before, Map<String, List<FanHub.Turn>> after, long now) {
        for (Map.Entry<String, List<FanHub.Turn>> e : after.entrySet()) {
            String[] m = find(e.getKey(), before.get(e.getKey()), e.getValue(), now);
            if (m != null) play(ctx, m[0], m[1], m[2], e.getValue().get(0).colour);
        }
    }

    /** Sends a moment to the launcher; false when it isn't there (or is an older one without moments). */
    static boolean play(Context ctx, String type, String title, String line, int colour) {
        ContentValues v = new ContentValues();
        v.put("type", type);
        v.put("title", title);
        v.put("line", line);
        v.put("colour", colour);
        try { return ctx.getContentResolver().update(Uri.parse(MOMENT), v, null, null) > 0; } catch (RuntimeException e) { return false; }
    }
}
