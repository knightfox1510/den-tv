package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import java.util.List;
import java.util.Random;

/**
 * The wake animations (looks/README.md, "wake"), played over Home when the TV switches on: Home starts hidden
 * behind the effect and comes out of it. One view per wake, gone when done; Home itself is never hidden, so an
 * interrupted wake can't leave it dark. "bloom" is DenLayer's own (covers that fade).
 */
final class DenWake extends View {
    static final List<String> STYLES = List.of("bloom", "boing", "hudboot", "termboot", "room", "fire", "powerup", "slash", "storm",
            "flutter", "sharingan", "tunnel", "panels", "ink", "crt");
    private static final long LENGTH = 1500;

    private final String style;
    private final int accent;
    private final long start = SystemClock.uptimeMillis();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final Random random = new Random(3);
    private final float[][] blobs = new float[9][3];
    long frozenAt = -1;   // tests draw one moment in time

    DenWake(Context ctx, String style, int accent) {
        super(ctx);
        this.style = style; this.accent = accent;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (float[] b : blobs) { b[0] = random.nextFloat() * 1920; b[1] = random.nextFloat() * 1080; b[2] = 0.6f + random.nextFloat() * 0.8f; }
        t.setTypeface(Typeface.MONOSPACE);
    }

    /** Plays a wake over Home, and for "boing" also bounces Home itself; false for bloom and unknown styles. */
    static boolean play(ViewGroup content, View home, String style, int accent) {
        if (!STYLES.contains(style) || "bloom".equals(style)) return false;
        if ("boing".equals(style) && home != null) {
            home.setScaleX(0.6f);
            home.setScaleY(0.6f);
            home.animate().scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(new OvershootInterpolator(3f)).start();
        }
        content.addView(new DenWake(content.getContext(), style, accent),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
    }

    private static int a(int colour, float alpha) { return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (colour & 0xffffff); }

    @Override protected void onDraw(Canvas c) {
        float el = frozenAt >= 0 ? frozenAt : SystemClock.uptimeMillis() - start;
        float k = el / LENGTH;
        if (k >= 1) {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.post(() -> parent.removeView(this));
            return;
        }
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        switch (style) {
            case "boing": black(c, 1 - k * 2); break;
            case "crt": crt(c, k); break;
            case "tunnel": hole(c, k, 1); break;
            case "room": hole(c, k, 2); break;
            case "ink": ink(c, k); break;
            case "panels": panels(c, k); break;
            case "slash": slash(c, k); break;
            case "storm": storm(c, el, k); break;
            case "fire": fire(c, k); break;
            case "powerup": powerup(c, k); break;
            case "sharingan": sharingan(c, k); break;
            case "flutter": flutter(c, k); break;
            case "hudboot": hud(c, k); break;
            case "termboot": term(c, k); break;
            default: break;
        }
        c.restore();
        if (frozenAt < 0) postInvalidateOnAnimation();
    }

    private void black(Canvas c, float alpha) {
        p.setShader(null);
        p.setColor(a(Color.BLACK, alpha));
        c.drawRect(0, 0, 1920, 1080, p);
    }

    /** An old TV: a white line opens into the picture. */
    private void crt(Canvas c, float k) {
        float open = Math.min(1, k / 0.45f);
        float h = 6 + 1074 * open * open;
        p.setColor(Color.BLACK);
        c.drawRect(0, 0, 1920, 540 - h / 2, p);
        c.drawRect(0, 540 + h / 2, 1920, 1080, p);
        p.setColor(a(Color.WHITE, (1 - open) * 0.9f + (k < 0.08f ? 1 : 0)));
        c.drawRect(0, 540 - h / 2, 1920, 540 + h / 2, p);
        if (k > 0.45f) black(c, (1 - k) * 0.3f);
    }

    /** A circle opens from the middle: the tunnel's end, or Law's ROOM with its blue rim. */
    private void hole(Canvas c, float k, int kind) {
        float rad = 40 + 1250 * k * k;
        path.reset();
        path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
        path.addCircle(960, 540, rad, Path.Direction.CW);
        p.setColor(kind == 2 ? 0xf0081420 : Color.BLACK);
        c.drawPath(path, p);
        if (kind == 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            p.setColor(a(0xff78c8ff, 1 - k));
            c.drawCircle(960, 540, rad, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** Brush blots open the picture. */
    private void ink(Canvas c, float k) {
        path.reset();
        path.setFillType(Path.FillType.INVERSE_WINDING);
        for (float[] b : blobs) path.addCircle(b[0], b[1], 900 * k * b[2], Path.Direction.CW);
        p.setColor(0xff0a0a0a);
        c.drawPath(path, p);
    }

    /** Manga panels slide off, alternately up and down. */
    private void panels(Canvas c, float k) {
        float w = 1920 / 5f;
        for (int i = 0; i < 5; i++) {
            float d = Math.max(0, Math.min(1, (k - i * 0.08f) / 0.6f));
            float y = (i % 2 == 0 ? -1 : 1) * 1080 * d * d;
            p.setColor(0xfff4f0e6);
            c.drawRect(i * w, y, (i + 1) * w, y + 1080, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(10);
            p.setColor(0xff111111);
            c.drawRect(i * w + 5, y + 5, (i + 1) * w - 5, y + 1075, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** Three sword strokes, then the halves fall apart. */
    private void slash(Canvas c, float k) {
        float split = Math.max(0, (k - 0.35f) / 0.65f);
        float off = 1300 * split * split;
        p.setColor(Color.BLACK);
        path.reset(); path.moveTo(0 - off, 0 - off); path.lineTo(1920 - off, 0 - off); path.lineTo(0 - off, 1080 - off); path.close();
        c.drawPath(path, p);
        path.reset(); path.moveTo(1920 + off, 0 + off); path.lineTo(1920 + off, 1080 + off); path.lineTo(0 + off, 1080 + off); path.close();
        c.drawPath(path, p);
        for (int i = 0; i < 3; i++) {
            float sk = Math.max(0, Math.min(1, (k - i * 0.1f) / 0.12f));
            if (sk <= 0 || k > 0.6f) continue;
            p.setColor(a(0xffe8f0ff, 1 - Math.max(0, k - 0.4f) * 5));
            p.setStrokeWidth(8 - i * 2);
            float ox = (i - 1) * 90;
            c.drawLine(ox + 1920 * (1 - sk) , 1080 * (1 - sk) - 1080, ox + 1920, -0, p);
            c.drawLine(ox, 1080, ox + 1920 * sk, 1080 - 1080 * sk, p);
        }
    }

    /** Dark, two lightning flashes, then the picture. */
    private void storm(Canvas c, float el, float k) {
        black(c, 1 - Math.max(0, (k - 0.55f) / 0.45f));
        boolean flash = (el > 250 && el < 330) || (el > 520 && el < 580);
        if (flash) { p.setColor(0xddffffff); c.drawRect(0, 0, 1920, 1080, p); }
        if (el > 240 && el < 600) {
            p.setColor(a(0xffdff6ff, 1));
            p.setStrokeWidth(6);
            float x = 1200, y = 0;
            Random r2 = new Random(11);
            for (int i = 0; i < 9; i++) {
                float nx = x + (r2.nextFloat() - 0.5f) * 160, ny = y + 90 + r2.nextFloat() * 40;
                c.drawLine(x, y, nx, ny, p);
                x = nx; y = ny;
            }
        }
    }

    /** Flames rise from the bottom and burn away. */
    private void fire(Canvas c, float k) {
        float burn = Math.max(0, (k - 0.35f) / 0.65f);
        p.setShader(new LinearGradient(0, 1080, 0, 0, new int[]{a(0xffff3a00, 1 - burn), a(0xffffa21a, 1 - burn), a(0xff2a0800, 1 - burn)},
                new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        float top = 1080 - 1600 * Math.min(1, k / 0.35f);
        path.reset();
        path.moveTo(0, 1080);
        for (int x = 0; x <= 1920; x += 80) path.lineTo(x, top + (float) Math.sin(x / 70.0 + k * 20) * 60 + (x % 160 == 0 ? -50 : 0));
        path.lineTo(1920, 1080);
        path.close();
        c.drawPath(path, p);
        p.setShader(null);
    }

    /** A golden aura swells from the middle, flashes, and fades. */
    private void powerup(Canvas c, float k) {
        black(c, 1 - k * 1.6f);
        float rr = 200 + 1500 * k;
        p.setShader(new RadialGradient(960, 600, rr, new int[]{a(0xffffffff, 1 - k), a(0xffffd23c, (1 - k) * 0.8f), 0}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 1920, 1080, p);
        p.setShader(null);
    }

    /** Red, the three tomoe spin and spread, then fade. */
    private void sharingan(Canvas c, float k) {
        p.setColor(a(0xffb00000, 1 - k));
        c.drawRect(0, 0, 1920, 1080, p);
        c.save();
        c.translate(960, 540);
        c.rotate(k * 720);
        float spread = 80 + 900 * k * k;
        p.setColor(a(0xff000000, 1 - k));
        c.drawCircle(0, 0, 40 * (1 - k) + 10, p);
        for (int i = 0; i < 3; i++) {
            c.save();
            c.rotate(i * 120);
            c.drawCircle(0, -spread, 40 + 80 * k, p);
            c.restore();
        }
        c.restore();
    }

    /** Butterflies scatter from the middle, uncovering the picture. */
    private void flutter(Canvas c, float k) {
        black(c, 1 - k * 1.5f);
        Random r2 = new Random(5);
        for (int i = 0; i < 40; i++) {
            double ang = r2.nextFloat() * Math.PI * 2;
            float dist = (200 + r2.nextFloat() * 1200) * k, x = 960 + (float) Math.cos(ang) * dist, y = 540 + (float) Math.sin(ang) * dist * 0.7f;
            float flap = (float) Math.abs(Math.cos(k * 40 + i));
            p.setColor(a(i % 2 == 0 ? 0xffc9a2ff : 0xff2fd0b0, 1 - k));
            r.set(x - 22 * flap, y - 14, x, y + 14); c.drawOval(r, p);
            r.set(x, y - 14, x + 22 * flap, y + 14); c.drawOval(r, p);
        }
    }

    /** A HUD boots: grid lines draw, the brackets lock, SYSTEM ONLINE, then it clears. */
    private void hud(Canvas c, float k) {
        black(c, k < 0.7f ? 0.92f : (1 - k) * 3);
        p.setColor(a(accent, 0.5f * (k < 0.8f ? 1 : (1 - k) * 5)));
        p.setStrokeWidth(2);
        float draw = Math.min(1, k / 0.4f);
        for (int x = 0; x <= 1920; x += 120) c.drawLine(x, 0, x, 1080 * draw, p);
        for (int y = 0; y <= 1080; y += 120) c.drawLine(0, y, 1920 * draw, y, p);
        if (k > 0.35f) {
            t.setTextSize(56);
            t.setTextAlign(Paint.Align.CENTER);
            t.setColor(a(accent, k < 0.8f ? 1 : (1 - k) * 5));
            c.drawText("SYSTEM ONLINE", 960, 560, t);
        }
    }

    /** A terminal types its boot lines, then clears. */
    private void term(Canvas c, float k) {
        black(c, k < 0.75f ? 0.95f : (1 - k) * 4);
        String[] lines = {"$ den-tv --wake", "[ ok ] looks loaded", "[ ok ] fan hub online", "[ ok ] watcher ready", "starting home..."};
        t.setTextSize(36);
        t.setTextAlign(Paint.Align.LEFT);
        int shown = (int) (lines.length * Math.min(1, k / 0.6f) + 0.999f);
        for (int i = 0; i < shown; i++) {
            t.setColor(a(i == 0 ? accent : 0xffc9d1d9, k < 0.75f ? 1 : (1 - k) * 4));
            c.drawText(lines[i], 160, 360 + i * 60, t);
        }
    }
}
