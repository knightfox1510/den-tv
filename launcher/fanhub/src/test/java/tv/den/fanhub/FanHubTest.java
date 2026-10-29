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
}
