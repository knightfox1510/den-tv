package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;
import java.util.Locale;

/**
 * den-tv app icon styles (looks/README.md, "icons"). Each Home tile keeps the app's own banner, which fills the
 * card the way TV apps are designed to be shown, and the look's style is a frame drawn over it: glass sheen, neon
 * edge, Wanted poster border, Dragon Ball stars and so on. The look sets the style once (DenLayer); AppAdapter
 * calls style() for every tile it binds. "tiles", an empty or unknown style leave the tile as the launcher draws it.
 */
final class DenIcons {
    static final List<String> STYLES = List.of("white", "dball", "wanted", "manga", "neon", "patch", "pin", "score",
            "pixel", "glass", "sticker", "hud");

    private static String style = "";
    private static int accent = 0xffff4d6d;
    private static boolean albiceleste;

    private DenIcons() {}

    /** The look's style and accent; called before Home binds its tiles. */
    static void use(String s, String accentHex, String lookId) {
        style = STYLES.contains(s) ? s : "";
        try { accent = Color.parseColor(accentHex); } catch (RuntimeException e) { accent = 0xffff4d6d; }
        albiceleste = "albiceleste".equals(lookId);
    }

    static boolean active() { return !style.isEmpty(); }

    /** Frames one bound tile; with no style on, takes any frame off so a recycled tile is plain again. */
    static void style(Context ctx, View card, String name, int position) {
        if (!(card instanceof FrameLayout)) return;
        float density = ctx.getResources().getDisplayMetrics().density;
        String badge = DenBadges.forLabel(name);
        ((FrameLayout) card).setForeground(style.isEmpty() && badge == null ? null : new Frame(style, name, position, accent, albiceleste, density, badge));
    }

    /** The style's frame over the whole card; the card's outline clips it to the tile's rounded corners. */
    static final class Frame extends Drawable {
        private final String style, name;
        private final int index, accent;
        private final boolean albiceleste;
        private final float dp;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();

        private final String badge;

        Frame(String style, String name, int index, int accent, boolean albiceleste, float density) {
            this(style, name, index, accent, albiceleste, density, null);
        }

        Frame(String style, String name, int index, int accent, boolean albiceleste, float density, String badge) {
            this.badge = badge;
            this.style = style; this.name = name == null ? "" : name; this.index = index; this.accent = accent;
            this.albiceleste = albiceleste; this.dp = density;
            t.setTextAlign(Paint.Align.CENTER);
        }

        private void fill(int colour) { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(colour); p.clearShadowLayer(); }
        private void stroke(int colour, float w) { p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setColor(colour); p.clearShadowLayer(); }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), rad = AppAdapter.cornerRadius(b.width(), dp);
            c.save();
            c.translate(b.left, b.top);
            switch (style) {
                case "glass": {
                    p.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x47ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
                    p.setStyle(Paint.Style.FILL);
                    r.set(0, 0, w, h * 0.5f);
                    c.drawRect(r, p);
                    border(c, w, h, rad, 0x99ffffff, 1.5f * dp);
                    stroke(0x8cffffff, 2.5f * dp);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    path.reset();
                    path.moveTo(8 * dp, h * 0.42f); path.quadTo(8 * dp, 8 * dp, w * 0.3f, 8 * dp);
                    c.drawPath(path, p);
                    break;
                }
                case "neon":
                    stroke(accent, 3 * dp);
                    p.setShadowLayer(8 * dp, 0, 0, accent);
                    r.set(2 * dp, 2 * dp, w - 2 * dp, h - 2 * dp);
                    c.drawRoundRect(r, rad, rad, p);
                    p.clearShadowLayer();
                    break;
                case "white": border(c, w, h, rad, 0xe6ffffff, 2 * dp); break;
                case "sticker": border(c, w, h, rad, Color.WHITE, 5 * dp); break;
                case "pin": {
                    float bw = 5 * dp;
                    p.setShader(new LinearGradient(0, 0, w, h, new int[]{0xfffff2b0, 0xffd4a636, 0xff6a4608}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(bw);
                    r.set(bw / 2, bw / 2, w - bw / 2, h - bw / 2);
                    c.drawRoundRect(r, rad, rad, p);
                    break;
                }
                case "manga": {
                    // Halftone in the bottom-right corner and a heavy ink border.
                    fill(0x59000000);
                    float step = 6 * dp;
                    for (float y = h * 0.55f; y < h; y += step) for (float x = w * 0.6f; x < w; x += step) {
                        float k = ((x - w * 0.6f) / (w * 0.4f) + (y - h * 0.55f) / (h * 0.45f)) / 2;
                        c.drawCircle(x, y, 1.6f * dp * k, p);
                    }
                    border(c, w, h, rad, 0xff0a0a0a, 5 * dp);
                    break;
                }
                case "patch": {
                    int[] pc = albiceleste ? new int[]{0xff75aadb, 0xfff4f8ff} : new int[]{0xffa50044, 0xff004d98};
                    float band = h * 0.12f, sw = 10 * dp;
                    for (float x = 0, k = 0; x < w; x += sw, k++) {
                        fill(pc[(int) k % 2]);
                        c.drawRect(x, h - band, x + sw, h, p);
                    }
                    border(c, w, h, rad, 0xffedbb00, 2.5f * dp);
                    break;
                }
                case "score": {
                    float band = h * 0.26f;
                    fill(0xe60b0c10);
                    c.drawRect(0, h - band, w, h, p);
                    String n = name.toUpperCase(Locale.ROOT);
                    t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
                    float size = band * 0.6f;
                    t.setTextSize(size);
                    float tw = t.measureText(n);
                    if (tw > w * 0.9f) t.setTextSize(size * w * 0.9f / tw);
                    t.setColor(0xffffc23a);
                    c.drawText(n, w / 2, h - band / 2 - (t.ascent() + t.descent()) / 2, t);
                    border(c, w, h, rad, 0xff34343c, 2 * dp);
                    break;
                }
                case "dball": {
                    border(c, w, h, rad, 0xffff9a1a, 3.5f * dp);
                    fill(0xffd0140c);
                    int stars = index % 7 + 1;
                    for (int j = 0; j < stars; j++) star(c, w - 12 * dp - j * 11 * dp, 12 * dp, 4.5f * dp);
                    break;
                }
                case "wanted": {
                    float bw = 7 * dp;
                    stroke(0xfff0dcae, bw);
                    r.set(bw / 2, bw / 2, w - bw / 2, h - bw / 2);
                    c.drawRoundRect(r, rad, rad, p);
                    stroke(0xff9c7a40, 1.2f * dp);
                    r.set(bw, bw, w - bw, h - bw);
                    c.drawRect(r, p);
                    float tabW = w * 0.42f, tabH = h * 0.17f;
                    fill(0xfff0dcae);
                    c.drawRect(w / 2 - tabW / 2, 0, w / 2 + tabW / 2, tabH, p);
                    t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
                    t.setTextSize(tabH * 0.72f);
                    t.setColor(0xff3b2610);
                    c.drawText("WANTED", w / 2, tabH * 0.78f, t);
                    break;
                }
                case "pixel": {
                    // An 8-bit frame: square blocks round the edge, the corners cut like old sprites.
                    float s = 5 * dp;
                    fill(accent);
                    for (float x = s; x < w - s; x += s * 2) { c.drawRect(x, 0, x + s, s, p); c.drawRect(x, h - s, x + s, h, p); }
                    for (float y = s; y < h - s; y += s * 2) { c.drawRect(0, y, s, y + s, p); c.drawRect(w - s, y, w, y + s, p); }
                    break;
                }
                case "hud": {
                    stroke(accent, 3 * dp);
                    float l = Math.min(w, h) * 0.22f, i = 3 * dp;
                    path.reset();
                    path.moveTo(i, i + l); path.lineTo(i, i); path.lineTo(i + l, i);
                    path.moveTo(w - i - l, i); path.lineTo(w - i, i); path.lineTo(w - i, i + l);
                    path.moveTo(w - i, h - i - l); path.lineTo(w - i, h - i); path.lineTo(w - i - l, h - i);
                    path.moveTo(i + l, h - i); path.lineTo(i, h - i); path.lineTo(i, h - i - l);
                    c.drawPath(path, p);
                    t.setTypeface(Typeface.MONOSPACE);
                    t.setTextSize(9 * dp);
                    t.setColor(accent);
                    t.setTextAlign(Paint.Align.RIGHT);
                    c.drawText("APP_" + String.format(Locale.ROOT, "%02d", index), w - 8 * dp, h - 8 * dp, t);
                    t.setTextAlign(Paint.Align.CENTER);
                    break;
                }
                default: break;
            }
            if (badge != null) badge(c, w);
            c.restore();
        }

        /** A pill in the top-right corner: red while live, orange for a new episode. */
        private void badge(Canvas c, float w) {
            t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            t.setTextSize(11 * dp);
            t.setTextAlign(Paint.Align.CENTER);
            float tw = t.measureText(badge), ph = 18 * dp, pw = tw + 14 * dp, x = w - pw - 6 * dp, y = 6 * dp;
            fill(badge.startsWith("LIVE") ? 0xffe0202e : 0xffff7a00);
            r.set(x, y, x + pw, y + ph);
            c.drawRoundRect(r, ph / 2, ph / 2, p);
            t.setColor(Color.WHITE);
            c.drawText(badge, x + pw / 2, y + ph / 2 - (t.ascent() + t.descent()) / 2, t);
        }

        private void border(Canvas c, float w, float h, float rad, int colour, float width) {
            stroke(colour, width);
            r.set(width / 2, width / 2, w - width / 2, h - width / 2);
            c.drawRoundRect(r, rad, rad, p);
        }

        private void star(Canvas c, float cx, float cy, float rr) {
            path.reset();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float q = i % 2 == 1 ? rr * 0.45f : rr;
                float x = cx + (float) Math.cos(a) * q, y = cy + (float) Math.sin(a) * q;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            c.drawPath(path, p);
        }

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
