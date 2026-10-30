package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Your widgets' lines (markets, weather, clocks, a countdown, headlines, a note), set up on the phone and fetched by
 * the Fan Hub (its Widgets); Home shows them along the bottom, taking turns with "Did you know?" (DenFactsView).
 * Read from the Fan Hub's provider about once a minute, off the main thread; Home itself stays offline.
 */
final class DenWidgets {
    private static final Uri HOME = Uri.parse("content://tv.den.fanhub.widgets/home");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile List<String[]> lines = new ArrayList<>();
    private static volatile long at;

    private DenWidgets() {}

    /** The lines as last read ({label, text}); asks for fresh ones when they're a minute old. */
    static List<String[]> now(Context ctx) {
        long t = System.currentTimeMillis();
        if (t - at > 60_000) {
            at = t;
            Context app = ctx.getApplicationContext();
            IO.execute(() -> lines = load(app));
        }
        return lines;
    }

    static List<String[]> load(Context ctx) {
        List<String[]> out = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(HOME, null, null, null, null)) {
            while (c != null && c.moveToNext()) out.add(new String[]{c.getString(0), c.getString(1)});
        } catch (RuntimeException ignored) {
            // No Fan Hub, an older one, or one with another key: no widgets.
        }
        return out;
    }
}
