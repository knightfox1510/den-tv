package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Matching a TV to cleanyourtv's tested lists (the real snapshot in assets), and the core-package guard. */
public class SpeedupTest {
    private static List<Speedup.TvList> lists() throws Exception {
        return Speedup.parse(new JSONObject(new String(Files.readAllBytes(new File("src/main/assets/cleanyourtv.json").toPath()), "UTF-8")));
    }

    @Test public void theSnapshotHasEveryTv() throws Exception {
        assertTrue(lists().size() >= 300);
    }

    @Test public void aVu4kIsTheVuList() throws Exception {
        // What a Vu 4K reports in Build (brand, manufacturer, model, device, product, board, hardware).
        List<Speedup.TvList> m = Speedup.match(lists(), "VU", "HVCL", "VU 4K", "lushan", "lushan", "lushan", "mt5867");
        assertEquals(1, m.size());
        assertEquals("vu-4k-lushan", m.get(0).folder);
    }

    @Test public void aChromecastIsMatchedByItsDeviceName() throws Exception {
        List<Speedup.TvList> m = Speedup.match(lists(), "google", "Google", "Chromecast", "sabrina", "sabrina", "sabrina", "sabrina");
        assertEquals(1, m.size());
        assertEquals("google-chromecast-gtv-4k", m.get(0).folder);
    }

    @Test public void theBrandHasToMatch() throws Exception {
        assertTrue(Speedup.match(lists(), "Acme", "Acme", "A1", "lushan").isEmpty());
    }

    @Test public void anUnknownTvGetsNothing() throws Exception {
        assertTrue(Speedup.match(lists(), "Sony", "Sony", "BRAVIA 2099", "nosuchdevice").isEmpty());
    }

    @Test public void coreAndroidIsNeverOnTheTable() {
        assertTrue(Speedup.CORE.matcher("com.android.systemui").matches());
        assertTrue(Speedup.CORE.matcher("com.google.android.permissioncontroller").matches());
        assertTrue(Speedup.CORE.matcher("com.mediatek.tv.agent").matches());
        assertTrue(Speedup.CORE.matcher("com.android.providers.media").matches());
        assertTrue(Speedup.CORE_OK.matcher("com.android.providers.calendar").matches());
        assertFalse(Speedup.CORE.matcher("com.vt.storemode").matches());
    }
}
