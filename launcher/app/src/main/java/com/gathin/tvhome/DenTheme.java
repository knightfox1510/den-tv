package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * den-tv: on the first start, copies the den-mix theme bundled in the APK (assets/den/theme: config.json,
 * wallpapers/, captions) to where the launcher reads a pushed theme, so a fresh install has the looks without
 * tools/theme.sh. When no theme is there yet, and again once when den-tv's settings change (VERSION); a theme pushed
 * later replaces it as usual.
 */
final class DenTheme {
    private static final String TAG = "DenTheme";
    private static final String ASSET_DIR = "den/theme";
    /** Raise when themes/den-mix/config.json changes: TVs set up by an older version get the new settings. */
    static final int VERSION = 4;
    /** The bundled wallpapers were last redrawn in this version (4: hero titles readable over any scene). */
    private static final int WALLS_VERSION = 4;
    private static final String KEY_VERSION = "theme_version";

    private DenTheme() {}

    static void ensure(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) return;   // storage not mounted yet: the launcher starts with config.xml, we try again next time
        File config = new File(dir, Config.FILE_NAME);
        android.content.SharedPreferences den = ctx.getSharedPreferences(DenLooks.PREFS, Context.MODE_PRIVATE);
        int had = config.exists() ? den.getInt(KEY_VERSION, 1) : VERSION;
        if (config.exists() && had >= VERSION) return;
        AssetManager assets = ctx.getAssets();
        try {
            File walls = new File(dir, "wallpapers");
            if (!walls.isDirectory() && !walls.mkdirs()) return;
            String[] names = assets.list(ASSET_DIR + "/wallpapers");
            if (names != null) for (String n : names) {
                File to = new File(walls, n);
                // New ones always; ones already there again when they were redrawn since (your photos live elsewhere).
                if (!to.exists() || had < WALLS_VERSION) copy(assets, ASSET_DIR + "/wallpapers/" + n, to);
            }
            // config.json last: an interrupted copy has no config.json, so the next start finishes it.
            copy(assets, ASSET_DIR + "/" + Config.FILE_NAME, config);
            den.edit().putInt(KEY_VERSION, VERSION).apply();
        } catch (IOException e) {
            Log.w(TAG, "could not set up the den-mix theme", e);
        }
    }

    private static void copy(AssetManager assets, String from, File to) throws IOException {
        File part = new File(to.getPath() + ".part");
        try (InputStream in = assets.open(from); OutputStream out = new FileOutputStream(part)) {
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        if (to.exists() && !to.delete()) throw new IOException("could not replace " + to);
        if (!part.renameTo(to)) throw new IOException("could not write " + to);
    }
}
