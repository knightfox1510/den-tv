package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Your own characters, made from pictures sent from the phone: a body picture (a cut-out PNG works best) and,
 * optionally, the same picture with the eyes closed, for blinking. One can be on Home at a time, as a big figure
 * on the right that leans toward the focused tile, or as a walker that walks to it. They stay on this TV only.
 */
final class DenCharacters {
    static final int MAX = 8;
    private static final String KEY = "characters", USE = "character_use", MODE = "character_mode";

    static final class Character {
        final String id, name;
        final boolean closed;
        Character(String id, String name, boolean closed) { this.id = id; this.name = name; this.closed = closed; }
    }

    private DenCharacters() {}

    static File dir(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        return base == null ? null : new File(base, "characters");
    }

    static List<Character> all(Context ctx) {
        List<Character> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(DenSettings.prefs(ctx).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                File d = dir(ctx);
                String id = o.optString("id");
                if (d == null || !new File(d, id + ".png").isFile()) continue;
                out.add(new Character(id, o.optString("name"), new File(d, id + "-closed.png").isFile()));
            }
        } catch (JSONException ignored) { }
        return out;
    }

    /** Adds a character whose body was just written as id.png; false when there are too many already. */
    static boolean add(Context ctx, String id, String name) {
        List<Character> all = all(ctx);
        if (all.size() >= MAX || !id.matches("c\\d{1,16}")) return false;
        JSONArray a = new JSONArray();
        try {
            for (Character c : all) a.put(new JSONObject().put("id", c.id).put("name", c.name));
            a.put(new JSONObject().put("id", id).put("name", name.length() > 24 ? name.substring(0, 24) : name));
        } catch (JSONException e) { return false; }
        DenSettings.prefs(ctx).edit().putString(KEY, a.toString()).apply();
        return true;
    }

    static boolean delete(Context ctx, String id) {
        List<Character> all = all(ctx);
        JSONArray a = new JSONArray();
        boolean found = false;
        try {
            for (Character c : all) { if (c.id.equals(id)) { found = true; continue; } a.put(new JSONObject().put("id", c.id).put("name", c.name)); }
        } catch (JSONException e) { return false; }
        if (!found) return false;
        DenSettings.prefs(ctx).edit().putString(KEY, a.toString()).apply();
        if (id.equals(inUse(ctx))) use(ctx, "", "off");
        File d = dir(ctx);
        if (d != null) { //noinspection ResultOfMethodCallIgnored
            new File(d, id + ".png").delete();
            //noinspection ResultOfMethodCallIgnored
            new File(d, id + "-closed.png").delete();
        }
        return true;
    }

    static String inUse(Context ctx) { return DenSettings.prefs(ctx).getString(USE, ""); }

    /** "figure" or "walker". */
    static String mode(Context ctx) { return DenSettings.prefs(ctx).getString(MODE, "figure"); }

    static void use(Context ctx, String id, String mode) {
        DenSettings.prefs(ctx).edit().putString(USE, "off".equals(mode) ? "" : id).putString(MODE, "walker".equals(mode) ? "walker" : "figure").apply();
    }

    /** The picture, scaled down to at most maxH pixels tall; null if it is missing. */
    static Bitmap picture(Context ctx, String id, boolean closed, int maxH) {
        File d = dir(ctx);
        if (d == null) return null;
        File f = new File(d, id + (closed ? "-closed" : "") + ".png");
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outHeight / (sample * 2) >= maxH) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
