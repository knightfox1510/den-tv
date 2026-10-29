package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * den-tv clock styles (looks/README.md, "clock"): drawn in place of Home's own clock, as in the mockup's
 * buildClock and tick. Sizes are the mockup's, on its 1920-wide grid, times SCALE so the clock sits in the
 * launcher's layout. Ticks once a second; nothing here takes focus.
 */
final class DenClockView extends View {
    static final List<String> STYLES = List.of("thin", "brush", "crt", "neon", "board", "term", "hud", "analog", "flap",
            "roll", "over", "words", "ring");
    private static final float SCALE = 0.7f;
    private static final String[] HOURS = {"twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven"};

    private final String style;
    private final int accent;
    private final float u;   // one mockup pixel in screen pixels
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Typeface light, thin, bold, bebas, vt, brushFace, mono;
    private final Calendar now = Calendar.getInstance();
    private String shown = "";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            now.setTimeInMillis(System.currentTimeMillis());
            String t = hm();
            if (!t.equals(shown)) { shown = t; requestLayout(); }   // the width follows the digits; once a minute at most
            invalidate();
            postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    DenClockView(Context ctx, String style, String accentHex) {
        super(ctx);
        this.style = STYLES.contains(style) ? style : "thin";
        int a;
        try { a = Color.parseColor(accentHex); } catch (RuntimeException e) { a = 0xffff4d6d; }
        accent = a;
        u = ctx.getResources().getDisplayMetrics().widthPixels / 1920f * SCALE;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Typeface outfit = font(ctx, R.font.outfit, Typeface.SANS_SERIF);
        light = weight(outfit, 300);
        thin = weight(outfit, 200);
        bold = weight(outfit, 700);
        bebas = font(ctx, R.font.bebas_neue, Typeface.DEFAULT_BOLD);
        vt = font(ctx, R.font.vt323, Typeface.MONOSPACE);
        brushFace = font(ctx, R.font.kaushan_script, Typeface.SERIF);
        mono = Typeface.MONOSPACE;
    }

    private static Typeface font(Context ctx, int id, Typeface fallback) {
        try { Typeface t = ctx.getResources().getFont(id); return t != null ? t : fallback; } catch (Exception e) { return fallback; }
    }

    private static Typeface weight(Typeface t, int w) {
        return android.os.Build.VERSION.SDK_INT >= 28 ? Typeface.create(t, w, false) : t;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tick.run();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private String hm() {
        boolean h24 = DateFormat.is24HourFormat(getContext());
        int h = now.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && h == 0) h = 12;
        return (h24 ? String.format(Locale.ROOT, "%02d", h) : String.valueOf(h)) + ":" + String.format(Locale.ROOT, "%02d", now.get(Calendar.MINUTE));
    }

    private int sec() { return now.get(Calendar.SECOND); }

    /** "twenty past nine" and the hour word separately, the hour drawn bold in the accent. */
    private String[] words() {
        int mm = Math.round(now.get(Calendar.MINUTE) / 5f) * 5;
        int h = now.get(Calendar.HOUR_OF_DAY) + (mm > 30 ? 1 : 0);
        String hw = HOURS[h % 12];
        String m;
        switch (mm) {
            case 5: m = "five past"; break;
            case 10: m = "ten past"; break;
            case 15: m = "quarter past"; break;
            case 20: m = "twenty past"; break;
            case 25: m = "twenty-five past"; break;
            case 30: m = "half past"; break;
            case 35: m = "twenty-five to"; break;
            case 40: m = "twenty to"; break;
            case 45: m = "quarter to"; break;
            case 50: m = "ten to"; break;
            case 55: m = "five to"; break;
            default: return new String[]{"IT'S ", hw.toUpperCase(Locale.ROOT), " O'CLOCK"};
        }
        return new String[]{m.toUpperCase(Locale.ROOT) + " ", hw.toUpperCase(Locale.ROOT), ""};
    }

    private void text(Typeface t, float size, int colour) {
        p.setTypeface(t);
        p.setTextSize(size * u);
        p.setColor(colour);
        p.setStyle(Paint.Style.FILL);
        p.clearShadowLayer();
        p.setLetterSpacing(0);
    }

    private float width(String s) { return p.measureText(s); }

    // ---- size ----------------------------------------------------------------------------------------------------

    @Override protected void onMeasure(int wSpec, int hSpec) {
        String t = hm();
        float w, h;
        switch (style) {
            case "brush": text(brushFace, 136, 0); w = width(t) + 12 * u; h = 170 * u; break;
            case "crt": text(vt, 170, 0); w = width(t) + 20 * u; h = 150 * u; break;
            case "neon": text(light, 150, 0); w = width(t) + 40 * u; h = 170 * u; break;
            case "board": text(bebas, 132, 0); w = Math.max(width(t), boardWidth()) + 52 * u; h = 196 * u; break;
            case "term": { text(mono, 140, 0); float tw = width(t) + 80 * u; text(mono, 26, 0); w = Math.max(tw, width(prompt())); h = 190 * u; break; }
            case "hud": text(mono, 120, 0); w = width(t) + 46 * u; h = 190 * u; break;
            case "analog": w = h = 240 * u; break;
            case "ring": w = h = 300 * u; break;
            case "flap": w = (92 * 4 + 8 * 4 + 40) * u; h = 132 * u; break;
            case "roll": text(thin, 150, 0); w = width(t) + 90 * u; h = 160 * u; break;
            case "over": text(light, 140, 0); w = width(t) + (26 + 6 * 32) * u; h = 160 * u; break;
            case "words": w = 760 * u; h = 150 * u; break;
            default: text(thin, 150, 0); w = width(t) + 10 * u; h = 160 * u; break;
        }
        setMeasuredDimension(Math.round(w), Math.round(h));
    }

    private float boardWidth() { text(bebas, 34, 0); p.setLetterSpacing(0.12f); return width(boardLine()); }

    private String boardLine() {
        return android.text.format.DateFormat.format("EEEE d MMMM", now).toString().toUpperCase(Locale.ROOT);
    }

    private static String prompt() { return "den@den-tv:~$ date +%H:%M"; }

    // ---- drawing -------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        String t = hm();
        float H = getHeight();
        switch (style) {
            case "brush":
                text(brushFace, 136, accent);
                c.drawText(t, 6 * u + 2 * u, H - 34 * u + 6 * u, p);
                text(brushFace, 136, Color.WHITE);
                p.setShadowLayer(24 * u, 0, 10 * u, 0x99000000);
                c.drawText(t, 2 * u, H - 34 * u, p);
                break;
            case "crt":
                text(vt, 170, accent);
                p.setShadowLayer(22 * u, 0, 0, accent);
                c.drawText(t, 10 * u, H - 22 * u, p);
                break;
            case "neon":
                text(light, 150, accent);
                p.setShadowLayer(40 * u, 0, 0, accent);
                c.drawText(t, 20 * u, H - 34 * u, p);
                text(light, 150, Color.WHITE);
                p.setShadowLayer(8 * u, 0, 0, Color.WHITE);
                c.drawText(t, 20 * u, H - 34 * u, p);
                break;
            case "board": {
                box.set(3 * u, 3 * u, getWidth() - 3 * u, H - 3 * u);
                fill.setShader(new LinearGradient(0, 0, 0, H, 0xff0c0d10, 0xff050506, Shader.TileMode.CLAMP));
                c.drawRoundRect(box, 14 * u, 14 * u, fill);
                fill.setShader(null);
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(2 * u);
                fill.setColor(0xff2a2a30);
                c.drawRoundRect(box, 14 * u, 14 * u, fill);
                fill.setStyle(Paint.Style.FILL);
                text(bebas, 132, 0xffffc23a);
                p.setLetterSpacing(0.04f);
                p.setShadowLayer(18 * u, 0, 0, 0xbfffaa1e);
                c.drawText(t, 26 * u, 128 * u, p);
                text(bebas, 34, 0xe6ffd98a);
                p.setLetterSpacing(0.12f);
                c.drawText(boardLine(), 26 * u, 172 * u, p);
                break;
            }
            case "term": {
                text(mono, 26, 0xff8b949e);
                String pre = "den@den-tv:~$ ";
                c.drawText(pre, 0, 30 * u, p);
                float x = width(pre);
                p.setColor(accent);
                c.drawText("date +%H:%M", x, 30 * u, p);
                text(mono, 140, Color.WHITE);
                c.drawText(t, 0, 160 * u, p);
                if (sec() % 2 == 0) {
                    float cx = width(t) + 10 * u;
                    fill.setColor(accent);
                    c.drawRect(cx, 52 * u, cx + 60 * u, 172 * u, fill);
                }
                break;
            }
            case "hud": {
                box.set(1, 1, getWidth() - 1, H - 1);
                fill.setColor(0x80030e18);
                c.drawRect(box, fill);
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(Math.max(1, u));
                fill.setColor(accent);
                c.drawRect(box, fill);
                fill.setStyle(Paint.Style.FILL);
                text(mono, 16, accent);
                p.setLetterSpacing(0.3f);
                c.drawText("LOCAL TIME · " + now.getTimeZone().getDisplayName(false, java.util.TimeZone.SHORT, Locale.ENGLISH), 22 * u, 34 * u, p);
                text(mono, 120, 0xffe8fbff);
                p.setShadowLayer(18 * u, 0, 0, accent);
                c.drawText(t, 22 * u, 148 * u, p);
                fill.setColor(0x1affffff);
                float bw = getWidth() - 44 * u;
                c.drawRect(22 * u, 166 * u, 22 * u + bw, 172 * u, fill);
                fill.setColor(accent);
                c.drawRect(22 * u, 166 * u, 22 * u + bw * sec() / 59f, 172 * u, fill);
                break;
            }
            case "analog": analog(c); break;
            case "ring": {
                float r = 140 * u, cx = getWidth() / 2f, cy = H / 2f;
                fill.setColor(0x59000000);
                c.drawCircle(cx, cy, r, fill);
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(6 * u);
                fill.setColor(0x26ffffff);
                c.drawCircle(cx, cy, r, fill);
                fill.setColor(accent);
                fill.setStrokeCap(Paint.Cap.ROUND);
                box.set(cx - r, cy - r, cx + r, cy + r);
                c.drawArc(box, -90, 360 * sec() / 59f, false, fill);
                fill.setStyle(Paint.Style.FILL);
                text(thin, 92, Color.WHITE);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(t, cx, cy + 32 * u, p);
                p.setTextAlign(Paint.Align.LEFT);
                break;
            }
            case "flap": {
                String d = t.replace(":", "");
                if (d.length() < 4) d = " " + d;
                float x = 0;
                for (int k = 0; k < 4; k++) {
                    if (k == 2) {
                        text(light, 90, accent);
                        c.drawText(":", x + 4 * u, 100 * u, p);
                        x += 40 * u;
                    }
                    box.set(x, 0, x + 92 * u, 132 * u);
                    fill.setShader(new LinearGradient(0, 0, 0, 132 * u, new int[]{0xff26262c, 0xff26262c, 0xff0c0c0f, 0xff1c1c21},
                            new float[]{0, 0.495f, 0.5f, 0.505f}, Shader.TileMode.CLAMP));
                    c.drawRoundRect(box, 10 * u, 10 * u, fill);
                    fill.setShader(null);
                    text(bebas, 120, 0xfff4f4f4);
                    p.setTextAlign(Paint.Align.CENTER);
                    c.drawText(String.valueOf(d.charAt(k)), x + 46 * u, 110 * u, p);
                    p.setTextAlign(Paint.Align.LEFT);
                    fill.setColor(0xff0c0c0f);
                    c.drawRect(x, 65 * u, x + 92 * u, 67 * u, fill);
                    x += 100 * u;
                }
                break;
            }
            case "roll": {
                text(thin, 150, Color.WHITE);
                p.setShadowLayer(30 * u, 0, 6 * u, 0x80000000);
                c.drawText(t, 0, 130 * u, p);
                float x = width(t) + 12 * u;
                text(thin, 60, accent);
                c.drawText(String.format(Locale.ROOT, "%02d", sec()), x, 128 * u, p);
                break;
            }
            case "over": {
                text(light, 140, Color.WHITE);
                c.drawText(t, 0, 130 * u, p);
                float x = width(t) + 26 * u;
                int on = sec() / 10;
                for (int k = 0; k < 6; k++) {
                    float cx = x + k * 32 * u + 11 * u, cy = 90 * u;
                    if (k <= on) { fill.setColor(accent); fill.setShadowLayer(12 * u, 0, 0, accent); c.drawCircle(cx, cy, 11 * u, fill); fill.clearShadowLayer(); }
                    else {
                        fill.setStyle(Paint.Style.STROKE); fill.setStrokeWidth(3 * u); fill.setColor(0x99ffffff);
                        c.drawCircle(cx, cy, 9.5f * u, fill); fill.setStyle(Paint.Style.FILL);
                    }
                }
                break;
            }
            case "words": {
                String[] w = words();
                text(light, 66, Color.WHITE);
                p.setShadowLayer(24 * u, 0, 4 * u, 0x99000000);
                // Two lines at most: the minutes on the first, the hour (in the accent) on the second when it is long.
                float line1 = 66 * u, line2 = 134 * u;
                if (width(w[0] + w[1] + w[2]) <= getWidth()) {
                    c.drawText(w[0], 0, line1, p);
                    float x = width(w[0]);
                    text(bold, 66, accent); c.drawText(w[1], x, line1, p); x += width(w[1]);
                    text(light, 66, Color.WHITE); c.drawText(w[2], x, line1, p);
                } else {
                    c.drawText(w[0].trim(), 0, line1, p);
                    text(bold, 66, accent); c.drawText(w[1], 0, line2, p);
                    float x = width(w[1]);
                    text(light, 66, Color.WHITE); c.drawText(w[2], x, line2, p);
                }
                break;
            }
            default:
                text(thin, 150, Color.WHITE);
                p.setShadowLayer(30 * u, 0, 6 * u, 0x80000000);
                c.drawText(t, 4 * u, 130 * u, p);
                break;
        }
    }

    private void analog(Canvas c) {
        float s = getWidth() / 230f, cx = getWidth() / 2f, cy = getHeight() / 2f;
        fill.setColor(0x73000000);
        c.drawCircle(cx, cy, 112 * s, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(3 * s);
        fill.setColor(accent);
        c.drawCircle(cx, cy, 112 * s, fill);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6);
            float r0 = (i % 5 == 0 ? 88 : 100) * s;
            fill.setStrokeWidth((i % 5 == 0 ? 5 : 2) * s);
            fill.setColor(i % 5 == 0 ? Color.WHITE : 0x66ffffff);
            c.drawLine(cx + (float) Math.sin(a) * r0, cy - (float) Math.cos(a) * r0, cx + (float) Math.sin(a) * 108 * s, cy - (float) Math.cos(a) * 108 * s, fill);
        }
        float hh = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f, mm = now.get(Calendar.MINUTE) + sec() / 60f;
        fill.setStrokeCap(Paint.Cap.ROUND);
        hand(c, cx, cy, hh * 30, 57 * s, 9 * s, Color.WHITE, 0);
        hand(c, cx, cy, mm * 6, 85 * s, 5 * s, Color.WHITE, 0);
        hand(c, cx, cy, sec() * 6, 95 * s, 2.5f * s, accent, 17 * s);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(accent);
        c.drawCircle(cx, cy, 6 * s, fill);
    }

    private void hand(Canvas c, float cx, float cy, float deg, float len, float w, int colour, float tail) {
        double a = Math.toRadians(deg);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(w);
        fill.setColor(colour);
        float sx = (float) Math.sin(a), cy2 = (float) Math.cos(a);
        c.drawLine(cx - sx * tail, cy + cy2 * tail, cx + sx * len, cy - cy2 * len, fill);
    }
}
