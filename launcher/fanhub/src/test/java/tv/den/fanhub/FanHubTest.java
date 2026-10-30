package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FanHubTest {
    private static final FanHub.Team BARCA = new FanHub.Team("FC Barcelona", "football", "133739", 0xffa50044);
    private static final FanHub.Team INDIA = new FanHub.Team("India", "cricket", "137143", 0xff1f5fd1);
    private static final long KICKOFF = 1_760_000_000_000L - 1_760_000_000_000L % 60_000L;

    private static JSONObject events(String event) throws Exception {
        return new JSONObject("{\"events\":[" + event + "]}");
    }

    private static String ts(long ms) {
        return java.time.Instant.ofEpochMilli(ms).toString().replace("Z", "");
    }

    @Test public void upcomingMatchCountsDown() throws Exception {
        JSONObject next = events("{\"strHomeTeam\":\"Barcelona\",\"strAwayTeam\":\"Real Madrid\",\"strLeague\":\"Spanish La Liga\","
                + "\"strTimestamp\":\"" + ts(KICKOFF) + "\",\"strStatus\":\"Not Started\",\"intHomeScore\":null}");
        FanHub.Turn t = FanHub.nextMatch(BARCA, next, KICKOFF - 3_600_000L);
        assertNotNull(t);
        assertEquals("BARÇA V REAL MADRID · LA LIGA", t.topLine);
        assertEquals("Kick-off", t.word);
        assertEquals(KICKOFF, t.start);
        assertEquals(KICKOFF + 2 * 3_600_000L, t.end);
        assertEquals(false, t.live);
    }

    @Test public void liveMatchShowsTheScore() throws Exception {
        JSONObject next = events("{\"strHomeTeam\":\"Barcelona\",\"strAwayTeam\":\"Real Madrid\",\"strLeague\":\"Spanish La Liga\","
                + "\"strTimestamp\":\"" + ts(KICKOFF) + "+00:00\",\"strStatus\":\"2H\",\"intHomeScore\":\"2\",\"intAwayScore\":\"1\",\"strProgress\":\"67\"}");
        FanHub.Turn t = FanHub.nextMatch(BARCA, next, KICKOFF + 70 * 60_000L);
        assertNotNull(t);
        assertTrue(t.live);
        assertEquals("BAR 2–1 RMA · 67'", t.topLine);
    }

    @Test public void finishedAndPastMatchesAreSkipped() throws Exception {
        JSONObject next = events("{\"strHomeTeam\":\"India\",\"strAwayTeam\":\"Australia\",\"strLeague\":\"ODI\",\"dateEvent\":\"2025-10-01\","
                + "\"strTime\":\"08:30:00\",\"strStatus\":\"Match Finished\",\"intHomeScore\":\"287\"}");
        assertNull(FanHub.nextMatch(INDIA, next, KICKOFF));
        assertNull(FanHub.nextMatch(INDIA, new JSONObject("{\"events\":null}"), KICKOFF));
    }

    @Test public void cricketLastsLongerThanFootball() {
        assertEquals(4 * 3_600_000L, FanHub.length("cricket", "Indian Premier League"));
        assertEquals(8 * 3_600_000L, FanHub.length("cricket", "ICC ODI"));
        assertEquals(2 * 3_600_000L, FanHub.length("football", "La Liga"));
    }

    @Test public void kickoffFromDateAndTime() throws Exception {
        long k = FanHub.kickoff(new JSONObject("{\"dateEvent\":\"2026-11-01\",\"strTime\":\"20:00:00\"}"));
        assertEquals(java.time.Instant.parse("2026-11-01T20:00:00Z").toEpochMilli(), k);
        assertEquals(-1, FanHub.kickoff(new JSONObject("{}")));
    }

    @Test public void episodePicksTheShowAiringNow() throws Exception {
        long now = KICKOFF;
        JSONObject r = new JSONObject("{\"data\":{\"Page\":{\"media\":["
                + "{\"id\":1,\"status\":\"FINISHED\",\"startDate\":{\"year\":1999,\"month\":10}},"
                + "{\"id\":2,\"status\":\"RELEASING\",\"startDate\":{\"year\":1999,\"month\":10},\"nextAiringEpisode\":{\"episode\":1150,\"airingAt\":"
                + (now / 1000 + 3600) + "}}]}}}");
        JSONObject m = FanHub.pickMedia(r);
        assertEquals(2, m.optInt("id"));
        FanHub.Turn t = FanHub.nextEpisode("One Piece", m, now);
        assertEquals("ONE PIECE · EPISODE 1150", t.topLine);
        assertEquals("New episode", t.word);
        assertNull(FanHub.nextEpisode("One Piece", new JSONObject("{\"id\":3}"), now));
    }

    @Test public void shortNames() {
        assertEquals("RMA", FanHub.shortName("Real Madrid"));
        assertEquals("MI", FanHub.shortName("Mumbai Indians"));
        assertEquals("AF", FanHub.shortName("AC Fiorentina"));
        assertEquals("GIR", FanHub.shortName("Girona"));
    }

    @Test public void refreshesOftenOnlyAroundAMatch() {
        long now = KICKOFF;
        List<FanHub.Turn> soon = Collections.singletonList(new FanHub.Turn("x", "Kick-off", now + 10 * 60_000L, now + 3 * 3_600_000L, 0, false));
        List<FanHub.Turn> later = Collections.singletonList(new FanHub.Turn("x", "Kick-off", now + 5 * 3_600_000L, now + 7 * 3_600_000L, 0, false));
        assertEquals(2 * 60_000L, FanHub.refreshAfter(soon, now));
        assertEquals(30 * 60_000L, FanHub.refreshAfter(later, now));
    }

    @Test public void flattenDropsEndedAndSorts() {
        long now = KICKOFF;
        Map<String, List<FanHub.Turn>> by = new HashMap<>();
        by.put("a", Collections.singletonList(new FanHub.Turn("late", "", now + 7_200_000L, now + 9_000_000L, 0, false)));
        by.put("b", Collections.singletonList(new FanHub.Turn("soon", "", now + 60_000L, now + 7_000_000L, 0, false)));
        by.put("c", Collections.singletonList(new FanHub.Turn("over", "", now - 9_000_000L, now - 1, 0, false)));
        List<FanHub.Turn> all = FanHub.flatten(by, now);
        assertEquals(2, all.size());
        assertEquals("soon", all.get(0).topLine);
    }

    @Test public void configReadsTheCommandLineFormat() throws Exception {
        FanHub.Config c = FanHub.Config.from(new JSONObject("{\"teams\":[{\"name\":\"India\",\"sport\":\"cricket\",\"sportsdb\":\"137143\"}],"
                + "\"shows\":[\"Naruto\"],\"keys\":{\"sportsdb\":\"\"}}"));
        assertEquals(1, c.teams.size());
        assertEquals("cricket", c.teams.get(0).sport);
        assertEquals(new ArrayList<>(Collections.singletonList("Naruto")), c.shows);
        assertEquals("123", c.sportsdbKey);
        assertEquals(3, FanHub.Config.from(new JSONObject("{}")).teams.size());
    }

    @Test public void longLinesUseShortNames() {
        assertEquals("NEW ZEALAND V INDIA \u00B7 T20I", FanHub.matchLine(FanHub.team("New Zealand Cricket"), FanHub.team("India Cricket"), "Twenty20 International Series"));
        assertEquals("BARÇA V DEPORTIVO ALAVÉS \u00B7 LA LIGA", FanHub.matchLine("Barcelona", "Deportivo Alavés", "Spanish La Liga"));
        assertEquals("MI V CSK \u00B7 IPL", FanHub.matchLine("Mumbai Indians", "Chennai Super Kings", "Indian Premier League"));
    }

    @Test public void quietHoursRunOverMidnight() {
        assertEquals(true, Timers.inside(23 * 60, Timers.minutes("22:30"), Timers.minutes("07:00")));
        assertEquals(true, Timers.inside(6 * 60, Timers.minutes("22:30"), Timers.minutes("07:00")));
        assertEquals(false, Timers.inside(12 * 60, Timers.minutes("22:30"), Timers.minutes("07:00")));
        assertEquals(true, Timers.inside(14 * 60, Timers.minutes("13:00"), Timers.minutes("15:00")));
        assertEquals(-1, Timers.minutes("25:00"));
    }

    @Test public void teamsFindTheirLook() {
        assertEquals("camp-nou", Rules.teamLook("FC Barcelona"));
        assertEquals("wankhede", Rules.teamLook("Mumbai Indians"));
        assertEquals("blue-billion", Rules.teamLook("India"));
        assertNull(Rules.teamLook("Arsenal"));
    }

    @Test public void festivalsByDate() {
        assertEquals("diwali", Rules.festival(java.time.LocalDateTime.of(2026, 11, 8, 20, 0)));
        assertEquals("diwali", Rules.festival(java.time.LocalDateTime.of(2026, 11, 6, 9, 0)));
        assertNull(Rules.festival(java.time.LocalDateTime.of(2026, 11, 11, 9, 0)));
        assertEquals("newyear", Rules.festival(java.time.LocalDateTime.of(2026, 12, 31, 22, 0)));
        assertNull(Rules.festival(java.time.LocalDateTime.of(2026, 12, 31, 12, 0)));
    }

    @Test public void hikePicksTheDrierWeekendDay() throws Exception {
        // Friday 30 October 2026, 18:00 in India.
        long now = java.time.ZonedDateTime.of(2026, 10, 30, 18, 0, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        JSONObject r = new JSONObject("{\"utc_offset_seconds\":19800,\"daily\":{\"time\":[\"2026-10-30\",\"2026-10-31\",\"2026-11-01\"],"
                + "\"temperature_2m_max\":[30,27.6,25],\"precipitation_probability_max\":[5,40,10]}}");
        FanHub.Turn t = FanHub.hikeTurn("Rajmachi", r, now);
        assertNotNull(t);
        assertEquals("HIKE \u00B7 RAJMACHI \u00B7 SUN 25\u00B0 \u00B7 10% RAIN", t.topLine);
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 1, 6, 0, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli(), t.start);
        // Tuesday: no forecast.
        long tue = java.time.ZonedDateTime.of(2026, 11, 3, 18, 0, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        assertNull(FanHub.hikeTurn("Rajmachi", r, tue));
    }

    @Test public void momentsFromOneFetchToTheNext() {
        long now = KICKOFF + 3_600_000L;
        java.util.function.BiFunction<Integer, String, List<FanHub.Turn>> f = (ours, word) ->
                Collections.singletonList(new FanHub.Turn("x", word, KICKOFF, KICKOFF + 7_200_000L, 0, true).with(ours, "BAR " + ours + "\u20131 RMA"));
        assertEquals("goal", Moments.find("team:1", f.apply(1, "Kick-off"), f.apply(2, "Kick-off"), now)[0]);
        assertNull(Moments.find("team:1", f.apply(2, "Kick-off"), f.apply(2, "Kick-off"), now));
        assertNull(Moments.find("team:1", null, f.apply(2, "Kick-off"), now));   // first sight of a live match: no moment
        assertEquals("six", Moments.find("team:2", f.apply(96, "First ball"), f.apply(104, "First ball"), now)[0]);
        assertNull(Moments.find("team:2", f.apply(104, "First ball"), f.apply(131, "First ball"), now));
        List<FanHub.Turn> ep = Collections.singletonList(new FanHub.Turn("ONE PIECE", "New episode", now - 60_000L, now + 1_740_000L, 0, false));
        assertEquals("drop", Moments.find("show:One Piece", null, ep, now)[0]);
    }

    @Test public void liveFootballFindsTheTeam() throws Exception {
        JSONObject fd = new JSONObject("{\"matches\":[{\"utcDate\":\"2026-10-31T20:00:00Z\",\"status\":\"IN_PLAY\",\"minute\":67,"
                + "\"homeTeam\":{\"name\":\"FC Barcelona\",\"shortName\":\"Barça\"},\"awayTeam\":{\"name\":\"Real Madrid CF\",\"shortName\":\"Real Madrid\"},"
                + "\"score\":{\"fullTime\":{\"home\":2,\"away\":1}}}]}");
        FanHub.Turn t = FanHub.liveFootball(BARCA, fd, KICKOFF);
        assertNotNull(t);
        assertEquals(2, t.ours);
        assertEquals("BAR 2\u20131 RMA \u00B7 67'", t.topLine);
        assertNull(FanHub.liveFootball(new FanHub.Team("India", "cricket", "1", 0), fd, KICKOFF));
    }

    @Test public void weekendPicksTheDrierDay() throws Exception {
        long now = java.time.ZonedDateTime.of(2026, 10, 29, 11, 0, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        JSONObject r = new JSONObject("{\"utc_offset_seconds\":19800,\"daily\":{\"time\":[\"2026-10-29\",\"2026-10-31\",\"2026-11-01\",\"2026-11-07\"],"
                + "\"temperature_2m_max\":[30,29,27,25],\"temperature_2m_min\":[21,20,18,17],\"precipitation_probability_max\":[0,60,5,0],"
                + "\"weather_code\":[0,80,0,0],\"sunrise\":[\"2026-10-29T06:36\",\"2026-10-31T06:37\",\"2026-11-01T06:38\",\"2026-11-07T06:40\"]}}");
        java.util.List<FanHub.Day> days = FanHub.weekend(r, now);
        assertEquals(2, days.size());
        assertEquals("Sat", days.get(0).label);
        assertEquals("Sun", days.get(1).label);
        assertTrue(days.get(1).best);
        assertFalse(days.get(0).best);
        assertEquals("06:38", days.get(1).sunrise);
        assertEquals("Showers", FanHub.sky(80));
        assertTrue(FanHub.weekend(null, now).isEmpty());
    }

    @Test public void formReadsTheLastResults() throws Exception {
        JSONObject last = new JSONObject("{\"results\":[{\"strHomeTeam\":\"Barcelona\",\"strAwayTeam\":\"Sevilla\",\"intHomeScore\":\"3\",\"intAwayScore\":\"1\"},"
                + "{\"strHomeTeam\":\"Real Madrid\",\"strAwayTeam\":\"Barcelona\",\"intHomeScore\":\"2\",\"intAwayScore\":\"2\"},"
                + "{\"strHomeTeam\":\"Girona\",\"strAwayTeam\":\"Barcelona\",\"intHomeScore\":\"1\",\"intAwayScore\":\"0\"}]}");
        assertEquals("LDW", FanHub.form("FC Barcelona", "football", last));
        JSONObject cricket = new JSONObject("{\"results\":[{\"strHomeTeam\":\"India\",\"strAwayTeam\":\"Australia\",\"strResult\":\"India won by 6 wickets\"},"
                + "{\"strHomeTeam\":\"New Zealand\",\"strAwayTeam\":\"India\",\"strResult\":\"New Zealand won by 20 runs\"}]}");
        assertEquals("LW", FanHub.form("India", "cricket", cricket));
        assertEquals("", FanHub.form("India", "cricket", null));
    }

    @Test public void tableFindsOurTeamAndItsNeighbours() throws Exception {
        StringBuilder t = new StringBuilder();
        String[] names = {"Real Madrid", "FC Barcelona", "Atlético Madrid", "Villarreal CF"};
        for (int i = 0; i < names.length; i++) t.append(i > 0 ? "," : "").append("{\"position\":").append(i + 1).append(",\"team\":{\"name\":\"").append(names[i])
                .append("\",\"shortName\":\"").append(names[i].replace("FC ", "")).append("\"},\"playedGames\":10,\"goalDifference\":").append(12 - i * 3).append(",\"points\":").append(25 - i * 2).append("}");
        JSONObject st = new JSONObject("{\"competition\":{\"name\":\"Primera Division\"},\"standings\":[{\"type\":\"TOTAL\",\"table\":[" + t + "]}]}");
        JSONObject table = FanHub.table("FC Barcelona", st);
        assertNotNull(table);
        assertEquals(3, table.getJSONArray("rows").length());
        assertEquals(1, table.getJSONArray("rows").getJSONObject(0).getInt("pos"));
        assertTrue(table.getJSONArray("rows").getJSONObject(1).getBoolean("ours"));
        assertNull(FanHub.table("Arsenal", st));
        assertEquals("PD", FanHub.competition("LA LIGA"));
        assertEquals("PL", FanHub.competition("Premier League"));
        assertEquals("", FanHub.competition("Indian Premier League"));
        assertEquals("", FanHub.competition("Copa del Rey"));
    }

    @Test public void geminiAnswersAreCheckedAgainstTheLooks() throws Exception {
        org.json.JSONArray looks = new org.json.JSONArray("[{\"id\":\"neon\",\"name\":\"Neon Tokyo\",\"category\":\"After dark\"},{\"id\":\"crimson-moon\",\"name\":\"Crimson Moon\",\"category\":\"Naruto\"}]");
        JSONObject parts = new JSONObject("{\"watcher\":[\"none\",\"crows\"],\"clock\":[\"thin\",\"neon\"]}");
        JSONObject ok = Gemini.check(new JSONObject("{\"name\":\"Rainy Crows\",\"base\":\"neon\",\"watcher\":\"crows\",\"clock\":\"laser\",\"accent\":\"#FF3DF2\",\"why\":\"Neon rain and Itachi's crows.\"}"), looks, parts);
        assertEquals("neon", ok.getString("base"));
        assertEquals("crows", ok.getString("watcher"));
        assertFalse(ok.has("clock"));                      // not a clock den-tv has
        assertEquals("#ff3df2", ok.getString("accent"));
        assertNull(Gemini.check(new JSONObject("{\"base\":\"tokyo\"}"), looks, parts));
        assertEquals("crimson-moon", Gemini.words("a crimson moon please", looks).getString("base"));
        assertNull(Gemini.words("zzz", looks));
        assertTrue(Gemini.prompt("rainy night", looks, parts).contains("crimson-moon: Crimson Moon"));
    }

    @Test public void aDesignedLookKeepsItsWordsCleaned() throws Exception {
        org.json.JSONArray looks = new org.json.JSONArray("[{\"id\":\"barca-wings\",\"name\":\"Barça Wings\",\"category\":\"Football\"}]");
        JSONObject parts = new JSONObject("{\"watcher\":[\"onetwo\"]}");
        JSONObject l = Gemini.check(new JSONObject("{\"name\":\"Raphinha Wings\",\"base\":\"barca-wings\",\"watcher\":\"onetwo\","
                + "\"title\":\"Raphinha the brilliant winger\",\"subtitle\":\"Barça · Brazil · No. 11\",\"number\":\"No. 11\","
                + "\"colours\":[\"#A50044\",\"blue\",\"#004d98\"],\"scene\":\"Camp Nou at night\\n in blaugrana\","
                + "\"facts\":[\"He joined Barça in 2022.\",\"\"]}"), looks, parts);
        assertEquals("RAPHINHA THE B", l.getString("title"));
        assertTrue(l.getString("title").length() <= 14);
        assertEquals("BARÇA · BRAZIL · NO. 11", l.getString("subtitle"));
        assertEquals("11", l.getString("number"));
        assertEquals(2, l.getJSONArray("colours").length());   // "blue" isn't a colour den-tv takes
        assertEquals("Camp Nou at night in blaugrana", l.getString("scene"));
        assertEquals(1, l.getJSONArray("facts").length());
    }

    /** The description that matched "Wanted" (on "want") and missed Barça Wings (Raphinha spelt Raphina). */
    @Test public void wordsFindTheRightLook() throws Exception {
        org.json.JSONArray looks = new org.json.JSONArray("["
                + "{\"id\":\"wanted\",\"name\":\"Wanted\",\"category\":\"One Piece\",\"words\":\"luffy grandline straw\"},"
                + "{\"id\":\"camp-nou\",\"name\":\"Camp Nou Nights\",\"category\":\"Football\",\"words\":\"campnou messi ball\"},"
                + "{\"id\":\"barca-wings\",\"name\":\"Barça Wings\",\"category\":\"Football\",\"words\":\"wings wings onetwo Raphinha to Yamal · goal\"},"
                + "{\"id\":\"master-blaster\",\"name\":\"Master Blaster\",\"category\":\"Cricket\",\"words\":\"sachin sharjah drive SACHIN 143 (131) · SHARJAH\"}]");
        assertEquals("barca-wings", Gemini.words("I want a theme for FC Barcelona player, Raphina", looks).getString("base"));
        assertEquals("master-blaster", Gemini.words("something for Tendulkar fans", looks).getString("base"));
        assertNull("filler words alone match nothing", Gemini.words("I want a cool theme please", looks));
        assertTrue(Gemini.near("raphina", "raphinha"));
        assertFalse(Gemini.near("wants", "wanted"));
    }

    @Test public void pastedKeysAreCleanedNotJudged() {
        String k = "AIzaSyD-abcdefghijklmnopqrstuvwxyz_0123";
        assertEquals(k, FanHub.cleanKey("  " + k + "\n"));
        assertEquals(k, FanHub.cleanKey("\u200B" + k + "\u200E"));          // zero-width space, direction mark from a phone paste
        assertEquals(k, FanHub.cleanKey("\"" + k + "\""));
        assertEquals("AQ.Ab8RN6Ix-y_z.0123456789abcdef", FanHub.cleanKey("AQ.Ab8RN6Ix-y_z.0123456789abcdef"));   // the newer format, with dots
        assertEquals("", FanHub.cleanKey("short"));
        assertEquals("", FanHub.cleanKey("näme-with-ümlauts-0123"));
    }
}
