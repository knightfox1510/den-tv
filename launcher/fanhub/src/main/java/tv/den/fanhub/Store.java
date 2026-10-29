package tv.den.fanhub;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The last fetch, kept in SharedPreferences so Home has something at once after a restart, and the one
 * background thread that refreshes it. A refresh ends with a change notice on the provider's URI, which
 * GameDay in the launcher watches.
 */
final class Store {
    private static final String TAG = "FanHub";
    static final String AUTHORITY = "tv.den.fanhub.games";
    static final Uri TURNS = Uri.parse("content://" + AUTHORITY + "/turns");
    private static final String PREFS = "fanhub";

    private static HandlerThread thread;
    private static Handler worker;
    private static boolean running;

    private Store() {}

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static synchronized Handler worker() {
        if (thread == null) {
            thread = new HandlerThread("fanhub");
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        return worker;
    }

    static List<FanHub.Turn> turns(Context ctx) {
        return FanHub.flatten(bySource(ctx), System.currentTimeMillis());
    }

    static long fetchedAt(Context ctx) { return prefs(ctx).getLong("fetched", 0); }

    static String errors(Context ctx) { return prefs(ctx).getString("errors", ""); }

    static Map<String, List<FanHub.Turn>> bySource(Context ctx) {
        Map<String, List<FanHub.Turn>> out = new HashMap<>();
        try {
            JSONObject j = new JSONObject(prefs(ctx).getString("by", "{}"));
            for (Iterator<String> it = j.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = j.getJSONArray(k);
                List<FanHub.Turn> l = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) l.add(FanHub.Turn.of(a.getJSONObject(i)));
                out.put(k, l);
            }
        } catch (JSONException e) {
            Log.w(TAG, "cache unreadable", e);
        }
        return out;
    }

    /** Fetch when the last one is older than refreshAfter says; force fetches now (the Fan Hub screen's OK). */
    static void refresh(Context ctx, boolean force) {
        Context app = ctx.getApplicationContext();
        worker().post(() -> {
            synchronized (Store.class) {
                if (running) return;
                running = true;
            }
            try {
                long now = System.currentTimeMillis();
                if (!force && now - fetchedAt(app) < FanHub.refreshAfter(turns(app), now)) return;
                Map<String, List<FanHub.Turn>> by = new HashMap<>();
                List<String> errors = new ArrayList<>();
                Map<String, List<FanHub.Turn>> before = bySource(app);
                List<FanHub.Turn> all = FanHub.fetch(config(app), now, before, by, errors);
                Moments.compare(app, before, by, now);
                JSONObject j = new JSONObject();
                for (Map.Entry<String, List<FanHub.Turn>> e : by.entrySet()) {
                    JSONArray a = new JSONArray();
                    for (FanHub.Turn t : e.getValue()) a.put(t.json());
                    j.put(e.getKey(), a);
                }
                prefs(app).edit().putString("by", j.toString()).putLong("fetched", now)
                        .putString("errors", String.join("\n", errors)).apply();
                app.getContentResolver().notifyChange(TURNS, null);
                // While a match is live or about to start, keep fetching even if Home does not ask.
                long again = FanHub.refreshAfter(all, now);
                if (again < 30 * 60_000L) worker().postDelayed(() -> refresh(app, false), again);
            } catch (JSONException e) {
                Log.w(TAG, "refresh failed", e);
            } finally {
                synchronized (Store.class) { running = false; }
            }
        });
    }

    /** config.json in the app's own folder on the TV (the command-line Fan Hub's format), else the defaults. */
    static FanHub.Config config(Context ctx) {
        FanHub.Config c = readConfig(ctx);
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean("hike_on", false)) c.hike = FanHub.place(p.getString("hike_place", "rajmachi"));
        c.footballKey = p.getString("key_football", "");
        c.cricketKey = p.getString("key_cricket", "");
        return c;
    }

    /** Whether each key is set (never the keys themselves), when the last fetch was, and what failed. */
    static org.json.JSONObject live(Context ctx) throws JSONException {
        SharedPreferences p = prefs(ctx);
        return new org.json.JSONObject().put("football", !p.getString("key_football", "").isEmpty()).put("cricket", !p.getString("key_cricket", "").isEmpty())
                .put("fetched", fetchedAt(ctx)).put("errors", errors(ctx));
    }

    /** Saves a key ("" removes it) and fetches again. */
    static org.json.JSONObject setLive(Context ctx, org.json.JSONObject in) throws JSONException {
        SharedPreferences.Editor e = prefs(ctx).edit();
        for (String k : new String[]{"football", "cricket"}) {
            if (!in.has(k)) continue;
            String v = in.optString(k).trim();
            if (v.isEmpty()) e.remove("key_" + k); else if (v.matches("[A-Za-z0-9-]{8,80}")) e.putString("key_" + k, v);
        }
        e.apply();
        refresh(ctx, true);
        return live(ctx).put("ok", true);
    }

    static org.json.JSONObject hike(Context ctx) throws JSONException {
        SharedPreferences p = prefs(ctx);
        org.json.JSONArray places = new org.json.JSONArray();
        for (FanHub.Place pl : FanHub.PLACES) places.put(new org.json.JSONObject().put("id", pl.id).put("name", pl.name));
        return new org.json.JSONObject().put("on", p.getBoolean("hike_on", false)).put("place", p.getString("hike_place", "rajmachi")).put("places", places);
    }

    static org.json.JSONObject setHike(Context ctx, org.json.JSONObject in) throws JSONException {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (in.has("on")) e.putBoolean("hike_on", in.optBoolean("on"));
        if (in.has("place")) e.putString("hike_place", FanHub.place(in.optString("place")).id);
        e.apply();
        refresh(ctx, true);
        return hike(ctx).put("ok", true);
    }

    private static FanHub.Config readConfig(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, "config.json");
        if (f == null || !f.isFile()) return FanHub.Config.defaults();
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 256 * 1024)];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return FanHub.Config.from(new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8)));
        } catch (IOException | JSONException e) {
            Log.w(TAG, "config.json unreadable, using the defaults", e);
            return FanHub.Config.defaults();
        }
    }
}
