package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Voice: the phone's search lands somewhere, and the Voice check finds what the remote's voice button is missing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VoiceTest {
    private final Application app = RuntimeEnvironment.getApplication();

    private void install(String pkg, String label, boolean mic) {
        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.packageName = pkg;
        pi.applicationInfo.nonLocalizedLabel = label;
        pi.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        pi.applicationInfo.enabled = true;
        pi.requestedPermissions = mic ? new String[]{android.Manifest.permission.RECORD_AUDIO} : null;
        shadowOf(app.getPackageManager()).installPackage(pi);
    }

    private static ResolveInfo handler(String pkg, String label) {
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.packageName = pkg;
        ri.activityInfo.name = pkg + ".Search";
        ri.nonLocalizedLabel = label;
        return ri;
    }

    @Test public void aMakersVoiceServiceSwitchedOffIsFound() throws Exception {
        install("com.vt.remote.voice", "Remote voice", true);
        install("com.vt.other", "Other", false);
        app.getPackageManager().setApplicationEnabledSetting("com.vt.remote.voice", PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
        app.getPackageManager().setApplicationEnabledSetting("com.vt.other", PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
        JSONObject v = Voice.check(app);
        JSONArray off = v.getJSONArray("micAppsOff");
        assertEquals(1, off.length());   // only the one that uses the microphone
        assertEquals("com.vt.remote.voice", off.getJSONObject(0).getString("pkg"));
        assertFalse(v.getJSONObject("google").getBoolean("installed"));
    }

    @Test public void theGoogleAppsStateIsReported() throws Exception {
        install(Voice.GOOGLE, "Google", true);
        app.getPackageManager().setApplicationEnabledSetting(Voice.GOOGLE, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
        JSONObject g = Voice.check(app).getJSONObject("google");
        assertTrue(g.getBoolean("installed"));
        assertFalse(g.getBoolean("on"));
        assertTrue(g.has("mic"));   // granted or not, as the TV says (Robolectric grants everything asked for)
    }

    @Test public void searchTriesTheTvsSearchThenGivesUp() throws Exception {
        assertNull(Voice.search(app, "one piece", "tv"));
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent("android.search.action.GLOBAL_SEARCH"), handler(Voice.GOOGLE, "Google"));
        assertEquals("Google", Voice.search(app, "one piece", "tv"));
        Intent started = shadowOf(app).getNextStartedActivity();
        assertEquals("one piece", started.getStringExtra(android.app.SearchManager.QUERY));
        assertNull(Voice.search(app, "lofi", "youtube"));   // no YouTube on this TV
    }
}
