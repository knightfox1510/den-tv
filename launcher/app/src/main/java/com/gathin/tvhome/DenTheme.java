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
 * tools/theme.sh. Only when no theme is there yet: a theme pushed later replaces it as usual.
 */
final class DenTheme {
    private static final String TAG = "DenTheme";
    private static final String ASSET_DIR = "den/theme";

    private DenTheme() {}

    static void ensure(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) return;   // storage not mounted yet: the launcher starts with config.xml, we try again next time
        File config = new File(dir, Config.FILE_NAME);
        if (config.exists()) return;
        AssetManager assets = ctx.getAssets();
        try {
            File walls = new File(dir, "wallpapers");
            if (!walls.isDirectory() && !walls.mkdirs()) return;
            String[] names = assets.list(ASSET_DIR + "/wallpapers");
            if (names != null) for (String n : names) {
                File to = new File(walls, n);
                if (!to.exists()) copy(assets, ASSET_DIR + "/wallpapers/" + n, to);
            }
            // config.json last: an interrupted copy has no config.json, so the next start finishes it.
            copy(assets, ASSET_DIR + "/" + Config.FILE_NAME, config);
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
        if (!part.renameTo(to)) throw new IOException("could not write " + to);
    }
}
