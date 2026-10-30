package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * den-tv looks: the recipes from the repo's looks/ folder (bundled under assets/den/looks), each matched to its
 * den-mix wallpaper. Putting a look on uses the launcher's own wallpaper choice, so Home rebuilds with it the
 * same way as after Launcher settings; "rotate" clears the choice and the theme's rotation takes over again.
 */
final class DenLooks {
    static final String PREFS = "den";
    static final String KEY_LOOK = "look";
    static final String ROTATE = "rotate";
    static final String ASSET_DIR = "den/looks";

    /** Watchers drawn as a pair of eyes (DenEyesView); DenWatcherView draws the rest. */
    static final List<String> EYES = List.of("sharingan", "mangekyo", "six", "saiyan", "ui", "kurama", "sage", "rinnegan");

    static final class Look {
        final String id, name, category, watcher, wake, accent, wallpaper;

        Look(String id, String name, String category, String watcher, String wake, String accent, String wallpaper) {
            this.id = id; this.name = name; this.category = category; this.watcher = watcher; this.wake = wake;
            this.accent = accent; this.wallpaper = wallpaper;
        }

        // Optional parts of the recipe (looks/README.md); empty when the look leaves them to the launcher.
        String clock = "", icons = "", iconLayout = "", hero = "", scene = "", audience = "everyone", moment = "none", extras = "auto",
                figure = "", focus = "";
        boolean hud;
        /** Place in the gallery: category blocks of 100 (looks/README.md). */
        int order = 9000;
        /** A photo look's photo (photos/photo-….jpg); "" for every other look. Its wallpaper is then PHOTO_FILE. */
        String photo = "";
        /** Words about the look beyond its name (hero, scene, its texts, tags), for describing a look in words. */
        String words = "";
        /** A described look's own "Did you know?" facts (Gemini's); empty: the facts of its hero or scene. */
        List<String> facts = new ArrayList<>();

        boolean hasEyes() { return EYES.contains(watcher); }
    }

    private DenLooks() {}

    /** The looks that have a wallpaper in the theme folder, in den-mix order (the number in front of each file name). */
    static List<Look> load(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        List<String> walls = new ArrayList<>();
        String[] files = dir == null ? null : new File(dir, "wallpapers").list();
        if (files != null) Collections.addAll(walls, files);
        Collections.sort(walls);
        List<Look> looks = new ArrayList<>();
        String[] names;
        try { names = ctx.getAssets().list(ASSET_DIR); } catch (IOException e) { names = null; }
        if (names == null) return looks;
        List<JSONObject> recipes = new ArrayList<>();
        for (String n : names) {
            if (!n.endsWith(".json")) continue;
            try { recipes.add(new JSONObject(read(ctx, ASSET_DIR + "/" + n))); } catch (IOException | JSONException ignored) { }
        }
        java.util.Map<String, String> sceneWall = new java.util.HashMap<>();
        List<JSONObject> left = new ArrayList<>(recipes);
        for (String wall : walls) {
            String id = wallpaperLookId(wall);
            if (id == null) continue;
            if (id.startsWith("hero-")) sceneWall.putIfAbsent("black", wall);   // the hero posters, for Hero Wall
            for (JSONObject j : recipes) {
                if (!id.equals(j.optString("id"))) continue;
                looks.add(look(j, wall));
                sceneWall.putIfAbsent(j.optString("scene"), wall);
                left.remove(j);
                break;
            }
        }
        // A look with no wallpaper of its own (Blue Billion, Our Wall, Hero Wall) borrows one painted from its scene.
        for (JSONObject j : left) {
            String wall = sceneWall.get(j.optString("scene"));
            if (wall != null) looks.add(look(j, wall));
        }
        // The gallery's order everywhere (the TV's picker, the phone, the provider): by category, then each look's
        // place in it ("order" in looks/*.json; a look without one goes to the end).
        looks.sort((a, b) -> Integer.compare(a.order, b.order));
        // Your own looks, made on the phone: each starts from a look's wallpaper and scene, with its own parts.
        try {
            JSONArray mine = new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MINE, "[]"));
            for (int i = 0; i < mine.length(); i++) {
                JSONObject j = mine.getJSONObject(i);
                Look base = byId(looks, j.optString("base"));
                if (base == null) continue;
                // A photo look: one of your photos as the wallpaper, with the base look's parts. Gone with its photo.
                String photo = j.optString("photo", "");
                File photos = DenPhotos.dir(ctx);
                if (!photo.isEmpty() && (!photo.matches(DenPhotos.NAME) || photo.endsWith("-p.jpg") || photos == null || !new File(photos, photo).isFile())) continue;
                // A described look (made) has its own painted wallpaper, kept as a photo, but it's a look, not a photo.
                boolean made = j.optBoolean("made");
                JSONObject recipe = new JSONObject().put("id", j.optString("id")).put("name", j.optString("name", "My look"))
                        .put("category", photo.isEmpty() || made ? "My looks" : "My photos")
                        .put("for", "owner").put("scene", base.scene).put("hero", base.hero);
                if (j.has("facts")) recipe.put("facts", j.optJSONArray("facts"));
                for (String k : new String[]{"watcher", "clock", "icons", "icon_layout", "focus", "wake", "extras", "accent", "moment"}) {
                    if (j.has(k)) recipe.put(k, j.optString(k));
                }
                Look own = look(recipe, photo.isEmpty() ? base.wallpaper : DenSettings.PHOTO_FILE);
                own.photo = photo;
                looks.add(own);
            }
        } catch (JSONException ignored) { }
        return looks;
    }

    /** Your own looks, as {"id": "mine-...", "name", "base": a look's id, and any parts}. */
    static final String MINE = "mine";

    /** Saves a look made on the phone (a new id adds, an existing one replaces); its id, or null. */
    static String saveMine(Context ctx, JSONObject in) {
        String id = in.optString("id");
        if (id.isEmpty()) id = "mine-" + System.currentTimeMillis();
        if (!id.matches("mine-\\d{1,16}") || in.optString("base").isEmpty()) return null;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(p.getString(MINE, "[]")), out = new JSONArray();
            String keptPhoto = "";
            for (int i = 0; i < old.length(); i++) {
                if (!id.equals(old.getJSONObject(i).optString("id"))) out.put(old.getJSONObject(i));
                else keptPhoto = old.getJSONObject(i).optString("photo", "");   // editing a photo look keeps its photo
            }
            if (out.length() >= 20) return null;
            String name = in.optString("name", "").trim();
            if (name.length() > 40) name = name.substring(0, 40);
            JSONObject j = new JSONObject().put("id", id).put("name", name.isEmpty() ? "My look" : name)
                    .put("base", in.optString("base"));
            String photo = in.has("photo") ? in.optString("photo", "") : keptPhoto;
            if (!photo.isEmpty()) {
                // Only your everyday photos: a private one (-p) never becomes a look anyone can pick.
                if (!photo.matches(DenPhotos.NAME) || photo.endsWith("-p.jpg")) return null;
                j.put("photo", photo);
            }
            for (String k : new String[]{"watcher", "clock", "icons", "icon_layout", "focus", "wake", "extras", "accent"}) {
                String v = in.optString(k, "").trim();
                if (!v.isEmpty() && (k.equals("accent") ? v.matches("#[0-9a-fA-F]{6}") : v.matches("[a-z0-9]{1,20}"))) j.put(k, v);
            }
            if (in.optBoolean("made") && j.has("photo")) j.put("made", true);
            // Its own facts (a described look's): a few short lines of plain text.
            JSONArray fs = in.optJSONArray("facts"), facts = new JSONArray();
            for (int i = 0; fs != null && i < fs.length() && facts.length() < 6; i++) {
                String f = fs.optString(i, "").replaceAll("\\p{Cntrl}", " ").trim();
                if (!f.isEmpty()) facts.put(f.length() > 160 ? f.substring(0, 160) : f);
            }
            if (facts.length() > 0) j.put("facts", facts);
            out.put(j);
            p.edit().putString(MINE, out.toString()).apply();
            return id;
        } catch (JSONException e) {
            return null;
        }
    }

    static boolean deleteMine(Context ctx, String id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(p.getString(MINE, "[]")), out = new JSONArray();
            for (int i = 0; i < old.length(); i++) if (!id.equals(old.getJSONObject(i).optString("id"))) out.put(old.getJSONObject(i));
            if (out.length() == old.length()) return false;
            p.edit().putString(MINE, out.toString()).apply();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private static Look look(JSONObject j, String wall) {
        String id = j.optString("id");
        Look l = new Look(id, j.optString("name", id), j.optString("category", ""), j.optString("watcher", "none"),
                j.optString("wake", "bloom"), j.optString("accent", "#ffffff"), wall);
        l.clock = j.optString("clock", "");
        l.icons = j.optString("icons", "");
        l.iconLayout = j.optString("icon_layout", "");
        l.hero = j.optString("hero", "");
        l.scene = j.optString("scene", "");
        l.audience = j.optString("for", "everyone");
        l.moment = j.optString("moment", "none");
        l.extras = j.optString("extras", "auto");
        l.figure = j.optString("figure", "");
        l.hud = j.optBoolean("hud", false);
        l.focus = j.optString("focus", "");
        l.order = j.optInt("order", 9000);
        StringBuilder w = new StringBuilder();
        for (String k : new String[]{"hero", "scene", "watcher", "goal_text", "scoreboard_text", "tags"}) w.append(j.optString(k, "")).append(' ');
        l.words = w.toString().trim();
        JSONArray fs = j.optJSONArray("facts");
        for (int i = 0; fs != null && i < fs.length(); i++) if (!fs.optString(i).isEmpty()) l.facts.add(fs.optString(i));
        return l;
    }

    /** "01-camp-nou.jpg" -> "camp-nou"; anything else (captions.txt, a hero-wall extra) -> null. */
    static String wallpaperLookId(String file) {
        int dash = file.indexOf('-'), dot = file.lastIndexOf('.');
        if (dash < 1 || dot <= dash + 1) return null;
        for (int i = 0; i < dash; i++) if (!Character.isDigit(file.charAt(i))) return null;
        String ext = file.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png") && !ext.equals("webp")) return null;
        return file.substring(dash + 1, dot);
    }

    /**
     * The look on Home, or null while the wallpapers rotate. A theme push clears the launcher's wallpaper
     * choice; a stored look whose wallpaper is no longer the choice counts as rotating too.
     */
    static Look current(Context ctx, List<Look> looks) {
        if (DenSafe.on(ctx)) return null;   // safe mode: the plain launcher, wallpapers rotating
        String id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOOK, ROTATE);
        String choice = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(Config.WALLPAPER_CHOICE_KEY, null);
        for (Look l : looks) {
            if (!l.id.equals(id)) continue;
            // Hero Wall moves through the hero posters (DenLayer), so any of them is still Hero Wall.
            if (l.wallpaper.equals(choice) || ("rotate".equals(l.hero) && choice != null && choice.matches("\\d+-hero-.+"))) return l;
        }
        return null;
    }

    /** The look with this id, or null. */
    /** Changes made from the phone to a look's watcher, clock, icons or colour, as {"watcher": ...} per look. */
    static final String OVERRIDE = "over:";

    /** Whether the phone has changed this look's parts (Restore to default look takes them off again). */
    static boolean changed(Context ctx, String id) {
        String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(OVERRIDE + id, null);
        try { return raw != null && new JSONObject(raw).length() > 0; } catch (JSONException e) { return false; }
    }

    /** The look with the phone's changes to it; the look itself when there are none. */
    static Look tweaked(Context ctx, Look look) {
        if (look == null) return null;
        String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(OVERRIDE + look.id, null);
        if (raw == null) return look;
        try {
            JSONObject o = new JSONObject(raw);
            Look t = new Look(look.id, look.name, look.category, o.optString("watcher", look.watcher), o.optString("wake", look.wake),
                    o.optString("accent", look.accent), look.wallpaper);
            t.clock = o.optString("clock", look.clock);
            t.icons = o.optString("icons", look.icons);
            t.iconLayout = o.optString("layout", look.iconLayout);
            t.hero = look.hero;
            t.scene = look.scene;
            t.audience = look.audience;
            t.moment = look.moment;
            t.extras = o.optString("extras", look.extras);
            t.figure = look.figure;
            t.hud = look.hud;
            t.focus = o.optString("focus", look.focus);
            t.order = look.order;
            t.photo = look.photo;
            t.words = look.words;
            t.facts = look.facts;
            return t;
        } catch (JSONException e) {
            return look;
        }
    }

    /** The next hero poster after the one on Home, for Hero Wall; null without posters. */
    static String nextHero(Context ctx) {
        java.io.File dir = ctx.getExternalFilesDir(null);
        String[] files = dir == null ? null : new java.io.File(dir, "wallpapers").list((d, n) -> n.matches("\\d+-hero-.+"));
        if (files == null || files.length == 0) return null;
        java.util.Arrays.sort(files);
        String on = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(Config.WALLPAPER_CHOICE_KEY, "");
        for (int i = 0; i < files.length; i++) if (files[i].equals(on)) return files[(i + 1) % files.length];
        return files[0];
    }

    static Look byId(List<Look> looks, String id) {
        for (Look l : looks) if (l.id.equals(id)) return l;
        return null;
    }

    /** Puts a look on Home (null: rotate every look). Home rebuilds on its next resume, as after Launcher settings. */
    static void apply(Context ctx, Look look) {
        DenSafe.off(ctx);   // picking a look tries den-tv's looks again after safe mode
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        DenPhotos.clear(ctx);   // a look or the rotation replaces a photo on Home
        if (look != null && !look.photo.isEmpty()) {
            // A photo look: its photo goes on Home as the photo does (never into the rotation), with the look's parts.
            try { if (DenPhotos.use(ctx, look.photo)) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LOOK, look.id).apply(); DenPeople.remember(ctx, look.id); return; } }
            catch (IOException ignored) { }
            look = null;   // its photo is gone: back to rotating
        }
        SharedPreferences.Editor e = choices.edit();
        if (look == null) e.remove(Config.WALLPAPER_CHOICE_KEY); else e.putString(Config.WALLPAPER_CHOICE_KEY, look.wallpaper);
        e.putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LOOK, look == null ? ROTATE : look.id).apply();
        DenPeople.remember(ctx, look == null ? ROTATE : look.id);   // each person keeps the look they chose
    }

    private static String read(Context ctx, String asset) throws IOException {
        try (InputStream in = ctx.getAssets().open(asset); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
