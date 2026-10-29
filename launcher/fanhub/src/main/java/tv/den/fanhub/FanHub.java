package tv.den.fanhub;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Fan Hub's data, ported from fanhub/fanhub.js: each team's next (or live) match from TheSportsDB and each
 * show's next episode from AniList, as the launcher's game_day turns (a top line, a countdown word, start and
 * end, a colour). Plain Java with org.json, so the parsing is tested on the JVM.
 */
final class FanHub {
    static final int TIMEOUT_MS = 8000;
    private static final long MIN = 60_000L, HOUR = 60 * MIN;
    private static final Pattern FINISHED = Pattern.compile("^(FT|AET|PEN|AP|MATCH FINISHED|FINISHED|ABD|AWD|CANC|PST|POSTP|ABANDONED|CANCELLED|POSTPONED).*");

    /** One team or show to follow. */
    static final class Team {
        final String name, sport, sportsdb;
        final int colour;
        Team(String name, String sport, String sportsdb, int colour) { this.name = name; this.sport = sport; this.sportsdb = sportsdb; this.colour = colour; }
    }

    static final class Config {
        final List<Team> teams = new ArrayList<>();
        final List<String> shows = new ArrayList<>();
        String sportsdbKey = "123";
        /** Free keys for live in-match scores (football-data.org, CricketData.org), kept on the TV only; "" for none. */
        String footballKey = "", cricketKey = "";
        /** The weekend hike forecast's place, from the phone; null when the forecast is off. */
        Place hike;

        /** fanhub/config.example.json's defaults: Barça, India, Mumbai Indians and three shows. */
        static Config defaults() {
            Config c = new Config();
            c.teams.add(new Team("FC Barcelona", "football", "133739", 0xffa50044));
            c.teams.add(new Team("India", "cricket", "137143", 0xff1f5fd1));
            c.teams.add(new Team("Mumbai Indians", "cricket", "135795", 0xff004ba0));
            Collections.addAll(c.shows, "One Piece", "Jujutsu Kaisen", "Demon Slayer");
            return c;
        }

        /** The same file the command-line Fan Hub reads (fanhub/config.json); anything missing keeps the default. */
        static Config from(JSONObject j) {
            Config d = defaults(), c = new Config();
            JSONArray teams = j.optJSONArray("teams");
            if (teams == null) c.teams.addAll(d.teams);
            else for (int i = 0; i < teams.length(); i++) {
                JSONObject t = teams.optJSONObject(i);
                if (t == null || t.optString("sportsdb").isEmpty()) continue;
                c.teams.add(new Team(t.optString("name", "Team"), t.optString("sport", "football"), t.optString("sportsdb"),
                        colour(t.optString("colour"), d.teams.size() > i ? d.teams.get(i).colour : 0xffff4d6d)));
            }
            JSONArray shows = j.optJSONArray("shows");
            if (shows == null) c.shows.addAll(d.shows);
            else for (int i = 0; i < shows.length(); i++) if (!shows.optString(i).isEmpty()) c.shows.add(shows.optString(i));
            JSONObject keys = j.optJSONObject("keys");
            if (keys != null && !keys.optString("sportsdb").isEmpty()) c.sportsdbKey = keys.optString("sportsdb");
            return c;
        }

        private static int colour(String hex, int fallback) {
            if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return fallback;
            return 0xff000000 | Integer.parseInt(hex.substring(1), 16);
        }
    }

    /** One line in Home's panel: GameDay's row. */
    static final class Turn {
        final String topLine, word;
        final long start, end;
        final int colour;
        final boolean live;
        /** Our side's goals or runs while live, -1 when unknown; what the moments compare between fetches. */
        int ours = -1;
        /** The other side's, for the moment's score line. */
        String score = "";

        Turn(String topLine, String word, long start, long end, int colour, boolean live) {
            this.topLine = topLine; this.word = word; this.start = start; this.end = end; this.colour = colour; this.live = live;
        }

        Turn with(int ours, String score) { this.ours = ours; this.score = score; return this; }

        JSONObject json() throws JSONException {
            return new JSONObject().put("top", topLine).put("word", word).put("start", start).put("end", end).put("colour", colour).put("live", live)
                    .put("ours", ours).put("score", score);
        }

        static Turn of(JSONObject j) {
            return new Turn(j.optString("top"), j.optString("word"), j.optLong("start"), j.optLong("end"), j.optInt("colour"), j.optBoolean("live"))
                    .with(j.optInt("ours", -1), j.optString("score"));
        }
    }

    private FanHub() {}

    // ---- names ---------------------------------------------------------------------------------------------------

    private static final Map<String, String> SHORT = new HashMap<>();
    static {
        String[][] s = {{"Barcelona", "BAR"}, {"FC Barcelona", "BAR"}, {"Barça", "BAR"}, {"Real Madrid", "RMA"}, {"Atlético Madrid", "ATM"},
                {"Atletico Madrid", "ATM"}, {"Sevilla", "SEV"}, {"Villarreal", "VIL"}, {"Real Sociedad", "RSO"}, {"Athletic Bilbao", "ATH"}, {"Deportivo Alavés", "ALA"}, {"Alavés", "ALA"}, {"Real Betis", "BET"}, {"Celta Vigo", "CEL"},
                {"India", "IND"}, {"Australia", "AUS"}, {"England", "ENG"}, {"Pakistan", "PAK"}, {"South Africa", "SA"}, {"New Zealand", "NZ"},
                {"Sri Lanka", "SL"}, {"Bangladesh", "BAN"}, {"West Indies", "WI"}, {"Afghanistan", "AFG"}, {"Mumbai Indians", "MI"},
                {"Chennai Super Kings", "CSK"}, {"Royal Challengers Bengaluru", "RCB"}, {"Royal Challengers Bangalore", "RCB"},
                {"Kolkata Knight Riders", "KKR"}, {"Delhi Capitals", "DC"}, {"Sunrisers Hyderabad", "SRH"}, {"Rajasthan Royals", "RR"},
                {"Punjab Kings", "PBKS"}, {"Gujarat Titans", "GT"}, {"Lucknow Super Giants", "LSG"}};
        for (String[] p : s) SHORT.put(p[0], p[1]);
    }

    static String clean(String n) { return n == null ? "" : n.replaceAll("\\s+", " ").trim(); }

    /** "Real Madrid" -> "RMA", an unknown two-word name -> its initials, one word -> its first three letters. */
    static String shortName(String n) {
        String c = clean(n);
        if (SHORT.containsKey(c)) return SHORT.get(c);
        String[] w = c.split(" ");
        if (w.length > 1) {
            StringBuilder b = new StringBuilder();
            for (String x : w) if (!x.isEmpty()) b.append(x.charAt(0));
            return b.toString().toUpperCase(Locale.ROOT);
        }
        return (c.length() > 3 ? c.substring(0, 3) : c).toUpperCase(Locale.ROOT);
    }

    /** TheSportsDB's cricket sides end in " Cricket" ("New Zealand Cricket"); the name alone reads better. */
    static String team(String n) {
        return clean(n).replaceAll("(?i) cricket$", "");
    }

    /** "Twenty20 International Series" -> "T20I", "Spanish La Liga" -> "La Liga": the panel has one short line. */
    static String league(String l) {
        String s = clean(l);
        if (s.equalsIgnoreCase("Indian Premier League")) return "IPL";
        return s.replaceAll("(?i)twenty20 international", "T20I").replaceAll("(?i)one day international", "ODI")
                .replaceAll("(?i)^(spanish|english|italian|german|french) ", "").replaceAll("(?i) series$", "");
    }

    /** "BARÇA v DEPORTIVO ALAVÉS · LA LIGA", or with short names ("NZ v IND · T20I") when that runs long. */
    static String matchLine(String home, String away, String leagueName) {
        String lg = league(leagueName), tail = lg.isEmpty() ? "" : " \u00B7 " + lg;
        String full = display(home) + " v " + display(away) + tail;
        String line = full.length() <= 34 ? full : shortName(home) + " v " + shortName(away) + tail;
        return line.toUpperCase(Locale.ROOT);
    }

    static String display(String n) {
        String c = clean(n);
        return c.equals("Barcelona") || c.equals("FC Barcelona") ? "Barça" : c;
    }

    // ---- TheSportsDB ---------------------------------------------------------------------------------------------

    /** An event's kick-off in epoch ms: strTimestamp (UTC), else dateEvent + strTime. -1 when there is neither. */
    static long kickoff(JSONObject e) {
        try {
            String ts = e.optString("strTimestamp", "");
            if (!ts.isEmpty() && !"null".equals(ts)) {
                ts = ts.replaceAll("\\+00:00$", "").replaceAll("Z$", "");
                return LocalDate.parse(ts.substring(0, 10)).atTime(LocalTime.parse(ts.substring(11))).toInstant(ZoneOffset.UTC).toEpochMilli();
            }
            String d = e.optString("dateEvent", ""), t = e.optString("strTime", "00:00:00");
            if (d.isEmpty() || "null".equals(d)) return -1;
            if (t.isEmpty() || "null".equals(t)) t = "00:00:00";
            return LocalDate.parse(d).atTime(LocalTime.parse(t.length() > 8 ? t.substring(0, 8) : t)).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (RuntimeException bad) {
            return -1;
        }
    }

    private static boolean has(JSONObject e, String k) {
        return !e.isNull(k) && !e.optString(k, "").isEmpty();
    }

    /** How long a match takes, so the panel shows LIVE until it is over. */
    static long length(String sport, String league) {
        if (!"cricket".equals(sport)) return 2 * HOUR;
        String l = league == null ? "" : league.toLowerCase(Locale.ROOT);
        if (l.contains("test")) return 8 * HOUR;
        if (l.contains("t20") || l.contains("premier league") || l.contains("ipl")) return 4 * HOUR;
        return 8 * HOUR;   // ODIs and anything unknown
    }

    /**
     * The team's turn from eventsnext.php: its first match that is not over. Live (kick-off passed and a score
     * there) shows the score; otherwise the panel counts down to kick-off.
     */
    static Turn nextMatch(Team team, JSONObject next, long now) {
        JSONArray events = next == null ? null : next.optJSONArray("events");
        if (events == null) return null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            long start = kickoff(e);
            if (start < 0) continue;
            String status = e.optString("strStatus", "").toUpperCase(Locale.ROOT);
            if (FINISHED.matcher(status).matches()) continue;
            String league = e.optString("strLeague", "");
            long end = start + length(team.sport, league);
            if (end <= now) continue;
            String home = team(e.optString("strHomeTeam")), away = team(e.optString("strAwayTeam"));
            boolean scored = has(e, "intHomeScore");
            boolean live = start <= now && scored;
            String top;
            if (live && "football".equals(team.sport)) {
                String min = has(e, "strProgress") ? " · " + e.optString("strProgress") + "'" : "";
                top = shortName(home) + " " + e.optString("intHomeScore") + "–" + e.optString("intAwayScore") + " " + shortName(away) + min;
            } else if (live) {
                top = shortName(home) + " " + e.optString("intHomeScore") + (has(e, "intAwayScore") ? " · " + shortName(away) + " " + e.optString("intAwayScore") : "");
            } else {
                top = matchLine(home, away, league);
            }
            int ours = -1;
            if (live) {
                boolean mineHome = same(team.name, home);
                String mine = e.optString(mineHome ? "intHomeScore" : "intAwayScore");
                try { ours = Integer.parseInt(mine.replaceAll("[^0-9].*$", "")); } catch (NumberFormatException ignored) { }
            }
            return new Turn(top, "football".equals(team.sport) ? "Kick-off" : "First ball", start, end, team.colour, live).with(ours, top);
        }
        return null;
    }

    // ---- live scores with keys -----------------------------------------------------------------------------------

    /** "FC Barcelona" and "Barcelona", "India" and "India Women" are told apart; "Barça" too. */
    static boolean same(String ours, String theirs) {
        String a = norm(ours), b = norm(theirs);
        return !a.isEmpty() && (a.equals(b) || b.startsWith(a + " ") && !b.contains(" women") || a.startsWith(b + " "));
    }

    private static String norm(String n) {
        return clean(n).toLowerCase(Locale.ROOT).replace("barça", "barcelona").replaceAll("^(fc|cf|afc) | (fc|cf|afc)$", "").replaceAll(" cricket$", "").trim();
    }

    /** The team's match from football-data.org's live list, with the score; null when it isn't playing. */
    static Turn liveFootball(Team team, JSONObject fd, long now) {
        JSONArray m = fd == null ? null : fd.optJSONArray("matches");
        if (m == null) return null;
        for (int i = 0; i < m.length(); i++) {
            JSONObject x = m.optJSONObject(i);
            if (x == null) continue;
            JSONObject h = x.optJSONObject("homeTeam"), a = x.optJSONObject("awayTeam");
            String hn = h == null ? "" : h.optString("name"), an = a == null ? "" : a.optString("name");
            boolean home = same(team.name, hn) || h != null && same(team.name, h.optString("shortName"));
            boolean away = same(team.name, an) || a != null && same(team.name, a.optString("shortName"));
            if (!home && !away) continue;
            JSONObject ft = x.optJSONObject("score") == null ? null : x.optJSONObject("score").optJSONObject("fullTime");
            int hs = ft == null || ft.isNull("home") ? 0 : ft.optInt("home"), as = ft == null || ft.isNull("away") ? 0 : ft.optInt("away");
            String min = "PAUSED".equals(x.optString("status")) ? " \u00B7 HT" : x.has("minute") && !x.isNull("minute") ? " \u00B7 " + x.optString("minute") + "'" : "";
            String hsn = shortName(h == null ? "" : h.optString("shortName", hn)), asn = shortName(a == null ? "" : a.optString("shortName", an));
            String line = hsn + " " + hs + "\u2013" + as + " " + asn;
            long start = now;
            try { start = Instant.parse(x.optString("utcDate")).toEpochMilli(); } catch (RuntimeException ignored) { }
            return new Turn(line + min, "Kick-off", start, Math.max(start + 2 * HOUR, now + 15 * MIN), team.colour, true)
                    .with(home ? hs : as, line);
        }
        return null;
    }

    /** The team's match from CricketData.org's current list, with the latest innings; null when it isn't playing. */
    static Turn liveCricket(Team team, JSONObject cd, long now) {
        JSONArray m = cd == null ? null : cd.optJSONArray("data");
        if (m == null) return null;
        for (int i = 0; i < m.length(); i++) {
            JSONObject x = m.optJSONObject(i);
            if (x == null || !x.optBoolean("matchStarted") || x.optBoolean("matchEnded")) continue;
            JSONArray teams = x.optJSONArray("teams");
            String mine = null, other = null;
            if (teams != null) for (int k = 0; k < teams.length(); k++) {
                if (same(team.name, teams.optString(k))) mine = teams.optString(k); else other = teams.optString(k);
            }
            if (mine == null) continue;
            JSONArray sc = x.optJSONArray("score");
            int ours = -1;
            StringBuilder line = new StringBuilder();
            if (sc != null) for (int k = 0; k < sc.length(); k++) {
                JSONObject in = sc.optJSONObject(k);
                if (in == null) continue;
                String inning = in.optString("inning");
                String who = inning.replaceAll("(?i) inning.*$", "");
                if (same(mine, who)) ours = in.optInt("r");
                if (line.length() > 0) line.append(" \u00B7 ");
                line.append(shortName(who)).append(' ').append(in.optInt("r")).append('/').append(in.optInt("w")).append(" (").append(in.optString("o")).append(')');
            }
            if (line.length() == 0) line.append(shortName(mine)).append(" v ").append(shortName(other == null ? "" : other));
            long start = now;
            try { start = Instant.parse(x.optString("dateTimeGMT").replaceAll("Z?$", "Z")).toEpochMilli(); } catch (RuntimeException ignored) { }
            String top = line.toString().toUpperCase(Locale.ROOT);
            return new Turn(top, "First ball", start, Math.max(start + length("cricket", x.optString("matchType")), now + 15 * MIN), team.colour, true)
                    .with(ours, top);
        }
        return null;
    }

    // ---- AniList -------------------------------------------------------------------------------------------------

    static final String ANI_SEARCH = "query($s:String){Page(perPage:8){media(search:$s,type:ANIME,sort:[SEARCH_MATCH]){id status title{english romaji} startDate{year month} nextAiringEpisode{episode airingAt}}}}";

    /** The search result to follow: the one airing now, else the newest. */
    static JSONObject pickMedia(JSONObject search) {
        JSONObject data = search == null ? null : search.optJSONObject("data");
        JSONObject page = data == null ? null : data.optJSONObject("Page");
        JSONArray media = page == null ? null : page.optJSONArray("media");
        if (media == null || media.length() == 0) return null;
        JSONObject newest = null;
        int best = -1;
        for (int i = 0; i < media.length(); i++) {
            JSONObject m = media.optJSONObject(i);
            if (m == null) continue;
            if ("RELEASING".equals(m.optString("status"))) return m;
            JSONObject d = m.optJSONObject("startDate");
            int when = d == null ? 0 : d.optInt("year") * 12 + d.optInt("month");
            if (when > best) { best = when; newest = m; }
        }
        return newest;
    }

    /** The show's next episode within a week, as a turn that is live for the half hour it airs. */
    static Turn nextEpisode(String show, JSONObject media, long now) {
        JSONObject na = media == null ? null : media.optJSONObject("nextAiringEpisode");
        if (na == null) return null;
        long at = na.optLong("airingAt") * 1000L;
        if (at <= 0 || at - now > 7 * 24 * HOUR || at + 30 * MIN <= now) return null;
        return new Turn((show + " · Episode " + na.optInt("episode")).toUpperCase(Locale.ROOT), "New episode", at, at + 30 * MIN, 0xffff7a00, at <= now);
    }

    // ---- fetching ------------------------------------------------------------------------------------------------

    static JSONObject get(String url) throws IOException, JSONException {
        return request(url, null);
    }

    private static JSONObject request(String url, String body) throws IOException, JSONException {
        return request(url, body, null, null);
    }

    static JSONObject request(String url, String body, String header, String value) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (header != null) c.setRequestProperty(header, value);
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "den-tv-fanhub/0.2");
        try {
            if (body != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode() + " from " + new URL(url).getHost());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (b.size() > 2_000_000) throw new IOException("response too large");
                }
                return new JSONObject(b.toString("UTF-8"));
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Everything Home should show, soonest first. A source that fails keeps its turns from last time (prev), so a
     * rate limit or a dropped connection never empties the panel.
     */
    static List<Turn> fetch(Config cfg, long now, Map<String, List<Turn>> prev, Map<String, List<Turn>> out, List<String> errors) {
        // Live in-match scores first, when there are keys: one call each covers every followed team.
        JSONObject fd = null, cd = null;
        if (!cfg.footballKey.isEmpty()) {
            try { fd = request("https://api.football-data.org/v4/matches?status=IN_PLAY,PAUSED", null, "X-Auth-Token", cfg.footballKey); }
            catch (IOException | JSONException e) { errors.add("football-data.org: " + e.getMessage()); }
        }
        if (!cfg.cricketKey.isEmpty()) {
            try {
                cd = get("https://api.cricapi.com/v1/currentMatches?offset=0&apikey=" + URLEncoder.encode(cfg.cricketKey, "UTF-8"));
                if (!"success".equals(cd.optString("status", "success"))) { errors.add("CricketData.org: " + cd.optString("reason", "refused")); cd = null; }
            } catch (IOException | JSONException e) { errors.add("CricketData.org: " + e.getMessage()); }
        }
        for (Team t : cfg.teams) {
            Turn live = "football".equals(t.sport) ? liveFootball(t, fd, now) : liveCricket(t, cd, now);
            if (live != null) { out.put("team:" + t.sportsdb, Collections.singletonList(live)); continue; }
            String key = "team:" + t.sportsdb;
            try {
                JSONObject next = get("https://www.thesportsdb.com/api/v1/json/" + URLEncoder.encode(cfg.sportsdbKey, "UTF-8")
                        + "/eventsnext.php?id=" + URLEncoder.encode(t.sportsdb, "UTF-8"));
                Turn turn = nextMatch(t, next, now);
                out.put(key, turn == null ? Collections.emptyList() : Collections.singletonList(turn));
            } catch (IOException | JSONException e) {
                errors.add(t.name + ": " + e.getMessage());
                if (prev.containsKey(key)) out.put(key, prev.get(key));
            }
        }
        for (String show : cfg.shows) {
            String key = "show:" + show;
            try {
                JSONObject vars = new JSONObject().put("s", show);
                JSONObject r = request("https://graphql.anilist.co", new JSONObject().put("query", ANI_SEARCH).put("variables", vars).toString());
                Turn turn = nextEpisode(show, pickMedia(r), now);
                out.put(key, turn == null ? Collections.emptyList() : Collections.singletonList(turn));
            } catch (IOException | JSONException e) {
                errors.add(show + ": " + e.getMessage());
                if (prev.containsKey(key)) out.put(key, prev.get(key));
            }
        }
        if (cfg.hike != null) {
            try {
                JSONObject r = get("https://api.open-meteo.com/v1/forecast?latitude=" + cfg.hike.lat + "&longitude=" + cfg.hike.lon
                        + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,sunrise&timezone=auto&forecast_days=7");
                lastForecast = r;
                Turn t = hikeTurn(cfg.hike.name, r, now);
                out.put("hike", t == null ? Collections.emptyList() : Collections.singletonList(t));
            } catch (IOException | JSONException e) {
                errors.add("Weather: " + e.getMessage());
                if (prev.containsKey("hike")) out.put("hike", prev.get("hike"));
            }
        }
        return flatten(out, now);
    }

    // ---- recent form and the league table ------------------------------------------------------------------------

    /**
     * A team's last results as W, D and L, oldest first (up to five), from TheSportsDB's eventslast.php. Football
     * compares the goals; cricket reads who won from the result line. "" when there is nothing to read.
     */
    static String form(String team, String sport, JSONObject last) {
        JSONArray r = last == null ? null : last.optJSONArray("results");
        if (r == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < r.length() && out.length() < 5; i++) {
            JSONObject e = r.optJSONObject(i);
            if (e == null) continue;
            String home = e.optString("strHomeTeam"), away = e.optString("strAwayTeam");
            boolean mineHome = same(team, home), mineAway = same(team, away);
            if (!mineHome && !mineAway) continue;
            char c = 0;
            if ("football".equals(sport) && has(e, "intHomeScore") && has(e, "intAwayScore")) {
                try {
                    int h = Integer.parseInt(e.optString("intHomeScore").trim()), a = Integer.parseInt(e.optString("intAwayScore").trim());
                    int us = mineHome ? h : a, them = mineHome ? a : h;
                    c = us > them ? 'W' : us == them ? 'D' : 'L';
                } catch (NumberFormatException ignored) { }
            } else {
                String res = e.optString("strResult", "") + " " + e.optString("strDescriptionEN", "");
                java.util.regex.Matcher m = Pattern.compile("(?i)([A-Za-z .'-]+?) won\\b").matcher(res);
                if (m.find()) c = same(team, m.group(1).trim()) || res.toLowerCase(Locale.ROOT).contains(norm(team) + " won") ? 'W' : 'L';
                else if (res.toLowerCase(Locale.ROOT).matches(".*\\b(tied|drawn|draw|no result)\\b.*")) c = 'D';
            }
            if (c != 0) out.append(c);
        }
        return out.reverse().toString();
    }

    /** football-data.org's code for a league line ("LA LIGA" -> "PD"); "" for a cup or a league it doesn't cover. */
    static String competition(String league) {
        String l = clean(league).toLowerCase(Locale.ROOT);
        if (l.contains("champions league")) return "CL";
        if (l.contains("la liga") || l.equals("primera division")) return "PD";
        if (l.contains("premier league") && !l.contains("indian")) return "PL";
        if (l.contains("serie a")) return "SA";
        if (l.contains("bundesliga")) return "BL1";
        if (l.contains("ligue 1")) return "FL1";
        if (l.contains("eredivisie")) return "DED";
        if (l.contains("primeira liga")) return "PPL";
        return "";
    }

    /**
     * Our team's place in a football-data.org standings answer, with the teams either side of it:
     * {"league": "La Liga", "rows": [{"pos", "name", "played", "gd", "pts", "ours"}]}; null when we're not in it.
     */
    static JSONObject table(String team, JSONObject standings) throws JSONException {
        JSONArray st = standings == null ? null : standings.optJSONArray("standings");
        if (st == null) return null;
        for (int k = 0; k < st.length(); k++) {
            JSONObject group = st.optJSONObject(k);
            if (group == null || !"TOTAL".equals(group.optString("type", "TOTAL"))) continue;
            JSONArray t = group.optJSONArray("table");
            if (t == null) continue;
            int at = -1;
            for (int i = 0; i < t.length(); i++) {
                JSONObject tm = t.getJSONObject(i).optJSONObject("team");
                if (tm != null && (same(team, tm.optString("name")) || same(team, tm.optString("shortName")))) { at = i; break; }
            }
            if (at < 0) continue;
            int from = Math.max(0, Math.min(at - 1, t.length() - 3)), to = Math.min(t.length(), from + 3);
            JSONArray rows = new JSONArray();
            for (int i = from; i < to; i++) {
                JSONObject r = t.getJSONObject(i), tm = r.optJSONObject("team");
                rows.put(new JSONObject().put("pos", r.optInt("position", i + 1)).put("name", tm == null ? "" : tm.optString("shortName", tm.optString("name")))
                        .put("played", r.optInt("playedGames")).put("gd", r.optInt("goalDifference")).put("pts", r.optInt("points")).put("ours", i == at));
            }
            JSONObject comp = standings.optJSONObject("competition");
            return new JSONObject().put("league", comp == null ? "" : league(comp.optString("name"))).put("rows", rows);
        }
        return null;
    }

    /** The last weather answer, for the Fan Hub screen's Trails card (Store keeps it); null until one comes. */
    static volatile JSONObject lastForecast;

    /** One weekend day's forecast for the Trails card. */
    static final class Day {
        final String label, sunrise;
        final long max, min;
        final int rain, code;
        final long date;
        boolean best;
        Day(String label, long date, long max, long min, int rain, int code, String sunrise) {
            this.label = label; this.date = date; this.max = max; this.min = min; this.rain = rain; this.code = code; this.sunrise = sunrise;
        }
    }

    /**
     * The coming Saturday and Sunday (today included) from a forecast, the better one for a hike marked: less
     * chance of rain, then the cooler. Empty when the forecast has neither.
     */
    static List<Day> weekend(JSONObject r, long now) {
        List<Day> out = new ArrayList<>();
        JSONObject d = r == null ? null : r.optJSONObject("daily");
        JSONArray days = d == null ? null : d.optJSONArray("time");
        if (days == null) return out;
        JSONArray max = d.optJSONArray("temperature_2m_max"), min = d.optJSONArray("temperature_2m_min"),
                rain = d.optJSONArray("precipitation_probability_max"), code = d.optJSONArray("weather_code"), rise = d.optJSONArray("sunrise");
        java.time.ZoneOffset zone = java.time.ZoneOffset.ofTotalSeconds(r.optInt("utc_offset_seconds"));
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int i = 0; i < days.length() && out.size() < 2; i++) {
            LocalDate day;
            try { day = LocalDate.parse(days.optString(i)); } catch (RuntimeException e) { continue; }
            if (day.isBefore(today) || day.isAfter(today.plusDays(6))) continue;
            if (day.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && day.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) continue;
            String sr = rise == null ? "" : rise.optString(i);
            out.add(new Day(day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                    day.atStartOfDay(zone).toInstant().toEpochMilli(),
                    max == null ? 0 : Math.round(max.optDouble(i)), min == null ? 0 : Math.round(min.optDouble(i)),
                    rain == null ? 0 : rain.optInt(i), code == null ? 0 : code.optInt(i), sr.length() >= 16 ? sr.substring(11, 16) : ""));
        }
        Day best = null;
        for (Day x : out) if (best == null || x.rain < best.rain || x.rain == best.rain && x.max < best.max) best = x;
        if (best != null) best.best = true;
        return out;
    }

    /** WMO weather code in words, short: "Clear", "Showers", "Thunder" and so on. */
    static String sky(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunder";
        return "Mixed";
    }

    // ---- the weekend hike forecast -------------------------------------------------------------------------------

    static final class Place {
        final String id, name;
        final double lat, lon;
        Place(String id, String name, double lat, double lon) { this.id = id; this.name = name; this.lat = lat; this.lon = lon; }
    }

    /** The mockup's places in the Sahyadris, and the two cities. */
    static final Place[] PLACES = {
            new Place("rajmachi", "Rajmachi", 18.83, 73.4), new Place("lonavala", "Lonavala", 18.75, 73.41),
            new Place("sinhagad", "Sinhagad", 18.37, 73.76), new Place("harishchandragad", "Harishchandragad", 19.39, 73.78),
            new Place("kalsubai", "Kalsubai", 19.6, 73.71), new Place("mumbai", "Mumbai", 19.08, 72.88), new Place("pune", "Pune", 18.52, 73.86)};

    static Place place(String id) {
        for (Place p : PLACES) if (p.id.equals(id)) return p;
        return PLACES[0];
    }

    /**
     * From Friday noon to Sunday noon, the better of Saturday and Sunday for a hike (less chance of rain, then the
     * cooler), as a turn that counts down to 6 in the morning and is on until noon. Open-Meteo answers in the place's
     * own time zone (timezone=auto), with its UTC offset.
     */
    static Turn hikeTurn(String place, JSONObject r, long now) {
        JSONObject d = r == null ? null : r.optJSONObject("daily");
        JSONArray days = d == null ? null : d.optJSONArray("time");
        JSONArray temp = d == null ? null : d.optJSONArray("temperature_2m_max");
        JSONArray rain = d == null ? null : d.optJSONArray("precipitation_probability_max");
        if (days == null || temp == null || rain == null) return null;
        java.time.ZoneOffset zone = java.time.ZoneOffset.ofTotalSeconds(r.optInt("utc_offset_seconds"));
        java.time.ZonedDateTime local = Instant.ofEpochMilli(now).atZone(zone);
        java.time.DayOfWeek dow = local.getDayOfWeek();
        boolean window = (dow == java.time.DayOfWeek.FRIDAY && local.getHour() >= 12) || dow == java.time.DayOfWeek.SATURDAY
                || (dow == java.time.DayOfWeek.SUNDAY && local.getHour() < 12);
        if (!window) return null;
        int best = -1;
        for (int i = 0; i < days.length(); i++) {
            LocalDate day = LocalDate.parse(days.optString(i));
            if (day.isBefore(local.toLocalDate()) || day.isAfter(local.toLocalDate().plusDays(2))) continue;
            if (day.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && day.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) continue;
            long end = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
            if (end <= now) continue;
            if (best < 0 || rain.optInt(i) < rain.optInt(best) || (rain.optInt(i) == rain.optInt(best) && temp.optDouble(i) < temp.optDouble(best))) best = i;
        }
        if (best < 0) return null;
        LocalDate day = LocalDate.parse(days.optString(best));
        String top = ("Hike \u00B7 " + place + " \u00B7 " + day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " "
                + Math.round(temp.optDouble(best)) + "\u00B0 \u00B7 " + rain.optInt(best) + "% rain").toUpperCase(Locale.ROOT);
        return new Turn(top, "Set off", day.atTime(6, 0).atZone(zone).toInstant().toEpochMilli(),
                day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), 0xff3ddc84, false);
    }

    static List<Turn> flatten(Map<String, List<Turn>> bySource, long now) {
        List<Turn> all = new ArrayList<>();
        for (List<Turn> l : bySource.values()) for (Turn t : l) if (t.end > now) all.add(t);
        all.sort(Comparator.comparingLong(t -> t.start));
        return all;
    }

    /** When to fetch again: every 2 minutes while something is live or about to start, else every 30. */
    static long refreshAfter(List<Turn> turns, long now) {
        for (Turn t : turns) if (t.start - now < 15 * MIN && t.end > now) return 2 * MIN;
        return 30 * MIN;
    }

    static String when(long ms) { return Instant.ofEpochMilli(ms).toString(); }
}
