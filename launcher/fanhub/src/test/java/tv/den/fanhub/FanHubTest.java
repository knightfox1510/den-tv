package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
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
}
