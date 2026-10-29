package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

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
        String clock = "", icons = "", iconLayout = "";

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
        return looks;
    }

    private static Look look(JSONObject j, String wall) {
        String id = j.optString("id");
        Look l = new Look(id, j.optString("name", id), j.optString("category", ""), j.optString("watcher", "none"),
                j.optString("wake", "bloom"), j.optString("accent", "#ffffff"), wall);
        l.clock = j.optString("clock", "");
        l.icons = j.optString("icons", "");
        l.iconLayout = j.optString("icon_layout", "");
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
        String id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOOK, ROTATE);
        String choice = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(Config.WALLPAPER_CHOICE_KEY, null);
        for (Look l : looks) if (l.id.equals(id) && l.wallpaper.equals(choice)) return l;
        return null;
    }

    /** The look with this id, or null. */
    static Look byId(List<Look> looks, String id) {
        for (Look l : looks) if (l.id.equals(id)) return l;
        return null;
    }

    /** Puts a look on Home (null: rotate every look). Home rebuilds on its next resume, as after Launcher settings. */
    static void apply(Context ctx, Look look) {
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = choices.edit();
        if (look == null) e.remove(Config.WALLPAPER_CHOICE_KEY); else e.putString(Config.WALLPAPER_CHOICE_KEY, look.wallpaper);
        e.putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LOOK, look == null ? ROTATE : look.id).apply();
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
