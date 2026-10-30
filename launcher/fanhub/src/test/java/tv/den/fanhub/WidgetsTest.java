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

    // ---- the newer kinds, on real answers (trimmed) from their sources ---------------------------------------------

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long FRI_1130_IST = ZonedDateTime.of(2026, 10, 30, 11, 30, 0, 0, IST).toInstant().toEpochMilli();

    private JSONObject res(String name) throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream("/widgets/" + name)) {
            return new JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test public void newKindsAreCheckedToo() throws Exception {
        assertEquals("[\"BTC\",\"ETH\"]", Widgets.clean(new JSONObject("{\"kind\":\"crypto\",\"coins\":[\"btc\",\"eth\",\"$$\"]}")).getJSONArray("coins").toString());
        assertEquals("INR", Widgets.clean(new JSONObject("{\"kind\":\"crypto\",\"coins\":[\"btc\"],\"currency\":\"XYZ\"}")).getString("currency"));
        assertNotNull(Widgets.clean(new JSONObject("{\"kind\":\"fixtures\",\"sport\":\"soccer\",\"league\":\"esp.1\",\"team\":\"83\",\"name\":\"Barcelona\"}")));
        assertNotNull(Widgets.clean(new JSONObject("{\"kind\":\"fixtures\",\"sport\":\"cricket\",\"team\":\"6\",\"name\":\"India\"}")));
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"fixtures\",\"sport\":\"soccer\",\"league\":\"../x\",\"team\":\"83\",\"name\":\"B\"}")));
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"fixtures\",\"sport\":\"curling\",\"team\":\"1\",\"name\":\"B\"}")));
        JSONObject hol = Widgets.clean(new JSONObject("{\"kind\":\"calendar\",\"country\":\"in\"}"));
        assertEquals("India holidays", hol.getString("label"));
        assertTrue(hol.getString("url").contains("en.indian%23holiday"));
        assertEquals("https://example.com/a.ics", Widgets.clean(new JSONObject("{\"kind\":\"calendar\",\"url\":\"webcal://example.com/a.ics\"}")).getString("url"));
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"calendar\",\"url\":\"http://example.com/a.ics\"}")));
        assertNull(Widgets.clean(new JSONObject("{\"kind\":\"air\",\"place\":\"Delhi\"}")));
        assertNotNull(Widgets.clean(new JSONObject("{\"kind\":\"f1\"}")));
    }

    @Test public void aTeamsFixturesAndLastResult() throws Exception {
        JSONArray f = Widgets.schedule(res("espn-past.json"), res("espn-next.json"), "83", FRI_1130_IST, IST);
        // Barça v Alavés, Sat 31 Oct 20:00 UTC: early Sunday in India.
        assertEquals("Sun 1 Nov, 1:30 AM  ·  Barça v Alavés", f.getString(0));
        assertEquals(4, f.length());   // three to come and the last result
        assertEquals("Last: Barça 1–2 Real Madrid", f.getString(3));   // the latest, though ESPN lists newest first
    }

    @Test public void aLiveMatchShowsItsScore() throws Exception {
        JSONObject live = new JSONObject("{\"events\":[{\"date\":\"2026-10-30T06:00Z\",\"competitions\":[{\"status\":{\"type\":{\"state\":\"in\",\"shortDetail\":\"34'\"}},"
                + "\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"shortDisplayName\":\"Barcelona\"},\"score\":\"1\"},{\"homeAway\":\"away\",\"team\":{\"shortDisplayName\":\"Alavés\"},\"score\":\"0\"}]}]}]}");
        assertEquals("LIVE  Barça 1–0 Alavés  ·  34'", Widgets.schedule(null, live, "83", FRI_1130_IST, IST).getString(0));
    }

    @Test public void cricketOnNowAndToday() throws Exception {
        JSONArray c = Widgets.cricket(res("espn-cricket.json"), "India", FRI_1130_IST, IST);
        assertEquals(2, c.length());   // not India Women or India A
        assertEquals("Today 11:45 AM  ·  India v Pakistan (Group C)", c.getString(0));
        assertEquals("Today 12:30 PM  ·  New Zealand v India (4th T20I)", c.getString(1));
        JSONObject live = new JSONObject("{\"sports\":[{\"leagues\":[{\"events\":[{\"status\":\"in\",\"competitors\":[{\"displayName\":\"New Zealand\",\"score\":\"\"},{\"displayName\":\"India\",\"score\":\"145/3 (16.2/20 ov)\"}]}]}]}]}");
        assertEquals("LIVE  New Zealand  ·  India 145/3 (16.2/20 ov)", Widgets.cricket(live, "india", FRI_1130_IST, IST).getString(0));
    }

    @Test public void theNextGrandPrix() throws Exception {
        assertTrue(Widgets.f1(res("f1.json"), IST).getString(0).startsWith("Mexico City GP  ·  "));
    }

    @Test public void airAndHistoryRead() throws Exception {
        assertEquals("Delhi air 176, unhealthy  ·  PM2.5 98", Widgets.airLine("Delhi", 176, 98.2));
        assertEquals("Pune air 42, good", Widgets.airLine("Pune", 42, Double.NaN));
        JSONArray h = Widgets.parseOnThisDay(new JSONObject("{\"selected\":[{\"year\":2020,\"text\":\"A long one about an earthquake in the Aegean Sea between Greece and Turkey.\"},{\"year\":1961,\"text\":\"Tsar Bomba is tested.\"}]}"));
        assertEquals("1961: Tsar Bomba is tested.", h.getString(0));
    }

    @Test public void calendarsRepeatAndReadNow() throws Exception {
        String ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART;VALUE=DATE:20261108\r\nSUMMARY:Diwali\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nDTSTART;VALUE=DATE:19900131\r\nRRULE:FREQ=YEARLY\r\nSUMMARY:Mum's birthday\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nDTSTART:20261031T043000Z\r\nSUMMARY:Dentist\\, Dr\r\n  Rao\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nDTSTART;TZID=Asia/Kolkata:20260101T190000\r\nRRULE:FREQ=WEEKLY;UNTIL=20260301T000000Z\r\nSUMMARY:Over\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nDTSTART;VALUE=DATE:20250101\r\nSUMMARY:Long gone\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        JSONArray ev = Widgets.events(ics, FRI_1130_IST, IST);
        assertEquals(2, ev.length());   // the birthday is in January, past the next 60 days
        List<String> lines = Widgets.upcoming(ev, FRI_1130_IST, IST);
        assertEquals("Dentist, Dr Rao  ·  Tomorrow 10:00 AM", lines.get(0));
        assertEquals("Diwali  ·  Sun 8 Nov", lines.get(1));
        long dec20 = ZonedDateTime.of(2026, 12, 20, 9, 0, 0, 0, IST).toInstant().toEpochMilli();
        assertEquals("Mum's birthday  ·  Sun 31 Jan", Widgets.upcoming(Widgets.events(ics, dec20, IST), dec20, IST).get(0));
    }

    @Test public void yearlyCountdownsAndPrivateCalendars() throws Exception {
        Widgets.prefs(app).edit().clear().commit();
        java.time.LocalDate today = java.time.LocalDate.now(ZoneId.systemDefault());
        String lastYearsIn5 = today.plusDays(5).minusYears(3).toString();
        Widgets.add(app, new JSONObject("{\"kind\":\"countdown\",\"label\":\"Riya's birthday\",\"yearly\":true,\"date\":\"" + lastYearsIn5 + "\"}"));
        Widgets.add(app, new JSONObject("{\"kind\":\"calendar\",\"label\":\"Family\",\"url\":\"https://calendar.google.com/calendar/ical/secret/basic.ics\"}"));
        List<String[]> lines = Widgets.lines(app, "home", System.currentTimeMillis());
        assertEquals("COMING UP", lines.get(0)[0]);
        assertEquals("Riya's birthday in 5 days", lines.get(0)[1]);
        // The phone never gets a calendar's secret address back.
        assertTrue(!Widgets.status(app).toString().contains("secret"));
    }
}
