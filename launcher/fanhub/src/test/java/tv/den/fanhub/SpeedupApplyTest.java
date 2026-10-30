package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What a tested list turns into on a TV (the safety checks), and switching it off and back on. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SpeedupApplyTest {
    private final Application app = RuntimeEnvironment.getApplication();

    private void install(String pkg, String... permissions) {
        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.packageName = pkg;
        pi.applicationInfo.enabled = true;
        pi.applicationInfo.nonLocalizedLabel = pkg;
        pi.requestedPermissions = permissions.length == 0 ? null : permissions;
        shadowOf(app.getPackageManager()).installPackage(pi);
    }

    private void tile(String pkg) {
        ComponentName c = new ComponentName(pkg, pkg + ".Main");
        shadowOf(app.getPackageManager()).addActivityIfNotPresent(c);
        IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
        f.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        shadowOf(app.getPackageManager()).addIntentFilterForActivity(c, f);
    }

    private Speedup.TvList list(String... entries) {
        Speedup.TvList l = new Speedup.TvList("test-tv", "Test TV", "Test");
        for (int i = 0; i < entries.length; i += 2) l.entries.add(new String[]{entries[i], entries[i + 1]});
        return l;
    }

    private static Speedup.Item find(List<Speedup.Item> items, String pkg) {
        for (Speedup.Item i : items) if (i.pkg.equals(pkg)) return i;
        return null;
    }

    @Test public void theSafetyChecksShapeTheList() {
        install("com.vendor.ads");
        install("com.vendor.player");
        tile("com.vendor.player");
        install("com.vendor.hdmi", "android.permission.TV_INPUT_HARDWARE");
        install("com.vendor.demo");
        install("com.android.systemui");
        List<Speedup.Item> plan = Speedup.plan(app, list(
                "com.vendor.ads", "ad service",
                "com.vendor.player", "media player",
                "com.vendor.hdmi", "inputs",
                "com.vendor.demo", "off by choice",
                "com.android.systemui", "never",
                "com.google.android.apps.tv.launcherx", "stock home",
                "com.not.installed", "missing"));
        assertTrue(find(plan, "com.vendor.ads").on);
        assertFalse("an app with a tile starts unticked", find(plan, "com.vendor.player").on);
        assertFalse("an owner's choice starts unticked", find(plan, "com.vendor.demo").on);
        assertEquals("the inputs' software is never offered", null, find(plan, "com.vendor.hdmi"));
        assertEquals(null, find(plan, "com.android.systemui"));
        assertEquals("home screens are HomeSetup's", null, find(plan, "com.google.android.apps.tv.launcherx"));
        assertEquals(null, find(plan, "com.not.installed"));
    }

    @Test public void offAndBackOn() throws Exception {
        PretendTv tv = new PretendTv();
        assertEquals(null, HomeSetup.makeHome(tv, line -> { }));
        tv.packages.add("com.vendor.ads");
        tv.packages.add("com.vendor.player");
        List<Speedup.Item> items = new ArrayList<>();
        items.add(new Speedup.Item("com.vendor.ads", "Ads", "", false, true));
        items.add(new Speedup.Item("com.vendor.player", "Player", "", true, false));
        List<String> off = Speedup.apply(app, tv, items, line -> { });
        assertEquals(List.of("com.vendor.ads"), off);
        assertTrue(tv.disabled.contains("com.vendor.ads"));
        assertFalse("unticked stays on", tv.disabled.contains("com.vendor.player"));
        assertTrue(Speedup.switchedOff(app).contains("com.vendor.ads"));
        Speedup.restore(app, tv, line -> { });
        assertFalse(tv.disabled.contains("com.vendor.ads"));
        assertTrue(Speedup.switchedOff(app).isEmpty());
    }

    @Test public void ifHomeBreaksEverythingComesBack() throws Exception {
        PretendTv tv = new PretendTv();   // Home is still Google's: the check after switching off fails
        tv.packages.add("com.vendor.ads");
        List<Speedup.Item> items = new ArrayList<>();
        items.add(new Speedup.Item("com.vendor.ads", "Ads", "", false, true));
        assertTrue(Speedup.apply(app, tv, items, line -> { }).isEmpty());
        assertFalse(tv.disabled.contains("com.vendor.ads"));
    }
}
