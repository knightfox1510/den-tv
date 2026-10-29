package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * den-tv's screensaver: the look on Home (or a random one while looks rotate) drifting slowly, darkened, with its
 * own clock style large in the middle, the date, what's next from the Fan Hub (a countdown, or LIVE) and a "Did you
 * know?" fact along the bottom. The clock block moves to a new spot every minute so nothing burns in. Used by
 * DenDream (the system screensaver) and by Home's own idle clock (ClockActivity).
 */
final class DenSaverView extends FrameLayout {
    private static final Uri TURNS = Uri.parse("content://tv.den.fanhub.games/turns");
    private static final long MOVE_MS = 60_000, DRIFT_MS = 90_000;

    private final Random random = new Random();
    private final LinearLayout block;
    private final TextView date, next;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            date.setText(DateFormat.format("EEEE d MMMM", new Date()));
            readNext();
            move();
            postDelayed(this, MOVE_MS);
        }
    };

    /** Null when there are no looks (the den-mix theme isn't on the TV): ClockActivity then keeps its own clock. */
    static DenSaverView make(Context ctx, boolean motion) {
        List<DenLooks.Look> looks = DenLooks.load(ctx);
        if (looks.isEmpty()) return null;
        DenLooks.Look look = DenLooks.tweaked(ctx, DenLooks.current(ctx, looks));
        if (look == null) look = looks.get(new Random().nextInt(looks.size()));
        return new DenSaverView(ctx, look, motion);
    }

    private DenSaverView(Context ctx, DenLooks.Look look, boolean motion) {
        super(ctx);
        setBackgroundColor(Color.BLACK);
        int accent;
        try { accent = Color.parseColor(look.accent); } catch (RuntimeException e) { accent = 0xffff4d6d; }
        addView(new Backdrop(ctx, look.wallpaper, motion), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        DenClockView clock = new DenClockView(ctx, look.clock.isEmpty() ? "thin" : look.clock, look.accent);
        // The Home clock's size, 1.8 times: the saver is read from across the room.
        FrameLayout clockBox = new FrameLayout(ctx) {
            @Override protected void onMeasure(int w, int h) {
                clock.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                setMeasuredDimension(Math.round(clock.getMeasuredWidth() * 1.8f), Math.round(clock.getMeasuredHeight() * 1.8f));
            }
            @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
                clock.layout(0, 0, clock.getMeasuredWidth(), clock.getMeasuredHeight());
                clock.setPivotX(0);
                clock.setPivotY(0);
                clock.setScaleX(1.8f);
                clock.setScaleY(1.8f);
            }
        };
        clockBox.addView(clock);
        block.addView(clockBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Typeface outfit;
        try { outfit = ctx.getResources().getFont(R.font.outfit); } catch (RuntimeException e) { outfit = Typeface.SANS_SERIF; }
        date = text(ctx, outfit, 26, 0xffe8e6f0);
        date.setLetterSpacing(0.18f);
        next = text(ctx, outfit, 22, accent);
        next.setLetterSpacing(0.08f);
        date.setText(DateFormat.format("EEEE d MMMM", new Date()));
        block.addView(date);
        block.addView(next);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(block, lp);
        DenFactsView facts = DenFactsView.make(ctx, look, motion);
        if (facts != null) addView(facts, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private static TextView text(Context ctx, Typeface face, float sp, int colour) {
        TextView t = new TextView(ctx);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setShadowLayer(12, 0, 2, 0xcc000000);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Math.round(sp * 0.6f), 0, 0);
        return t;
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }

    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); io.shutdownNow(); super.onDetachedFromWindow(); }

    /** A new spot for the clock block, inside the middle of the screen. */
    private void move() {
        if (getWidth() == 0) return;
        float dx = getWidth() * 0.16f, dy = getHeight() * 0.14f;
        float tx = (random.nextFloat() * 2 - 1) * dx, ty = (random.nextFloat() * 2 - 1) * dy;
        block.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            block.setTranslationX(tx);
            block.setTranslationY(ty);
            block.animate().alpha(1f).setDuration(600).start();
        }).start();
    }

    /** What's next from the Fan Hub, read off the main thread: "INDIA V AUSTRALIA · T20I · in 2 h 18 min" or LIVE. */
    private void readNext() {
        Context ctx = getContext();
        try {
            io.execute(() -> {
                String line = "";
                try (Cursor c = ctx.getContentResolver().query(TURNS, new String[]{"top_line", "start_ms", "end_ms"}, null, null, null)) {
                    long now = System.currentTimeMillis();
                    while (c != null && c.moveToNext()) {
                        long start = c.getLong(1), end = c.getLong(2);
                        if (end <= now) continue;
                        line = now >= start ? "● LIVE  " + c.getString(0) : c.getString(0) + "  ·  " + in(start - now);
                        break;
                    }
                } catch (RuntimeException ignored) { }
                String shown = line;
                post(() -> { next.setText(shown); next.setVisibility(shown.isEmpty() ? GONE : VISIBLE); });
            });
        } catch (RuntimeException ignored) { }
    }

    static String in(long ms) {
        long m = Math.max(1, ms / 60_000), h = m / 60, d = h / 24;
        if (d >= 2) return "in " + d + " days";
        if (h >= 1) return "in " + h + " h " + (m % 60) + " min";
        return "in " + m + " min";
    }

    /** The wallpaper, darkened, drifting and zooming very slowly (also keeps it from burning in). */
    private static final class Backdrop extends View {
        private final boolean motion;
        private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG), shade = new Paint();
        private final RectF r = new RectF();
        private final long start = SystemClock.uptimeMillis();
        private Bitmap wall;

        Backdrop(Context ctx, String file, boolean motion) {
            super(ctx);
            this.motion = motion;
            File dir = ctx.getExternalFilesDir(null);
            if (dir != null && file != null) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;   // 960x540 is plenty under the dark and the drift, and light on a 1.8 GB TV
                wall = BitmapFactory.decodeFile(new File(new File(dir, "wallpapers"), file).getPath(), o);
            }
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (wall != null) {
                double ph = motion ? (SystemClock.uptimeMillis() - start) / (double) DRIFT_MS * 2 * Math.PI : 0;
                float zoom = 1.08f + 0.04f * (float) Math.sin(ph / 2), ox = (float) Math.sin(ph) * w * 0.025f, oy = (float) Math.cos(ph * 0.7) * h * 0.02f;
                float k = Math.max(w / wall.getWidth(), h / wall.getHeight()) * zoom, bw = wall.getWidth() * k, bh = wall.getHeight() * k;
                r.set((w - bw) / 2 + ox, (h - bh) / 2 + oy, (w + bw) / 2 + ox, (h + bh) / 2 + oy);
                c.drawBitmap(wall, null, r, img);
            }
            shade.setShader(new LinearGradient(0, 0, 0, h, 0x99000000, 0xd9000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, shade);
            // A slow drift needs few frames: 10 a second is smooth enough at this speed and cheap for the TV.
            if (motion) postInvalidateDelayed(100);
        }
    }
}
