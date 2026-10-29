package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * den-tv watcher: a pair of eyes at the top of Home that look at the focused tile and blink, drawn the same way
 * as the mockup (mockups/den-looks.html, eyeSVG). Everything is laid out on a 1920x1080 grid and scaled to the
 * view. It draws only when the gaze moves or the eyes blink; no blur, so it stays cheap on a 1-2 GB TV.
 */
final class DenEyesView extends View {
    private static final float CX = 960, CY = 250, DOCK_Y = 860;

    private final int g0, g1, g2, glow, lash, brow;
    private final boolean sharp, whiskers, pigment;
    private final String kind;
    private final boolean motion;
    private final Path shape, lashLine, browShape;
    private final float irisY, irisR;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient irisShade, glowShade, shadowShade;

    private float lookX = CX, lookY = DOCK_Y;   // where the eyes look, on the 1920x1080 grid
    private float lid = 1f;                     // 1 open, near 0 shut
    private ValueAnimator gaze, blinking;

    private final Runnable blink = new Runnable() {
        @Override public void run() {
            if (blinking != null) blinking.cancel();
            blinking = ValueAnimator.ofFloat(1f, 0.06f, 1f).setDuration(260);
            blinking.addUpdateListener(a -> { lid = (float) a.getAnimatedValue(); invalidate(); });
            blinking.start();
            postDelayed(this, 3000 + (long) (Math.random() * 6000));
        }
    };

    DenEyesView(Context ctx, String kind, boolean motion) {
        super(ctx);
        this.kind = kind;
        this.motion = motion;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int[] g; int gl, ls, br = 0; boolean sh = false, wh = false, pg = false;
        switch (kind) {
            case "six": g = new int[]{0xffe9fdff, 0xff7fd8ff, 0xff1d5fd6}; gl = Color.argb(153, 120, 210, 255); ls = 0xfff4f8ff; break;
            case "saiyan": g = new int[]{0xffd4fff2, 0xff22c7a6, 0xff07463e}; gl = Color.argb(178, 255, 210, 60); ls = 0xff111111; sh = true; br = 0xff1a1206; break;
            case "ui": g = new int[]{0xffffffff, 0xffc6d0e2, 0xff5a6a8a}; gl = Color.argb(178, 190, 210, 255); ls = 0xff1a1f2e; sh = true; br = 0xffc9d2e6; break;
            case "rinnegan": g = new int[]{0xffe4d6ff, 0xffa78bff, 0xff5a3aa8}; gl = Color.argb(153, 160, 120, 255); ls = 0xff111111; sh = true; break;
            case "kurama": g = new int[]{0xffffd27a, 0xffff5a00, 0xff6a1000}; gl = Color.argb(178, 255, 90, 0); ls = 0xff1a0500; sh = true; wh = true; break;
            case "sage": g = new int[]{0xfffff2a8, 0xffe2b400, 0xff6a4a00}; gl = Color.argb(140, 255, 140, 40); ls = 0xff2a0e00; pg = true; break;
            default: g = new int[]{0xffff3b3b, 0xffb00000, 0xff3a0000}; gl = Color.argb(153, 255, 40, 40); ls = 0xff000000; break;   // sharingan, mangekyo
        }
        g0 = g[0]; g1 = g[1]; g2 = g[2]; glow = gl; lash = ls; brow = br; sharp = sh; whiskers = wh; pigment = pg;
        shape = new Path();
        lashLine = new Path();
        if (sharp) {
            shape.moveTo(2, 26); shape.quadTo(96, 2, 198, 58); shape.quadTo(96, 98, 2, 26); shape.close();
            lashLine.moveTo(2, 26); lashLine.quadTo(96, 2, 198, 58);
            irisY = 54; irisR = 28;
        } else {
            shape.moveTo(6, 45); shape.quadTo(100, -18, 194, 45); shape.quadTo(100, 108, 6, 45); shape.close();
            lashLine.moveTo(6, 45); lashLine.quadTo(100, -18, 194, 45);
            irisY = 56; irisR = 34;
        }
        browShape = new Path();
        browShape.moveTo(-4, -6); browShape.lineTo(204, 30); browShape.lineTo(200, 44); browShape.lineTo(-2, 8); browShape.close();
        irisShade = new RadialGradient(0, 0, irisR, new int[]{g0, g1, g2}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        glowShade = new RadialGradient(100, 45, 110, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        shadowShade = new RadialGradient(0, 0, 380, Color.argb(178, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Turns the eyes toward a point in screen pixels (the focused tile's centre). */
    void lookAt(float x, float y) {
        if (getWidth() == 0) return;
        float s = 1920f / getWidth();
        float tx = x * s, ty = y * s;
        if (gaze != null) gaze.cancel();
        if (!motion) { lookX = tx; lookY = ty; invalidate(); return; }
        float fx = lookX, fy = lookY;
        gaze = ValueAnimator.ofFloat(0f, 1f).setDuration(500);
        gaze.setInterpolator(new OvershootInterpolator(1.3f));
        gaze.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            lookX = fx + (tx - fx) * t;
            lookY = fy + (ty - fy) * t;
            invalidate();
        });
        gaze.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (motion) postDelayed(blink, 2500);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(blink);
        if (gaze != null) gaze.cancel();
        if (blinking != null) blinking.cancel();
        super.onDetachedFromWindow();
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

    @Override protected void onDraw(Canvas c) {
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        c.translate(CX, CY);
        c.save();
        c.scale(1f, 0.25f);
        fill.setColor(Color.BLACK);   // a shader draws at the paint's alpha, so start opaque
        fill.setShader(shadowShade);
        c.drawCircle(0, 0, 380, fill);
        fill.setShader(null);
        c.restore();
        float dy = clamp((lookY - DOCK_Y) / 300f) * 9f;
        float dl = clamp((lookX - (CX - 150)) / 800f) * 30f, dr = clamp((lookX - (CX + 150)) / 800f) * 30f;
        eye(c, -250, -45, false, dl, dy);
        eye(c, 50, -45, true, -dr, dy);   // drawn mirrored, so its sideways shift is mirrored too
        c.restore();
    }

    private void eye(Canvas c, float x, float y, boolean mirror, float dx, float dy) {
        c.save();
        c.translate(x, y);
        if (mirror) { c.translate(200, 0); c.scale(-1, 1); }
        c.scale(1f, lid, 100, 45);
        c.save();
        c.scale(1f, 30f / 80f, 100, 45);
        fill.setColor(Color.BLACK);
        fill.setShader(glowShade);
        c.drawCircle(100, 45, 110, fill);
        fill.setShader(null);
        c.restore();
        if (brow != 0) { fill.setColor(brow); c.drawPath(browShape, fill); }
        if (whiskers) {
            line.setColor(0xff1a0500); line.setStrokeWidth(7);
            for (int k = 0; k < 3; k++) {
                float wy = 92 + k * 16;
                Path p = new Path(); p.moveTo(24 + k * 4, wy); p.quadTo(80, wy - 6, 140 - k * 6, wy + 4);
                c.drawPath(p, line);
            }
        }
        if (pigment) {
            Path p = new Path(); p.moveTo(-6, 38); p.quadTo(100, -40, 206, 38);
            line.setColor(Color.argb(217, 220, 80, 30)); line.setStrokeWidth(16); c.drawPath(p, line);
            p = new Path(); p.moveTo(24, 70); p.quadTo(100, 104, 176, 70);
            line.setColor(Color.argb(178, 220, 80, 30)); line.setStrokeWidth(8); c.drawPath(p, line);
        }
        c.save();
        c.clipPath(shape);
        fill.setColor(Color.argb(237, 220, 213, 210)); c.drawRect(0, 0, 200, 90, fill);
        fill.setColor(Color.argb(89, 0, 0, 0)); c.drawRect(0, 0, 200, 30, fill);
        c.translate(100 + dx, irisY + dy);
        fill.setColor(Color.BLACK); fill.setShader(irisShade); c.drawCircle(0, 0, irisR, fill); fill.setShader(null);
        pattern(c);
        fill.setColor(Color.argb(217, 255, 255, 255)); c.drawCircle(-9, -10, 4, fill);
        c.restore();
        line.setColor(lash); line.setStrokeWidth("kurama".equals(kind) ? 9 : 6);
        c.drawPath(lashLine, line);
        c.restore();
    }

    private void pattern(Canvas c) {
        switch (kind) {
            case "sharingan":
                for (int a = 0; a < 360; a += 120) {
                    c.save(); c.rotate(a);
                    fill.setColor(0xff0a0000); c.drawCircle(0, -19, 5.2f, fill);
                    Path tail = new Path(); tail.moveTo(5, -19); tail.quadTo(8, -10, -2, -8);
                    line.setColor(0xff0a0000); line.setStrokeWidth(3); c.drawPath(tail, line);
                    c.restore();
                }
                line.setColor(Color.argb(204, 26, 0, 0)); line.setStrokeWidth(1.6f); c.drawCircle(0, 0, 22, line);
                fill.setColor(0xff050000); c.drawCircle(0, 0, 6.5f, fill);
                break;
            case "mangekyo":
                fill.setColor(0xff0a0000);
                for (int a = 0; a < 360; a += 120) {
                    c.save(); c.rotate(a);
                    Path b = new Path(); b.moveTo(0, -6); b.cubicTo(10, -14, 24, -16, 30, -6); b.cubicTo(22, -10, 12, -6, 6, 2); b.close();
                    c.drawPath(b, fill);
                    c.restore();
                }
                line.setColor(0xff0a0000); line.setStrokeWidth(4); c.drawCircle(0, 0, 9, line);
                fill.setColor(0xff050000); c.drawCircle(0, 0, 6, fill);
                break;
            case "six":
                line.setColor(Color.argb(140, 234, 255, 255)); line.setStrokeWidth(0.8f);
                for (int i = 0; i < 36; i++) {
                    double a = Math.toRadians(i * 10);
                    c.drawLine((float) Math.cos(a) * 9, (float) Math.sin(a) * 9, (float) Math.cos(a) * 32, (float) Math.sin(a) * 32, line);
                }
                fill.setColor(0xff0a2a55); c.drawCircle(0, 0, 6, fill);
                break;
            case "rinnegan":
                line.setColor(0xff2a1a5a); line.setStrokeWidth(1.8f);
                for (int r = 6; r <= 24; r += 6) c.drawCircle(0, 0, r, line);
                fill.setColor(0xff1a0f3a); c.drawCircle(0, 0, 3.5f, fill);
                break;
            case "kurama":
                fill.setColor(0xff0a0000); c.drawOval(-4.5f, -26, 4.5f, 26, fill);
                break;
            case "sage":
                fill.setColor(0xff120800); c.drawRoundRect(-15, -4.5f, 15, 4.5f, 4, 4, fill);
                break;
            default:   // saiyan, ui
                fill.setColor("ui".equals(kind) ? 0xff2a3550 : 0xff04241f); c.drawCircle(0, 0, "ui".equals(kind) ? 4 : 5, fill);
                break;
        }
    }
}
