package tv.den.fanhub;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Screen time, the kids timer, the sleep timer and quiet hours. The helper says which app is in front; the service
 * calls tick() twice a minute. Everything stays on the TV, in this app's own preferences.
 */
final class Timers {
    private static final String PREFS = "timers";

    private Timers() {}

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }

    // ---- screen time ---------------------------------------------------------------------------------------------

    /** Minutes per app today, as {package: milliseconds}. */
    static synchronized JSONObject screenTime(Context ctx) {
        account(ctx, System.currentTimeMillis());
        return json(prefs(ctx), "st");
    }

    /**
     * Adds the time since the last note to the app that was in front, and to the person watching (DenPeople in the
     * launcher). At midnight a new day starts, and yesterday's apps go into the week's running total, which fades a
     * little each day (smart order sorts by it).
     */
    private static void account(Context ctx, long now) {
        SharedPreferences p = prefs(ctx);
        JSONObject st = json(p, "st"), stp = json(p, "stp");
        SharedPreferences.Editor e = p.edit();
        if (!today().equals(p.getString("st_date", ""))) {
            JSONObject wk = json(p, "wk"), next = new JSONObject();
            try {
                for (java.util.Iterator<String> it = wk.keys(); it.hasNext(); ) { String k = it.next(); long v = Math.round(wk.optLong(k) * 0.7); if (v > 60_000) next.put(k, v); }
                for (java.util.Iterator<String> it = st.keys(); it.hasNext(); ) { String k = it.next(); next.put(k, next.optLong(k) + st.optLong(k)); }
            } catch (JSONException ignored) { }
            e.putString("wk", next.toString());
            st = new JSONObject();
            stp = new JSONObject();
        }
        String front = p.getString("st_front", "");
        long since = p.getLong("st_since", now);
        if (!front.isEmpty() && now > since && now - since < 10 * 60_000L) {   // a long gap is the TV asleep, not watching
            String who = person(ctx);
            try {
                st.put(front, st.optLong(front) + (now - since));
                if (!who.isEmpty() && !front.equals("tv.den.launcher")) stp.put(who, stp.optLong(who) + (now - since));
            } catch (JSONException ignored) { }
        }
        e.putString("st", st.toString()).putString("stp", stp.toString()).putString("st_date", today()).putLong("st_since", now).apply();
    }

    private static JSONObject json(SharedPreferences p, String key) {
        try { return new JSONObject(p.getString(key, "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    private static String person;
    private static long personAt;

    /** The name of whoever is watching, from the launcher, asked at most once a minute. */
    private static String person(Context ctx) {
        long now = System.currentTimeMillis();
        if (person != null && now - personAt < 60_000L) return person;
        String who = "";
        try (android.database.Cursor c = ctx.getContentResolver().query(android.net.Uri.parse("content://tv.den.launcher.looks/people"), null, null, null, null)) {
            while (c != null && c.moveToNext()) if ("1".equals(c.getString(5))) who = c.getString(1);
        } catch (RuntimeException ignored) { }
        person = who;
        personAt = now;
        return who;
    }

    /** Whoever is watching, by name ("" with one person, or no launcher). */
    static String who(Context ctx) { return person(ctx); }

    /** When each app was last left, as {package: ms}. */
    static synchronized JSONObject lastLeft(Context ctx) { return json(prefs(ctx), "last"); }

    /** Minutes per person today, as {name: milliseconds}. */
    static synchronized JSONObject byPerson(Context ctx) {
        account(ctx, System.currentTimeMillis());
        return json(prefs(ctx), "stp");
    }

    /** The apps by how much they were watched lately (this week, fading, plus today), most first. */
    static synchronized java.util.List<String> mostWatched(Context ctx) {
        account(ctx, System.currentTimeMillis());
        JSONObject wk = json(prefs(ctx), "wk"), st = json(prefs(ctx), "st");
        java.util.Map<String, Long> total = new java.util.HashMap<>();
        for (JSONObject j : new JSONObject[]{wk, st}) for (java.util.Iterator<String> it = j.keys(); it.hasNext(); ) { String k = it.next(); total.merge(k, j.optLong(k), Long::sum); }
        total.remove("tv.den.launcher");
        total.remove(ctx.getPackageName());
        java.util.List<String> out = new java.util.ArrayList<>(total.keySet());
        out.sort((a, b) -> Long.compare(total.get(b), total.get(a)));
        return out;
    }

    static synchronized void foreground(Context ctx, String pkg) {
        long now = System.currentTimeMillis();
        account(ctx, now);
        // When each app was last left, for the launcher's CONTINUE badge (StatsProvider).
        String prev = prefs(ctx).getString("st_front", "");
        JSONObject last = json(prefs(ctx), "last");
        if (!prev.isEmpty() && !prev.equals(pkg)) try { last.put(prev, now); } catch (JSONException ignored) { }
        prefs(ctx).edit().putString("st_front", pkg).putString("last", last.toString()).apply();
        Store.worker().post(() -> tick(ctx));
    }

    // ---- the checks ----------------------------------------------------------------------------------------------

    static synchronized void tick(Context ctx) {
        long now = System.currentTimeMillis();
        account(ctx, now);
        SharedPreferences p = prefs(ctx);
        // Sleep timer.
        long sleepAt = p.getLong("sleep_at", 0);
        if (sleepAt > 0 && now >= sleepAt) {
            p.edit().remove("sleep_at").apply();
            if (!Helper.sleep()) Helper.home();
        }
        // Kids timer: once today's minutes in the chosen apps reach the limit, those apps go back Home.
        if (p.getBoolean("kids_on", false)) {
            JSONArray apps;
            try { apps = new JSONArray(p.getString("kids_apps", "[]")); } catch (JSONException e) { apps = new JSONArray(); }
            String front = Helper.front();
            boolean kidsApp = false;
            long used = 0;
            JSONObject st;
            try { st = new JSONObject(p.getString("st", "{}")); } catch (JSONException e) { st = new JSONObject(); }
            for (int i = 0; i < apps.length(); i++) {
                used += st.optLong(apps.optString(i));
                if (apps.optString(i).equals(front)) kidsApp = true;
            }
            if (kidsApp && used >= p.getInt("kids_min", 60) * 60_000L) {
                Helper.home();
                toast(ctx, "That's today's time for these apps");
            }
        }
        // Smart order, once a day when it is on: the most watched apps first.
        if (p.getBoolean("smart_order", false) && !today().equals(p.getString("smart_day", ""))) {
            p.edit().putString("smart_day", today()).apply();
            sortApps(ctx);
        }
        // Quiet hours: the volume stays at or under the cap.
        if (p.getBoolean("quiet_on", false) && inside(p.getString("quiet_from", "22:30"), p.getString("quiet_to", "07:00"))) {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cap = Math.round(max * p.getInt("quiet_cap", 30) / 100f);
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
        }
    }

    /** Whether now is between from and to ("22:30" to "07:00" runs over midnight). */
    static boolean inside(String from, String to) {
        Calendar c = Calendar.getInstance();
        return inside(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), minutes(from), minutes(to));
    }

    static boolean inside(int now, int from, int to) {
        if (from < 0 || to < 0 || from == to) return false;
        return from < to ? now >= from && now < to : now >= from || now < to;
    }

    static int minutes(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) return -1;
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
        return h < 24 && m < 60 ? h * 60 + m : -1;
    }

    private static void toast(Context ctx, String s) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx.getApplicationContext(), s, Toast.LENGTH_LONG).show());
    }

    // ---- what the phone reads and sets ---------------------------------------------------------------------------

    static JSONObject get(Context ctx) throws JSONException {
        SharedPreferences p = prefs(ctx);
        long sleepAt = p.getLong("sleep_at", 0);
        JSONArray apps;
        try { apps = new JSONArray(p.getString("kids_apps", "[]")); } catch (JSONException e) { apps = new JSONArray(); }
        return new JSONObject()
                .put("sleepIn", sleepAt > System.currentTimeMillis() ? Math.round((sleepAt - System.currentTimeMillis()) / 60000.0) : 0)
                .put("kids", new JSONObject().put("on", p.getBoolean("kids_on", false)).put("minutes", p.getInt("kids_min", 60)).put("apps", apps))
                .put("quiet", new JSONObject().put("on", p.getBoolean("quiet_on", false)).put("from", p.getString("quiet_from", "22:30"))
                        .put("to", p.getString("quiet_to", "07:00")).put("cap", p.getInt("quiet_cap", 30)));
    }

    static JSONObject set(Context ctx, JSONObject in) throws JSONException {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (in.has("sleepIn")) {
            int m = in.optInt("sleepIn");
            if (m <= 0) e.remove("sleep_at"); else e.putLong("sleep_at", System.currentTimeMillis() + Math.min(m, 600) * 60_000L);
        }
        JSONObject k = in.optJSONObject("kids");
        if (k != null) {
            e.putBoolean("kids_on", k.optBoolean("on"));
            e.putInt("kids_min", Math.max(5, Math.min(600, k.optInt("minutes", 60))));
            JSONArray apps = k.optJSONArray("apps");
            if (apps != null) e.putString("kids_apps", apps.toString());
        }
        JSONObject q = in.optJSONObject("quiet");
        if (q != null) {
            e.putBoolean("quiet_on", q.optBoolean("on"));
            if (minutes(q.optString("from")) >= 0) e.putString("quiet_from", q.optString("from"));
            if (minutes(q.optString("to")) >= 0) e.putString("quiet_to", q.optString("to"));
            e.putInt("quiet_cap", Math.max(5, Math.min(100, q.optInt("cap", 30))));
        }
        e.apply();
        return get(ctx).put("ok", true);
    }

    /** Sends the most-watched order to the launcher; false when it can't (no launcher, or nothing watched yet). */
    static boolean sortApps(Context ctx) {
        java.util.List<String> order = mostWatched(ctx);
        if (order.isEmpty()) return false;
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("order", String.join(",", order));
        try { return ctx.getContentResolver().update(android.net.Uri.parse("content://tv.den.launcher.looks/order"), v, null, null) > 0; }
        catch (RuntimeException e) { return false; }
    }

    /** Opens an app, through the helper when another app is in front (Android blocks ordinary apps from that). */
    static boolean open(Context ctx, Intent i) {
        if (Helper.start(i)) return true;
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
