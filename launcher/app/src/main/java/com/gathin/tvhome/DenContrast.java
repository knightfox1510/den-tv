package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Keeps Home's text readable over any wallpaper (a floodlight behind the match line, a bright pitch behind the
 * date): a soft shade behind the clock and the panel under it, drawn between the wallpaper and the text, and a
 * shadow under the text itself. "contrast" (phone: Tweak, Look): "" normal, "strong" for bright wallpapers, "off".
 */
final class DenContrast extends View {
    static final String KEY = "contrast";
    private final boolean strong;
    private final Paint paint = new Paint();

    private DenContrast(Context ctx, boolean strong) {
        super(ctx);
        this.strong = strong;
    }

    static String level(Context ctx) { return DenSettings.prefs(ctx).getString(KEY, ""); }

    /** Adds the shade under Home's text and shadows to the text, unless contrast is off. */
    static void apply(Activity a) {
        String level = level(a);
        if ("off".equals(level)) return;
        boolean strong = "strong".equals(level);
        ViewGroup root = a.findViewById(R.id.root);
        View[] texts = {a.findViewById(R.id.leftBlock), a.findViewById(R.id.racePanel), a.findViewById(R.id.hero)};
        if (root != null) {
            // Just below the lowest of the text's own layers in Home's frame, so above the wallpaper and the sky.
            int at = root.getChildCount();
            for (View v : texts) {
                View top = v;
                while (top != null && top.getParent() != root) top = top.getParent() instanceof View ? (View) top.getParent() : null;
                if (top != null) at = Math.min(at, root.indexOfChild(top));
            }
            root.addView(new DenContrast(a, strong), at, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        float dp = a.getResources().getDisplayMetrics().density;
        for (View v : texts) shadow(v, (strong ? 6 : 4) * dp, dp, strong ? 0xf0000000 : 0xcc000000);
    }

    /** A shadow under every text in a view, where it has none of its own. */
    private static void shadow(View v, float radius, float dy, int colour) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (t.getShadowRadius() == 0) t.setShadowLayer(radius, 0, dy, colour);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) shadow(g.getChildAt(i), radius, dy, colour);
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int a = strong ? 0xa0 : 0x5c;
        // Centred on the clock and the panel under it (top left), fading out before the middle of the screen.
        paint.setShader(new RadialGradient(w * 0.17f, h * 0.3f, w * 0.55f, new int[]{a << 24, (a * 6 / 10) << 24, 0},
                new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        if (strong) {
            // And the facts line along the bottom.
            Paint bottom = new Paint();
            bottom.setShader(new LinearGradient(0, getHeight() * 0.86f, 0, getHeight(), 0, 0xa0000000, Shader.TileMode.CLAMP));
            c.drawRect(0, getHeight() * 0.86f, getWidth(), getHeight(), bottom);
        }
    }
}
