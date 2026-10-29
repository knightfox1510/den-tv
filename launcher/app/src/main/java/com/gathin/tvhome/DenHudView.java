package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.view.View;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The detailed HUD panels on the right of Home, as on the Geek look: SYSTEM (memory, storage, uptime), NETWORK
 * (address, Wi-Fi strength and speed) and FEED (what's next from the Fan Hub). Values refresh every few seconds,
 * read off the main thread.
 */
final class DenHudView extends View {
    private static final Uri TURNS = Uri.parse("content://tv.den.fanhub.games/turns");
    private final int accent;
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile String[][] panels = new String[0][];
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            try { io.execute(() -> { panels = read(); postInvalidate(); }); } catch (RuntimeException ignored) { }
            postDelayed(this, 4000);
        }
    };

    DenHudView(Context ctx, String accentHex) {
        super(ctx);
        int a;
        try { a = Color.parseColor(accentHex); } catch (RuntimeException e) { a = 0xff3ddc84; }
        accent = a;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.setTypeface(Typeface.MONOSPACE);
        line.setStyle(Paint.Style.STROKE);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(refresh); }

    @Override protected void onDetachedFromWindow() { removeCallbacks(refresh); io.shutdownNow(); super.onDetachedFromWindow(); }

    private static String size(long b) { return b >= 1_000_000_000L ? String.format(Locale.ROOT, "%.1f GB", b / 1e9) : (b / 1_000_000) + " MB"; }

    private String[][] read() {
        Context ctx = getContext();
        List<String[]> out = new ArrayList<>();
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        out.add(new String[]{"SYSTEM",
                "MEM  " + size(mi.totalMem - mi.availMem) + " / " + size(mi.totalMem),
                "DISK " + size(fs.getTotalBytes() - fs.getAvailableBytes()) + " / " + size(fs.getTotalBytes()),
                "UP   " + DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000)});
        String ip = "-", wifi = "-";
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
            if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) if (la.getAddress() instanceof Inet4Address) ip = la.getAddress().getHostAddress();
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                android.net.wifi.WifiInfo w = ((WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo();
                wifi = w.getRssi() + " dBm  " + w.getLinkSpeed() + " Mbps";
            } else if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) wifi = "ETHERNET";
            else if (nc == null) wifi = "OFFLINE";
        } catch (RuntimeException ignored) { }
        out.add(new String[]{"NETWORK", "IP   " + ip, "LINK " + wifi});
        List<String> feed = new ArrayList<>();
        feed.add("FEED");
        try (Cursor c = ctx.getContentResolver().query(TURNS, new String[]{"top_line", "word", "start_ms", "end_ms"}, null, null, null)) {
            long now = System.currentTimeMillis();
            while (c != null && c.moveToNext() && feed.size() < 4) {
                String top = c.getString(0);
                long start = c.getLong(2), end = c.getLong(3);
                if (end <= now) continue;
                String when = now >= start ? "LIVE" : "T-" + DateUtils.formatElapsedTime((start - now) / 1000).replaceAll(":\\d\\d$", "");
                feed.add((top.length() > 26 ? top.substring(0, 26) : top) + " " + when);
            }
        } catch (RuntimeException ignored) { }
        if (feed.size() == 1) feed.add("no fan hub data");
        out.add(feed.toArray(new String[0]));
        return out.toArray(new String[0][]);
    }

    @Override protected void onDraw(Canvas c) {
        String[][] ps = panels;
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        float x = 1420, y = 250, w = 420;
        for (String[] p : ps) {
            float h = 36 + (p.length - 1) * 30 + 14;
            r.set(x, y, x + w, y + h);
            box.setColor(0x9903101c);
            c.drawRect(r, box);
            line.setColor((accent & 0xffffff) | 0xaa000000);
            line.setStrokeWidth(1.5f);
            c.drawRect(r, line);
            line.setStrokeWidth(4);
            c.drawLine(x, y, x + 24, y, line);
            c.drawLine(x, y, x, y + 24, line);
            text.setTextSize(15);
            text.setLetterSpacing(0.25f);
            text.setColor(accent);
            c.drawText(p[0], x + 16, y + 26, text);
            text.setLetterSpacing(0);
            text.setTextSize(19);
            text.setColor(0xffd8f6ff);
            for (int i = 1; i < p.length; i++) c.drawText(p[i], x + 16, y + 26 + i * 30, text);
            y += h + 14;
        }
        c.restore();
    }
}
