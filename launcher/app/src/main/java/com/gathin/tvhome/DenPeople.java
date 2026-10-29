package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Who's watching: a few people on this TV, each with a name for the greeting, a colour and the look they last
 * chose. A guest sees only looks marked "everyone", no match panel and no name, and the TV goes back to the
 * first person when it sleeps. The people belong to this TV; a friend's TV has its own.
 */
final class DenPeople {
    static final int MAX = 6;
    private static final String KEY = "people", WHO = "who";

    static final class Person {
        final String id, name, colour, look;
        final boolean guest;

        Person(String id, String name, String colour, String look, boolean guest) {
            this.id = id; this.name = name; this.colour = colour; this.look = look; this.guest = guest;
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("colour", colour).put("look", look).put("guest", guest);
        }
    }

    private DenPeople() {}

    private static SharedPreferences prefs(Context ctx) { return DenSettings.prefs(ctx); }

    /** Everyone, the first person first. Before anyone is added: you (by the setup's name) and a guest. */
    static List<Person> all(Context ctx) {
        List<Person> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Person(o.optString("id"), o.optString("name"), o.optString("colour", "#ffd36e"), o.optString("look", ""), o.optBoolean("guest")));
            }
        } catch (JSONException ignored) { }
        if (out.isEmpty()) {
            String me = prefs(ctx).getString(DenSettings.NAME, "");
            out.add(new Person("me", me.isEmpty() ? "Me" : me, "#ffd36e", "", false));
            out.add(new Person("guest", "Guest", "#9fe6ff", "", true));
        }
        return out;
    }

    static void save(Context ctx, List<Person> people) {
        JSONArray a = new JSONArray();
        for (Person p : people) try { a.put(p.json()); } catch (JSONException ignored) { }
        prefs(ctx).edit().putString(KEY, a.toString()).apply();
    }

    /** The person watching; null only if the list is somehow empty. */
    static Person current(Context ctx) {
        List<Person> all = all(ctx);
        String who = prefs(ctx).getString(WHO, "");
        for (Person p : all) if (p.id.equals(who)) return p;
        return all.isEmpty() ? null : all.get(0);
    }

    static Person byId(Context ctx, String id) {
        for (Person p : all(ctx)) if (p.id.equals(id)) return p;
        return null;
    }

    /** Switches to someone, puts their look on, and asks Home to rebuild. */
    static boolean use(Context ctx, String id) {
        Person p = byId(ctx, id);
        if (p == null) return false;
        prefs(ctx).edit().putString(WHO, id).apply();
        List<DenLooks.Look> looks = DenLooks.load(ctx);
        if (!p.look.isEmpty()) {
            DenLooks.Look l = DenLooks.byId(looks, p.look);
            if (l != null && (!p.guest || "everyone".equals(l.audience))) DenLooks.apply(ctx, l);
            else if (DenLooks.ROTATE.equals(p.look)) DenLooks.apply(ctx, null);
        } else if (p.guest) {
            DenLooks.Look cur = DenLooks.current(ctx, looks);
            if (cur != null && !"everyone".equals(cur.audience)) DenLooks.apply(ctx, null);
        }
        bump(ctx);
        return true;
    }

    /** The next person after the one watching (the TV's Looks screen cycles with OK). */
    static Person next(Context ctx) {
        List<Person> all = all(ctx);
        Person cur = current(ctx);
        int i = cur == null ? -1 : indexOf(all, cur.id);
        return all.get((i + 1) % all.size());
    }

    private static int indexOf(List<Person> all, String id) {
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(id)) return i;
        return -1;
    }

    /** The look just put on becomes the current person's own. */
    static void remember(Context ctx, String lookId) {
        List<Person> all = all(ctx);
        Person cur = current(ctx);
        if (cur == null) return;
        int i = indexOf(all, cur.id);
        if (i < 0) return;
        all.set(i, new Person(cur.id, cur.name, cur.colour, lookId, cur.guest));
        save(ctx, all);
    }

    /** Back to the first person (the TV went to sleep while a guest was watching). */
    static void endGuest(Context ctx) {
        Person cur = current(ctx);
        if (cur != null && cur.guest) use(ctx, all(ctx).get(0).id);
    }

    /** Adds, renames or recolours someone (by id; a new id adds), or removes them; false when it can't. */
    static boolean edit(Context ctx, String id, String name, String colour, Boolean guest, boolean remove) {
        List<Person> all = all(ctx);
        int i = indexOf(all, id);
        if (remove) {
            if (i <= 0) return false;   // the first person stays
            all.remove(i);
            save(ctx, all);
            if (id.equals(prefs(ctx).getString(WHO, ""))) use(ctx, all.get(0).id);
            return true;
        }
        String n = name == null ? null : name.trim();
        if (n != null && n.length() > 20) n = n.substring(0, 20);
        String c = colour != null && colour.matches("#[0-9a-fA-F]{6}") ? colour : null;
        if (i < 0) {
            if (all.size() >= MAX || n == null || n.isEmpty()) return false;
            all.add(new Person("p" + System.currentTimeMillis(), n, c == null ? "#ff9ec4" : c, "", guest != null && guest));
        } else {
            Person p = all.get(i);
            all.set(i, new Person(p.id, n == null || n.isEmpty() ? p.name : n, c == null ? p.colour : c, p.look, i > 0 && guest != null ? guest : p.guest));
        }
        save(ctx, all);
        bump(ctx);
        return true;
    }

    private static void bump(Context ctx) {
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }
}
