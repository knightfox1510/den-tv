package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class GameDayTest {
    private static final RaceWeekend.Race AUSTIN = new RaceWeekend.Race(18, "United States Grand Prix", "Austin",
            Arrays.asList(
                    new RaceWeekend.Session("FP1", Instant.parse("2026-10-23T17:30:00Z")),
                    new RaceWeekend.Session("FP2", Instant.parse("2026-10-23T21:00:00Z")),
                    new RaceWeekend.Session("FP3", Instant.parse("2026-10-24T17:30:00Z")),
                    new RaceWeekend.Session("Qualifying", Instant.parse("2026-10-24T21:00:00Z")),
                    new RaceWeekend.Session("race", Instant.parse("2026-10-25T20:00:00Z"))));
    private static final List<RaceWeekend.Race> CALENDAR = Collections.singletonList(AUSTIN);
    /** Saturday afternoon: FP3 is over, qualifying is in 2h 05m. */
    private static final Instant NOW = Instant.parse("2026-10-24T18:55:00Z");
    private static final long HOUR = 3_600_000L;

    private static GameDay.Turn game(String top, long fromNowMs, long lengthMs) {
        long start = NOW.toEpochMilli() + fromNowMs;
        return new GameDay.Turn(top, "Puck drop", start, start + lengthMs, 0xFF2E7D32);
    }

    @Test public void noTurnsHidesPanel() {
        assertTrue(GameDay.merge(Collections.emptyList(), null, NOW.toEpochMilli()).isEmpty());
    }

    @Test public void endedGamesAreDropped() {
        List<GameDay.Turn> t = GameDay.merge(Arrays.asList(
                game("Ended", -4 * HOUR, 3 * HOUR), game("Tonight", HOUR, 3 * HOUR)), null, NOW.toEpochMilli());
        assertEquals(1, t.size());
        assertEquals("Tonight", t.get(0).topLine);
    }

    @Test public void f1JoinsSortedByItsNextSession() {
        RaceWeekend.State f1 = RaceWeekend.at(CALENDAR, NOW);
        List<GameDay.Turn> t = GameDay.merge(Arrays.asList(
                game("In two days", 48 * HOUR, 3 * HOUR), game("In an hour", HOUR, 3 * HOUR)), f1, NOW.toEpochMilli());
        assertEquals(3, t.size());
        assertEquals("In an hour", t.get(0).topLine);
        assertTrue(t.get(1).f1);
        assertEquals(Instant.parse("2026-10-24T21:00:00Z").toEpochMilli(), t.get(1).startMs);
        assertEquals("In two days", t.get(2).topLine);
    }

    @Test public void f1LiveSortsFirst() {
        Instant duringQualifying = Instant.parse("2026-10-24T21:20:00Z");
        RaceWeekend.State f1 = RaceWeekend.at(CALENDAR, duringQualifying);
        long soon = duringQualifying.toEpochMilli() + 30 * 60_000L;
        List<GameDay.Turn> t = GameDay.merge(Arrays.asList(
                new GameDay.Turn("Soon", "Puck drop", soon, soon + 3 * HOUR, 0)), f1, duringQualifying.toEpochMilli());
        assertTrue(t.get(0).f1);
        assertEquals(duringQualifying.toEpochMilli(), t.get(0).startMs);
    }

    @Test public void noF1OutsideItsWeekend() {
        RaceWeekend.State none = RaceWeekend.at(CALENDAR, Instant.parse("2026-11-10T12:00:00Z"));
        assertEquals(1, GameDay.merge(Arrays.asList(game("Tonight", HOUR, 3 * HOUR)), none, NOW.toEpochMilli()).size());
    }

    @Test public void rotationIndex() {
        assertEquals(0, GameDay.current(3, 29_999, 30));
        assertEquals(1, GameDay.current(3, 31_000, 30));
        assertEquals(0, GameDay.current(3, 90_000, 30));
        assertEquals(0, GameDay.current(0, 5_000, 30));
        assertEquals(0, GameDay.current(1, 500_000, 30));
    }

    @Test public void nextTurnIsNeverMoreThanOneTurnAway() {
        assertEquals(30_000, GameDay.delayToNextTurn(0, 30));
        assertEquals(1_000, GameDay.delayToNextTurn(29_000, 30));
        assertEquals(30_000, GameDay.delayToNextTurn(60_000, 30));
        // A clock set back while Home is open must not stretch the turn.
        assertTrue(GameDay.delayToNextTurn(-45_000, 30) <= 30_000);
        assertTrue(GameDay.delayToNextTurn(-45_000, 30) > 0);
    }

    @Test public void turnIsLiveOnlyBetweenStartAndEnd() {
        GameDay.Turn t = game("Game", HOUR, 3 * HOUR);
        assertFalse(GameDay.live(t, t.startMs - 1));
        assertTrue(GameDay.live(t, t.startMs));
        assertTrue(GameDay.live(t, t.endMs - 1));
        assertFalse(GameDay.live(t, t.endMs));
    }

    @Test public void rowsAreParsedAndBadOnesSkipped() {
        List<Object[]> rows = Arrays.asList(
                new Object[] {"Minnesota Wild at Dallas Stars", "Puck drop", 1000L, 5000L, 0xFF2E7D32, "Minnesota Wild", 45},
                new Object[] {null, "Puck drop", 1000L, 5000L, 0, "x", 45},
                new Object[] {"Bad start", "Kickoff", -1L, 5000L, 0, "x", 45},
                new Object[] {"Ends first", "Kickoff", 5000L, 5000L, 0, "x", 45},
                new Object[] {"No word", null, 2000L, 6000L, 0xFF1565C0, "x", 45});
        List<GameDay.Turn> t = GameDay.fromRows(rows);
        assertEquals(2, t.size());
        assertEquals("Minnesota Wild at Dallas Stars", t.get(0).topLine);
        assertEquals("Puck drop", t.get(0).word);
        assertEquals(1000L, t.get(0).startMs);
        assertEquals(5000L, t.get(0).endMs);
        assertEquals(0xFF2E7D32, t.get(0).colour);
        assertEquals("", t.get(1).word);
        assertEquals(45, GameDay.turnSeconds(rows));
    }

    @Test public void turnSecondsOutOfRangeBecomesThirty() {
        assertEquals(30, GameDay.turnSeconds(Collections.singletonList(new Object[] {"a", "b", 1L, 2L, 0, "c", 500})));
        assertEquals(30, GameDay.turnSeconds(Collections.singletonList(new Object[] {"a", "b", 1L, 2L, 0, "c", 3})));
        assertEquals(30, GameDay.turnSeconds(Collections.emptyList()));
    }

    @Test public void durationHelperMatchesTheCountdown() {
        assertEquals(Duration.ofMinutes(125), GameDay.until(game("Game", 125 * 60_000L, HOUR), NOW.toEpochMilli()));
    }

    /** LIVE only for a match under way that the Fan Hub has a score for; never for an episode or the weekend trail. */
    @Test public void liveMeansAMatchWithAScore() {
        long now = NOW.toEpochMilli(), start = now - HOUR, end = now + HOUR;
        assertTrue(GameDay.live("Kick-off", start, end, 1, now));
        assertFalse(GameDay.live("Kick-off", start, end, 0, now));    // past kick-off, no score yet
        assertFalse(GameDay.live("Kick-off", now + HOUR, now + 3 * HOUR, 1, now));   // not started
        assertFalse(GameDay.live("Kick-off", start - 3 * HOUR, start, 1, now));      // over
        assertFalse(GameDay.live("Set off", start, end, 0, now));     // the weekend trail, all morning
        assertFalse(GameDay.live("New episode", start, end, 1, now));
        // An add-on that doesn't say keeps the old meaning, except for den-tv's own episode and trail words.
        assertTrue(GameDay.live("Puck drop", start, end, -1, now));
        assertFalse(GameDay.live("Set off", start, end, -1, now));
    }

    @Test public void theLiveColumnIsReadWhenThere() {
        long now = NOW.toEpochMilli();
        List<GameDay.Turn> t = GameDay.fromRows(Arrays.asList(
                new Object[]{"BAR 1-0 RMA", "Kick-off", now - HOUR, now + HOUR, 0, null, 15, 1},
                new Object[]{"HIKE · RAJMACHI", "Set off", now - HOUR, now + HOUR, 0, null, 15, 0},
                new Object[]{"Old add-on", "Puck drop", now - HOUR, now + HOUR, 0, null, 15}));
        assertTrue(GameDay.live(t.get(0), now));
        assertFalse(GameDay.live(t.get(1), now));
        assertTrue(GameDay.live(t.get(2), now));
    }

    @Test public void theScreensaverSaysWhatItIs() {
        long now = NOW.toEpochMilli();
        java.text.DateFormat clock = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH);
        clock.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        assertEquals("● LIVE  BAR 1–0 RMA", GameDay.saverLine("BAR 1–0 RMA", "Kick-off", now - HOUR, now + HOUR, 1, now, clock));
        assertEquals("BARÇA V SEVILLA  ·  started 5:55 PM", GameDay.saverLine("BARÇA V SEVILLA", "Kick-off", now - HOUR, now + HOUR, 0, now, clock));
        assertEquals("HIKE · RAJMACHI", GameDay.saverLine("HIKE · RAJMACHI", "Set off", now - HOUR, now + HOUR, 0, now, clock));
        assertEquals("NEW  ONE PIECE · EPISODE 1149", GameDay.saverLine("ONE PIECE · EPISODE 1149", "New episode", now - 60_000, now + HOUR, 1, now, clock));
        assertEquals("INDIA V AUSTRALIA  ·  in 2 h 5 min", GameDay.saverLine("INDIA V AUSTRALIA", "First ball", now + 125 * 60_000L, now + 6 * HOUR, 0, now, clock));
        assertEquals(null, GameDay.saverLine("OVER", "Kick-off", now - 3 * HOUR, now - HOUR, 1, now, clock));
    }
}
