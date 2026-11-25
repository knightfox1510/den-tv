package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.view.KeyEvent;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Your remote's buttons: learning one, what it may open, and which keys are never taken. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RemoteKeysTest {
    private final Application app = RuntimeEnvironment.getApplication();

    @Before public void clean() { app.getSharedPreferences("remote_keys", Context.MODE_PRIVATE).edit().clear().commit(); }

    private static KeyEvent key(int action, int code, int scan, int repeat) {
        return new KeyEvent(0, 0, action, code, repeat, 0, 0, scan);
    }

    @Test public void buttonsHaveNames() {
        // On a TV, "Button 3" and "Prog red"; where Android has no name for the code, its number.
        assertTrue(RemoteKeys.name(KeyEvent.KEYCODE_BUTTON_3, 0).matches("Button (3|190)"));
        assertTrue(RemoteKeys.name(KeyEvent.KEYCODE_PROG_RED, 0).matches("Prog red|Button 183"));
        assertEquals("Button 490", RemoteKeys.name(KeyEvent.KEYCODE_UNKNOWN, 490));
    }

    @Test public void theKeysThatRunTheTvAreNeverTaken() throws Exception {
        for (int k : new int[]{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_POWER}) {
            assertTrue(RemoteKeys.protectedKey(k));
            assertFalse(RemoteKeys.save(app, k, 0, "", "home"));
            assertFalse(RemoteKeys.onKey(app, key(KeyEvent.ACTION_DOWN, k, 0, 0)));
        }
        assertFalse(RemoteKeys.protectedKey(KeyEvent.KEYCODE_BUTTON_3));
    }

    @Test public void aButtonOpensAnAppOrAnAction() throws Exception {
        assertFalse(RemoteKeys.save(app, KeyEvent.KEYCODE_BUTTON_3, 0, "", "com.not.installed"));
        assertFalse(RemoteKeys.save(app, KeyEvent.KEYCODE_BUTTON_3, 0, "", "rm -rf"));
        assertTrue(RemoteKeys.save(app, KeyEvent.KEYCODE_BUTTON_3, 0, "Netflix button", "settings"));
        assertTrue(RemoteKeys.wanted(app));
        // Saving the same key again replaces it.
        assertTrue(RemoteKeys.save(app, KeyEvent.KEYCODE_BUTTON_3, 0, "", "mute"));
        JSONObject s = RemoteKeys.status(app);
        assertEquals(1, s.getJSONArray("buttons").length());
        assertEquals(RemoteKeys.name(KeyEvent.KEYCODE_BUTTON_3, 0), s.getJSONArray("buttons").getJSONObject(0).getString("label"));
        assertEquals("Mute", s.getJSONArray("buttons").getJSONObject(0).getString("what"));
        // Its press, repeats and release are all swallowed; other keys pass.
        assertTrue(RemoteKeys.onKey(app, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_3, 0, 0)));
        assertTrue(RemoteKeys.onKey(app, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_3, 0, 1)));
        assertTrue(RemoteKeys.onKey(app, key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_3, 0, 0)));
        assertFalse(RemoteKeys.onKey(app, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_4, 0, 0)));
        RemoteKeys.remove(app, s.getJSONArray("buttons").getJSONObject(0).getString("id"));
        assertFalse(RemoteKeys.wanted(app));
    }

    @Test public void anInstalledAppCanBeTheTarget() throws Exception {
        PackageInfo pi = new PackageInfo();
        pi.packageName = "com.netflix.ninja";
        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.packageName = pi.packageName;
        org.robolectric.Shadows.shadowOf(app.getPackageManager()).installPackage(pi);
        android.content.Intent main = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(pi.packageName);
        org.robolectric.Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(main, resolve(pi.packageName));
        assertTrue(RemoteKeys.save(app, KeyEvent.KEYCODE_UNKNOWN, 583, "", pi.packageName));
        // A key Android has no code for is matched by its scancode.
        assertTrue(RemoteKeys.onKey(app, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, 583, 0)));
        assertFalse(RemoteKeys.save(app, KeyEvent.KEYCODE_UNKNOWN, 0, "", pi.packageName));   // no code and no scancode: nothing to match
    }

    private static android.content.pm.ResolveInfo resolve(String pkg) {
        android.content.pm.ResolveInfo ri = new android.content.pm.ResolveInfo();
        ri.activityInfo = new android.content.pm.ActivityInfo();
        ri.activityInfo.packageName = pkg;
        ri.activityInfo.name = pkg + ".Main";
        return ri;
    }

    @Test public void learningHearsTheNextButton() throws Exception {
        RemoteKeys.learn();
        assertEquals("listening", RemoteKeys.learning().getString("state"));
        assertTrue(RemoteKeys.wanted(app));
        assertFalse(RemoteKeys.onKey(app, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0, 0)));   // arrows are never learned
        assertTrue(RemoteKeys.onKey(app, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_RED, 0, 0)));
        assertTrue(RemoteKeys.onKey(app, key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_RED, 0, 0)));
        JSONObject l = RemoteKeys.learning();
        assertEquals("heard", l.getString("state"));
        assertEquals(KeyEvent.KEYCODE_PROG_RED, l.getInt("code"));
        assertEquals(RemoteKeys.name(KeyEvent.KEYCODE_PROG_RED, 0), l.getString("name"));
    }
}
