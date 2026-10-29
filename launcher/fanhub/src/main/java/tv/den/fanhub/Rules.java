package tv.den.fanhub;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto: the look changes by itself for the moment, the way the mockup's rules do. Two hours before one of your
 * teams plays, its look goes on until the match ends; a new episode puts its show's look on for the evening;
 * late at night it goes to Midnight Lounge. When no rule applies any more, the look you had comes back. Off until
 * you switch it on from the phone; each rule can be switched off on its own.
 */
final class Rules {
    private static final String LOOKS = "content://tv.den.launcher.looks/looks", CURRENT = "content://tv.den.launcher.looks/current";
    static final String[][] RULES = {
            {"match", "Match day", "Two hours before kick-off, your team's look until the final whistle"},
            {"cricket", "India and IPL", "The same for India and your IPL team"},
            {"episode", "New episode", "A show's look for the evening its new episode comes out"},
            {"late", "Late night", "Midnight Lounge from 11 at night to 5 in the morning"},
            {"festival", "Festivals", "Diwali Night for the five days of Diwali, Neon City on New Year's Eve"},
    };
    // In order: "mumbai indians" before "india", which it contains.
    private static final Map<String, String> TEAM_LOOK = new java.util.LinkedHashMap<>(), SHOW_LOOK = new HashMap<>();
    static {
        String[][] t = {{"barcelona", "camp-nou"}, {"real madrid", "clasico"}, {"argentina", "albiceleste"}, {"mumbai indians", "wankhede"},
                {"royal challengers", "king-kohli"}, {"india", "blue-billion"}};
        for (String[] x : t) TEAM_LOOK.put(x[0], x[1]);
        String[][] s = {{"One Piece", "straw-hat"}, {"Jujutsu Kaisen", "domain"}, {"Demon Slayer", "water-breathing"}, {"Naruto", "nine-tails"},
                {"Dragon Ball", "super-saiyan"}, {"Solo Leveling", "arise"}, {"Attack on Titan", "strongest"}, {"Bleach", "bankai"}};
        for (String[] x : s) SHOW_LOOK.put(x[0].toLowerCase(), x[1]);
    }

    private Rules() {}

    /** Lakshmi Puja, the main night of Diwali (it moves with the lunar calendar), 2025 to 2035. */
    private static final String[] DIWALI = {"2025-10-20", "2026-11-08", "2027-10-29", "2028-10-17", "2029-11-05", "2030-10-26",
            "2031-11-14", "2032-11-02", "2033-10-22", "2034-11-10", "2035-10-30"};

    /** "diwali" from two days before Lakshmi Puja to two days after, "newyear" from 6pm on 31 December; else null. */
    static String festival(java.time.LocalDateTime now) {
        java.time.LocalDate today = now.toLocalDate();
        for (String d : DIWALI) {
            java.time.LocalDate puja = java.time.LocalDate.parse(d);
            if (!today.isBefore(puja.minusDays(2)) && !today.isAfter(puja.plusDays(2))) return "diwali";
        }
        if ((now.getMonthValue() == 12 && now.getDayOfMonth() == 31 && now.getHour() >= 18) || (now.getMonthValue() == 1 && now.getDayOfMonth() == 1 && now.getHour() < 3)) return "newyear";
        return null;
    }

    /** The look for a team, by name ("FC Barcelona" -> Camp Nou Nights); null for a team without one. */
    static String teamLook(String name) {
        String n = name.toLowerCase();
        for (Map.Entry<String, String> e : TEAM_LOOK.entrySet()) if (n.contains(e.getKey())) return e.getValue();
        return null;
    }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences("rules", Context.MODE_PRIVATE); }

    /** The look the rules want now, with the reason; null when none applies. */
    static String[] want(Context ctx, long now) {
        SharedPreferences p = prefs(ctx);
        Map<String, List<FanHub.Turn>> by = Store.bySource(ctx);
        FanHub.Config cfg = Store.config(ctx);
        for (FanHub.Team team : cfg.teams) {
            boolean cricket = "cricket".equals(team.sport);
            if (!p.getBoolean(cricket ? "r_cricket" : "r_match", true)) continue;
            List<FanHub.Turn> turns = by.get("team:" + team.sportsdb);
            String look = teamLook(team.name);
            if (turns == null || look == null) continue;
            for (FanHub.Turn t : turns) if (now >= t.start - 2 * 3_600_000L && now < t.end) return new String[]{look, team.name + " plays today"};
        }
        if (p.getBoolean("r_episode", true)) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            for (String show : cfg.shows) {
                String look = SHOW_LOOK.get(show.toLowerCase());
                List<FanHub.Turn> turns = by.get("show:" + show);
                if (look == null || turns == null) continue;
                for (FanHub.Turn t : turns) if (now >= t.start && now - t.start < 8 * 3_600_000L && hour >= 17) return new String[]{look, "New " + show + " episode"};
            }
        }
        if (p.getBoolean("r_festival", true)) {
            String f = festival(java.time.LocalDateTime.now());
            if (f != null) return new String[]{f.equals("diwali") ? "diwali" : "neon", f.equals("diwali") ? "Diwali" : "New Year's Eve"};
        }
        if (p.getBoolean("r_late", true)) {
            Calendar c = Calendar.getInstance();
            if (Timers.inside(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), 23 * 60, 5 * 60)) return new String[]{"midnight-lounge", "Late night"};
        }
        return null;
    }

    /** Applies the rules once; called by the service every half minute. */
    static synchronized void tick(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.getBoolean("auto", false)) return;
        String[] w = want(ctx, System.currentTimeMillis());
        String active = p.getString("active", "");
        String current = current(ctx);
        if (w != null) {
            if (w[0].equals(current)) return;
            // Keep the look you chose, to bring back later; not one a rule put on.
            if (active.isEmpty()) p.edit().putString("before", current == null ? "rotate" : current).apply();
            if (set(ctx, w[0])) p.edit().putString("active", w[0]).apply();
        } else if (!active.isEmpty()) {
            // Only put yours back if the rule's look is still on: a look you picked meanwhile stays.
            if (active.equals(current)) set(ctx, p.getString("before", "rotate"));
            p.edit().remove("active").remove("before").apply();
        }
    }

    static String current(Context ctx) {
        try (Cursor c = ctx.getContentResolver().query(Uri.parse(LOOKS), null, null, null, null)) {
            while (c != null && c.moveToNext()) if (c.getInt(3) == 1) return c.getString(0);
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static boolean set(Context ctx, String id) {
        ContentValues v = new ContentValues();
        v.put("look", id);
        try { return ctx.getContentResolver().update(Uri.parse(CURRENT), v, null, null) == 1; } catch (RuntimeException e) { return false; }
    }

    static JSONObject get(Context ctx) throws JSONException {
        SharedPreferences p = prefs(ctx);
        org.json.JSONArray list = new org.json.JSONArray();
        for (String[] r : RULES) list.put(new JSONObject().put("id", r[0]).put("name", r[1]).put("note", r[2]).put("on", p.getBoolean("r_" + r[0], true)));
        String[] w = want(ctx, System.currentTimeMillis());
        return new JSONObject().put("auto", p.getBoolean("auto", false)).put("rules", list)
                .put("now", w == null ? JSONObject.NULL : new JSONObject().put("look", w[0]).put("why", w[1]));
    }

    static JSONObject set(Context ctx, JSONObject in) throws JSONException {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (in.has("auto")) e.putBoolean("auto", in.optBoolean("auto"));
        JSONObject r = in.optJSONObject("rules");
        if (r != null) for (String[] x : RULES) if (r.has(x[0])) e.putBoolean("r_" + x[0], r.optBoolean(x[0]));
        e.apply();
        if (in.has("auto") && !in.optBoolean("auto")) {
            // Switching Auto off puts your own look back if a rule's is on.
            SharedPreferences p = prefs(ctx);
            String active = p.getString("active", "");
            if (!active.isEmpty() && active.equals(current(ctx))) set(ctx, p.getString("before", "rotate"));
            p.edit().remove("active").remove("before").apply();
        }
        Store.worker().post(() -> tick(ctx));
        return get(ctx).put("ok", true);
    }
}
