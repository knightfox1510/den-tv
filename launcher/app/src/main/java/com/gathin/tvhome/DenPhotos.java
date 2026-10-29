package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Your own photos as Home's wallpaper, sent from the phone page. They stay on the TV in photos/ beside the theme,
 * never in the theme's wallpapers/ collection, so "Rotate every look" never shows them. The one on Home is copied
 * into wallpapers/ under a fixed name while it is picked, and removed again when a look takes over.
 */
final class DenPhotos {
    static final int MAX = 12;

    private DenPhotos() {}

    static File dir(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        return base == null ? null : new File(base, "photos");
    }

    /** Newest first. */
    static List<File> list(Context ctx) {
        File d = dir(ctx);
        File[] files = d == null ? null : d.listFiles((f) -> f.getName().endsWith(".jpg"));
        List<File> out = new ArrayList<>();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        out.addAll(Arrays.asList(files));
        return out;
    }

    /** A new photo file to write into; the oldest go once there are more than MAX. */
    static File create(Context ctx) throws IOException {
        File d = dir(ctx);
        if (d == null || (!d.isDirectory() && !d.mkdirs())) throw new IOException("storage not ready");
        List<File> all = list(ctx);
        for (int i = MAX - 1; i < all.size(); i++) //noinspection ResultOfMethodCallIgnored
            all.get(i).delete();
        return new File(d, "photo-" + System.currentTimeMillis() + ".jpg");
    }

    /** Puts a saved photo on Home. */
    static boolean use(Context ctx, String name) throws IOException {
        File d = dir(ctx), base = ctx.getExternalFilesDir(null);
        if (d == null || base == null || !name.matches("photo-\\d+\\.jpg")) return false;
        File from = new File(d, name);
        if (!from.isFile()) return false;
        File walls = new File(base, "wallpapers"), to = new File(walls, DenSettings.PHOTO_FILE);
        if (!walls.isDirectory() && !walls.mkdirs()) return false;
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, DenSettings.PHOTO_FILE)
                .putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        DenSettings.prefs(ctx).edit().putString(DenLooks.KEY_LOOK, DenSettings.PHOTO).putString("photo_name", name).apply();
        return true;
    }

    static boolean delete(Context ctx, String name) {
        File d = dir(ctx);
        if (d == null || !name.matches("photo-\\d+\\.jpg")) return false;
        if (name.equals(DenSettings.prefs(ctx).getString("photo_name", ""))) clear(ctx);
        return new File(d, name).delete();
    }

    /** Takes the photo off Home's wallpapers/ (the choice itself is replaced by whoever calls this). */
    static void clear(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        if (base != null) //noinspection ResultOfMethodCallIgnored
            new File(new File(base, "wallpapers"), DenSettings.PHOTO_FILE).delete();
        DenSettings.prefs(ctx).edit().remove("photo_name").apply();
    }
}
