package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Opening an app and coming back Home (the mockup's "open" and "back" animations). Opening: zoom (the tile grows
 * into the app, Android's own scale-up), portal (a clip reveal from the tile), and six drawn over Home for a third
 * of a second before the app starts: warp (speed lines from the tile), slash (three sword cuts), panels (manga
 * panels slam shut), shatter (shards burst from the tile), ink (an ink blot spreads) and flash (a camera flash).
 * Coming back: shrink (the screen shrinks into the tile), iris (a circle opens on the tile), slide (a curtain
 * lifts), shatter (shards fly off) and fade. Each follows the look (from its wake), or is chosen on the phone, or
 * random, or off. Calm motion keeps only zoom and fade; Off has none.
 */
final class DenLaunch {
    static final List<String> OPEN = List.of("zoom", "portal", "warp", "slash", "panels", "shatter", "ink", "flash");
    static final List<String> BACK = List.of("shrink", "iris", "slide", "shatter", "fade");
    static final String OPEN_KEY = "open_anim", BACK_KEY = "back_anim";
    private static final long OPEN_MS = 330, BACK_MS = 420;

    /** An app was opened from a tile; Home plays its back animation on the next resume (DenLayer). */
    private static long openedAt;
    private static final Random random = new Random();

    private DenLaunch() {}

    /** The look's own opening, from its wake: a slash look slashes, a manga look shuts its panels, and so on. */
    static String lookOpen(DenLooks.Look look) {
        switch (look == null ? "" : look.wake) {
            case "slash": return "slash";
            case "panels": return "panels";
            case "ink": return "ink";
            case "storm": case "crt": case "hudboot": case "termboot": return "flash";
            case "room": case "sharingan": return "portal";
            case "powerup": case "fire": case "flutter": return "warp";
            case "boing": return "shatter";
            default: return "zoom";
        }
    }

    static String backFor(String open) {
        switch (open) {
            case "portal": case "ink": return "iris";
            case "warp": case "panels": return "slide";
            case "slash": case "shatter": return "shatter";
            case "flash": return "fade";
            default: return "shrink";
        }
    }

    private static String choice(Context ctx, String key, List<String> all, String lookOwn) {
        String c = DenSettings.prefs(ctx).getString(key, "look");
        if ("off".equals(c)) return "";
        if ("random".equals(c)) return all.get(random.nextInt(all.size()));
        return all.contains(c) ? c : lookOwn;
    }

    /**
     * Opens through the chosen animation: start gets ActivityOptions (zoom, portal) or null after an overlay has
     * played. False when there is none (motion off, or chosen off): the caller starts the app as before.
     */
    static boolean open(Activity a, View tile, Consumer<Bundle> start) {
        if (!motionOn(a) || tile == null || tile.getWidth() == 0) return false;
        DenLooks.Look look = DenLooks.tweaked(a, DenLooks.current(a, DenLooks.load(a)));
        String style = DenSettings.calm(a) ? "zoom" : choice(a, OPEN_KEY, OPEN, lookOpen(look));
        if (style.isEmpty()) return false;
        openedAt = SystemClock.uptimeMillis();
        lastOpen = style;
        if (style.equals("zoom")) { start.accept(ActivityOptions.makeScaleUpAnimation(tile, 0, 0, tile.getWidth(), tile.getHeight()).toBundle()); return true; }
        if (style.equals("portal")) { start.accept(ActivityOptions.makeClipRevealAnimation(tile, 0, 0, tile.getWidth(), tile.getHeight()).toBundle()); return true; }
        ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return false;
        Fx fx = new Fx(a, style, false, rectOf(tile, root), accent(look));
        root.addView(fx, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fx.postDelayed(() -> {
            start.accept(null);
            a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            fx.postDelayed(() -> root.removeView(fx), 700);   // Home is behind the app by then
        }, OPEN_MS);
        return true;
    }

    private static String lastOpen = "zoom";

    /** The TV woke up: its wake plays, not a back animation. */
    static void forget() { openedAt = 0; }

    /** On Home's resume: the back animation, once, when Home sent the TV into an app (within the last 6 hours). */
    static void back(Activity a, View focusedTile) {
        if (openedAt == 0 || SystemClock.uptimeMillis() - openedAt > 6 * 3_600_000L) { openedAt = 0; return; }
        openedAt = 0;
        if (!motionOn(a)) return;
        DenLooks.Look look = DenLooks.tweaked(a, DenLooks.current(a, DenLooks.load(a)));
        String style = DenSettings.calm(a) ? "fade" : choice(a, BACK_KEY, BACK, backFor(lastOpen));
        if (style.isEmpty()) return;
        ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return;
        Fx fx = new Fx(a, style, true, focusedTile == null ? null : rectOf(focusedTile, root), accent(look));
        root.addView(fx, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fx.postDelayed(() -> root.removeView(fx), BACK_MS + 50);
    }

    private static boolean motionOn(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean("animations", true)
                && !DenSettings.reducedMotion(ctx, false);
    }

    private static int accent(DenLooks.Look look) {
        try { return Color.parseColor(look == null ? "#ff4d6d" : look.accent); } catch (RuntimeException e) { return 0xffff4d6d; }
    }

    private static RectF rectOf(View v, ViewGroup root) {
        int[] a = new int[2], b = new int[2];
        v.getLocationInWindow(a);
        root.getLocationInWindow(b);
        float x = a[0] - b[0], y = a[1] - b[1];
        return new RectF(x, y, x + v.getWidth() * v.getScaleX(), y + v.getHeight() * v.getScaleY());
    }

    /** One opening or back animation, drawn over Home. */
    static final class Fx extends View {
        private final String style;
        private final boolean back;
        private final RectF tile;
        private final int accent;
        private final long start = SystemClock.uptimeMillis();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float[][] shards;
        private long frozen = -1;

        Fx(Context ctx, String style, boolean back, RectF tile, int accent) {
            super(ctx);
            this.style = style; this.back = back; this.tile = tile; this.accent = accent;
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            Random r = new Random(7);
            shards = new float[28][];
            for (int i = 0; i < shards.length; i++) shards[i] = new float[]{r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()};
        }

        /** For the screen renders: draw at this progress (0 to 1). */
        void freeze(float t) { frozen = Math.round(t * 1000); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            long dur = back ? BACK_MS : OPEN_MS;
            float t = frozen >= 0 ? frozen / 1000f : Math.min(1, (SystemClock.uptimeMillis() - start) / (float) dur);
            float cx = tile == null ? w / 2 : tile.centerX(), cy = tile == null ? h / 2 : tile.centerY();
            float far = (float) Math.hypot(Math.max(cx, w - cx), Math.max(cy, h - cy));
            float e = (float) (1 - Math.pow(1 - t, 3));
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            if (!back) switch (style) {
                case "warp": {
                    p.setColor((Math.round(200 * e) << 24));
                    c.drawRect(0, 0, w, h, p);
                    Random r = new Random(3);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    for (int i = 0; i < 90; i++) {
                        double ang = r.nextFloat() * Math.PI * 2;
                        float a0 = far * (0.05f + 0.9f * r.nextFloat()) * e, len = far * 0.35f * e;
                        p.setStrokeWidth(2 + r.nextFloat() * 4);
                        p.setColor(i % 4 == 0 ? accent : 0xddffffff);
                        c.drawLine(cx + (float) Math.cos(ang) * a0, cy + (float) Math.sin(ang) * a0,
                                cx + (float) Math.cos(ang) * (a0 + len), cy + (float) Math.sin(ang) * (a0 + len), p);
                    }
                    break;
                }
                case "slash": {
                    p.setColor((Math.round(180 * t) << 24));
                    c.drawRect(0, 0, w, h, p);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    for (int k = 0; k < 3; k++) {
                        float q = Math.max(0, Math.min(1, t * 3 - k * 0.6f));
                        if (q <= 0) continue;
                        float y0 = h * (0.15f + 0.3f * k), x0 = -w * 0.1f, x1 = x0 + w * 1.2f * q;
                        p.setColor(0xffffffff);
                        p.setStrokeWidth(10 * (1 - q * 0.5f));
                        c.drawLine(x0, y0 + h * 0.25f, x1, y0 + h * 0.25f - (x1 - x0) * 0.35f, p);
                        p.setColor(accent);
                        p.setStrokeWidth(3);
                        c.drawLine(x0, y0 + h * 0.25f + 8, x1, y0 + h * 0.25f + 8 - (x1 - x0) * 0.35f, p);
                    }
                    break;
                }
                case "panels": {
                    // Six manga panels slam in from alternate sides, ink black with white gutters.
                    float gw = w / 3f, gh = h / 2f;
                    for (int i = 0; i < 6; i++) {
                        float q = Math.max(0, Math.min(1, (t - i * 0.08f) / 0.5f)), qe = (float) (1 - Math.pow(1 - q, 3));
                        float px = (i % 3) * gw, py = (i / 3) * gh, off = (i % 2 == 0 ? -1 : 1) * w * (1 - qe);
                        p.setColor(0xff0a0a0a);
                        c.drawRect(px + off + 6, py + 6, px + gw + off - 6, py + gh - 6, p);
                        p.setColor(0xff1d1d1d);
                        for (float sx = px + off + 20; sx < px + gw + off - 10; sx += 18) c.drawRect(sx, py + 6, sx + 2, py + gh - 6, p);
                    }
                    break;
                }
                case "shatter": {
                    p.setColor((Math.round(220 * e) << 24));
                    c.drawRect(0, 0, w, h, p);
                    drawShards(c, cx, cy, far * e, 1 - t * 0.3f);
                    break;
                }
                case "ink": {
                    p.setColor(0xff070708);
                    path.reset();
                    float rad = far * 1.1f * e;
                    for (int i = 0; i <= 48; i++) {
                        double ang = i / 48.0 * Math.PI * 2;
                        float wob = 1 + 0.12f * (float) Math.sin(ang * 5 + t * 4) + 0.06f * (float) Math.sin(ang * 11);
                        float x = cx + (float) Math.cos(ang) * rad * wob, y = cy + (float) Math.sin(ang) * rad * wob;
                        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case "flash": {
                    float k = t < 0.25f ? t / 0.25f : 1 - (t - 0.25f) / 0.75f * 0.4f;
                    p.setShader(new RadialGradient(cx, cy, Math.max(1, far), new int[]{0xffffffff, 0xf0ffffff, (Math.round(255 * k) << 24) | 0xffffff},
                            new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
                    p.setAlpha(Math.round(255 * Math.min(1, k)));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                    p.setColor((Math.round(255 * Math.max(0, t - 0.6f) / 0.4f) << 24));
                    c.drawRect(0, 0, w, h, p);
                    break;
                }
                default: break;
            } else switch (style) {
                case "shrink": {
                    // The app's screen shrinking back into its tile.
                    RectF to = tile == null ? new RectF(w / 2, h / 2, w / 2, h / 2) : tile;
                    float l = to.left * e, top = to.top * e, r = w + (to.right - w) * e, b = h + (to.bottom - h) * e;
                    p.setColor((Math.round(235 * (1 - t)) << 24) | 0x0a0a10);
                    c.drawRoundRect(l, top, r, b, 24 * e, 24 * e, p);
                    break;
                }
                case "iris": {
                    path.reset();
                    path.addRect(0, 0, w, h, Path.Direction.CW);
                    path.addCircle(cx, cy, Math.max(1, far * e), Path.Direction.CCW);
                    p.setColor(0xff050507);
                    c.drawPath(path, p);
                    break;
                }
                case "slide": {
                    p.setColor(0xff050507);
                    c.drawRect(0, -h * e, w, h * (1 - e), p);
                    p.setColor(accent);
                    c.drawRect(0, h * (1 - e) - 6, w, h * (1 - e), p);
                    break;
                }
                case "shatter": {
                    drawShards(c, w / 2, h / 2, far * (0.2f + e), 1 - t);
                    break;
                }
                default: {
                    p.setColor((Math.round(255 * (1 - e)) << 24));
                    c.drawRect(0, 0, w, h, p);
                    break;
                }
            }
            if (frozen < 0 && t < 1) postInvalidateOnAnimation();
        }

        /** Triangle shards in the look's colour and white, flying out from (cx, cy) by spread. */
        private void drawShards(Canvas c, float cx, float cy, float spread, float alpha) {
            for (float[] s : shards) {
                double ang = s[0] * Math.PI * 2;
                float d = spread * (0.3f + 0.7f * s[1]), size = 30 + 90 * s[2];
                float x = cx + (float) Math.cos(ang) * d, y = cy + (float) Math.sin(ang) * d;
                p.setColor(((Math.round(255 * Math.max(0, Math.min(1, alpha)))) << 24) | ((s[3] < 0.5f ? accent : 0xffffff) & 0xffffff));
                path.reset();
                float rot = s[4] * 6.28f + spread / 300f;
                for (int k = 0; k < 3; k++) {
                    double a = rot + k * 2.1;
                    float px = x + (float) Math.cos(a) * size, py = y + (float) Math.sin(a) * size * 0.6f;
                    if (k == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                c.drawPath(path, p);
            }
        }
    }
}
