package com.gathin.tvhome;

import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.view.View;

import org.robolectric.RuntimeEnvironment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Renders den-tv's screens to PNGs (app/build/den-screens/) so they can be looked at without a TV:
 * ./gradlew testReleaseUnitTest -Pden.screens=1 --tests '*DenScreensTest'. Skipped otherwise.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-xhdpi")
public class DenScreensTest {
    private final File out = new File("build/den-screens");

    @Before public void only() {
        assumeFalse(System.getProperty("den.screens", "").isEmpty());
        out.mkdirs();
    }

    @Test public void eyes() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/01-camp-nou.jpg");
        for (String kind : DenLooks.EYES) {
            DenEyesView eyes = new DenEyesView(ctx, kind, false);
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (wall != null) c.drawBitmap(wall, 0, 0, null);
            layout(eyes, 1920, 1080);
            eyes.lookAt(kind.equals("six") ? 300 : 1500, 860, "YouTube");   // no motion: jumps straight there
            eyes.draw(c);
            save(b, "eyes-" + kind);
        }
    }

    @Test public void watchers() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/01-camp-nou.jpg");
        for (String kind : DenWatcherView.KINDS) {
            DenWatcherView w = new DenWatcherView(ctx, kind, "#ff4d6d", false);
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (wall != null) c.drawBitmap(wall, 0, 0, null);
            layout(w, 1920, 1080);
            w.lookAt(1200, 860, "YouTube");   // a tile right of centre, on the dock row
            w.draw(c);
            save(b, "watcher-" + kind);
        }
    }

    @Test public void clocks() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        int rows = (DenClockView.STYLES.size() + 2) / 3;
        Bitmap b = Bitmap.createBitmap(1920, rows * 300, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xff1a1d2a);
        for (int i = 0; i < DenClockView.STYLES.size(); i++) {
            DenClockView v = new DenClockView(ctx, DenClockView.STYLES.get(i), "#ff4d6d");
            v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
            c.save();
            c.translate(40 + (i % 3) * 640, 40 + (i / 3) * 300);
            v.draw(c);
            c.restore();
        }
        save(b, "clocks");
    }

    @Test public void icons() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        Bitmap banner = BitmapFactory.decodeFile("src/main/res/drawable-xhdpi/den_looks_banner.png");
        List<String> styles = DenIcons.STYLES;
        Bitmap b = Bitmap.createBitmap(1920, ((styles.size() + 5) / 6) * 220, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xff1a1d2a);
        float density = ctx.getResources().getDisplayMetrics().density;
        for (int i = 0; i < styles.size(); i++) {
            int x = 40 + (i % 6) * 310, y = 40 + (i / 6) * 220;
            if (banner != null) c.drawBitmap(banner, null, new android.graphics.Rect(x, y, x + 270, y + 152), null);
            DenIcons.Frame f = new DenIcons.Frame(styles.get(i), "Prime Video", i, 0xff4dd2ff, false, density);
            f.setBounds(x, y, x + 270, y + 152);   // a dock tile, 16:9
            f.draw(c);
        }
        save(b, "icons");
    }

    @Test public void factsAndFigure() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        DenTheme.ensure(ctx);
        DenLooks.Look look = DenLooks.byId(DenLooks.load(ctx), "domain");
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/" + look.wallpaper);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (wall != null) c.drawBitmap(wall, null, new android.graphics.Rect(0, 0, 1920, 1080), null);
        View facts = DenFactsView.make(ctx, look, false);
        layout(facts, 1920, 1080);
        facts.draw(c);
        // A stand-in character: one of the code-drawn walker pictures.
        Bitmap body;
        try (java.io.InputStream in = ctx.getAssets().open("den/watchers/walk-luffy.webp")) { body = BitmapFactory.decodeStream(in); }
        DenFigureView fig = new DenFigureView(ctx, body, null, false, false);
        layout(fig, 1920, 1080);
        fig.lookAt(600, 860, "Netflix");
        fig.draw(c);
        save(b, "facts-figure");
    }

    @Test public void picker() throws IOException {
        DenLooksActivity a = Robolectric.buildActivity(DenLooksActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        layout(root, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        save(b, "picker");
    }

    @Test public void home() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        DenTheme.ensure(ctx);
        DenLooks.Look look = DenLooks.byId(DenLooks.load(ctx), "domain");   // Domain Expansion: Six Eyes, thin clock
        DenLooks.apply(ctx, look);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        layout(root, 1920, 1080);
        View focused = root.findFocus();
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        save(b, "home-domain");
    }

    private static void layout(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
    }

    private void save(Bitmap b, String name) throws IOException {
        try (FileOutputStream f = new FileOutputStream(new File(out, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, f); }
    }
}
