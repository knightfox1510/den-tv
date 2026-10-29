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
import java.util.Map;
import java.util.Random;

/**
 * Small moving things over Home (looks/README.md, "extras"): camera flashes, flags, fireworks, crows, petals,
 * embers, confetti, sparks, gold and silver, leaves, wisteria, rain and snow. "auto" picks the scene's own, as in the
 * mockup. Kept sparse and dim, and drawn at 30 frames a second, so a 1-2 GB TV stays smooth.
 */
final class DenExtras extends View {
    static final List<String> KINDS = List.of("auto", "none", "flashes", "flags", "fireworks", "crows", "petals", "embers", "confetti",
            "sparks", "gold", "silver", "leaves", "wisteria", "rain", "snow");
    private static final Map<String, String> SCENE = Map.ofEntries(Map.entry("campnou", "flags"), Map.entry("floodlights", "flashes"),
            Map.entry("wankhede", "fireworks"), Map.entry("bluebillion", "flashes"), Map.entry("crimson", "crows"), Map.entry("domain", "sparks"),
            Map.entry("diwali", "fireworks"), Map.entry("saiyan", "gold"), Map.entry("ui", "silver"), Map.entry("kurama", "embers"),
            Map.entry("hokage", "leaves"), Map.entry("zoro", "leaves"), Map.entry("wisteria", "wisteria"), Map.entry("leaf", "leaves"),
            Map.entry("firefist", "embers"), Map.entry("room", "snow"), Map.entry("drum", "snow"), Map.entry("hitman", "fireworks"),
            Map.entry("gate", "sparks"), Map.entry("asura", "embers"), Map.entry("lost", "leaves"), Map.entry("vegeta", "sparks"),
            Map.entry("noir", "rain"), Map.entry("velvet", "embers"), Map.entry("monsoon", "rain"), Map.entry("neon", "rain"),
            Map.entry("seoul", "petals"), Map.entry("marquee", "embers"));

    private final String kind;
    private final int accent;
    private final float[][] bits;   // x, y, speed, phase, size, colour pick
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private long last = SystemClock.uptimeMillis();

    private DenExtras(Context ctx, String kind, int accent) {
        super(ctx);
        this.kind = kind; this.accent = accent;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int n = kind.equals("rain") ? 70 : kind.equals("flashes") || kind.equals("fireworks") ? 12 : kind.equals("crows") ? 5 : 34;
        bits = new float[n][6];
        for (float[] b : bits) reset(b, true);
    }

    /** The extras for a look; null for none, or when motion is off. */
    static DenExtras make(Context ctx, DenLooks.Look look, boolean motion) {
        if (!motion || look == null) return null;
        String k = "auto".equals(look.extras) ? SCENE.get(look.scene) : look.extras;
        if (k == null || "none".equals(k) || !KINDS.contains(k)) return null;
        int accent;
        try { accent = android.graphics.Color.parseColor(look.accent); } catch (RuntimeException e) { accent = 0xffff4d6d; }
        return new DenExtras(ctx, k, accent);
    }

    private boolean rises() { return kind.equals("embers") || kind.equals("sparks"); }

    private void reset(float[] b, boolean anywhere) {
        b[0] = random.nextFloat() * 1920;
        b[1] = anywhere ? random.nextFloat() * 1080 : rises() ? 1100 : -30;
        b[2] = kind.equals("rain") ? 1400 + random.nextFloat() * 500 : kind.equals("crows") ? 120 + random.nextFloat() * 80 : 40 + random.nextFloat() * 90;
        b[3] = random.nextFloat() * 6.28f;
        b[4] = 0.6f + random.nextFloat() * 0.8f;
        b[5] = random.nextInt(3);
        if (kind.equals("crows")) { b[0] = -100 - random.nextFloat() * 600; b[1] = 80 + random.nextFloat() * 300; }
        if (kind.equals("flashes") || kind.equals("fireworks")) { b[1] = 60 + random.nextFloat() * 380; b[3] = random.nextFloat() * 4; }
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.1f, (now - last) / 1000f), time = now / 1000f;
        last = now;
        float s = getWidth() / 1920f;
        c.save();
        c.scale(s, s);
        p.setShader(null);
        for (float[] b : bits) {
            step(b, dt);
            draw(c, b, time);
        }
        c.restore();
        postInvalidateDelayed(33);
    }

    private void step(float[] b, float dt) {
        switch (kind) {
            case "flashes": case "fireworks":
                b[3] += dt;
                if (b[3] > (kind.equals("fireworks") ? 2.4f : 3f)) { reset(b, false); b[3] = 0; }
                return;
            case "crows":
                b[0] += b[2] * dt;
                b[1] += (float) Math.sin(b[0] / 120) * 12 * dt;
                if (b[0] > 2050) reset(b, false);
                return;
            default:
                b[1] += (rises() ? -1 : 1) * b[2] * dt;
                if (!kind.equals("rain")) b[0] += (float) Math.sin(b[1] / 90 + b[3]) * 30 * dt;
                if (b[1] > 1110 || b[1] < -40) reset(b, false);
        }
    }

    private static int a(int colour, float alpha) { return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (colour & 0xffffff); }

    private void draw(Canvas c, float[] b, float time) {
        float x = b[0], y = b[1], z = b[4];
        switch (kind) {
            case "flashes": {
                float on = b[3] < 0.15f ? 1 - b[3] / 0.15f : 0;
                if (on <= 0) return;
                p.setShader(new RadialGradient(x, y, 36 * z, a(0xffffffff, on * 0.9f), 0, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, 36 * z, p);
                p.setShader(null);
                return;
            }
            case "fireworks": {
                if (b[3] > 1.4f) return;
                float rr = 140 * z * (float) Math.sqrt(b[3] / 1.4f);
                int col = b[5] == 0 ? accent : b[5] == 1 ? 0xffffd23c : 0xffff6ad5;
                p.setColor(a(col, (1.4f - b[3]) * 0.6f));
                for (int i = 0; i < 16; i++) {
                    double ang = i * Math.PI / 8;
                    c.drawCircle(x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr + 30 * b[3] * b[3], 3.5f, p);
                }
                return;
            }
            case "crows": {
                float flap = (float) Math.sin(time * 9 + b[3]) * 14;
                p.setColor(0xb0101014);
                path.reset();
                path.moveTo(x - 30, y - flap); path.quadTo(x - 12, y - 4, x, y + 4); path.quadTo(x + 12, y - 4, x + 30, y - flap);
                path.quadTo(x + 12, y + 2, x, y + 10); path.quadTo(x - 12, y + 2, x - 30, y - flap);
                c.drawPath(path, p);
                return;
            }
            case "rain":
                p.setColor(0x55a0c8ff);
                p.setStrokeWidth(2);
                c.drawLine(x, y, x - 6, y + 34 * z, p);
                return;
            case "snow":
                p.setColor(0x99ffffff);
                c.drawCircle(x, y, 4 * z, p);
                return;
            case "embers": case "sparks": case "gold": case "silver": {
                int col = kind.equals("embers") ? 0xffff7a1a : kind.equals("sparks") ? 0xff7fd8ff : kind.equals("gold") ? 0xffffd23c : 0xffe6ecf5;
                float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(time * 3 + b[3]));
                p.setColor(a(col, 0.7f * tw));
                c.drawCircle(x, y, 3.2f * z, p);
                return;
            }
            case "flags": {
                int col = b[5] == 0 ? 0xffa50044 : b[5] == 1 ? 0xff004d98 : 0xffedbb00;
                p.setColor(a(col, 0.55f));
                c.save();
                c.translate(x, y);
                c.rotate((float) Math.sin(time * 2 + b[3]) * 25);
                path.reset(); path.moveTo(0, 0); path.lineTo(26 * z, 9 * z); path.lineTo(0, 18 * z); path.close();
                c.drawPath(path, p);
                c.restore();
                return;
            }
            default: {   // petals, leaves, wisteria, confetti
                int col;
                switch (kind) {
                    case "leaves": col = b[5] == 0 ? 0xff6aa84f : b[5] == 1 ? 0xffd9822b : 0xffb5532a; break;
                    case "wisteria": col = b[5] == 0 ? 0xffb48cff : 0xffd9b8ff; break;
                    case "confetti": col = b[5] == 0 ? accent : b[5] == 1 ? 0xffffffff : 0xffffd23c; break;
                    default: col = b[5] == 0 ? 0xffffb7d5 : 0xffffd6e6; break;
                }
                p.setColor(a(col, 0.6f));
                c.save();
                c.translate(x, y);
                c.rotate(time * 60 * (b[5] - 1) + b[3] * 57);
                if (kind.equals("confetti")) c.drawRect(-6 * z, -3 * z, 6 * z, 3 * z, p);
                else c.drawOval(-8 * z, -4.5f * z, 8 * z, 4.5f * z, p);
                c.restore();
            }
        }
    }
}
