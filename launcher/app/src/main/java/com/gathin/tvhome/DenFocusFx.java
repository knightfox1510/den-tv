package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.List;

/**
 * The effect when you move between apps (looks/README.md, "focus"): spot (a spotlight beam on the picked tile, the
 * rest a little darker), pass (a football passed from the last tile to the new one), arc (a glowing ball-tracking
 * arc between them), ripple (rings spreading from the tile) and aura (an energy glow that pulses behind it).
 */
final class DenFocusFx extends View implements DenLayer.Watcher {
    static final List<String> KINDS = List.of("spot", "pass", "arc", "ripple", "aura");
    private static final long MOVE_MS = 450;

    private final String kind;
    private final int accent;
    private final boolean motion;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float fromX = Float.NaN, fromY, x = Float.NaN, y;
    private long at;

    DenFocusFx(Context ctx, String kind, int accent, boolean motion) {
        super(ctx);
        this.kind = kind; this.accent = accent; this.motion = motion;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static boolean draws(String kind) { return KINDS.contains(kind); }

    @Override public void lookAt(float sx, float sy, String label) {
        if (getWidth() == 0) return;
        float s = 1920f / getWidth();
        fromX = Float.isNaN(x) ? sx * s : x;
        fromY = Float.isNaN(x) ? sy * s : y;
        x = sx * s;
        y = sy * s;
        at = SystemClock.uptimeMillis();
        invalidate();
    }

    private static int a(int colour, float alpha) { return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (colour & 0xffffff); }

    @Override protected void onDraw(Canvas c) {
        if (Float.isNaN(x)) return;
        long el = SystemClock.uptimeMillis() - at;
        float k = motion ? Math.min(1, el / (float) MOVE_MS) : 1;
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        boolean again = motion && k < 1;
        switch (kind) {
            case "spot": {
                float cx = fromX + (x - fromX) * ease(k);
                p.setShader(new RadialGradient(cx, y, 420, new int[]{0x00000000, 0x00000000, 0x4d000000}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, 1920, 1080, p);
                p.setShader(new RadialGradient(cx, -100, 900, a(0xffffffff, 0.12f), 0, Shader.TileMode.CLAMP));
                path.reset(); path.moveTo(cx - 60, -10); path.lineTo(cx + 60, -10); path.lineTo(cx + 230, y + 80); path.lineTo(cx - 230, y + 80); path.close();
                c.drawPath(path, p);
                break;
            }
            case "pass": {
                if (k >= 1) break;
                float e = ease(k), bx = fromX + (x - fromX) * e, by = fromY + (y - fromY) * e - (float) Math.sin(e * Math.PI) * 120 - 90;
                p.setColor(0x88000000);
                c.drawOval(bx - 18, y + 70, bx + 18, y + 78, p);
                p.setColor(0xfff4f4f4);
                c.drawCircle(bx, by, 16, p);
                p.setColor(0xff222222);
                c.drawCircle(bx + 4, by - 3, 5, p);
                break;
            }
            case "arc": {
                // Only while the ball flies: with motion off, or on the first tile (from and to the same), the arc
                // has nowhere to go and used to stay as a stub standing on the tile.
                float fade = motion ? Math.max(0, 1 - (el - MOVE_MS) / 600f) : 0;
                if (fade <= 0 || Math.abs(x - fromX) < 10) break;
                again = motion && el < MOVE_MS + 600;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                path.reset();
                path.moveTo(fromX, fromY - 80);
                path.quadTo((fromX + x) / 2, Math.min(fromY, y) - 320, x, y - 80);
                for (int i = 3; i >= 1; i--) {
                    p.setStrokeWidth(i * 7);
                    p.setColor(a(accent, 0.25f * fade * (4 - i)));
                    c.drawPath(path, p);
                }
                break;
            }
            case "ripple": {
                again = motion && el < 1200;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4);
                for (int i = 0; i < 3; i++) {
                    float t = (el - i * 180) / 900f;
                    if (t <= 0 || t >= 1) continue;
                    p.setColor(a(0xff9fd8ff, 0.6f * (1 - t)));
                    c.drawOval(x - 60 - 260 * t, y - 30 - 110 * t, x + 60 + 260 * t, y + 30 + 110 * t, p);
                }
                break;
            }
            case "aura": {
                float pulse = motion ? 0.75f + 0.25f * (float) Math.sin(SystemClock.uptimeMillis() / 300.0) : 0.85f;
                p.setShader(new RadialGradient(x, y, 260 * pulse, new int[]{a(accent, 0.45f), a(accent, 0.15f), 0}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, 260 * pulse, p);
                if (motion) { c.restore(); postInvalidateDelayed(40); return; }
                break;
            }
            default: break;
        }
        c.restore();
        if (again) postInvalidateOnAnimation();
    }

    private static float ease(float k) { return (float) (1 - Math.pow(1 - k, 3)); }
}
