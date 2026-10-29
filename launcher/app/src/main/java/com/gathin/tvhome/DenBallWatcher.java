package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.List;

/**
 * Ball watchers, drawn in code on the 1920x1080 grid: drive (Sachin's straight drive: a cricket ball races along the
 * ground to the picked app and the crowd chants "SA-CHIN!"), trivela (Yamal: a football curls in to it with the
 * outside of the boot, trailing the look's colour) and onetwo (Raphinha's 11 on the left and Yamal's 10 on the
 * right run with the app, and play a one-two into it). They move only when the focus moves; nothing animates idle.
 */
final class DenBallWatcher extends View implements DenLayer.Watcher {
    static final List<String> KINDS = List.of("drive", "trivela", "onetwo");

    private final String kind;
    private final int accent;
    private final boolean motion;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private float fromX = Float.NaN, fromY, x = Float.NaN, y;
    private long at;
    private boolean moved;

    DenBallWatcher(Context ctx, String kind, String accentHex, boolean motion) {
        super(ctx);
        this.kind = kind;
        this.motion = motion;
        int a;
        try { a = Color.parseColor(accentHex); } catch (RuntimeException e) { a = 0xffedbb00; }
        accent = a;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Typeface f;
        try { f = ctx.getResources().getFont(R.font.outfit); } catch (RuntimeException e) { f = Typeface.SANS_SERIF; }
        t.setTypeface(android.os.Build.VERSION.SDK_INT >= 28 ? Typeface.create(f, 900, false) : Typeface.create(f, Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
    }

    @Override public void lookAt(float sx, float sy, String label) {
        if (getWidth() == 0) return;
        float s = 1920f / getWidth();
        float nx = clamp(sx * s, 120, 1800), ny = sy * s;
        moved = !Float.isNaN(x) && Math.abs(nx - x) > 4;
        fromX = Float.isNaN(x) ? nx : x;
        fromY = Float.isNaN(x) ? ny : y;
        x = nx;
        y = ny;
        at = SystemClock.uptimeMillis();
        invalidate();
    }

    private long frozen = -1;

    /** For the screen renders: draw this many ms after the last move. */
    void freeze(long ms) { frozen = ms; }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static float ease(float k) { return (float) (1 - Math.pow(1 - k, 3)); }

    private static int a(int colour, float alpha) { return (Math.round(clamp(alpha, 0, 1) * 255) << 24) | (colour & 0xffffff); }

    @Override protected void onDraw(Canvas c) {
        if (Float.isNaN(x)) return;
        long el = frozen >= 0 ? frozen : motion && moved ? SystemClock.uptimeMillis() - at : 100_000;
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        boolean again;
        switch (kind) {
            case "drive": again = drive(c, el); break;
            case "trivela": again = trivela(c, el); break;
            default: again = oneTwo(c, el); break;
        }
        c.restore();
        if (again) postInvalidateOnAnimation();
    }

    // ---- Sachin: the straight drive ---------------------------------------------------------------------------------

    private boolean drive(Canvas c, long el) {
        float k = clamp(el / 520f, 0, 1), e = ease(k);
        float gy = y - 150, bx = fromX + (x - fromX) * e;
        if (k < 1) {
            // Speed lines behind the ball, along the ground.
            float dir = Math.signum(x - fromX);
            for (int i = 1; i <= 6; i++) {
                p.setColor(a(0xffffffff, 0.28f * (1 - i / 7f)));
                p.setStrokeWidth(4);
                c.drawLine(bx - dir * i * 26, gy - 6 + (i % 2) * 12, bx - dir * (i * 26 + 40), gy - 6 + (i % 2) * 12, p);
            }
        }
        p.setColor(0x55000000);
        c.drawOval(bx - 20, gy + 14, bx + 20, gy + 22, p);
        cricketBall(c, bx, gy, 17, (x - fromX) * e / 17f);
        // The crowd: "SA-CHIN!" rises over the app twice, as the stands chant it.
        boolean chanting = el >= 450 && el < 2400;
        if (chanting) {
            for (int beat = 0; beat < 2; beat++) {
                float q = (el - 450 - beat * 800) / 1100f;
                if (q <= 0 || q >= 1) continue;
                float al = (q < 0.2f ? q / 0.2f : 1) * (1 - q);
                t.setTextSize(64 + q * 14);
                t.setLetterSpacing(0.12f);
                t.setShadowLayer(14, 0, 3, a(0xff000000, 0.9f * al));
                t.setColor(a(lift(accent), al));
                c.drawText("SA-CHIN!", x, gy - 56 - q * 80, t);
                t.clearShadowLayer();
            }
        }
        return k < 1 || el < 2400;
    }

    private void cricketBall(Canvas c, float cx, float cy, float rad, float turns) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xffb3121c);
        c.drawCircle(cx, cy, rad, p);
        p.setColor(0x33000000);
        c.drawCircle(cx + rad * 0.25f, cy + rad * 0.25f, rad * 0.75f, p);
        p.setColor(0x66ffffff);
        c.drawCircle(cx - rad * 0.35f, cy - rad * 0.4f, rad * 0.22f, p);
        c.save();
        c.rotate(turns * 57.3f, cx, cy);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        p.setColor(0xfff2e6c8);
        r.set(cx - rad * 0.9f, cy - rad * 1.6f, cx + rad * 0.9f, cy + rad * 1.6f);
        c.drawArc(r, 60, 60, false, p);
        c.drawArc(r, 240, 60, false, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    // ---- Yamal: the trivela -----------------------------------------------------------------------------------------

    private boolean trivela(Canvas c, long el) {
        float k = clamp(el / 760f, 0, 1);
        float sy = fromY - 150, ey = y - 150, dx = x - fromX;
        // Struck with the outside of the boot: it starts out away from the target and bends back in.
        float cx = (fromX + x) / 2 - dx * 0.45f, cy = Math.min(sy, ey) - 230 - Math.abs(dx) * 0.08f;
        if (k < 1) {
            for (int i = 10; i >= 1; i--) {
                float q = ease(Math.max(0, k - i * 0.025f));
                float[] pt = quad(fromX, sy, cx, cy, x, ey, q);
                p.setColor(a(accent, 0.5f * (1 - i / 11f)));
                c.drawCircle(pt[0], pt[1], 19 * (1 - i / 16f), p);
            }
        } else if (el < 1260) {
            // It lands: a ring spreads in the look's colour.
            float q = (el - 760) / 500f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(a(accent, 0.8f * (1 - q)));
            c.drawOval(x - 30 - 60 * q, ey + 14 - 10 * q, x + 30 + 60 * q, ey + 26 + 10 * q, p);
            p.setStyle(Paint.Style.FILL);
        }
        float[] pt = quad(fromX, sy, cx, cy, x, ey, ease(k));
        p.setColor(0x55000000);
        c.drawOval(pt[0] - 20, ey + 16, pt[0] + 20, ey + 24, p);
        football(c, pt[0], pt[1], 20, k * 720);
        return el < 1260;
    }

    private static float[] quad(float x0, float y0, float x1, float y1, float x2, float y2, float q) {
        float u = 1 - q;
        return new float[]{u * u * x0 + 2 * u * q * x1 + q * q * x2, u * u * y0 + 2 * u * q * y1 + q * q * y2};
    }

    private void football(Canvas c, float cx, float cy, float rad, float spin) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xfff6f6f6);
        c.drawCircle(cx, cy, rad, p);
        c.save();
        c.rotate(spin, cx, cy);
        p.setColor(0xff1b1b1f);
        path.reset();
        for (int i = 0; i < 5; i++) {
            double ang = -Math.PI / 2 + i * 2 * Math.PI / 5;
            float px = cx + (float) Math.cos(ang) * rad * 0.38f, py = cy + (float) Math.sin(ang) * rad * 0.38f;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, p);
        for (int i = 0; i < 5; i++) {
            double ang = -Math.PI / 2 + i * 2 * Math.PI / 5;
            c.drawCircle(cx + (float) Math.cos(ang) * rad * 0.92f, cy + (float) Math.sin(ang) * rad * 0.92f, rad * 0.2f, p);
        }
        c.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0x33000000);
        c.drawCircle(cx, cy, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Yamal and Raphinha: the one-two ----------------------------------------------------------------------------

    private boolean oneTwo(Canvas c, long el) {
        // The two run with the app (500 ms), then 11 slips it to 10 (400 ms) and 10 plays it into the app (400 ms).
        float run = ease(clamp(el / 500f, 0, 1));
        float px = fromX + (x - fromX) * run, py = (fromY + (y - fromY) * run) - 160;
        float lx = clamp(px - 250, 60, 1860), rx = clamp(px + 250, 60, 1860);
        float pass1 = clamp((el - 500) / 400f, 0, 1), pass2 = clamp((el - 900) / 400f, 0, 1);
        float bx, by;
        if (pass1 < 1) { float q = ease(pass1); bx = lx + 34 + (rx - 34 - lx - 34) * q; by = py + 18 - (float) Math.sin(q * Math.PI) * 40; }
        else { float q = ease(pass2); bx = rx - 34 + (x - rx + 34) * q; by = py + 18 + (y - 130 - py - 18) * q; }
        if (el > 500 && el < 1300) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 10}, 0));
            p.setColor(a(0xffffffff, 0.5f));
            path.reset();
            path.moveTo(lx + 34, py + 18);
            path.quadTo((lx + rx) / 2, py - 30, rx - 34, py + 18);
            path.lineTo(x, y - 130);
            c.drawPath(path, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);
        }
        chip(c, lx, py, "11", 0xff004d98);
        chip(c, rx, py, "10", 0xffa50044);
        football(c, bx, by, 15, el * 0.8f);
        return el < 1300;
    }

    private void chip(Canvas c, float cx, float cy, String num, int shirt) {
        p.setColor(0x55000000);
        c.drawOval(cx - 30, cy + 30, cx + 30, cy + 40, p);
        p.setColor(shirt);
        c.drawCircle(cx, cy, 30, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        p.setColor(0xffedbb00);
        c.drawCircle(cx, cy, 30, p);
        p.setStyle(Paint.Style.FILL);
        t.setTextSize(30);
        t.setLetterSpacing(0);
        t.setColor(Color.WHITE);
        c.drawText(num, cx, cy + 11, t);
    }

    /** A colour bright enough to read over any wallpaper. */
    private static int lift(int colour) {
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        return 0.299 * r + 0.587 * g + 0.114 * b < 120 ? 0xffffffff : colour | 0xff000000;
    }
}
