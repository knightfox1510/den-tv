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

import java.util.Random;

/**
 * A big moment over Home, about four seconds, then gone (the mockup's moments): goal (flash, GOAL!, confetti in the
 * team's colour), six (a ball sails out of the ground, fireworks), beam (a Kamehameha charges and fires across the
 * screen), arise (shadow soldiers rise), punch (a fist hits, the screen shakes white), gear5 (a white burst, bouncing
 * letters, drums) and drop (a new episode's card slides in). With reduced motion it shows only the card.
 */
final class DenMomentView extends View {
    static final long LENGTH = 4200;
    private final String type, title, line;
    private final int colour;
    private final boolean motion;
    private final long start = SystemClock.uptimeMillis();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7);
    private final float[][] bits = new float[90][6];   // confetti / sparks: x, y, vx, vy, spin, colour pick
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Typeface bebas, outfit;
    long frozenAt = -1;   // tests draw one moment in time

    DenMomentView(Context ctx, String type, String title, String line, int colour, boolean motion) {
        super(ctx);
        this.type = type; this.title = title == null ? "" : title; this.line = line == null ? "" : line;
        this.colour = colour == 0 ? 0xffff4d6d : colour | 0xff000000; this.motion = motion;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        bebas = font(ctx, R.font.bebas_neue);
        outfit = font(ctx, R.font.outfit);
        for (float[] b : bits) {
            b[0] = 960 + (random.nextFloat() - 0.5f) * 300; b[1] = 600;
            double a = -Math.PI / 2 + (random.nextFloat() - 0.5f) * 2.4;
            float v = 900 + random.nextFloat() * 900;
            b[2] = (float) Math.cos(a) * v; b[3] = (float) Math.sin(a) * v; b[4] = random.nextFloat() * 360; b[5] = random.nextInt(3);
        }
    }

    private static Typeface font(Context ctx, int id) {
        try { return ctx.getResources().getFont(id); } catch (Exception e) { return Typeface.DEFAULT_BOLD; }
    }

    @Override protected void onDraw(Canvas c) {
        float el = frozenAt >= 0 ? frozenAt : SystemClock.uptimeMillis() - start;
        float k = Math.min(1, el / LENGTH);
        if (k >= 1) {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.post(() -> parent.removeView(this));
            return;
        }
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        float fadeOut = el > LENGTH - 500 ? (LENGTH - el) / 500f : 1;
        if (!motion) { card(c, 1, fadeOut); c.restore(); postInvalidateDelayed(100); return; }
        switch (type) {
            case "six": six(c, el, fadeOut); break;
            case "beam": beam(c, el, fadeOut); break;
            case "arise": arise(c, el, fadeOut); break;
            case "punch": punch(c, el, fadeOut); break;
            case "gear5": gear5(c, el, fadeOut); break;
            case "drop": drop(c, el, fadeOut); break;
            default: goal(c, el, fadeOut); break;
        }
        c.restore();
        postInvalidateOnAnimation();
    }

    private static int alpha(int colour, float a) { return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (colour & 0xffffff); }

    private void dim(Canvas c, float a) {
        p.setShader(null);
        p.setColor(alpha(0xff000000, a));
        c.drawRect(0, 0, 1920, 1080, p);
    }

    /** The big word, centred, popping in; and the line under it. */
    private void word(Canvas c, String w, float size, float y, int fill, float pop, float a) {
        t.setTypeface(bebas);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(size * pop);
        t.setColor(alpha(fill, a));
        t.setShadowLayer(40, 0, 0, alpha(colour, a));
        c.drawText(w.toUpperCase(), 960, y, t);
        t.clearShadowLayer();
    }

    private void sub(Canvas c, String s, float y, float a) {
        t.setTypeface(outfit);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(44);
        t.setColor(alpha(0xffffffff, a));
        t.setLetterSpacing(0.08f);
        c.drawText(s, 960, y, t);
        t.setLetterSpacing(0);
    }

    private static float pop(float el, float from) {
        float x = Math.max(0, Math.min(1, (el - from) / 350f));
        return x <= 0 ? 0.01f : (float) (1 + 0.25 * Math.sin(x * Math.PI)) * Math.min(1, x * 1.5f);
    }

    private void confetti(Canvas c, float el, float from, int[] colours, float a) {
        float dt = Math.max(0, (el - from) / 1000f);
        if (dt <= 0) return;
        for (float[] b : bits) {
            float x = b[0] + b[2] * dt * 0.6f, y = b[1] + b[3] * dt * 0.6f + 700 * dt * dt;
            c.save();
            c.translate(x, y);
            c.rotate(b[4] + dt * 360);
            p.setShader(null);
            p.setColor(alpha(colours[(int) b[5] % colours.length], a));
            c.drawRect(-9, -5, 9, 5, p);
            c.restore();
        }
    }

    private void goal(Canvas c, float el, float a) {
        float flash = el < 250 ? 1 - el / 250f : 0;
        dim(c, 0.45f * a);
        p.setColor(alpha(0xffffffff, flash * 0.9f));
        c.drawRect(0, 0, 1920, 1080, p);
        // Camera flashes in the stands.
        for (int i = 0; i < 14; i++) {
            float fx = (i * 137) % 1920, fy = 120 + (i * 61) % 300;
            float on = (float) Math.max(0, Math.sin(el / 90.0 + i * 1.7));
            p.setShader(new RadialGradient(fx, fy, 40, alpha(0xffffffff, on * a), 0, Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, 40, p);
        }
        p.setShader(null);
        word(c, title.isEmpty() ? "Goal!" : title, 300, 600, 0xffffffff, pop(el, 150), a);
        sub(c, line, 690, Math.min(1, Math.max(0, (el - 600) / 400f)) * a);
        confetti(c, el, 300, new int[]{colour, 0xffffffff, 0xffedbb00}, a);
    }

    private void six(Canvas c, float el, float a) {
        dim(c, 0.4f * a);
        // The ball's flight: from the bat, out over the roof.
        float f = Math.min(1, el / 1400f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        p.setColor(alpha(0xffffffff, 0.5f * a));
        path.reset();
        path.moveTo(300, 950);
        for (float q = 0; q <= f; q += 0.02f) path.lineTo(300 + q * 1500, 950 - (float) Math.sin(q * Math.PI * 0.85) * 820);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        float bx = 300 + f * 1500, by = 950 - (float) Math.sin(f * Math.PI * 0.85) * 820;
        p.setColor(alpha(0xffd0140c, a));
        c.drawCircle(bx, by, 16, p);
        // Fireworks once it lands.
        for (int b = 0; b < 4; b++) {
            float t0 = 1300 + b * 350, dt = (el - t0) / 1000f;
            if (dt <= 0 || dt > 1.4f) continue;
            float cx = 400 + b * 380, cy = 220 + (b % 2) * 120;
            for (int i = 0; i < 24; i++) {
                double ang = i * Math.PI / 12;
                float rr = 220 * (float) Math.sqrt(dt);
                p.setColor(alpha(b % 2 == 0 ? colour : 0xffffd23c, (1.4f - dt) * a));
                c.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr + 60 * dt * dt, 6, p);
            }
        }
        word(c, title.isEmpty() ? "Six!" : title, 280, 600, 0xffffffff, pop(el, 900), a);
        sub(c, line, 690, Math.min(1, Math.max(0, (el - 1300) / 400f)) * a);
    }

    private void beam(Canvas c, float el, float a) {
        dim(c, 0.55f * a);
        float charge = Math.min(1, el / 1200f);
        float ox = 260, oy = 560;
        p.setShader(new RadialGradient(ox, oy, 60 + 120 * charge, new int[]{alpha(0xffffffff, a), alpha(0xff7fd8ff, a), 0}, new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(ox, oy, 60 + 120 * charge, p);
        if (el > 1200) {
            float reach = Math.min(1, (el - 1200) / 350f) * 1800;
            float wob = (float) Math.sin(el / 30.0) * 8;
            p.setShader(new LinearGradient(0, oy - 90, 0, oy + 90, new int[]{0, alpha(0xff3ab0ff, a), alpha(0xffffffff, a), alpha(0xff3ab0ff, a), 0},
                    new float[]{0, 0.25f, 0.5f, 0.75f, 1}, Shader.TileMode.CLAMP));
            r.set(ox, oy - 90 - wob, ox + reach, oy + 90 + wob);
            c.drawRoundRect(r, 90, 90, p);
        }
        p.setShader(null);
        word(c, title, 200, 300, 0xffffffff, pop(el, 1300), a);
        sub(c, line, 860, Math.min(1, Math.max(0, (el - 1600) / 400f)) * a);
    }

    private void arise(Canvas c, float el, float a) {
        p.setShader(new RadialGradient(960, 1080, 1300, new int[]{alpha(0xff2a0a5a, 0.85f * a), alpha(0xff050208, 0.9f * a)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 1920, 1080, p);
        p.setShader(null);
        for (int i = 0; i < 9; i++) {
            float rise = Math.min(1, Math.max(0, (el - 300 - i * 120) / 700f));
            float x = 160 + i * 200, h = 300 + (i % 3) * 60, top = 1080 - h * rise;
            p.setColor(alpha(0xff06040c, a));
            r.set(x - 45, top + 60, x + 45, 1080);
            c.drawRoundRect(r, 20, 20, p);
            c.drawCircle(x, top + 34, 34, p);
            p.setColor(alpha(0xff9a8aff, a * rise));
            c.drawCircle(x - 11, top + 30, 5, p);
            c.drawCircle(x + 11, top + 30, 5, p);
        }
        word(c, title, 260, 420, 0xffd8ccff, pop(el, 1300), a);
        sub(c, line, 510, Math.min(1, Math.max(0, (el - 1600) / 400f)) * a);
    }

    private void punch(Canvas c, float el, float a) {
        float hit = 700;
        float shake = el > hit && el < hit + 500 ? (float) Math.sin(el / 18.0) * 24 * (1 - (el - hit) / 500) : 0;
        c.translate(shake, 0);
        dim(c, 0.35f * a);
        float z = Math.min(1, el / hit);
        // The fist rushes at the screen.
        float size = 120 + 900 * z * z;
        float fist = a * (el > hit ? Math.max(0, 1 - (el - hit) / 250f) : 1);
        p.setColor(alpha(0xffd0140c, fist));
        r.set(960 - size / 2, 540 - size * 0.38f, 960 + size * 0.3f, 540 + size * 0.38f);
        c.drawRoundRect(r, size * 0.2f, size * 0.2f, p);
        p.setColor(alpha(0xfff6d6b0, fist));   // the wrist
        r.set(960 + size * 0.24f, 540 - size * 0.2f, 960 + size / 2, 540 + size * 0.2f);
        c.drawRoundRect(r, size * 0.08f, size * 0.08f, p);
        p.setColor(alpha(0xff7a0a06, fist));   // knuckles
        p.setStrokeWidth(size * 0.025f);
        for (int i = 1; i < 4; i++) c.drawLine(960 - size / 2 + i * size * 0.2f, 540 - size * 0.38f, 960 - size / 2 + i * size * 0.2f, 540 + size * 0.38f, p);
        if (el > hit) {
            float f = Math.max(0, 1 - (el - hit) / 300f);
            p.setColor(alpha(0xffffffff, f));
            c.drawRect(-40, 0, 1960, 1080, p);
            p.setColor(alpha(0xffffffff, 0.6f * a));
            p.setStrokeWidth(6);
            for (int i = 0; i < 24; i++) {
                double ang = i * Math.PI / 12;
                c.drawLine(960 + (float) Math.cos(ang) * 420, 540 + (float) Math.sin(ang) * 420, 960 + (float) Math.cos(ang) * 900, 540 + (float) Math.sin(ang) * 900, p);
            }
        }
        word(c, title, 240, 600, 0xffffd23c, pop(el, hit + 100), a);
        sub(c, line, 690, Math.min(1, Math.max(0, (el - hit - 500) / 400f)) * a);
    }

    private void gear5(Canvas c, float el, float a) {
        float burst = Math.min(1, el / 500f);
        p.setShader(new RadialGradient(960, 540, 1400 * burst + 1, new int[]{alpha(0xffffffff, 0.95f * a), alpha(0xfff4f0ff, 0.7f * a), alpha(0xffb9a8ff, 0.4f * a)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 1920, 1080, p);
        p.setShader(null);
        String w = title.toUpperCase();
        t.setTypeface(bebas);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(260);
        float total = t.measureText(w), x = 960 - total / 2;
        for (int i = 0; i < w.length(); i++) {
            String ch = String.valueOf(w.charAt(i));
            float cw = t.measureText(ch), bounce = (float) Math.abs(Math.sin(el / 160.0 + i * 0.6)) * 70 * Math.min(1, el / 600f);
            t.setColor(alpha(0xff2a1f3a, a));
            c.drawText(ch, x + cw / 2, 600 - bounce, t);
            x += cw;
        }
        t.setTextSize(64);
        t.setColor(alpha(0xff6a4ad0, a * (float) Math.abs(Math.sin(el / 220.0))));
        c.drawText("DON  DON  DON", 960, 760, t);
        t.setTypeface(outfit);
        t.setTextSize(44);
        t.setColor(alpha(0xff2a1f3a, a));
        c.drawText(line, 960, 840, t);
    }

    private void drop(Canvas c, float el, float a) {
        float in = Math.min(1, el / 500f);
        card(c, (float) (1 - Math.pow(1 - in, 3)), a);
    }

    /** The new episode (or, with reduced motion, any moment) as a card on the right. */
    private void card(Canvas c, float in, float a) {
        float w = 760, h = 230, x = 1920 - 80 - w + (1 - in) * (w + 120), y = 140;
        p.setShader(null);
        p.setColor(alpha(0xe6101016, a));
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, 24, 24, p);
        p.setColor(alpha(colour, a));
        r.set(x, y, x + 14, y + h);
        c.drawRoundRect(r, 7, 7, p);
        t.setTypeface(bebas);
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(44);
        t.setLetterSpacing(0.12f);
        t.setColor(alpha(colour, a));
        c.drawText(title.toUpperCase(), x + 50, y + 75, t);
        t.setLetterSpacing(0);
        t.setTypeface(outfit);
        t.setTextSize(48);
        t.setColor(alpha(Color.WHITE, a));
        CharSequence l = android.text.TextUtils.ellipsize(line, new android.text.TextPaint(t), w - 100, android.text.TextUtils.TruncateAt.END);
        c.drawText(l, 0, l.length(), x + 50, y + 155, t);
    }
}
