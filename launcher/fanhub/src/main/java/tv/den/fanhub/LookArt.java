package tv.den.fanhub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONArray;

import java.util.Random;

/**
 * A described look's wallpaper, 1920 by 1080, in the style of den-tv's own: a picture (Gemini's, with a key that may
 * paint) or, without one, a poster drawn here in the look's colours; then, like the built-in hero walls, the shirt
 * number in outline, the title and the subtitle at the bottom right, over a soft dark glow so they read on anything.
 */
final class LookArt {
    static final int W = 1920, H = 1080;

    private LookArt() {}

    /** picture: Gemini's image bytes, or null to draw a poster. colours: #RRGGBB, at least one. */
    static Bitmap make(Context ctx, byte[] picture, JSONArray colours, String number, String title, String subtitle, long seed) {
        int c1 = colour(colours, 0, 0xffff4d6d), c2 = colour(colours, 1, mix(c1, 0xff2a5aa8, 0.6f)), c3 = colour(colours, 2, c1);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Bitmap src = picture == null ? null : decode(picture);
        if (src != null) cover(c, src);
        else poster(c, c1, c2, c3, new Random(seed));
        words(ctx, c, c1, number, title, subtitle);
        return out;
    }

    private static Bitmap decode(byte[] b) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        if (o.outWidth <= 0) return null;
        o = new BitmapFactory.Options();
        o.inSampleSize = Api.sample(o.outWidth, o.outHeight, W, H);
        try { return BitmapFactory.decodeByteArray(b, 0, b.length, o); } catch (OutOfMemoryError e) { return null; }
    }

    /** The picture scaled to fill the frame, cropped in the middle. */
    private static void cover(Canvas c, Bitmap src) {
        float scale = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
        int cw = Math.round(W / scale), ch = Math.round(H / scale);
        Rect from = new Rect((src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2, (src.getWidth() + cw) / 2, (src.getHeight() + ch) / 2);
        c.drawBitmap(src, from, new Rect(0, 0, W, H), new Paint(Paint.FILTER_BITMAP_FLAG));
    }

    /** A poster in the look's colours: a dark sky lit in them, stripes of them, floodlight haze and specks of light. */
    private static void poster(Canvas c, int c1, int c2, int c3, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{0xff05050a, mix(0xff05050a, c2, 0.35f), mix(0xff05050a, c1, 0.25f)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        glow(c, 1450, 430, 620, alpha(c1, 0.55f));
        glow(c, 260, 980, 700, alpha(c2, 0.45f));
        glow(c, 900, 120, 520, alpha(c3, 0.10f));
        // Club-style stripes, leaning, faded toward the left so the clock side stays calm.
        c.save();
        c.rotate(-14, W / 2f, H / 2f);
        int[] stripes = {c1, c2, c1, c2, c3};
        for (int i = 0; i < 9; i++) {
            float x = 980 + i * 120;
            int sc = stripes[i % stripes.length];
            // Light colours (white, gold) wash a dark poster grey: they get less.
            float lum = (Color.red(sc) * 0.3f + Color.green(sc) * 0.59f + Color.blue(sc) * 0.11f) / 255f;
            p.setShader(new LinearGradient(x, 0, x - 500, 0, alpha(sc, lum > 0.7f ? 0.14f : 0.34f), 0, Shader.TileMode.CLAMP));
            c.drawRect(x - 500, -300, x + 70, H + 300, p);
        }
        c.restore();
        // Floodlight haze from the top corners, and specks of light.
        p.setShader(null);
        glow(c, 120, 60, 420, 0x26ffffff);
        glow(c, 1820, 60, 420, 0x26ffffff);
        for (int i = 0; i < 260; i++) {
            p.setColor(i % 5 == 0 ? alpha(c3, 0.8f) : 0xccffffff);
            p.setAlpha(40 + r.nextInt(140));
            c.drawCircle(r.nextFloat() * W, r.nextFloat() * H * 0.75f, 1 + r.nextFloat() * 2.2f, p);
        }
        // A darker floor, as the hero walls have.
        p.setShader(new LinearGradient(0, H * 0.7f, 0, H, 0, 0xcc000000, Shader.TileMode.CLAMP));
        c.drawRect(0, H * 0.7f, W, H, p);
    }

    /** The number in outline behind, then the title and subtitle, as den-tv's hero walls draw them (mockup denPaint). */
    private static void words(Context ctx, Canvas c, int accent, String number, String title, String subtitle) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // The soft dark glow behind the words, over the whole frame so it has no edge.
        p.setShader(new RadialGradient(1600, 750, 560, new int[]{0x80000000, 0x47000000, 0}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        Typeface heavy = font(ctx, 800), bold = font(ctx, 700);
        if (number != null && !number.isEmpty()) {
            p.setTypeface(heavy);
            p.setTextSize(520);
            p.setTextAlign(Paint.Align.CENTER);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            p.setColor(alpha(accent, 0.75f));
            c.drawText(number, 1500, 640, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setTextAlign(Paint.Align.RIGHT);
        if (title != null && !title.isEmpty()) {
            p.setTypeface(heavy);
            p.setTextSize(fit(p, title, 96, 1100, 0.14f));
            p.setLetterSpacing(0.14f);
            p.setColor(Color.WHITE);
            p.setShadowLayer(24, 0, 0, 0xd9000000);
            c.drawText(title, 1830, 735, p);
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            p.setTypeface(bold);
            p.setTextSize(fit(p, subtitle, 28, 1200, 0.28f));
            p.setLetterSpacing(0.28f);
            p.setShadowLayer(10, 0, 0, 0xd9000000);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(6);
            p.setColor(0xcc000000);
            c.drawText(subtitle, 1820, 780, p);
            p.setStyle(Paint.Style.FILL);
            p.clearShadowLayer();
            p.setColor(lift(accent));
            c.drawText(subtitle, 1820, 780, p);
        }
    }

    /** The largest size up to max at which the text fits in width. */
    private static float fit(Paint p, String text, float max, float width, float spacing) {
        p.setLetterSpacing(spacing);
        p.setTextSize(max);
        float w = p.measureText(text);
        return w <= width ? max : Math.max(max * 0.5f, max * width / w);
    }

    private static Typeface font(Context ctx, int weight) {
        // Outfit is a variable font: the weight is a variation setting, as on the Fan Hub screen (HubView).
        try { return new Typeface.Builder(ctx.getAssets(), "fonts/outfit.ttf").setFontVariationSettings("'wght' " + weight).setWeight(weight).build(); }
        catch (RuntimeException e) { return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); }
    }

    private static void glow(Canvas c, float x, float y, float r, int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(x, y, r, colour, colour & 0x00ffffff, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
    }

    static int colour(JSONArray colours, int i, int fallback) {
        try { return colours != null && i < colours.length() ? Color.parseColor(colours.getString(i)) : fallback; }
        catch (Exception e) { return fallback; }
    }

    private static int alpha(int c, float a) { return (Math.round(a * 255) << 24) | (c & 0xffffff); }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t), Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** A colour light enough to read over the dark glow (a navy accent becomes a clear blue). */
    private static int lift(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] = Math.max(hsv[2], 0.85f);
        hsv[1] = Math.min(hsv[1], 0.85f);
        return Color.HSVToColor(hsv);
    }
}
