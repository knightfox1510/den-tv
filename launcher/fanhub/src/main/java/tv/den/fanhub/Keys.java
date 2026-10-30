package tv.den.fanhub;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 * The keys the Fan Hub can use, and how each one is doing, for the phone's TV tab: what it unlocks and where to get
 * one, whether it's added (shown only by its last four characters: the phone never gets a key back), and whether it
 * works, with why not and when that was last known. A key is checked when it's saved, when Test is pressed, once a
 * day when the phone asks, and every time the Fan Hub uses it (Store.refresh, LookArt).
 */
final class Keys {
    /** id, name, what it unlocks, where to get one. */
    static final String[][] SERVICES = {
            {"gemini", "Gemini", "Describe a look: Gemini designs new looks and can paint their wallpapers.", "https://aistudio.google.com/app/apikey"},
            {"football", "football-data.org", "Football scores during play, goal moments, and league tables in the Fan Hub.", "https://www.football-data.org/client/register"},
            {"cricket", "CricketData.org", "Cricket scores during play, and moments for sixes and fifties.", "https://cricketdata.org/"}};
    private static final long RECHECK = 24 * 3_600_000L;

    private Keys() {}

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences("fanhub", Context.MODE_PRIVATE); }

    static String key(Context ctx, String id) { return prefs(ctx).getString("key_" + id, ""); }

    /** "••••ab12": enough to tell which key it is, not enough to use it. */
    static String tail(String key) {
        return key.length() < 12 ? "••••" : "••••" + key.substring(key.length() - 4);
    }

    /** What's known about a key now: ok or not, why, and when. */
    static void record(Context ctx, String id, boolean ok, String message) {
        try {
            prefs(ctx).edit().putString("check_" + id, new JSONObject().put("ok", ok).put("message", message)
                    .put("at", System.currentTimeMillis()).put("tail", tail(key(ctx, id))).toString()).apply();
        } catch (JSONException ignored) { }
    }

    /** After a fetch: a keyed source with no error worked; one with an error says what went wrong. */
    static void afterFetch(Context ctx, List<String> errors) {
        String[][] sources = {{"football", "football-data.org:"}, {"cricket", "CricketData.org:"}};
        for (String[] s : sources) {
            if (key(ctx, s[0]).isEmpty()) continue;
            String failed = null;
            for (String e : errors) if (e.startsWith(s[1])) failed = e.substring(s[1].length()).trim();
            if (failed == null) record(ctx, s[0], true, "Working: used for scores just now.");
            else record(ctx, s[0], false, explain(s[0], failed));
        }
    }

    /** Whether Gemini could paint a wallpaper for a described look the last time it was asked. */
    static void pictures(Context ctx, boolean ok, String message) {
        prefs(ctx).edit().putBoolean("pictures_ok", ok).putString("pictures", message).putLong("pictures_at", System.currentTimeMillis()).apply();
    }

    /** Every service with its key's state, for the phone. Keys never checked (or not for a day) are checked meanwhile. */
    static JSONObject status(Context ctx) throws JSONException {
        JSONArray out = new JSONArray();
        long now = System.currentTimeMillis();
        for (String[] s : SERVICES) {
            String key = key(ctx, s[0]);
            JSONObject row = new JSONObject().put("id", s[0]).put("name", s[1]).put("unlocks", s[2]).put("get", s[3]).put("set", !key.isEmpty());
            if (!key.isEmpty()) {
                row.put("tail", tail(key));
                JSONObject check = null;
                try { check = new JSONObject(prefs(ctx).getString("check_" + s[0], "")); } catch (JSONException ignored) { }
                // A check of another key (since replaced) doesn't count.
                if (check != null && !tail(key).equals(check.optString("tail"))) check = null;
                if (check == null) row.put("state", "checking");
                else row.put("state", check.optBoolean("ok") ? "ok" : "problem").put("message", check.optString("message")).put("at", check.optLong("at"));
                if (check == null || now - check.optLong("at") > RECHECK) Store.worker().post(() -> test(ctx, s[0]));
            } else {
                row.put("state", "none");
            }
            if (s[0].equals("gemini")) {
                row.put("model", Store.geminiModel(ctx));
                if (prefs(ctx).contains("pictures")) row.put("pictures", prefs(ctx).getBoolean("pictures_ok", false)).put("picturesNote", prefs(ctx).getString("pictures", ""));
            }
            out.put(row);
        }
        return new JSONObject().put("keys", out);
    }

    /** Tries a key on its service now and records what it said. */
    static void test(Context ctx, String id) {
        String key = key(ctx, id);
        if (key.isEmpty()) return;
        switch (id) {
            case "gemini": {
                Gemini.Check c = Gemini.test(key, Store.geminiModel(ctx));
                record(ctx, id, c.ok, c.ok ? "Working: " + c.message.replaceFirst("^Gemini answered: the key works", "Gemini answered").trim() : c.message);
                break;
            }
            case "football":
                try {
                    FanHub.request("https://api.football-data.org/v4/competitions/PD", null, "X-Auth-Token", key);
                    record(ctx, id, true, "Working: football-data.org answered.");
                } catch (IOException | JSONException e) {
                    record(ctx, id, false, explain(id, String.valueOf(e.getMessage())));
                }
                break;
            case "cricket":
                try {
                    JSONObject r = FanHub.get("https://api.cricapi.com/v1/currentMatches?offset=0&apikey=" + URLEncoder.encode(key, "UTF-8"));
                    if ("success".equals(r.optString("status"))) {
                        JSONObject info = r.optJSONObject("info");
                        String used = info == null || !info.has("hitsLimit") ? "" : " " + info.optInt("hitsToday") + " of " + info.optInt("hitsLimit") + " calls used today.";
                        record(ctx, id, true, "Working: CricketData.org answered." + used);
                    } else {
                        record(ctx, id, false, explain(id, r.optString("reason", "refused")));
                    }
                } catch (IOException | JSONException e) {
                    record(ctx, id, false, explain(id, String.valueOf(e.getMessage())));
                }
                break;
            default:
        }
    }

    /** A service's error in words a person can act on. */
    static String explain(String id, String error) {
        String e = error.toLowerCase(java.util.Locale.ROOT);
        String name = id.equals("football") ? "football-data.org" : id.equals("cricket") ? "CricketData.org" : "Gemini";
        if (e.contains("http 400") || e.contains("http 401") || e.contains("http 403") || e.contains("invalid") || e.contains("api key"))
            return name + " refused this key. Copy it again from your account there, or make a new one.";
        if (e.contains("http 429") || e.contains("limit") || e.contains("blocked for"))
            return "The key works, but today's free calls are used up. It picks up again by itself.";
        if (e.contains("unable to resolve") || e.contains("timeout") || e.contains("timed out") || e.contains("failed to connect"))
            return "The TV couldn't reach " + name + ". Check its internet.";
        return name + ": " + error;
    }

    static void remove(Context ctx, String id) {
        prefs(ctx).edit().remove("key_" + id).remove("check_" + id).apply();
        if (id.equals("gemini")) prefs(ctx).edit().remove("pictures").remove("pictures_ok").remove("pictures_at").apply();
    }
}
