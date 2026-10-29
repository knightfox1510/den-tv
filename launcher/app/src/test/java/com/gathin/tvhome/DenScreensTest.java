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
        DenLooks.Look look = DenLooks.byId(DenLooks.load(ctx), "domain");   // Domain Expansion: Six Eyes
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
