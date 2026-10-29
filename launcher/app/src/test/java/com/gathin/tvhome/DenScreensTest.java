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

    @Test public void moments() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/01-camp-nou.jpg");
        String[][] m = {{"goal", "Goal!", "BAR 2\u20131 RMA \u00B7 67'", "1100"}, {"six", "100 up", "IND 100/2 (11.4)", "1800"},
                {"beam", "Kamehameha!", "BAR 2\u20131 RMA", "1700"}, {"arise", "Arise", "BAR 2\u20131 RMA", "1900"},
                {"punch", "One punch", "BAR 2\u20131 RMA", "1300"}, {"gear5", "Gear 5", "BAR 2\u20131 RMA", "1500"},
                {"drop", "New episode", "ONE PIECE \u00B7 EPISODE 1181", "900"}};
        for (String[] x : m) {
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (wall != null) c.drawBitmap(wall, null, new android.graphics.Rect(0, 0, 1920, 1080), null);
            DenMomentView v = new DenMomentView(ctx, x[0], x[1], x[2], 0xffa50044, true);
            v.frozenAt = Long.parseLong(x[3]);
            layout(v, 1920, 1080);
            v.draw(c);
            save(b, "moment-" + x[0]);
        }
    }

    @Test public void wakes() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/01-camp-nou.jpg");
        List<String> styles = DenWake.STYLES.subList(1, DenWake.STYLES.size());
        Bitmap sheet = Bitmap.createBitmap(1920, ((styles.size() + 3) / 4) * 270, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        for (int i = 0; i < styles.size(); i++) {
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (wall != null) c.drawBitmap(wall, null, new android.graphics.Rect(0, 0, 1920, 1080), null);
            DenWake w = new DenWake(ctx, styles.get(i), 0xff3ddc84);
            w.frozenAt = 520;
            layout(w, 1920, 1080);
            w.draw(c);
            sc.drawBitmap(b, null, new android.graphics.Rect((i % 4) * 480, (i / 4) * 270, (i % 4) * 480 + 480, (i / 4) * 270 + 270), null);
        }
        save(sheet, "wakes");
    }

    @Test public void layouts() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        List<String> kinds = DenLayouts.LAYOUTS.subList(1, DenLayouts.LAYOUTS.size());
        Bitmap sheet = Bitmap.createBitmap(1920, kinds.size() * 360, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        sc.drawColor(0xff101624);
        for (int i = 0; i < kinds.size(); i++) {
            androidx.recyclerview.widget.RecyclerView rv = new androidx.recyclerview.widget.RecyclerView(ctx);
            rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(ctx, androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false));
            rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                @Override public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup p, int t) {
                    android.widget.FrameLayout cell = new android.widget.FrameLayout(ctx);
                    cell.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(320, 200));
                    cell.setFocusable(true);
                    View tile = new View(ctx);
                    tile.setBackgroundColor(0xffe0e0e8);
                    cell.addView(tile, new android.widget.FrameLayout.LayoutParams(240, 135, android.view.Gravity.CENTER));
                    return new androidx.recyclerview.widget.RecyclerView.ViewHolder(cell) { };
                }
                @Override public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder h, int pos) {
                    ((android.widget.FrameLayout) h.itemView).getChildAt(0).setBackgroundColor(pos == 2 ? 0xffff4d6d : 0xffe0e0e8);
                }
                @Override public int getItemCount() { return 8; }
            });
            DenLayouts.attach(rv, kinds.get(i), 0xffffd23c);
            android.widget.FrameLayout host = new android.widget.FrameLayout(ctx);
            host.addView(rv, new android.widget.FrameLayout.LayoutParams(1920, 200, android.view.Gravity.CENTER));
            layout(host, 1920, 360);
            rv.getChildAt(2).requestFocus();
            layout(host, 1920, 360);
            rv.getViewTreeObserver().dispatchOnGlobalLayout();
            sc.save();
            sc.translate(0, i * 360);
            sc.clipRect(0, 0, 1920, 360);
            host.draw(sc);
            sc.restore();
        }
        save(sheet, "layouts");
    }

    @Test public void homeFigureAndHud() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        DenTheme.ensure(ctx);
        DenSettings.prefs(ctx).edit().putBoolean(DenSettings.WAKE, false).apply();   // the picture, not the first frame of a wake
        for (String id : new String[]{"straw-hat", "geek"}) {
            DenLooks.Look look = DenLooks.byId(DenLooks.load(ctx), id);
            if (look == null) continue;
            DenLooks.apply(ctx, look);
            MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
            View root = a.getWindow().getDecorView();
            layout(root, 1920, 1080);
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            save(b, "home-" + id);
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

    /**
     * The last app of a long row focused: the cloud stays on screen, and the row fades at the left with a pill
     * saying 14 more apps that way, and the track under it at the end. Stand-in tiles; the wallpaper is real.
     */
    @Test public void rowEnd() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/01-camp-nou.jpg");
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (wall != null) c.drawBitmap(wall, 0, 0, null);
        android.graphics.Paint tile = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float top = 760, h = 230, w = 400;
        for (int i = 0; i < 6; i++) {
            float x = 1900 - (i + 1) * (w + 40) + 20;
            tile.setColor(i == 0 ? 0xffe50914 : 0xff2a2f45 + i * 0x0a0a0a);
            c.drawRoundRect(x, top, x + w, top + h, 24, 24, tile);
        }
        DenWatcherView cloud = new DenWatcherView(ctx, "cloud", "#ff4d6d", false);
        layout(cloud, 1920, 1080);
        cloud.lookAt(1900 - w / 2 - 20, top + h / 2, "Netflix");
        cloud.draw(c);
        DenRowHints.forTest(0xff4d9fff, 2).draw(c, 1920, top, top + h, 14, 0, 19, 20);
        save(b, "row-end");
    }

    /** The ball watchers mid-move and settled, on their own looks' wallpapers; and the bat with Kohli's 18. */
    @Test public void balls() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        String[][] runs = {{"drive", "81-master-blaster", "#ffb347"}, {"trivela", "82-rocafonda-304", "#edbb00"}, {"onetwo", "84-barca-wings", "#ff4d8a"}};
        for (String[] run : runs) {
            Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/" + run[1] + ".jpg");
            for (long ms : new long[]{300, 900, 5000}) {
                Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                if (wall != null) c.drawBitmap(wall, 0, 0, null);
                DenBallWatcher w = new DenBallWatcher(ctx, run[0], run[2], true);
                layout(w, 1920, 1080);
                w.lookAt(500, 870, "Netflix");
                w.lookAt(1300, 870, "JioHotstar");
                w.freeze(ms);
                w.draw(c);
                save(b, "ball-" + run[0] + "-" + ms);
            }
        }
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/08-chase-master.jpg");
        if (wall != null) c.drawBitmap(wall, 0, 0, null);
        DenWatcherView bat = new DenWatcherView(ctx, "bat", "#ff9933", false).number(DenWatcherView.numberFor("kohli"));
        layout(bat, 1920, 1080);
        bat.lookAt(1200, 860, "JioHotstar");
        bat.draw(c);
        save(b, "watcher-bat-18");
    }

    /** The screensaver over the look on Home (Master Blaster). */
    @Test public void saver() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        DenTheme.ensure(ctx);
        DenLooks.apply(ctx, DenLooks.byId(DenLooks.load(ctx), "master-blaster"));
        DenSaverView v = DenSaverView.make(ctx, false);
        org.junit.Assert.assertNotNull(v);
        layout(v, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        save(b, "saver");
    }

    /**
     * Home with twenty apps in every icon layout, each with a different icon style, focused mid-row and on the first
     * app: the focus glass must sit on the picked tile wherever the layout puts it.
     */
    @Test public void row() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowPackageManager pm = org.robolectric.Shadows.shadowOf(ctx.getPackageManager());
        int[] colours = {0xffe50914, 0xff1db954, 0xff0f79af, 0xffff0000, 0xff6f2da8, 0xff00a8e1, 0xfff5c518};
        for (int i = 0; i < 20; i++) {
            String pkg = "com.example.app" + (char) ('a' + i);
            android.content.pm.PackageInfo pi = new android.content.pm.PackageInfo();
            pi.packageName = pkg;
            pi.applicationInfo = new android.content.pm.ApplicationInfo();
            pi.applicationInfo.packageName = pkg;
            pi.applicationInfo.nonLocalizedLabel = "App " + (char) ('A' + i);
            pm.installPackage(pi);
            android.content.ComponentName cn = new android.content.ComponentName(pkg, pkg + ".Main");
            android.content.pm.ActivityInfo ai = new android.content.pm.ActivityInfo();
            ai.packageName = pkg;
            ai.name = cn.getClassName();
            ai.exported = true;
            ai.enabled = true;
            ai.applicationInfo = pi.applicationInfo;
            ai.nonLocalizedLabel = "App " + (char) ('A' + i);
            pm.addOrUpdateActivity(ai);
            android.content.IntentFilter f = new android.content.IntentFilter(android.content.Intent.ACTION_MAIN);
            f.addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);
            f.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            pm.addIntentFilterForActivity(cn, f);
        }
        DenTheme.ensure(ctx);
        DenSettings.prefs(ctx).edit().putBoolean(DenSettings.WAKE, false).apply();
        // layout, icon style, look
        String[][] runs = {{"row", "score", "chase-master"}, {"formation", "patch", "wonderkid"}, {"field", "score", "master-blaster"},
                {"arc", "wanted", "straw-hat"}, {"shelf", "manga", "hana-hana"}, {"carousel", "pin", "king-kohli"}, {"row", "sticker", "navigator"},
                {"arc", "dball", "super-saiyan"}, {"carousel", "glass", "domain"}};
        for (String[] run : runs) {
            DenLooks.apply(ctx, DenLooks.byId(DenLooks.load(ctx), run[2]));
            DenSettings.prefs(ctx).edit().putString(DenLooks.OVERRIDE + run[2], new org.json.JSONObject().put("layout", run[0]).put("icons", run[1]).put("watcher", "none").toString()).apply();
            for (int target : new int[]{0, 7}) for (boolean anim : new boolean[]{false, true}) {
                // Still frames: no tile entrance with animations off (Robolectric doesn't run it to the end); with them
                // on the tiles zoom on focus, and the glass must still match.
                ctx.getSharedPreferences(com.gathin.tvhome.Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().putBoolean("animations", anim).apply();
                org.robolectric.android.controller.ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
                MainActivity a = ctl.get();
                View root = a.getWindow().getDecorView();
                androidx.recyclerview.widget.RecyclerView grid = a.findViewById(R.id.grid);
                grid.setLayoutAnimation(null);   // the tiles' entrance: Robolectric never runs it to the end
                for (int k = 0; k < 4; k++) { layout(root, 1920, 1080); org.robolectric.shadows.ShadowLooper.idleMainLooper(); }
                for (int c = 0; c < grid.getChildCount(); c++) grid.getChildAt(c).clearAnimation();
                // Home starts on the app it remembers; step from there to the target, a tile at a time, as the remote does.
                View start = grid.getChildAt(0).findViewById(R.id.card);
                start.requestFocusFromTouch();   // a TV is never in touch mode; the test window starts in it
                for (int k = 0; k < 3; k++) { org.robolectric.shadows.ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS); layout(root, 1920, 1080); }
                int at = grid.getChildAdapterPosition((View) start.getParent());
                while (at != target) {
                    at += at < target ? 1 : -1;
                    androidx.recyclerview.widget.RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(at);
                    if (vh == null) { grid.scrollToPosition(at); layout(root, 1920, 1080); vh = grid.findViewHolderForAdapterPosition(at); }
                    if (vh != null) vh.itemView.findViewById(R.id.card).requestFocus();
                    for (int k = 0; k < 4; k++) { org.robolectric.shadows.ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS); layout(root, 1920, 1080); }
                }
                Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
                root.draw(new Canvas(b));
                View f = root.findFocus();
                System.out.println("run " + run[0] + "/" + run[1] + " target " + target + (anim ? " anim" : "") + ": focus "
                        + (f != null && f.getId() == R.id.card ? grid.getChildAdapterPosition((View) f.getParent()) : f == null ? "none" : f.getClass().getSimpleName()));
                save(b, "row-" + run[0] + "-" + run[1] + "-" + target + (anim ? "-anim" : ""));
                ctl.pause().stop().destroy();
            }
        }
    }

    /** Every opening and back animation half way, over Camp Nou with a tile picked at the bottom: one sheet. */
    @Test public void launches() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        Bitmap wall = BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/01-camp-nou.jpg");
        java.util.List<String[]> all = new java.util.ArrayList<>();
        for (String o : DenLaunch.OPEN) if (!o.equals("zoom") && !o.equals("portal")) all.add(new String[]{o, "open"});
        for (String b : DenLaunch.BACK) all.add(new String[]{b, "back"});
        Bitmap sheet = Bitmap.createBitmap(1920, ((all.size() + 2) / 3) * 360, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        android.graphics.Paint label = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        label.setColor(0xffffffff);
        label.setTextSize(28);
        for (int i = 0; i < all.size(); i++) {
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (wall != null) c.drawBitmap(wall, 0, 0, null);
            DenLaunch.Fx fx = new DenLaunch.Fx(ctx, all.get(i)[0], all.get(i)[1].equals("back"), new android.graphics.RectF(760, 800, 1160, 1025), 0xffedbb00);
            layout(fx, 1920, 1080);
            fx.freeze(0.5f);
            fx.draw(c);
            c.drawText(all.get(i)[1] + ": " + all.get(i)[0], 30, 60, label);
            sc.drawBitmap(Bitmap.createScaledBitmap(b, 640, 360, true), (i % 3) * 640, (i / 3) * 360, null);
        }
        save(sheet, "launches");
    }

    private static void layout(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
    }

    private void save(Bitmap b, String name) throws IOException {
        try (FileOutputStream f = new FileOutputStream(new File(out, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, f); }
    }
}
