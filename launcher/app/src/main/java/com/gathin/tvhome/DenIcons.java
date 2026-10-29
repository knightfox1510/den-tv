package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * den-tv app icon styles (looks/README.md, "icons"): each Home tile drawn as the mockup's iconInner draws it, with
 * the app's own icon where the mockup has a logo. The look sets the style once (DenLayer); AppAdapter calls
 * style() for every tile it binds. "tiles", an empty or unknown style leave the tile as the launcher draws it.
 */
final class DenIcons {
    static final List<String> STYLES = List.of("white", "dball", "wanted", "manga", "neon", "patch", "pin", "score",
            "pixel", "glass", "sticker", "hud");

    private static String style = "";
    private static int accent = 0xffff4d6d;
    private static boolean albiceleste;
    private static final LruCache<ComponentName, Drawable> ICONS = new LruCache<>(48);

    private DenIcons() {}

    /** The look's style and accent; called before Home binds its tiles. */
    static void use(String s, String accentHex, String lookId) {
        style = STYLES.contains(s) ? s : "";
        try { accent = Color.parseColor(accentHex); } catch (RuntimeException e) { accent = 0xffff4d6d; }
        albiceleste = "albiceleste".equals(lookId);
    }

    static boolean active() { return !style.isEmpty(); }

    /** Restyles one bound tile. Leaves it alone when no style is on, so AppAdapter's own drawing stands. */
    static void style(Context ctx, View card, ImageView banner, ImageView icon, TextView label, ComponentName component,
                      String name, int position) {
        if (style.isEmpty() || component == null) return;
        Drawable app = ICONS.get(component);
        if (app == null) {
            try { app = ctx.getPackageManager().getActivityIcon(component); } catch (PackageManager.NameNotFoundException e) { app = null; }
            if (app != null) ICONS.put(component, app);
        }
        card.setBackground(new Tile(style, app, name, position, accent, albiceleste));
        banner.setImageDrawable(null);
        banner.setVisibility(View.GONE);
        icon.setVisibility(View.GONE);
        label.setVisibility(View.GONE);
    }

    /** The styled tile, drawn in the mockup's 100x100 (or 100x134 for posters) box, centred in the card. */
    static final class Tile extends Drawable {
        private final String style, name;
        private final Drawable app;
        private final int index, accent;
        private final boolean albiceleste;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();
        private Bitmap pixels;

        Tile(String style, Drawable app, String name, int index, int accent, boolean albiceleste) {
            this.style = style; this.app = app; this.name = name == null ? "" : name; this.index = index;
            this.accent = accent; this.albiceleste = albiceleste;
            t.setTextAlign(Paint.Align.CENTER);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            boolean poster = style.equals("wanted");
            float boxH = b.height() * 0.94f, boxW = poster ? boxH * 100 / 134f : boxH;
            float s = boxW / 100f;
            c.save();
            c.translate(b.exactCenterX() - boxW / 2, b.exactCenterY() - boxH / 2);
            c.scale(s, s);
            switch (style) {
                case "dball": dball(c); break;
                case "wanted": wanted(c); break;
                case "manga": manga(c); break;
                case "neon": neon(c); break;
                case "patch": patch(c); break;
                case "pin": pin(c); break;
                case "score": score(c); break;
                case "pixel": pixel(c); break;
                case "glass": glass(c); break;
                case "sticker": sticker(c); break;
                case "hud": hud(c); break;
                default: logo(c, 15, 15, 70, white()); break;   // white
            }
            c.restore();
        }

        private void fill(int colour) { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(colour); }
        private void stroke(int colour, float w) { p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setColor(colour); }

        private static ColorFilter white() { return new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN); }
        private static ColorFilter tint(int c) { return new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN); }
        private static ColorFilter grey() {
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(0);
            return new ColorMatrixColorFilter(m);
        }

        /** The app's icon in a size x size square at (x, y), optionally recoloured. */
        private void logo(Canvas c, float x, float y, float size, ColorFilter f) {
            logo(c, x, y, size, f, f != null);
        }

        /**
         * bare: only the logo, without the icon's own background. Most TV icons are adaptive icons on a solid
         * square, which a white or neon recolour would turn into a plain block; their foreground layer is the logo.
         */
        private void logo(Canvas c, float x, float y, float size, ColorFilter f, boolean bare) {
            if (app == null) return;
            Drawable d = app;
            if (bare && android.os.Build.VERSION.SDK_INT >= 26 && app instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                Drawable fg = ((android.graphics.drawable.AdaptiveIconDrawable) app).getForeground();
                if (fg != null) {
                    // The foreground is 108dp with the logo in the middle 72dp: draw it larger so the logo fills the box.
                    d = fg;
                    float grow = size * 0.25f;
                    x -= grow; y -= grow; size += 2 * grow;
                }
            }
            d.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size));
            d.setColorFilter(f);
            d.draw(c);
            d.setColorFilter(null);
        }

        private void label(Canvas c, String s, float x, float y, float size, Typeface face, int colour) {
            t.setTypeface(face);
            t.setTextSize(size);
            t.setColor(colour);
            c.drawText(s, x, y, t);
        }

        private void dball(Canvas c) {
            p.setShader(new RadialGradient(36, 30, 80, new int[]{0xfffff6c8, 0xffffb21f, 0xffe66a00, 0xff7a3000},
                    new float[]{0, 0.3f, 0.72f, 1}, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(50, 50, 47, p);
            fill(0xffd0140c);
            int stars = index % 7 + 1;
            for (int j = 0; j < stars; j++) {
                double g = j / (double) stars * 6.283 - 1.571;
                star(c, 50 + (float) Math.cos(g) * 36, 50 + (float) Math.sin(g) * 36, 5);
            }
            logo(c, 30, 30, 40, null);
            fill(0x73ffffff);
            c.save();
            c.rotate(-30, 34, 24);
            r.set(20, 17, 48, 31);
            c.drawOval(r, p);
            c.restore();
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

        private void wanted(Canvas c) {
            fill(0xfff0dcae);
            r.set(2, 2, 98, 132);
            c.drawRoundRect(r, 2, 2, p);
            stroke(0xff9c7a40, 2);
            c.drawRoundRect(r, 2, 2, p);
            stroke(0x596e4614, 1);
            c.drawRect(6, 6, 94, 128, p);
            label(c, "WANTED", 50, 22, 19, Typeface.create(Typeface.SERIF, Typeface.BOLD), 0xff3b2610);
            fill(0xffe2c890);
            c.drawRect(14, 29, 86, 87, p);
            stroke(0xff5a3a14, 2);
            c.drawRect(14, 29, 86, 87, p);
            logo(c, 27, 35, 46, null);
            String n = name.toUpperCase(Locale.ROOT);
            label(c, n.length() > 14 ? n.substring(0, 14) : n, 50, 104, 15, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), 0xff3b2610);
            long bounty = ((index * 7919L) % 900 + 100) * 1_000_000L;
            label(c, "฿ " + String.format(Locale.US, "%,d", bounty) + "-", 50, 122, 10, Typeface.create(Typeface.SERIF, Typeface.BOLD), 0xff3b2610);
        }

        private void manga(Canvas c) {
            fill(Color.WHITE);
            c.drawRect(4, 4, 96, 96, p);
            fill(0x4d000000);
            for (int y = 7; y < 96; y += 6) for (int x = 7; x < 96; x += 6) c.drawCircle(x, y, 1.1f, p);
            stroke(0xff0a0a0a, 6);
            c.drawRect(4, 4, 96, 96, p);
            logo(c, 19, 19, 62, grey());
        }

        private void neon(Canvas c) {
            fill(0x59000000);
            r.set(5, 5, 95, 95);
            c.drawRoundRect(r, 22, 22, p);
            stroke(accent, 2.5f);
            p.setShadowLayer(6, 0, 0, accent);
            c.drawRoundRect(r, 22, 22, p);
            p.clearShadowLayer();
            logo(c, 20, 20, 60, tint(accent));
        }

        private void patch(Canvas c) {
            int[] pc = albiceleste ? new int[]{0xff75aadb, 0xfff4f8ff} : new int[]{0xffa50044, 0xff004d98};
            path.reset();
            path.moveTo(50, 3); path.lineTo(93, 15); path.lineTo(93, 50);
            path.cubicTo(93, 75, 74, 89, 50, 97);
            path.cubicTo(26, 89, 7, 75, 7, 50);
            path.lineTo(7, 15); path.close();
            c.save();
            c.clipPath(path);
            for (int x = 0; x < 100; x += 24) {
                fill(pc[0]); c.drawRect(x, 0, x + 12, 100, p);
                fill(pc[1]); c.drawRect(x + 12, 0, x + 24, 100, p);
            }
            c.restore();
            stroke(0xffedbb00, 3.5f);
            c.drawPath(path, p);
            logo(c, 27, 24, 46, null);
        }

        private void pin(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 100, 100, new int[]{0xfffff2b0, 0xffd4a636, 0xff6a4608}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(50, 50, 47, p);
            fill(0xff1c1c22);
            c.drawCircle(50, 50, 39, p);
            stroke(0x59000000, 1.5f);
            c.drawCircle(50, 50, 39, p);
            logo(c, 25, 25, 50, null);
            stroke(0x59ffffff, 5);
            p.setStrokeCap(Paint.Cap.ROUND);
            r.set(20, 20, 80, 80);
            c.drawArc(r, 200, 70, false, p);
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        private void score(Canvas c) {
            fill(0xff0b0c10);
            r.set(3, 12, 97, 88);
            c.drawRoundRect(r, 8, 8, p);
            stroke(0xff34343c, 3);
            c.drawRoundRect(r, 8, 8, p);
            String n = name.toUpperCase(Locale.ROOT);
            label(c, n.length() > 10 ? n.substring(0, 10) : n, 50, 40, 19, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), 0xffffc23a);
            stroke(0xff2a2a30, 2);
            c.drawLine(10, 48, 90, 48, p);
            logo(c, 12, 53, 26, null);
            t.setTextAlign(Paint.Align.RIGHT);
            label(c, "OV " + (index * 3 + 4) + "." + (index % 6), 88, 77, 18, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), 0xffffd98a);
            t.setTextAlign(Paint.Align.CENTER);
        }

        private void pixel(Canvas c) {
            if (pixels == null) {
                pixels = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
                Canvas pc = new Canvas(pixels);
                pc.drawColor(0xff22222a);
                if (app != null) { app.setBounds(3, 3, 17, 17); app.draw(pc); }
            }
            Paint crisp = new Paint();
            crisp.setFilterBitmap(false);
            c.drawBitmap(pixels, null, new RectF(0, 0, 100, 100), crisp);
        }

        private void glass(Canvas c) {
            r.set(4, 4, 96, 96);
            p.setShader(new LinearGradient(0, 4, 0, 96, 0x57ffffff, 0x0fffffff, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(r, 24, 24, p);
            stroke(0x99ffffff, 1.5f);
            c.drawRoundRect(r, 24, 24, p);
            stroke(0x8cffffff, 3);
            p.setStrokeCap(Paint.Cap.ROUND);
            path.reset();
            path.moveTo(14, 32); path.quadTo(14, 13, 33, 13); path.lineTo(70, 13);
            c.drawPath(path, p);
            p.setStrokeCap(Paint.Cap.BUTT);
            logo(c, 21, 21, 58, null);
        }

        private void sticker(Canvas c) {
            // A white die-cut edge: the icon's shape in white, a little larger, behind the icon.
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                logo(c, 8 + (float) Math.cos(a) * 4, 8 + (float) Math.sin(a) * 4, 84, white());
            }
            logo(c, 8, 8, 84, null, true);
        }

        private void hud(Canvas c) {
            stroke(accent, 4);
            path.reset();
            path.moveTo(4, 24); path.lineTo(4, 4); path.lineTo(24, 4);
            path.moveTo(76, 4); path.lineTo(96, 4); path.lineTo(96, 24);
            path.moveTo(96, 76); path.lineTo(96, 96); path.lineTo(76, 96);
            path.moveTo(24, 96); path.lineTo(4, 96); path.lineTo(4, 76);
            c.drawPath(path, p);
            logo(c, 25, 20, 50, null);
            t.setLetterSpacing(0.1f);
            label(c, "APP_0" + index, 50, 90, 8, Typeface.MONOSPACE, accent);
            t.setLetterSpacing(0);
        }

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
