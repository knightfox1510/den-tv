package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Your character on Home (DenCharacters). As a figure it stands at the bottom right, leans toward the focused tile,
 * breathes and blinks (with an eyes-closed picture). As a walker it is smaller and walks to the focused tile, hopping
 * as it goes and turning to face the way it walks. Laid out on the same 1920x1080 grid as the other watchers.
 */
final class DenFigureView extends View implements DenLayer.Watcher {
    private final Bitmap body, closed;
    private final boolean walker, motion;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private float lookX = 960, lookY = 860;
    private float x = Float.NaN, fromX, toX;
    private long walkStart, blinkUntil, nextBlink;
    private boolean left;

    DenFigureView(Context ctx, Bitmap body, Bitmap closed, boolean walker, boolean motion) {
        super(ctx);
        this.body = body; this.closed = closed; this.walker = walker; this.motion = motion;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        nextBlink = SystemClock.uptimeMillis() + 2500;
    }

    @Override public void lookAt(float sx, float sy, String label) {
        if (getWidth() == 0) return;
        float s = 1920f / getWidth();
        lookX = sx * s;
        lookY = sy * s;
        if (walker) {
            float target = lookX - 110;
            if (Float.isNaN(x) || !motion) { x = target; walkStart = 0; }
            else { fromX = x; toX = target; left = toX < fromX; walkStart = SystemClock.uptimeMillis(); }
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (body == null) return;
        long now = SystemClock.uptimeMillis();
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        boolean animating = false;
        if (motion && closed != null && now >= nextBlink) { blinkUntil = now + 160; nextBlink = now + 2500 + (long) (Math.random() * 4000); }
        Bitmap pic = closed != null && now < blinkUntil ? closed : body;
        if (walker) {
            float hop = 0;
            if (walkStart != 0) {
                float t = Math.min(1f, (now - walkStart) / 1100f);
                x = fromX + (toX - fromX) * t;
                hop = (float) Math.abs(Math.sin(t * Math.PI * 6)) * 8;
                if (t >= 1) walkStart = 0; else animating = true;
            }
            float h = 250, w = h * pic.getWidth() / pic.getHeight(), feet = lookY - 10;
            c.translate(Float.isNaN(x) ? lookX - 110 : x, feet - hop);
            if (left) c.scale(-1, 1);
            dst.set(-w / 2, -h, w / 2, 0);
            c.drawBitmap(pic, null, dst, paint);
        } else {
            float h = 520, w = h * pic.getWidth() / pic.getHeight(), cx = 1920 - 80 - w / 2;
            float lean = Math.max(-1, Math.min(1, (lookX - cx) / 700f)) * 2.5f;
            float breathe = motion ? (float) Math.sin(now / 900.0) * 0.006f : 0;
            c.translate(cx, 1080);
            c.rotate(lean);
            c.scale(1, 1 + breathe);
            dst.set(-w / 2, -h, w / 2, 0);
            c.drawBitmap(pic, null, dst, paint);
            animating = motion;
        }
        c.restore();
        if (animating || (motion && closed != null)) postInvalidateDelayed(walkStart != 0 ? 16 : 50);
    }
}
