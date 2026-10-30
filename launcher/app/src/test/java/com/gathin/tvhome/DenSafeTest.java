package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Safe mode: two crashes within ten minutes, and picking a look tries again. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34)
public class DenSafeTest {
    private final Application app = RuntimeEnvironment.getApplication();

    @Test public void oneCrashIsNotEnough() {
        DenSafe.crashed(app, 1_000_000L);
        assertFalse(DenSafe.on(app));
        DenSafe.crashed(app, 1_000_000L + 11 * 60_000L);   // the next a while later
        assertFalse(DenSafe.on(app));
    }

    @Test public void twoInARowStartSafe() {
        DenSafe.crashed(app, 1_000_000L);
        DenSafe.crashed(app, 1_000_000L + 30_000L);
        assertTrue(DenSafe.on(app));
        assertNull("no look in safe mode", DenLooks.current(app, DenLooks.load(app)));
        DenLooks.apply(app, null);   // picking a look (here: rotate) tries again
        assertFalse(DenSafe.on(app));
    }
}
