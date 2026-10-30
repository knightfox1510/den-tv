package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Making den-tv Home from the TV itself: the steps against a pretend TV, and the ADB client against its adbd. */
public class HomeSetupTest {
    private static final HomeSetup.Progress QUIET = line -> { };

    @Test public void makesDenTvHomeOnGoogleTv() throws Exception {
        PretendTv tv = new PretendTv();
        List<String> steps = new ArrayList<>();
        assertNull(HomeSetup.makeHome(tv, steps::add));
        assertEquals(HomeSetup.LAUNCHER, tv.home);
        assertTrue(tv.disabled.contains("com.google.android.apps.tv.launcherx"));
        assertTrue(tv.disabled.contains(HomeSetup.WIZARD));
        // den-tv started before anything was switched off.
        assertTrue(indexOf(tv, "am start -W") < indexOf(tv, "pm disable-user"));
        assertTrue(tv.log.contains("appops set tv.den.fanhub REQUEST_INSTALL_PACKAGES allow"));
        assertTrue(tv.log.contains("pm grant tv.den.fanhub android.permission.WRITE_SECURE_SETTINGS"));
        assertEquals("den-tv is the home screen", steps.get(steps.size() - 1));
    }

    @Test public void aStartThatFailsChangesNothing() throws Exception {
        PretendTv tv = new PretendTv();
        tv.startFails = true;
        assertNotNull(HomeSetup.makeHome(tv, QUIET));
        assertTrue(tv.disabled.isEmpty());
        assertEquals("com.google.android.apps.tv.launcherx", tv.home);
    }

    @Test public void aLockedHomePutsTheTvsOwnBack() throws Exception {
        PretendTv tv = new PretendTv();
        tv.homeLocked = true;
        String why = HomeSetup.makeHome(tv, QUIET);
        assertNotNull(why);
        assertTrue(why, why.contains("put back"));
        assertTrue(tv.disabled.isEmpty());
        assertEquals("com.google.android.apps.tv.launcherx", tv.home);
    }

    @Test public void neverWhileTheWizardOwnsTheWifi() throws Exception {
        PretendTv tv = new PretendTv();
        tv.wifiOwner = HomeSetup.WIZARD + ":10031";
        assertEquals(HomeSetup.WIFI_OWNED, HomeSetup.makeHome(tv, QUIET));
        assertTrue("nothing changed", tv.disabled.isEmpty());
        assertEquals(-1, indexOf(tv, "am start"));
    }

    @Test public void anUnreadableWifiOwnerStopsUnlessOnACable() throws Exception {
        PretendTv tv = new PretendTv();
        tv.wifiOwner = null;
        assertEquals(HomeSetup.WIFI_UNKNOWN, HomeSetup.makeHome(tv, QUIET, false));
        assertTrue(tv.disabled.isEmpty());
        assertNull(HomeSetup.makeHome(tv, QUIET, true));
        assertTrue(tv.disabled.contains(HomeSetup.WIZARD));
    }

    @Test public void aWizardThatDoesntAnswerHomeStaysOn() throws Exception {
        PretendTv tv = new PretendTv();
        tv.wizardClaimsHome = false;
        tv.wifiOwner = HomeSetup.WIZARD + ":10031";   // and it owns the Wi-Fi: no matter, it isn't touched
        assertNull(HomeSetup.makeHome(tv, QUIET));
        assertEquals(HomeSetup.LAUNCHER, tv.home);
        assertFalse(tv.disabled.contains(HomeSetup.WIZARD));
    }

    @Test public void theHelperJoinsOtherServices() throws Exception {
        PretendTv tv = new PretendTv();
        tv.settings.put("secure enabled_accessibility_services", "com.other/.Svc");
        HomeSetup.helperOn(tv);
        HomeSetup.helperOn(tv);
        assertEquals("com.other/.Svc:" + HomeSetup.HELPER, tv.settings.get("secure enabled_accessibility_services"));
        assertEquals("1", tv.settings.get("secure accessibility_enabled"));
    }

    @Test public void givesHomeBack() throws Exception {
        PretendTv tv = new PretendTv();
        assertNull(HomeSetup.makeHome(tv, QUIET));
        HomeSetup.screensaverOn(tv);
        assertNull(HomeSetup.giveBack(tv, QUIET));
        assertEquals("com.google.android.apps.tv.launcherx", tv.home);
        assertTrue(tv.disabled.isEmpty());
        assertFalse(tv.settings.containsKey("secure screensaver_components"));
    }

    // ---- the ADB client ------------------------------------------------------------------------------------------

    private static KeyPair newKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    @Test public void publicKeyIsInAdbdsFormat() throws Exception {
        RSAPublicKey k = (RSAPublicKey) newKey().getPublic();
        byte[] enc = Adb.publicKey(k, "den-tv@fanhub");
        String s = new String(enc, "UTF-8");
        assertTrue(s.endsWith(" den-tv@fanhub\0"));
        ByteBuffer b = ByteBuffer.wrap(Base64.getDecoder().decode(s.substring(0, s.indexOf(' ')))).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(64, b.getInt());
        long n0inv = b.getInt() & 0xffffffffL;
        long n0 = k.getModulus().longValue() & 0xffffffffL;
        assertEquals(0xffffffffL, (n0inv * n0) & 0xffffffffL);   // n0inv = -1 / n[0] mod 2^32
        assertEquals(k.getModulus(), Adb.modulusOf(enc));
        b.position(8 + 64 * 4);
        BigInteger rr = BigInteger.ZERO;
        for (int i = 0; i < 64; i++) rr = rr.or(BigInteger.valueOf(b.getInt() & 0xffffffffL).shiftLeft(32 * i));
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(k.getModulus()), rr);
        assertEquals(65537, b.getInt());
    }

    @Test public void theTvAsksOnceThenKnowsTheKey() throws Exception {
        PretendTv tv = new PretendTv();
        int port = tv.listen();
        KeyPair key = newKey();
        int[] asked = {0};
        try (Adb adb = Adb.connect("127.0.0.1", port, key, () -> asked[0]++, 5000)) {
            assertEquals("package:" + HomeSetup.LAUNCHER + "\n", adb.shell("pm list packages " + HomeSetup.LAUNCHER));
        }
        assertEquals(1, asked[0]);
        try (Adb adb = Adb.connect("127.0.0.1", port, key, () -> asked[0]++, 5000)) {
            assertTrue(adb.shell(HomeSetup.HOME_QUERY).trim().endsWith("launcherx/.Home"));
        }
        assertEquals("a key the TV allowed isn't asked about again", 1, asked[0]);
        assertEquals(1, tv.prompts);
        tv.stop();
    }

    @Test public void denyingIsNotAllowed() throws Exception {
        PretendTv tv = new PretendTv();
        tv.allow = false;
        int port = tv.listen();
        try {
            Adb.connect("127.0.0.1", port, newKey(), null, 5000).close();
            fail("connected without being allowed");
        } catch (Adb.NotAllowed expected) {
            // what the setup screen turns into "choose Allow"
        }
        tv.stop();
    }

    @Test public void theWholeSetupOverAdb() throws Exception {
        PretendTv tv = new PretendTv();
        int port = tv.listen();
        try (Adb adb = Adb.connect("127.0.0.1", port, newKey(), null, 5000)) {
            HomeSetup.Shell sh = adb::shell;
            assertNull(HomeSetup.makeHome(sh, QUIET));
            HomeSetup.helperOn(sh);
            HomeSetup.debuggingOff(sh);
        }
        assertEquals(HomeSetup.LAUNCHER, tv.home);
        assertEquals(HomeSetup.HELPER, tv.settings.get("secure enabled_accessibility_services"));
        assertEquals("0", tv.settings.get("global adb_enabled"));
        tv.stop();
    }

    // ---- updates -------------------------------------------------------------------------------------------------

    @Test public void onlyNewerInstalledAppsUpdate() throws Exception {
        JSONObject release = new JSONObject("{\"tag\":\"v1.0.2\",\"launcher\":{\"code\":25,\"name\":\"den-1.0.2\"},\"fanhub\":{\"code\":13,\"name\":\"1.0.1\"}}");
        List<Updater.App> both = Updater.newer(release, 24, 12);
        assertEquals(2, both.size());
        assertEquals(HomeSetup.LAUNCHER, both.get(0).pkg);   // the launcher goes first
        assertEquals(1, Updater.newer(release, 24, 13).size());
        assertEquals(0, Updater.newer(release, 25, 13).size());
        assertEquals("an app that isn't installed is left alone", 0, Updater.newer(release, -1, 13).size());
    }

    @Test public void releasesWaitThreeDays() throws Exception {
        long published = 1_760_000_000L;
        JSONObject release = new JSONObject().put("tag", "v1.2.0").put("date", published);
        assertFalse(Updater.due(release, published * 1000L + 3_600_000L));
        assertFalse(Updater.due(release, published * 1000L + Updater.DELAY - 1));
        assertTrue(Updater.due(release, published * 1000L + Updater.DELAY));
        assertTrue("a release with no date (older ones) is due", Updater.due(new JSONObject().put("tag", "v1.1.0"), 0));
    }

    private static int indexOf(PretendTv tv, String prefix) {
        for (int i = 0; i < tv.log.size(); i++) if (tv.log.get(i).startsWith(prefix)) return i;
        return -1;
    }
}
