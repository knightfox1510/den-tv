package tv.den.fanhub;

import static org.junit.Assume.assumeFalse;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Renders the setup's screens to PNGs (build/setup-screens/); -Pden.screens=1 --tests '*SetupScreensTest'. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-xhdpi")
public class SetupScreensTest {
    @Test public void screens() throws Exception {
        assumeFalse(System.getProperty("den.screens", "").isEmpty());
        File out = new File("build/setup-screens");
        out.mkdirs();
        SetupActivity a = Robolectric.buildActivity(SetupActivity.class).setup().get();
        Field step = SetupActivity.class.getDeclaredField("step");
        step.setAccessible(true);
        Method show = SetupActivity.class.getDeclaredMethod("show");
        show.setAccessible(true);
        String[] names = {"welcome", "wifi", "pair", "phone", "done"};
        for (int i = 0; i < names.length; i++) {
            step.setInt(a, i);
            show.invoke(a);
            View root = a.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1920, 1080);
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            try (FileOutputStream f = new FileOutputStream(new File(out, names[i] + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, f); }
        }
    }

    /** The home screen setup (HomeSetupActivity), each step; -Pden.screens=1 --tests '*SetupScreensTest.homeSetup'. */
    @Test public void homeSetup() throws Exception {
        assumeFalse(System.getProperty("den.screens", "").isEmpty());
        File out = new File("build/setup-screens");
        out.mkdirs();
        HomeSetupActivity a = Robolectric.buildActivity(HomeSetupActivity.class).setup().get();
        java.util.List<String> lines = java.util.Arrays.asList("Starting den-tv", "Switching off the TV's own home screen",
                "den-tv is the home screen", "den-tv helper on", "den-tv screensaver on", "USB debugging off");
        for (String step : new String[]{"INTRO", "ASK_HELPER", "DEBUG", "ALLOW", "WORKING", "DONE", "IS_HOME", "NO_LAUNCHER"}) {
            a.showForTest(step, step.equals("WORKING") ? lines.subList(0, 3) : lines);
            View root = a.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1920, 1080);
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            try (FileOutputStream f = new FileOutputStream(new File(out, "home-" + step.toLowerCase() + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, f); }
        }
    }

    /** The Fan Hub dashboard with sample data, at Thursday 29 October 11:13 in Mumbai, over Camp Nou Nights. */
    @Test public void hub() throws Exception {
        assumeFalse(System.getProperty("den.screens", "").isEmpty());
        File out = new File("build/setup-screens");
        out.mkdirs();
        java.time.ZoneId z = java.time.ZoneId.of("Asia/Kolkata");
        long now = java.time.ZonedDateTime.of(2026, 10, 29, 11, 13, 0, 0, z).toInstant().toEpochMilli(), h = 3_600_000L;
        for (int k = 0; k < 3; k++) {
            HubView.Data d = new HubView.Data();
            d.lookName = "Camp Nou Nights";
            d.accent = 0xffedbb00;
            d.wallpaper = android.graphics.BitmapFactory.decodeFile("../../themes/den-mix/wallpapers/01-camp-nou.jpg");
            d.place = "Rajmachi";
            d.fetched = now - 4 * 60_000L;
            d.address = "http://192.168.1.23:8765";
            d.code = "4821";
            d.paired = k == 2 ? 0 : 1;
            d.qr = Qr.of(org.robolectric.RuntimeEnvironment.getApplication().getResources(), d.address).getBitmap();
            if (k == 1) d.teams.add(new HubView.Item(new FanHub.Turn("BAR 2\u20131 RMA \u00B7 67'", "Kick-off", now - h, now + h, 0xffa50044, true), "football", "FC Barcelona"));
            d.teams.add(new HubView.Item(new FanHub.Turn("INDIA V AUSTRALIA \u00B7 T20I", "First ball", now + 2 * h + 18 * 60_000L + 35_000L, now + 6 * h, 0xff1f5fd1, false), "cricket", "India"));
            d.teams.add(new HubView.Item(new FanHub.Turn("BAR\u00C7A V SEVILLA \u00B7 LA LIGA", "Kick-off", now + 57 * h, now + 59 * h, 0xffa50044, false), "football", "FC Barcelona"));
            d.teams.add(new HubView.Item(new FanHub.Turn("MUMBAI INDIANS V CHENNAI SUPER KINGS \u00B7 IPL", "First ball", now + 130 * h, now + 134 * h, 0xff004ba0, false), "cricket", "Mumbai Indians"));
            d.shows.add(new HubView.Item(new FanHub.Turn("JUJUTSU KAISEN \u00B7 EPISODE 14", "New episode", now + 10 * h + 17 * 60_000L, now + 11 * h, 0xffff7a00, false), "show", "Jujutsu Kaisen"));
            d.shows.add(new HubView.Item(new FanHub.Turn("DEMON SLAYER \u00B7 EPISODE 9", "New episode", now + 34 * h, now + 35 * h, 0xffff7a00, false), "show", "Demon Slayer"));
            d.shows.add(new HubView.Item(new FanHub.Turn("ONE PIECE \u00B7 EPISODE 1149", "New episode", now + 72 * h + 30 * 60_000L, now + 74 * h, 0xffff7a00, false), "show", "One Piece"));
            d.weekend = FanHub.weekend(new org.json.JSONObject("{\"utc_offset_seconds\":19800,\"daily\":{\"time\":[\"2026-10-29\",\"2026-10-30\",\"2026-10-31\",\"2026-11-01\"],"
                    + "\"temperature_2m_max\":[30,30,29,27],\"temperature_2m_min\":[21,21,20,18],\"precipitation_probability_max\":[10,20,60,5],"
                    + "\"weather_code\":[1,2,80,0],\"sunrise\":[\"2026-10-29T06:36\",\"2026-10-30T06:37\",\"2026-10-31T06:37\",\"2026-11-01T06:38\"]}}"), now);
            d.extras = new org.json.JSONObject("{\"India\":{\"form\":\"WWLWD\"},\"FC Barcelona\":{\"form\":\"WDWWL\",\"table\":{\"league\":\"La Liga\",\"rows\":["
                    + "{\"pos\":1,\"name\":\"Real Madrid\",\"played\":10,\"gd\":13,\"pts\":24},{\"pos\":2,\"name\":\"Barça\",\"played\":10,\"gd\":16,\"pts\":24,\"ours\":true},"
                    + "{\"pos\":3,\"name\":\"Atleti\",\"played\":10,\"gd\":9,\"pts\":20}]}}}");
            HubView v = new HubView(org.robolectric.RuntimeEnvironment.getApplication());
            v.freeze(now, z);
            v.show(d);
            v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, 1920, 1080);
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(b));
            try (FileOutputStream f = new FileOutputStream(new File(out, "hub-" + new String[]{"next", "live", "pair"}[k] + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, f); }
        }
    }
}
