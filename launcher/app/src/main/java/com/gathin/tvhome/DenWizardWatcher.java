package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The Wizarding World watchers, on the 1920x1080 grid like the others (the mockup's hpwFrame): an owl that flies to
 * the picked app in an arc and hovers over it, flapping; a phoenix that glides there trailing embers; the Golden
 * Snitch, which zips over and then darts round the app; and a snake, drawn in code, that slithers along the top of
 * the row and rears its head at the app, flicking its tongue. The owl, phoenix and Snitch are pictures from the
 * mockup (assets/den/watchers, tools/watchers/sprites.js). With motion off they jump to the app and keep still.
 */
final class DenWizardWatcher extends View implements DenLayer.Watcher {
    static final List<String> KINDS = List.of("owl", "phoenix", "snake", "snitch");
    private static final String TAG = "DenWizard";
    private static final String DIR = "den/watchers";

    private final String kind;
    private final boolean motion;
    private final Random random = new Random();
    private final Bitmap[] frames;
    private final float[][] anchors;
    private final Paint draw = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tongue = new Path();
    private final List<float[]> embers = new ArrayList<>();   // x, y, born, vx, vy

    private boolean placed;
    private float x = 960, y = 640, fx, fy, tx = 960, ty = 640;
    private long start, dur;
    private int face = 1;
    // the Snitch's next dart
    private long dartAt;
    private float dx, dy;
    // the snake: its head's x moving from from to to, along the row's top at base
    private float head = 960, from = 960, to = 960, base = 760;
    private final Runnable frame = this::invalidate;

    DenWizardWatcher(Context ctx, String kind, boolean motion) {
        super(ctx);
        this.kind = kind;
        this.motion = motion;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int n = kind.equals("snitch") ? 2 : kind.equals("snake") ? 0 : 3;
        frames = new Bitmap[n];
        anchors = new float[n][];
        JSONObject sprites = readSprites(ctx);
        for (int k = 0; k < n; k++) {
            String name = kind + "-" + k;
            try (InputStream in = ctx.getAssets().open(DIR + "/" + name + ".webp")) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inScaled = false;
                frames[k] = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception e) {
                Log.w(TAG, "missing watcher picture " + name);
            }
            JSONArray a = sprites == null ? null : sprites.optJSONArray(name);
            anchors[k] = a != null ? new float[]{(float) a.optDouble(0), (float) a.optDouble(1)}
                    : frames[k] != null ? new float[]{frames[k].getWidth() / 2f, frames[k].getHeight() / 2f} : new float[]{0, 0};
        }
    }

    private static JSONObject readSprites(Context ctx) {
        try (InputStream in = ctx.getAssets().open(DIR + "/sprites.json")) {
            byte[] b = new byte[in.available()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new JSONObject(new String(b, 0, n, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Ease in and out, as the mockup's. */
    private static float ease(float t) { return t < .5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; }

    @Override public void lookAt(float sx, float sy, String label) {
        if (getWidth() == 0) return;
        float s = 1920f / getWidth();
        float ax = sx * s, ay = sy * s;
        long now = SystemClock.uptimeMillis();
        if (kind.equals("snake")) {
            float target = clamp(ax, 200, 1720);
            base = ay - 118;
            if (!placed || !motion) { head = from = to = target; placed = true; dur = 0; invalidate(); return; }
            from = head;
            to = target;
            if (Math.abs(to - from) > 4) face = to > from ? 1 : -1;
            start = now;
            dur = (long) clamp(Math.abs(to - from) * 1.1f, 400, 1500);
        } else {
            float lift = kind.equals("snitch") ? 135 : kind.equals("phoenix") ? 175 : 165;
            float nx = clamp(ax, 140, 1780), ny = ay - lift;
            if (!placed || !motion) { x = tx = nx; y = ty = ny; placed = true; dur = 0; invalidate(); return; }
            fx = x; fy = y; tx = nx; ty = ny;
            start = now;
            dur = kind.equals("snitch") ? 380 : kind.equals("phoenix") ? 1100 : 900;
            if (Math.abs(tx - fx) > 4) face = tx > fx ? 1 : -1;
        }
        removeCallbacks(frame);
        postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        if (!placed) return;
        long now = SystemClock.uptimeMillis();
        float t = dur > 0 ? clamp((now - start) / (float) dur, 0, 1) : 1, e = ease(t);
        boolean flying = t < 1;
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        if (kind.equals("snake")) drawSnake(c, now, e, flying);
        else drawFlier(c, now, t, e, flying);
        c.restore();
        if (flying) postInvalidateOnAnimation();
        else if (motion) postDelayed(frame, 33);   // hovering, darting, flicking: ~30 fps keeps a weak TV cool
    }

    private void drawFlier(Canvas c, long now, float t, float e, boolean flying) {
        if (flying) {
            float arc = kind.equals("snitch") ? 30 : 140;
            x = fx + (tx - fx) * e;
            y = fy + (ty - fy) * e - (float) Math.sin(t * Math.PI) * arc;
        } else if (kind.equals("snitch") && motion) {
            if (now > dartAt) {
                dx = (random.nextFloat() - .5f) * 180;
                dy = (random.nextFloat() - .5f) * 70;
                dartAt = now + 300 + random.nextInt(500);
                face = dx > 0 ? 1 : -1;
            }
            x += (tx + dx - x) * .2f;
            y += (ty + dy - y) * .2f;
        } else {
            x = tx;
            y = ty + (motion ? (float) Math.sin(now / 420.0) * 7 : 0);
        }
        int f;
        if (kind.equals("snitch")) f = motion ? (int) (now / 40 % 2) : 0;
        else {
            long period = flying ? (kind.equals("phoenix") ? 120 : 80) : (kind.equals("phoenix") ? 260 : 170);
            f = motion ? new int[]{0, 1, 2, 1}[(int) (now / period % 4)] : 1;
        }
        if (kind.equals("phoenix")) drawEmbers(c, now, flying);
        Bitmap b = frames.length > f ? frames[f] : null;
        if (b == null) return;
        c.save();
        c.translate(x, y);
        c.scale(face, 1);
        if (flying && !kind.equals("snitch")) c.rotate((float) Math.toDegrees(ty - fy > 0 ? .08 : -.08));
        c.drawBitmap(b, -anchors[f][0], -anchors[f][1], draw);
        c.restore();
    }

    /** Sparks shed from the phoenix's tail, rising and fading over 0.9 s; a stream in flight, a few while it hovers. */
    private void drawEmbers(Canvas c, long now, boolean flying) {
        if (motion && random.nextFloat() < (flying ? .9f : .35f) && embers.size() < 60)
            embers.add(new float[]{x - face * 60, y + 10, now, (random.nextFloat() - .5f) * .06f, -.03f - random.nextFloat() * .05f});
        ink.setStyle(Paint.Style.FILL);
        for (Iterator<float[]> it = embers.iterator(); it.hasNext(); ) {
            float[] m = it.next();
            float age = now - m[2];
            if (age >= 900) { it.remove(); continue; }
            float a = age / 900f;
            ink.setColor(((int) ((1 - a) * 255) << 24) | (255 << 16) | ((int) (200 - a * 120) << 8) | 60);
            c.drawCircle(m[0] + m[3] * age, m[1] + m[4] * age, 4 * (1 - a) + 1, ink);
        }
    }

    /**
     * The snake: 30 rings from tail to head along the row's top, a travelling wave through them that runs faster
     * while it moves; once it arrives its head rears up and sways, and its tongue flicks.
     */
    private void drawSnake(Canvas c, long now, float e, boolean flying) {
        head = from + (to - from) * e;
        int n = 30;
        float gap = 15, travel = head * .045f * face, wob = flying ? 16 : 7, sp = flying || !motion ? 0 : now / 600f;
        float lift = flying ? 0 : motion ? clamp((now - start - dur) / 400f, 0, 1) : 1;
        float[] px = new float[n], py = new float[n];
        for (int k = 0; k < n; k++) {
            int i = n - 1 - k;   // k: 0 is the tail
            float amp = Math.min(wob, i * 1.6f);
            px[k] = head - face * i * gap;
            py[k] = base + 6 + (float) Math.sin(i * .5f - travel - sp) * amp;
        }
        py[n - 1] -= 10 + lift * 22;
        py[n - 2] -= 4 + lift * 10;
        ink.setStyle(Paint.Style.FILL);
        for (int k = 0; k < n; k++) {
            float tt = k / (float) (n - 1), rr = 5 + 15 * (float) Math.pow(tt, .7);
            ink.setColor(0xff1f7a32);
            c.drawCircle(px[k], py[k], rr, ink);
            ink.setColor(0xff5fd06a);
            c.drawCircle(px[k] - rr * .3f, py[k] - rr * .4f, rr * .45f, ink);
            if (k % 3 == 0 && k < n - 3) {
                ink.setColor(0x8c08280e);
                c.drawOval(px[k] - rr * .55f, py[k] - rr * .5f, px[k] + rr * .55f, py[k] + rr * .1f, ink);
            }
        }
        boolean flick = !flying && motion && now % 1400 < 260;
        float look = flying || !motion ? 0 : (float) Math.sin(now / 900.0) * .12f;
        c.save();
        c.translate(px[n - 1], py[n - 1]);
        c.scale(face * 1.25f, 1.25f);
        c.rotate((float) Math.toDegrees(look));
        if (flick) {
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(2.5f);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setColor(0xffe0283a);
            tongue.reset();
            tongue.moveTo(26, 2); tongue.lineTo(44, 2); tongue.lineTo(52, -4);
            tongue.moveTo(44, 2); tongue.lineTo(52, 8);
            c.drawPath(tongue, ink);
            ink.setStyle(Paint.Style.FILL);
        }
        ink.setColor(0xff1f7a32);
        c.drawOval(-16, -15, 32, 15, ink);
        ink.setColor(0xff5fd06a);
        c.drawOval(-6, -12, 22, 2, ink);
        ink.setColor(0xffe8f04a);
        c.drawOval(11, -10, 21, -2, ink);
        ink.setColor(0xff0a0a0a);
        c.drawRect(15, -9.5f, 17, -2.5f, ink);
        ink.setColor(0x80000000);
        c.drawCircle(28, -1, 1.6f, ink);
        c.restore();
        if (lift < 1 && !flying) postInvalidateOnAnimation();
    }
}
