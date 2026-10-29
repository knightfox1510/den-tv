package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Moments sent by the Fan Hub (DenLooksProvider "moment"). Home plays one at once when it is in front; otherwise
 * the latest waits, and plays when Home comes back within ten minutes.
 */
final class DenMoments {
    interface Player { boolean play(String type, String title, String line, int colour); }

    private static final long KEEP_MS = 10 * 60_000L;
    private static Player player;
    private static String[] pending;
    private static int pendingColour;
    private static long pendingAt;

    private DenMoments() {}

    static synchronized void attach(Player p) { player = p; }

    static synchronized void detach(Player p) { if (player == p) player = null; }

    /** From the provider's thread: hand it to Home on the main thread, or keep it for later. */
    static void post(String type, String title, String line, int colour) {
        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (DenMoments.class) {
                if (player != null && player.play(type, title, line, colour)) return;
                pending = new String[]{type, title, line};
                pendingColour = colour;
                pendingAt = SystemClock.elapsedRealtime();
            }
        });
    }

    /** Home is back: a moment it missed, if it is recent. */
    static synchronized void replay() {
        if (pending == null || player == null) return;
        if (SystemClock.elapsedRealtime() - pendingAt < KEEP_MS) player.play(pending[0], pending[1], pending[2], pendingColour);
        pending = null;
    }
}
