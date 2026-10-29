package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * den-tv watchers other than the eyes: hats that turn, swords that point, Dragon Balls that gather, walkers that
 * walk to the focused tile and the rest of the mockup's list (mockups/den-looks.html, look()). The parts are
 * pictures rendered from the mockup (assets/den/watchers, made by tools/watchers); this view only places them on a
 * 1920x1080 grid scaled to the screen. Like the eyes it never takes focus or keys.
 */
final class DenWatcherView extends View implements DenLayer.Watcher {
    private static final String TAG = "DenWatcher";
    private static final String DIR = "den/watchers";
    private static final float DOCK_Y = 860;

    /** Every watcher this view draws; the eye watchers are DenEyesView's. */
    static final List<String> KINDS = List.of("luffy", "zoro", "dragonballs", "pole", "cloud", "butterflies", "chidori",
            "crows", "room", "hearts", "hands", "chopperhat", "bat", "fedora", "dice", "ballondor", "dribble", "crown",
            "reticle", "caret", "walkluffy", "walkg5", "walkzoro", "compass", "scouter", "odm", "getsuga", "shadows",
            "fist", "nichirin", "cleave");

    private static final int IDLE_NONE = 0, IDLE_BOB = 1, IDLE_FLAP = 2, IDLE_BEAT = 3, IDLE_SPIN = 4, IDLE_FLICKER = 5;

    /** One picture on the grid, easing from where it was to where it is going, with an optional idle motion. */
    private static final class Actor {
        Bitmap bmp;
        float ax, ay;
        float x, y, rot, sx = 1, sy = 1, alpha = 1;
        float fx, fy, frot, fsx = 1, fa = 1;
        float tx, ty, trot, tsx = 1, ta = 1;
        long start, delay, dur;
        Interpolator ease = new DecelerateInterpolator();
        int idle;
        float idleSpeed = 1, phase;
        boolean flip;
        Paint paint;

        void moveTo(float x, float y, float rot, float sx, long dur, long delay, Interpolator ease, long now) {
            fx = this.x; fy = this.y; frot = this.rot; fsx = this.sx; fa = this.alpha;
            tx = x; ty = y; trot = rot; tsx = sx; ta = 1;
            this.dur = dur; this.delay = delay; this.start = now;
            if (ease != null) this.ease = ease;
            if (dur <= 0) { this.x = x; this.y = y; this.rot = rot; this.sx = sx; }
        }

        /** Returns true while it is still moving. */
        boolean step(long now) {
            if (dur <= 0) return false;
            float t = (now - start - delay) / (float) dur;
            if (t <= 0) return true;
            if (t >= 1) { x = tx; y = ty; rot = trot; sx = tsx; alpha = ta; dur = 0; return false; }
            float e = ease.getInterpolation(t);
            x = fx + (tx - fx) * e; y = fy + (ty - fy) * e; rot = frot + (trot - frot) * e; sx = fsx + (tsx - fsx) * e;
            alpha = fa + (ta - fa) * Math.min(1f, t * 2);
            return true;
        }
    }

    private final String kind;
    private final boolean motion;
    private final int accent;
    private final Random random = new Random();
    private final Map<String, Bitmap> pictures = new HashMap<>();
    private final Map<String, float[]> anchors = new HashMap<>();
    private final List<Actor> actors = new ArrayList<>();
    private final List<Actor> cuts = new ArrayList<>();   // cleave: short-lived slashes
    private final Paint draw = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private Actor hat, hatEyes, compassNeedle, walker, ball;
    private float lookX = 960, lookY = DOCK_Y;
    private String label = "";
    private boolean started;
    // pole and ODM cables are drawn, not pictures
    private float poleAng, poleLen, poleFromAng, poleFromLen, poleToAng, poleToLen;
    private long poleStart;
    private float cableX, cableY;
    private long cableStart;
    // dribble: a weave from the last x
    private float dribFrom, dribTo, dribY;
    private long dribStart;
    // walker: where it is heading, and when it stops walking
    private long walkUntil, walkTurnAt;
    private float walkTurnX;

    private final Runnable frame = this::invalidate;

    DenWatcherView(Context ctx, String kind, String accentHex, boolean motion) {
        super(ctx);
        this.kind = kind;
        this.motion = motion;
        int a;
        try { a = Color.parseColor(accentHex); } catch (RuntimeException e) { a = 0xffff4d6d; }
        accent = a;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        readAnchors();
        text.setTypeface(Typeface.MONOSPACE);
        build();
    }

    static boolean draws(String kind) { return KINDS.contains(kind); }

    // ---- pictures ------------------------------------------------------------------------------------------------

    private void readAnchors() {
        try (InputStream in = getContext().getAssets().open(DIR + "/sprites.json")) {
            byte[] b = new byte[in.available()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            JSONObject j = new JSONObject(new String(b, 0, n, "UTF-8"));
            for (Iterator<String> it = j.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray v = j.getJSONArray(k);
                anchors.put(k, new float[]{(float) v.getDouble(0), (float) v.getDouble(1)});
            }
        } catch (Exception e) {
            Log.w(TAG, "no watcher anchors", e);
        }
    }

    private Bitmap picture(String name) {
        Bitmap b = pictures.get(name);
        if (b != null || pictures.containsKey(name)) return b;
        try (InputStream in = getContext().getAssets().open(DIR + "/" + name + ".webp")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (IOException e) {
            Log.w(TAG, "missing watcher picture " + name);
        }
        pictures.put(name, b);
        return b;
    }

    private Actor actor(String name, int idle) {
        Actor a = new Actor();
        a.bmp = picture(name);
        float[] an = anchors.get(name);
        if (an != null) { a.ax = an[0]; a.ay = an[1]; }
        else if (a.bmp != null) { a.ax = a.bmp.getWidth() / 2f; a.ay = a.bmp.getHeight() / 2f; }
        a.idle = motion ? idle : IDLE_NONE;
        a.phase = random.nextFloat() * 6.28f;
        a.x = 960; a.y = 700;
        actors.add(a);
        return a;
    }

    private void build() {
        switch (kind) {
            case "luffy": case "chopperhat": case "fedora": {
                String n = kind.equals("luffy") ? "strawhat" : kind;
                hatEyes = actor(n + "-eyes", IDLE_BOB);
                hat = actor(n, IDLE_BOB);
                hat.idleSpeed = hatEyes.idleSpeed = 0.7f;
                hatEyes.phase = hat.phase;
                break;
            }
            case "zoro": for (int k = 0; k < 3; k++) actor("sword" + k, IDLE_NONE); break;
            case "bat": actor("bat", IDLE_NONE); break;
            case "nichirin": actor("nichirin", IDLE_NONE); break;
            case "hands": for (int k = 0; k < 3; k++) actor("arm", IDLE_NONE); break;
            case "dragonballs": for (int n = 1; n <= 7; n++) actor("dball" + n, IDLE_BOB); break;
            case "cloud": actor("cloud", IDLE_BOB); break;
            case "butterflies": for (int k = 0; k < 6; k++) actor("butterfly", IDLE_FLAP).idleSpeed = 3.6f; break;
            case "chidori": actor("chidori", IDLE_FLICKER); break;
            case "crows": for (int k = 0; k < 7; k++) actor("crow", IDLE_FLAP).idleSpeed = 4.5f; break;
            case "room": actor("room", IDLE_SPIN).idleSpeed = 0.15f; break;
            case "hearts":
                for (int k = 0; k < 6; k++) { Actor h = actor("heart", IDLE_BEAT); h.sx = h.tsx = (40 + k * 8) / 54f; h.idleSpeed = 1.4f; }
                break;
            case "dice": actor("die3", IDLE_NONE); actor("die5", IDLE_NONE); break;
            case "ballondor": actor("ballondor", IDLE_BOB); break;
            case "crown": actor("crown", IDLE_BOB); break;
            case "dribble": ball = actor("ball", IDLE_NONE); break;
            case "reticle": {
                Actor r = actor("reticle", IDLE_SPIN);
                r.idleSpeed = 0.22f;
                r.paint = new Paint(draw);
                r.paint.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
                break;
            }
            case "scouter": actor("scouter", IDLE_SPIN).idleSpeed = 0.22f; break;
            case "walkluffy": case "walkg5": case "walkzoro": walker = actor("walk-" + kind.substring(4), IDLE_NONE); break;
            case "compass": {
                Actor dial = actor("compass", IDLE_NONE);
                dial.x = dial.tx = 960; dial.y = dial.ty = 560;
                compassNeedle = actor("compass-needle", IDLE_NONE);
                compassNeedle.x = 960; compassNeedle.y = 560;
                break;
            }
            case "getsuga": actor("getsuga", IDLE_FLICKER); break;
            case "shadows": for (int k = 0; k < 5; k++) actor("shadow", IDLE_BOB); break;
            case "fist": actor("fist", IDLE_NONE); break;
            case "cleave": picture("cut"); break;
            default: break;   // pole, odm, caret: drawn in onDraw
        }
    }

    // ---- following the focus -------------------------------------------------------------------------------------

    @Override public void lookAt(float x, float y, String name) {
        if (getWidth() == 0) return;
        float s = 1920f / getWidth();
        boolean first = !started;
        started = true;
        lookX = x * s;
        lookY = y * s;
        label = name == null ? "" : name;
        place(first || !motion);
        kick();
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** The mockup's look(): where each part goes for the focused tile at (lookX, lookY). */
    private void place(boolean jump) {
        long now = SystemClock.uptimeMillis();
        float ax = lookX, ay = lookY, dy = ay - DOCK_Y, wx = clamp(ax, 250, 1670);
        float n = clamp((ax - 960) / 800f, -1, 1);
        Interpolator spring = new OvershootInterpolator(1.4f), smooth = new AccelerateDecelerateInterpolator();
        switch (kind) {
            case "luffy": case "chopperhat": case "fedora":
                hat.moveTo(960 + n * 30, 250, n * 9, 1, jump ? 0 : 600, 0, smooth, now);
                hatEyes.moveTo(960 + n * 26, 250, 0, 1, jump ? 0 : 600, 0, smooth, now);
                break;
            case "zoro": case "bat": case "nichirin": case "hands": {
                float[][] piv = kind.equals("hands") ? new float[][]{{-40, 300}, {1960, 360}, {960, -40}}
                        : kind.equals("zoro") ? new float[][]{{890, 150}, {960, 150}, {1030, 150}} : new float[][]{{960, 110}};
                float ty = kind.equals("hands") ? ay - 80 : ay;
                for (int k = 0; k < actors.size(); k++) {
                    Actor a = actors.get(k);
                    float px = piv[k][0], py = piv[k][1];
                    float ang = (float) Math.toDegrees(Math.atan2(ty - py, ax - px)) + (kind.equals("zoro") ? (k - 1) * 9 : 0);
                    float dist = (float) Math.hypot(ax - px, ty - py);
                    float sx = kind.equals("hands") ? clamp((dist - 180) / 400f, 0.6f, 2.2f) : 1;
                    if (jump) { a.x = px; a.y = py; }
                    a.moveTo(px, py, nearest(a.rot, ang), sx, jump ? 0 : 700, k * 60L, spring, now);
                }
                break;
            }
            case "pole": {
                float px = 960, py = 60, ty = 760 + dy;
                poleFromAng = poleAng; poleFromLen = poleLen;
                poleToAng = nearest(poleAng, (float) Math.toDegrees(Math.atan2(ty - py, ax - px)));
                poleToLen = (float) Math.hypot(ax - px, ty - py) - 120;
                poleStart = jump ? 0 : now;
                if (jump) { poleAng = poleToAng; poleLen = poleToLen; }
                break;
            }
            case "dragonballs":
                for (int k = 0; k < 7; k++) {
                    double a = (k - 3) * 0.42;
                    actors.get(k).moveTo(wx + (float) Math.sin(a) * 230, 690 + dy - (float) Math.cos(a) * 130 + (k == 3 ? -20 : 0),
                            0, 1, jump ? 0 : 900, Math.abs(k - 3) * 60L, spring, now);
                }
                break;
            case "cloud": actors.get(0).moveTo(ax, 560 + dy + 115, 0, 1, jump ? 0 : 700, 0, spring, now); break;
            case "butterflies":
                for (int k = 0; k < 6; k++) {
                    double a = k / 6.0 * 6.283 + random.nextFloat() * 0.6;
                    actors.get(k).moveTo(wx + (float) Math.cos(a) * (70 + random.nextFloat() * 60),
                            below(ay, 660 + dy + (float) Math.sin(a) * 70 - random.nextFloat() * 40), 0, 1, jump ? 0 : 1000, k * 80L, smooth, now);
                }
                break;
            case "chidori": actors.get(0).moveTo(ax, 640 + dy, 0, 1, jump ? 0 : 350, 0, new DecelerateInterpolator(2), now); break;
            case "crows":
                for (int k = 0; k < 7; k++)
                    actors.get(k).moveTo(wx + (k - 3) * 70 + (random.nextFloat() - 0.5f) * 30,
                            below(ay, 650 + dy - Math.abs(k - 3) * 22 - random.nextFloat() * 40), 0, 1, jump ? 0 : 900, k * 70L, smooth, now);
                break;
            case "room": actors.get(0).moveTo(ax, ay, actors.get(0).rot, 1, jump ? 0 : 500, 0, spring, now); break;
            case "hearts":
                for (int k = 0; k < 6; k++) {
                    Actor h = actors.get(k);
                    h.moveTo(wx - 30 + (random.nextFloat() - 0.5f) * 220, below(ay, 560 + dy + random.nextFloat() * 140), 0, h.tsx,
                            jump ? 0 : 1000, k * 100L, smooth, now);
                }
                break;
            case "dice":
                for (int k = 0; k < 2; k++)
                    actors.get(k).moveTo(ax + (k == 1 ? 50 : -50), 680 + dy + (k == 1 ? -20 : 10), actors.get(k).rot + (random.nextFloat() - 0.5f) * 120 + 360,
                            1, jump ? 0 : 800, 0, new DecelerateInterpolator(1.5f), now);
                break;
            case "ballondor": actors.get(0).moveTo(ax, 650 + dy, 0, 1, jump ? 0 : 800, 0, spring, now); break;
            case "crown": actors.get(0).moveTo(ax, 660 + dy, 0, 1, jump ? 0 : 800, 0, spring, now); break;
            case "dribble":
                dribFrom = jump ? ax : ball.x; dribTo = ax; dribY = 690 + dy;
                dribStart = jump ? 0 : now;
                if (jump) { ball.x = ax; ball.y = dribY; }
                break;
            case "reticle": case "scouter": actors.get(0).moveTo(ax, ay, actors.get(0).rot, 1, jump ? 0 : 450, 0, spring, now); break;
            case "walkluffy": case "walkg5": case "walkzoro": {
                float to = ax - 110, from = walker.x;
                walker.y = 850 + dy;
                if (jump) { walker.moveTo(to, walker.y, 0, 1, 0, 0, null, now); break; }
                if (kind.equals("walkzoro")) {
                    // Zoro sets off the wrong way first, then turns round.
                    float wrong = from - (to - from) * 0.5f;
                    walker.flip = wrong < from;
                    walker.moveTo(wrong, walker.y, 0, 1, 900, 0, new LinearInterpolator(), now);
                    walkTurnAt = now + 900; walkTurnX = to; walkUntil = now + 2000;
                } else {
                    walker.flip = to < from;
                    walker.moveTo(to, walker.y, 0, 1, 1100, 0, new LinearInterpolator(), now);
                    walkTurnAt = 0; walkUntil = now + 1100;
                }
                break;
            }
            case "compass": {
                float ang = (float) Math.toDegrees(Math.atan2(ay - 560, ax - 960)) + 90 + 180 + (random.nextFloat() - 0.5f) * 60;
                compassNeedle.moveTo(960, 560, nearest(compassNeedle.rot, ang), 1, jump ? 0 : 1200, 0, new OvershootInterpolator(2.5f), now);
                break;
            }
            case "odm": cableX = ax; cableY = 800 + dy; cableStart = jump ? 0 : now; break;
            case "getsuga": actors.get(0).moveTo(ax, 700 + dy, 0, 1, jump ? 0 : 400, 0, new DecelerateInterpolator(2.5f), now); break;
            case "shadows":
                for (int k = 0; k < 5; k++)
                    actors.get(k).moveTo(wx + (k - 2) * 95, 640 + dy + Math.abs(k - 2) * 18 + 85, 0, 1, jump ? 0 : 900, k * 60L, smooth, now);
                break;
            case "fist": {
                Actor f = actors.get(0);
                f.moveTo(ax - 150, 640 + dy, 0, 1, jump ? 0 : 350, 0, spring, now);
                break;
            }
            case "cleave":
                if (jump || !motion) break;
                for (int k = 0; k < 3; k++) {
                    Actor c = new Actor();
                    c.bmp = picture("cut");
                    if (c.bmp == null) break;
                    c.ax = c.bmp.getWidth() / 2f; c.ay = c.bmp.getHeight() / 2f;
                    c.x = ax + (random.nextFloat() - 0.5f) * 120; c.y = 700 + dy + (random.nextFloat() - 0.5f) * 80;
                    c.rot = -35 + random.nextFloat() * 70;
                    c.start = now + k * 80L; c.dur = 900; c.sx = 0;
                    cuts.add(c);
                }
                break;
            default: break;
        }
    }

    /**
     * Swarms (butterflies, crows, hearts) stay close over the row and never climb into the panel under the clock:
     * at most 140 grid pixels above the focused tile's centre.
     */
    private static float below(float tileY, float y) { return Math.max(tileY - 140, y); }

    /** The angle closest to from that points the same way as to, so a pointer never spins the long way round. */
    private static float nearest(float from, float to) {
        float d = ((to - from) % 360 + 540) % 360 - 180;
        return from + d;
    }

    private void kick() {
        removeCallbacks(frame);
        postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    // ---- drawing -------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (!started) return;
        long now = SystemClock.uptimeMillis();
        boolean moving = false, idling = false;
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);

        if (walker != null) {
            if (walkTurnAt != 0 && now >= walkTurnAt) {
                walker.flip = walkTurnX < walker.x;
                walker.moveTo(walkTurnX, walker.y, 0, 1, 1100, 0, new LinearInterpolator(), now);
                walkTurnAt = 0;
            }
            boolean walking = now < walkUntil;
            String base = "walk-" + kind.substring(4);
            walker.bmp = picture(walking ? base + ((now / 150) % 2 == 0 ? "-a" : "-b") : base);
            if (walking) moving = true;
        }
        if (ball != null && dribStart != 0) {
            float t = clamp((now - dribStart) / 700f, 0, 1);
            float e = new AccelerateDecelerateInterpolator().getInterpolation(t);
            ball.x = dribFrom + (dribTo - dribFrom) * e;
            ball.y = dribY + (float) Math.sin(t * Math.PI * 4) * 36 * (dribFrom == dribTo ? 0 : 1);
            ball.rot = t * 900;
            if (t < 1) moving = true; else dribStart = 0;
        }

        if (kind.equals("pole")) moving |= drawPole(c, now);
        if (kind.equals("odm")) moving |= drawCables(c, now);

        for (Actor a : actors) {
            moving |= a.step(now);
            idling |= a.idle != IDLE_NONE;
            drawActor(c, a, now);
        }
        for (Iterator<Actor> it = cuts.iterator(); it.hasNext(); ) {
            Actor a = it.next();
            float t = (now - a.start) / (float) a.dur;
            if (t >= 1) { it.remove(); continue; }
            moving = true;
            if (t < 0) continue;
            a.sx = t < 0.2f ? t / 0.2f : 1;
            a.alpha = t < 0.2f ? 1 : 1 - (t - 0.2f) / 0.8f;
            drawActor(c, a, now);
        }
        if (kind.equals("caret")) idling |= drawCaret(c, now);
        if (kind.equals("reticle") || kind.equals("scouter")) drawReadout(c);
        c.restore();

        if (moving) postInvalidateOnAnimation();
        else if (idling && motion) postDelayed(frame, 33);   // idle motion at ~30 fps keeps a weak TV cool
    }

    private void drawActor(Canvas c, Actor a, long now) {
        if (a.bmp == null) return;
        float t = now / 1000f * a.idleSpeed + a.phase;
        float ox = 0, oy = 0, r = 0, ix = 1, iy = 1;
        float alpha = a.alpha;
        switch (a.idle) {
            case IDLE_BOB: oy = (float) (Math.sin(t * 2.6) * 4 - 4); break;
            case IDLE_FLAP: ix = 0.25f + 0.75f * (float) Math.abs(Math.cos(t * 3)); break;
            case IDLE_BEAT: { float b = 1 + 0.125f * (1 - (float) Math.cos(t * 9)); ix = iy = b; break; }
            case IDLE_SPIN: r = (t * 80) % 360; break;
            case IDLE_FLICKER: alpha *= 0.75f + 0.25f * (float) Math.abs(Math.sin(t * 23)); r = (float) Math.sin(t * 7) * 4; break;
            default: break;
        }
        Paint p = a.paint != null ? a.paint : draw;
        p.setAlpha(Math.round(255 * clamp(alpha, 0, 1)));
        c.save();
        c.translate(a.x + ox, a.y + oy);
        c.rotate(a.rot + r);
        c.scale(a.sx * ix * (a.flip ? -1 : 1), a.sy * iy);
        c.drawBitmap(a.bmp, -a.ax, -a.ay, p);
        c.restore();
    }

    private boolean drawPole(Canvas c, long now) {
        boolean moving = false;
        if (poleStart != 0) {
            float t = clamp((now - poleStart) / 600f, 0, 1);
            float e = new OvershootInterpolator(1.2f).getInterpolation(t);
            poleAng = poleFromAng + (poleToAng - poleFromAng) * e;
            poleLen = poleFromLen + (poleToLen - poleFromLen) * e;
            if (t < 1) moving = true; else poleStart = 0;
        }
        c.save();
        c.translate(960, 60);
        c.rotate(poleAng);
        ink.setShader(null);
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(0x66dfe8ff);
        box.set(-6, -16, 40 + Math.max(0, poleLen) + 46, 16);
        c.drawRoundRect(box, 14, 14, ink);
        ink.setColor(0xffe0b040);
        box.set(0, -10, 40, 10);
        c.drawRoundRect(box, 4, 4, ink);
        ink.setShader(new LinearGradient(0, -9, 0, 9, new int[]{0xffff6a5a, 0xffc0180e, 0xff6a0804}, null, Shader.TileMode.CLAMP));
        c.drawRect(40, -9, 40 + Math.max(0, poleLen), 9, ink);
        ink.setShader(null);
        ink.setColor(0xffe0b040);
        box.set(40 + poleLen + 6, -10, 40 + poleLen + 46, 10);
        c.drawRoundRect(box, 4, 4, ink);
        c.restore();
        return moving;
    }

    private boolean drawCables(Canvas c, long now) {
        float t = cableStart == 0 ? 1 : clamp((now - cableStart) / 260f, 0, 1);
        float e = new DecelerateInterpolator().getInterpolation(t);
        ink.setShader(null);
        for (int k = 0; k < 2; k++) {
            float sx = k == 0 ? 0 : 1920, sy = 40, tx = cableX + (k == 1 ? 40 : -40), ty = cableY;
            float ex = sx + (tx - sx) * e, ey = sy + (ty - sy) * e;
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(3);
            ink.setColor(0xffd9dde6);
            c.drawLine(sx, sy, ex, ey, ink);
            ink.setStyle(Paint.Style.FILL);
            ink.setColor(0xff9fc4ff);
            c.drawCircle(ex, ey, 9, ink);
        }
        if (t >= 1) cableStart = 0;
        return t < 1;
    }

    private boolean drawCaret(Canvas c, long now) {
        float x = lookX - 6, y = 720 + (lookY - DOCK_Y);
        boolean on = !motion || (now / 500) % 2 == 0;
        ink.setShader(null);
        ink.setStyle(Paint.Style.FILL);
        if (on) { ink.setColor(accent); c.drawRect(x, y, x + 12, y + 70, ink); }
        String shown = "open(\"" + label + "\")";
        text.setTextSize(22);
        float w = text.measureText(shown);
        box.set(x + 22, y + 10, x + 22 + w + 20, y + 10 + 36);
        ink.setColor(0xd90d1117);
        c.drawRoundRect(box, 6, 6, ink);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(1);
        ink.setColor(0xff30363d);
        c.drawRoundRect(box, 6, 6, ink);
        float tx = x + 32, ty = y + 36;
        text.setColor(0xffff7b72);
        c.drawText("open", tx, ty, text);
        tx += text.measureText("open");
        text.setColor(0xffe6edf3);
        c.drawText("(", tx, ty, text);
        tx += text.measureText("(");
        text.setColor(0xffa5d6ff);
        c.drawText("\"" + label + "\"", tx, ty, text);
        tx += text.measureText("\"" + label + "\"");
        text.setColor(0xffe6edf3);
        c.drawText(")", tx, ty, text);
        return true;   // the cursor blinks
    }

    private void drawReadout(Canvas c) {
        if (actors.isEmpty()) return;
        Actor r = actors.get(0);
        boolean scouter = kind.equals("scouter");
        String s = scouter ? "POWER LEVEL › " + powerLevel(label)
                : "LOCK › " + label.toUpperCase() + "  [" + Math.round(lookX) + ", " + Math.round(lookY) + "]";
        text.setTextSize(18);
        int col = scouter ? 0xff5bff7a : accent;
        text.setColor(col);
        text.setShadowLayer(8, 0, 0, col);
        c.drawText(s, r.x + 35, r.y - 155 + 18, text);
        text.clearShadowLayer();
    }

    /** The mockup's power level: steady per app, and over 9000 for the Fan Hub. */
    static String powerLevel(String label) {
        if (label.toLowerCase().contains("fan hub")) return "OVER 9000!";
        return String.valueOf(1000 + Math.abs(label.hashCode() % 8000));
    }
}
