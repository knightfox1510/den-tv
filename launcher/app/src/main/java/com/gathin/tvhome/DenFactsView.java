package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Did you know?": a line along the bottom of Home with a fact about the look's hero, team or show
 * (assets/den/facts.json, from the mockup), a new one every 30 seconds. Off unless switched on from the phone.
 */
final class DenFactsView extends View {
    private static final long TURN_MS = 30_000, FADE_MS = 600;
    private final List<String> facts;
    private final boolean motion;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint();
    private final long start = SystemClock.uptimeMillis();
    private final int accent;

    private DenFactsView(Context ctx, List<String> facts, int accent, boolean motion) {
        super(ctx);
        this.facts = facts; this.accent = accent; this.motion = motion;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Typeface outfit;
        try { outfit = ctx.getResources().getFont(R.font.outfit); } catch (Exception e) { outfit = Typeface.SANS_SERIF; }
        text.setTypeface(outfit);
        label.setTypeface(Typeface.create(outfit, Typeface.BOLD));
    }

    /** The facts for this look; null when it has none (a look without a hero, team or show). */
    static DenFactsView make(Context ctx, DenLooks.Look look, boolean motion) {
        if (!look.facts.isEmpty()) {
            // A described look's own facts.
            List<String> own = new ArrayList<>(look.facts);
            Collections.shuffle(own);
            int accent;
            try { accent = android.graphics.Color.parseColor(look.accent); } catch (RuntimeException e) { accent = 0xffff4d6d; }
            return new DenFactsView(ctx, own, accent, motion);
        }
        try (InputStream in = ctx.getAssets().open("den/facts.json"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            JSONObject j = new JSONObject(out.toString("UTF-8"));
            JSONArray topics = j.getJSONObject("hero").optJSONArray(look.hero);
            if (topics == null) topics = j.getJSONObject("scene").optJSONArray(look.scene);
            if (topics == null) return null;
            List<String> facts = new ArrayList<>();
            for (int i = 0; i < topics.length(); i++) {
                JSONArray list = j.getJSONObject("facts").optJSONArray(topics.optString(i));
                if (list != null) for (int k = 0; k < list.length(); k++) facts.add(list.optString(k));
            }
            if (facts.isEmpty()) return null;
            Collections.shuffle(facts);
            int accent;
            try { accent = android.graphics.Color.parseColor(look.accent); } catch (RuntimeException e) { accent = 0xffff4d6d; }
            return new DenFactsView(ctx, facts, accent, motion);
        } catch (Exception e) {
            return null;
        }
    }

    @Override protected void onDraw(Canvas c) {
        float s = getWidth() / 1920f;
        long t = SystemClock.uptimeMillis() - start;
        int i = (int) (t / TURN_MS) % facts.size();
        long in = t % TURN_MS;
        float alpha = !motion ? 1 : Math.min(1, Math.min(in, TURN_MS - in) / (float) FADE_MS);
        c.save();
        c.scale(s, s);
        shade.setShader(new LinearGradient(0, 990, 0, 1080, 0x00000000, 0xcc000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 990, 1920, 1080, shade);
        label.setTextSize(18);
        label.setLetterSpacing(0.2f);
        label.setColor(accent);
        label.setAlpha(Math.round(255 * alpha));
        c.drawText("DID YOU KNOW", 110, 1050, label);
        float x = 110 + label.measureText("DID YOU KNOW") + 24;
        text.setTextSize(24);
        text.setColor(0xffeeeeee);
        text.setAlpha(Math.round(230 * alpha));
        CharSequence line = TextUtils.ellipsize(facts.get(i), new android.text.TextPaint(text), 1810 - x, TextUtils.TruncateAt.END);
        c.drawText(line, 0, line.length(), x, 1051, text);
        c.restore();
        long wait = !motion ? TURN_MS - in : in < FADE_MS || TURN_MS - in < FADE_MS ? 33 : TURN_MS - FADE_MS - in;
        postInvalidateDelayed(Math.max(16, wait));
    }
}
