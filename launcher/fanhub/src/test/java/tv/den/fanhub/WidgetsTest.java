package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Your widgets: what the phone may set up, how prices and headlines read, and the lines Home gets. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WidgetsTest {
    private final Application app = RuntimeEnvironment.getApplication();

    @Test public void onlyWellFormedWidgets() throws Exception {
        assertNotNull(Widgets.clean(new JSONObject("{\"kind\":\"markets\",\"symbols\":[\"^BSESN\",\"reliance.ns\",\"bad symbol!\"]}")));
        assertEquals(2, Widgets.clean(new JSONObject("{\"kind\":\"markets\",\"symbols\":[\"^BSESN\",\"reliance.ns\",\"bad symbol!\"]}")).getJSONArray("symbols").length());
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"markets\",\"symbols\":[]}")));
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"weather\",\"place\":\"Mumbai\"}")));   // no position
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"clocks\",\"zones\":[{\"label\":\"Nowhere\",\"tz\":\"Mars/Base\"}]}")));
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"countdown\",\"label\":\"Trip\",\"date\":\"next week\"}")));
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"script\",\"text\":\"x\"}")));
        assertEquals("both", Widgets.clean(new JSONObject("{\"kind\":\"note\",\"text\":\"Hi\",\"where\":\"everywhere\"}")).getString("where"));
    }

    @Test public void pricesReadLikeATicker() throws Exception {
        JSONObject sensex = new JSONObject("{\"symbol\":\"^BSESN\",\"shortName\":\"S&P BSE SENSEX\",\"currency\":\"INR\",\"regularMarketPrice\":74313.64,\"chartPreviousClose\":74151.04}");
        assertEquals("SENSEX 74,314 ▲0.22%", Widgets.quote("^BSESN", sensex));
        JSONObject rel = new JSONObject("{\"symbol\":\"RELIANCE.NS\",\"shortName\":\"RELIANCE INDUSTRIES LTD\",\"currency\":\"INR\",\"regularMarketPrice\":1162.5,\"chartPreviousClose\":1167.4}");
        assertEquals("RELIANCE 1,162.50 ▼0.42%", Widgets.quote("RELIANCE.NS", rel));
        JSONObject big = new JSONObject("{\"currency\":\"INR\",\"regularMarketPrice\":123456,\"chartPreviousClose\":123456}");
        assertTrue(Widgets.quote("X.NS", big).contains("1,23,456"));   // Indian grouping for rupees
    }

    @Test public void headlinesFromRss() {
        String rss = "<rss><channel><title>\"x\" - Google News</title><item><title>Sensex ends 300 points higher &amp; Nifty at record - The Hindu</title></item>"
                + "<item><title><![CDATA[Gold rises]]></title></item></channel></rss>";
        JSONArray h = Widgets.parseHeadlines(rss);
        assertEquals(2, h.length());
        assertEquals("Sensex ends 300 points higher & Nifty at record - The Hindu", h.optString(0));
        assertEquals("Gold rises", h.optString(1));
    }

    @Test public void clocksAndCountdownsAreWorkedOutNow() throws Exception {
        Widgets.prefs(app).edit().clear().commit();
        Widgets.add(app, new JSONObject("{\"kind\":\"clocks\",\"where\":\"home\",\"zones\":[{\"label\":\"London\",\"tz\":\"Europe/London\"}]}"));
        String in3 = java.time.LocalDate.now(ZoneId.systemDefault()).plusDays(3).toString();
        Widgets.add(app, new JSONObject("{\"kind\":\"countdown\",\"where\":\"hub\",\"label\":\"Goa trip\",\"date\":\"" + in3 + "\"}"));
        long noon = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, ZoneId.of("Europe/London")).toInstant().toEpochMilli();
        List<String[]> home = Widgets.lines(app, "home", noon);
        assertEquals(1, home.size());
        assertEquals("WORLD CLOCK", home.get(0)[0]);
        assertEquals("London 12:00 PM", home.get(0)[1]);
        List<String[]> hub = Widgets.lines(app, "hub", System.currentTimeMillis());
        assertEquals("Goa trip in 3 days", hub.get(0)[1]);
        JSONObject s = Widgets.status(app);
        assertEquals(2, s.getJSONArray("widgets").length());
        Widgets.remove(app, s.getJSONArray("widgets").getJSONObject(0).getString("id"));
        assertEquals(1, Widgets.list(app).length());
    }
}
