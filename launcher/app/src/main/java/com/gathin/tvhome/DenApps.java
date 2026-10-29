package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-app and per-look choices made on the phone that the look library doesn't hold: your own picture for an app's
 * tile, apps hidden for one person only, and your favourite looks. All on the TV, in den-tv's own storage.
 */
final class DenApps {
    private static final String FAVS = "favs", HIDDEN = "hidden:";

    private DenApps() {}

    // ---- your own picture for an app's tile ------------------------------------------------------------------------

    static File pictureDir(Context ctx) { return new File(ctx.getFilesDir(), "icons"); }

    /** The file for a package's picture; null for a package name that isn't one. */
    static File pictureFile(Context ctx, String pkg) {
        return pkg != null && pkg.matches("[A-Za-z0-9_.]{1,200}") ? new File(pictureDir(ctx), pkg + ".img") : null;
    }

    static boolean hasPicture(Context ctx, String pkg) {
        File f = pictureFile(ctx, pkg);
        return f != null && f.isFile() && f.length() > 0;
    }

    /** The picture as the tile's banner, decoded small (a tile is about 400 by 225), or null. */
    static Drawable picture(Context ctx, String pkg) {
        if (!hasPicture(ctx, pkg)) return null;
        String path = pictureFile(ctx, pkg).getPath();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        o.inJustDecodeBounds = false;
        o.inSampleSize = Math.max(1, Math.min(o.outWidth / 480, o.outHeight / 270));
        Bitmap b = BitmapFactory.decodeFile(path, o);
        return b == null ? null : new BitmapDrawable(ctx.getResources(), b);
    }

    // ---- apps hidden for one person ------------------------------------------------------------------------------

    /** Apps hidden for whoever is watching (a guest sees everyone's). */
    static Set<String> hiddenNow(Context ctx) {
        DenPeople.Person p = DenPeople.current(ctx);
        return p == null ? new HashSet<>() : hiddenFor(ctx, p.id);
    }

    static Set<String> hiddenFor(Context ctx, String person) {
        Set<String> out = new LinkedHashSet<>();
        try {
            JSONArray a = new JSONArray(DenSettings.prefs(ctx).getString(HIDDEN + person, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    /** Hides or shows an app for the person watching now; false with no one to hide it for. */
    static boolean hideForNow(Context ctx, String pkg, boolean hide) {
        DenPeople.Person p = DenPeople.current(ctx);
        if (p == null || pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}")) return false;
        Set<String> s = hiddenFor(ctx, p.id);
        if (hide) s.add(pkg); else s.remove(pkg);
        DenSettings.prefs(ctx).edit().putString(HIDDEN + p.id, new JSONArray(s).toString()).apply();
        return true;
    }

    // ---- favourite looks -------------------------------------------------------------------------------------------

    static Set<String> favourites(Context ctx) {
        Set<String> out = new LinkedHashSet<>();
        try {
            JSONArray a = new JSONArray(DenSettings.prefs(ctx).getString(FAVS, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    static boolean favourite(Context ctx, String id, boolean on) {
        if (id == null || !id.matches("[a-z0-9-]{1,40}")) return false;
        Set<String> s = favourites(ctx);
        if (on) s.add(id); else s.remove(id);
        DenSettings.prefs(ctx).edit().putString(FAVS, new JSONArray(s).toString()).apply();
        return true;
    }
}
