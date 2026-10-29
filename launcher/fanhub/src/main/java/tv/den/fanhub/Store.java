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

    private static final long EXTRAS_EVERY = 6 * 60 * 60_000L;

    /**
     * Each team's recent form (TheSportsDB, free) and, for football with a football-data.org key, its place in the
     * league table, every 6 hours: {"FC Barcelona": {"form": "WWDLW", "code": "PD", "table": {...}}}. A league is
     * remembered once seen, so a cup tie as the next match doesn't lose the table. One call per team, one per league.
     */
    private static void extras(Context app, Map<String, List<FanHub.Turn>> by, long now) {
        SharedPreferences p = prefs(app);
        if (now - p.getLong("extras_at", 0) < EXTRAS_EVERY) return;
        FanHub.Config cfg = config(app);
        JSONObject old = extras(app), out = new JSONObject();
        Map<String, JSONObject> tables = new HashMap<>();
        for (FanHub.Team t : cfg.teams) {
            try {
                JSONObject e = new JSONObject(), prev = old.optJSONObject(t.name);
                try {
                    JSONObject last = FanHub.get("https://www.thesportsdb.com/api/v1/json/" + java.net.URLEncoder.encode(cfg.sportsdbKey, "UTF-8")
                            + "/eventslast.php?id=" + java.net.URLEncoder.encode(t.sportsdb, "UTF-8"));
                    e.put("form", FanHub.form(t.name, t.sport, last));
                } catch (java.io.IOException | JSONException x) { if (prev != null) e.put("form", prev.optString("form")); }
                if ("football".equals(t.sport) && !cfg.footballKey.isEmpty()) {
                    String code = "";
                    for (FanHub.Turn turn : by.getOrDefault("team:" + t.sportsdb, java.util.Collections.emptyList())) {
                        String[] dot = turn.topLine.split(" \u00B7 ");
                        if (dot.length > 1) code = FanHub.competition(dot[dot.length - 1]);
                    }
                    if (code.isEmpty() || code.equals("CL")) code = prev == null ? "" : prev.optString("code");
                    if (!code.isEmpty()) {
                        e.put("code", code);
                        try {
                            JSONObject st = tables.get(code);
                            if (st == null) { st = FanHub.request("https://api.football-data.org/v4/competitions/" + code + "/standings", null, "X-Auth-Token", cfg.footballKey); tables.put(code, st); }
                            JSONObject table = FanHub.table(t.name, st);
                            if (table != null) e.put("table", table);
                        } catch (java.io.IOException | JSONException x) { if (prev != null && prev.has("table")) e.put("table", prev.get("table")); }
                    }
                }
                out.put(t.name, e);
            } catch (JSONException ignored) { }
        }
        p.edit().putString("extras", out.toString()).putLong("extras_at", now).apply();
    }

    /** What extras() last found, by team name; {} before the first time. */
    static JSONObject extras(Context ctx) {
        try { return new JSONObject(prefs(ctx).getString("extras", "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    static String geminiKey(Context ctx) { return prefs(ctx).getString("key_gemini", ""); }

    static String geminiModel(Context ctx) { return prefs(ctx).getString("gemini_model", Gemini.DEFAULT_MODEL); }

    /** The last weekend forecast for the hike place, or null (the forecast is off, or not fetched yet). */
    static JSONObject forecast(Context ctx) {
        if (config(ctx).hike == null) return null;
        try { return new JSONObject(prefs(ctx).getString("forecast", "")); } catch (JSONException e) { return null; }
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
                SharedPreferences.Editor ed = prefs(app).edit().putString("by", j.toString()).putLong("fetched", now)
                        .putString("errors", String.join("\n", errors));
                JSONObject fc = FanHub.lastForecast;
                if (fc != null) ed.putString("forecast", fc.toString());
                ed.apply();
                extras(app, by, now);
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
                .put("gemini", !p.getString("key_gemini", "").isEmpty()).put("geminiModel", p.getString("gemini_model", Gemini.DEFAULT_MODEL))
                .put("fetched", fetchedAt(ctx)).put("errors", errors(ctx));
    }

    /** Saves a key ("" removes it) and fetches again. A Gemini key is tried on Gemini at once, and the answer said. */
    static org.json.JSONObject setLive(Context ctx, org.json.JSONObject in) throws JSONException {
        SharedPreferences.Editor e = prefs(ctx).edit();
        for (String k : new String[]{"football", "cricket"}) {
            if (!in.has(k)) continue;
            String raw = in.optString(k).trim(), v = FanHub.cleanKey(raw);
            if (raw.isEmpty()) e.remove("key_" + k);
            else if (v.isEmpty()) return live(ctx).put("ok", false).put("error", "That " + k + " key has characters a key can't have. Copy it again.");
            else e.putString("key_" + k, v);
        }
        if (in.has("geminiModel")) {
            String m = in.optString("geminiModel").trim();
            if (m.isEmpty()) e.remove("gemini_model"); else if (m.matches("[a-z0-9.-]{3,60}")) e.putString("gemini_model", m);
        }
        String check = null;
        if (in.has("gemini")) {
            // The Gemini key never comes back to a phone: live() says only whether there is one.
            String raw = in.optString("gemini").trim(), v = FanHub.cleanKey(raw);
            if (raw.isEmpty()) e.remove("key_gemini");
            else if (v.isEmpty()) return live(ctx).put("ok", false).put("error", "That Gemini key has characters a key can't have. Copy it again from aistudio.google.com.");
            else {
                e.putString("key_gemini", v);
                String model = in.optString("geminiModel", "").trim().isEmpty() ? geminiModel(ctx) : in.optString("geminiModel").trim();
                Gemini.Check c = Gemini.test(v, model);
                if (c.model != null && !c.model.equals(model)) e.putString("gemini_model", c.model);
                check = c.message;
                if (!c.ok) { e.apply(); return live(ctx).put("ok", false).put("error", "Saved, but " + c.message); }
            }
        }
        e.remove("extras_at");   // a new key: fetch the tables now, not in 6 hours
        e.apply();
        refresh(ctx, true);
        org.json.JSONObject out = live(ctx).put("ok", true);
        if (check != null) out.put("geminiCheck", check);
        return out;
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
