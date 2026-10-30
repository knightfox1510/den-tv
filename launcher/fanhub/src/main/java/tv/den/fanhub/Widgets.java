package tv.den.fanhub;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Widgets: small lines of your own on Home (along the bottom, taking turns with "Did you know?") and on the Fan Hub
 * (a card): market prices (indices, shares, gold, currencies), crypto, a team's fixtures and results, the next
 * Formula 1 race, the weather now, the air quality, world clocks, a countdown (or a yearly one, for birthdays),
 * holidays or your own calendar, headlines on a topic, "on this day", or a note. Set up on the phone (Tweak,
 * Widgets). The Fan Hub fetches what needs the internet while the TV is on; Home reads the lines from
 * WidgetsProvider and stays offline. None of them need a key.
 *
 * Market and crypto prices come from Yahoo Finance's public chart data (can be delayed, and it isn't an official API,
 * so it may change); fixtures from ESPN's public scoreboards (the same: unofficial); Formula 1 from Jolpica (the
 * Ergast API's successor); weather and air quality from Open-Meteo; holidays from Google Calendar's public holiday
 * calendars; headlines from Google News' RSS; "on this day" from Wikipedia.
 */
final class Widgets {
    private static final String PREFS = "widgets";
    static final int MAX = 8;
    static final List<String> KINDS = java.util.Arrays.asList("markets", "crypto", "fixtures", "f1", "weather", "air", "clocks", "countdown",
            "calendar", "headlines", "onthisday", "note");
    private static final long MIN = 60_000L;
    /** Google Calendar's public holiday calendars, by country. */
    static final String[][] HOLIDAYS = {{"in", "India", "en.indian"}, {"us", "USA", "en.usa"}, {"gb", "UK", "en.uk"}, {"au", "Australia", "en.australian"},
            {"ca", "Canada", "en.canadian"}, {"sg", "Singapore", "en.singapore"}, {"nz", "New Zealand", "en.new_zealand"}, {"ie", "Ireland", "en.irish"},
            {"ae", "UAE", "en.ae"}, {"pk", "Pakistan", "en.pk"}};
    /** ESPN's sports, by the number its team ids start with (s:600~t:83 is Barcelona's football team). */
    static final String[][] SPORTS = {{"600", "soccer"}, {"40", "basketball"}, {"20", "football"}, {"70", "hockey"}, {"1", "baseball"}, {"200", "cricket"}};
    /** Familiar names for common symbols; anything else shows the market's short name. */
    static final String[][] NAMES = {{"^BSESN", "SENSEX"}, {"^NSEI", "NIFTY 50"}, {"^NSEBANK", "BANK NIFTY"}, {"^CNXIT", "NIFTY IT"},
            {"^GSPC", "S&P 500"}, {"^IXIC", "NASDAQ"}, {"^DJI", "DOW"}, {"^FTSE", "FTSE 100"}, {"^N225", "NIKKEI"}, {"GC=F", "GOLD"},
            {"SI=F", "SILVER"}, {"CL=F", "CRUDE"}, {"INR=X", "USD/INR"}, {"EURINR=X", "EUR/INR"}, {"BTC-USD", "BITCOIN"}, {"ETH-USD", "ETHER"}};

    private Widgets() {}

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static JSONArray list(Context ctx) {
        try { return new JSONArray(prefs(ctx).getString("list", "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    private static void save(Context ctx, JSONArray a) { prefs(ctx).edit().putString("list", a.toString()).apply(); }

    // ---- setting them up -----------------------------------------------------------------------------------------

    /** Adds a widget from the phone, checked and cleaned; null when it isn't one den-tv can show. */
    static JSONObject add(Context ctx, JSONObject in) throws JSONException {
        JSONArray all = list(ctx);
        if (all.length() >= MAX) return null;
        JSONObject w = clean(in);
        if (w == null) return null;
        w.put("id", "w" + System.currentTimeMillis());
        all.put(w);
        save(ctx, all);
        Store.worker().post(() -> refresh(ctx, w.optString("id")));
        return w;
    }

    /** What a widget is, from the phone's fields, or null. where: home, hub or both. */
    static JSONObject clean(JSONObject in) throws JSONException {
        String kind = in.optString("kind");
        if (!KINDS.contains(kind)) return null;
        String where = in.optString("where", "both");
        JSONObject w = new JSONObject().put("kind", kind).put("where", where.matches("home|hub|both") ? where : "both");
        switch (kind) {
            case "markets": {
                JSONArray syms = new JSONArray();
                JSONArray s = in.optJSONArray("symbols");
                for (int i = 0; s != null && i < s.length() && syms.length() < 6; i++) {
                    String sym = s.optString(i).trim().toUpperCase(Locale.ROOT);
                    if (sym.matches("[A-Z0-9^=.\\-&]{1,20}")) syms.put(sym);
                }
                if (syms.length() == 0) return null;
                return w.put("symbols", syms);
            }
            case "crypto": {
                JSONArray coins = new JSONArray();
                JSONArray c = in.optJSONArray("coins");
                for (int i = 0; c != null && i < c.length() && coins.length() < 6; i++) {
                    String coin = c.optString(i).trim().toUpperCase(Locale.ROOT);
                    if (coin.matches("[A-Z0-9]{2,10}")) coins.put(coin);
                }
                if (coins.length() == 0) return null;
                String cur = in.optString("currency", "INR").toUpperCase(Locale.ROOT);
                return w.put("coins", coins).put("currency", cur.matches("INR|USD|EUR|GBP|AED") ? cur : "INR");
            }
            case "fixtures": {
                String sport = in.optString("sport"), league = in.optString("league"), team = in.optString("team"), name = Gemini.clean(in.optString("name"), 30);
                boolean known = false;
                for (String[] sp : SPORTS) if (sp[1].equals(sport)) known = true;
                if (!known || !team.matches("[0-9]{1,8}") || name.isEmpty()) return null;
                if (!sport.equals("cricket") && !league.matches("[a-z0-9._-]{1,40}")) return null;
                return w.put("sport", sport).put("league", sport.equals("cricket") ? "" : league).put("team", team).put("name", name);
            }
            case "f1": return w;
            case "onthisday": return w;
            case "weather":
            case "air": {
                double lat = in.optDouble("lat", Double.NaN), lon = in.optDouble("lon", Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
                return w.put("place", Gemini.clean(in.optString("place"), 30)).put("lat", lat).put("lon", lon);
            }
            case "calendar": {
                // A country's holidays, or a calendar's own address ("Secret address in iCal format" in Google Calendar).
                String country = in.optString("country"), url = in.optString("url").trim();
                for (String[] h : HOLIDAYS) {
                    if (!h[0].equals(country)) continue;
                    return w.put("label", h[1] + " holidays").put("url", "https://calendar.google.com/calendar/ical/" + h[2]
                            + "%23holiday%40group.v.calendar.google.com/public/basic.ics");
                }
                if (url.startsWith("webcal://")) url = "https://" + url.substring(9);
                if (!url.matches("https://[^\\s]{4,1000}")) return null;
                String label = Gemini.clean(in.optString("label"), 30);
                return w.put("label", label.isEmpty() ? "Calendar" : label).put("url", url);
            }
            case "clocks": {
                JSONArray zones = new JSONArray();
                JSONArray z = in.optJSONArray("zones");
                for (int i = 0; z != null && i < z.length() && zones.length() < 4; i++) {
                    JSONObject one = z.optJSONObject(i);
                    if (one == null) continue;
                    try { ZoneId.of(one.optString("tz")); } catch (RuntimeException e) { continue; }
                    zones.put(new JSONObject().put("label", Gemini.clean(one.optString("label"), 20)).put("tz", one.optString("tz")));
                }
                if (zones.length() == 0) return null;
                return w.put("zones", zones);
            }
            case "countdown": {
                String label = Gemini.clean(in.optString("label"), 40), date = in.optString("date");
                try { LocalDate.parse(date); } catch (RuntimeException e) { return null; }
                if (label.isEmpty()) return null;
                // Yearly: a birthday or an anniversary, counted to its next one.
                return w.put("label", label).put("date", date).put("yearly", in.optBoolean("yearly"));
            }
            case "headlines": {
                String topic = Gemini.clean(in.optString("topic"), 60);
                if (topic.isEmpty()) return null;
                return w.put("topic", topic);
            }
            default: {
                String text = Gemini.clean(in.optString("text"), 140);
                if (text.isEmpty()) return null;
                return w.put("text", text);
            }
        }
    }

    static void remove(Context ctx, String id) {
        JSONArray all = list(ctx), out = new JSONArray();
        for (int i = 0; i < all.length(); i++) if (!id.equals(all.optJSONObject(i).optString("id"))) out.put(all.optJSONObject(i));
        save(ctx, out);
        prefs(ctx).edit().remove("data:" + id).apply();
        changed(ctx);
    }

    // ---- fetching ------------------------------------------------------------------------------------------------

    /** PhoneService's tick: while the screen is on, fetch what's due (markets every 5 minutes, the rest every 30). */
    static void tick(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        JSONArray all = list(ctx);
        long now = System.currentTimeMillis();
        for (int i = 0; i < all.length(); i++) {
            JSONObject w = all.optJSONObject(i);
            long every = every(w.optString("kind"));
            if (every < 0) continue;
            if (now - prefs(ctx).getLong("at:" + w.optString("id"), 0) >= every) refresh(ctx, w.optString("id"));
        }
    }

    /** How often a kind is fetched while the TV is on; -1 for those worked out on the TV (clocks, countdowns, notes). */
    static long every(String kind) {
        switch (kind) {
            case "markets": case "crypto": return 5 * MIN;
            case "fixtures": return 10 * MIN;
            case "weather": case "air": case "headlines": return 30 * MIN;
            case "calendar": return 60 * MIN;
            case "f1": return 60 * MIN;
            case "onthisday": return 6 * 60 * MIN;
            default: return -1;
        }
    }

    /** Fetches one widget's data now. */
    static void refresh(Context ctx, String id) {
        JSONArray all = list(ctx);
        for (int i = 0; i < all.length(); i++) {
            JSONObject w = all.optJSONObject(i);
            if (!id.equals(w.optString("id"))) continue;
            JSONArray lines;
            try {
                switch (w.optString("kind")) {
                    case "markets": lines = markets(w.getJSONArray("symbols")); break;
                    case "crypto": lines = crypto(w.getJSONArray("coins"), w.optString("currency", "INR")); break;
                    case "fixtures": lines = fixtures(w, System.currentTimeMillis(), ZoneId.systemDefault()); break;
                    case "f1": lines = f1(new JSONObject(fetch("https://api.jolpi.ca/ergast/f1/current/next.json")), ZoneId.systemDefault()); break;
                    case "weather": lines = weather(w); break;
                    case "air": lines = air(w); break;
                    case "calendar": lines = events(fetch(w.optString("url"), 6_000_000), System.currentTimeMillis(), ZoneId.systemDefault()); break;
                    case "headlines": lines = headlines(w.optString("topic")); break;
                    case "onthisday": lines = onThisDay(System.currentTimeMillis()); break;
                    default: return;
                }
            } catch (IOException | JSONException | RuntimeException e) {
                prefs(ctx).edit().putLong("at:" + id, System.currentTimeMillis()).apply();   // try again at the next turn, not at once
                return;
            }
            prefs(ctx).edit().putString("data:" + id, lines.toString()).putLong("at:" + id, System.currentTimeMillis()).apply();
            changed(ctx);
        }
    }

    private static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(WidgetsProvider.HOME, null);
    }

    /** Each symbol's price and the day's change ("SENSEX 74,314 ▲0.22%"): Home runs them on one line, the Fan Hub lists them. */
    static JSONArray markets(JSONArray symbols) throws IOException, JSONException {
        JSONArray parts = new JSONArray();
        for (int i = 0; i < symbols.length(); i++) {
            String sym = symbols.getString(i);
            JSONObject meta = new JSONObject(fetch("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8")
                    + "?range=1d&interval=15m")).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta");
            parts.put(quote(sym, meta));
        }
        return parts;
    }

    /** "NIFTY 50 22,982.50 ▲0.20%" from a symbol and its chart meta. */
    static String quote(String sym, JSONObject meta) { return quote(sym, meta, null); }

    static String quote(String sym, JSONObject meta, String name) {
        double price = meta.optDouble("regularMarketPrice", Double.NaN), prev = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN));
        if (name == null) for (String[] n : NAMES) if (n[0].equalsIgnoreCase(sym)) name = n[1];
        if (name == null) {
            String s = meta.optString("shortName", "");
            name = !s.isEmpty() && s.length() <= 18 ? s : sym.replaceAll("\\.(NS|BO)$", "");
        }
        // ICU's, which knows Indian grouping (1,23,456) for rupees.
        android.icu.text.NumberFormat f = android.icu.text.NumberFormat.getNumberInstance("INR".equals(meta.optString("currency")) ? new Locale("en", "IN") : Locale.US);
        int digits = price < 1 ? 4 : price < 10000 ? 2 : 0;   // a coin's fractions; a share's paise and cents; an index's points
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        String out = name.toUpperCase(Locale.ROOT) + " " + (Double.isNaN(price) ? "–" : f.format(price));
        if (!Double.isNaN(price) && !Double.isNaN(prev) && prev != 0) {
            double ch = (price - prev) / prev * 100;
            out += " " + (ch >= 0 ? "▲" : "▼") + String.format(Locale.ROOT, "%.2f%%", Math.abs(ch));
        }
        return out;
    }

    /** Each coin in the chosen currency ("BTC ₹74,77,458 ▲1.02%"), from Yahoo's BTC-INR and the like. */
    static JSONArray crypto(JSONArray coins, String currency) throws IOException, JSONException {
        JSONArray parts = new JSONArray();
        for (int i = 0; i < coins.length(); i++) {
            String coin = coins.getString(i), sym = coin + "-" + currency;
            JSONObject meta = new JSONObject(fetch("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8")
                    + "?range=1d&interval=15m")).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta");
            parts.put(quote(sym, meta, coin).replaceFirst(" ", " " + symbol(currency)));
        }
        return parts;
    }

    private static String symbol(String currency) {
        switch (currency) { case "INR": return "₹"; case "USD": return "$"; case "EUR": return "€"; case "GBP": return "£"; default: return currency + " "; }
    }

    // ---- fixtures (ESPN) -----------------------------------------------------------------------------------------

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH),
            TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** "Today 8:30 PM", "Tomorrow 1:30 AM" or "Sat 31 Oct, 8:30 PM", in the TV's time. */
    static String when(long at, long now, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(at).atZone(zone);
        long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), t.toLocalDate());
        return days == 0 ? "Today " + t.format(TIME) : days == 1 ? "Tomorrow " + t.format(TIME) : t.format(DAY);
    }

    private static long time(String iso) {
        try { return Instant.parse(iso.matches(".*T\\d\\d:\\d\\dZ$") ? iso.replace("Z", ":00Z") : iso).toEpochMilli(); }
        catch (RuntimeException e) { return -1; }
    }

    private static JSONArray fixtures(JSONObject w, long now, ZoneId zone) throws IOException, JSONException {
        if (w.optString("sport").equals("cricket"))
            return cricket(new JSONObject(fetch("https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket&lang=en&region=in")), w.optString("name"), now, zone);
        String base = "https://site.api.espn.com/apis/site/v2/sports/" + w.optString("sport") + "/" + w.optString("league") + "/teams/" + w.optString("team") + "/schedule";
        return schedule(new JSONObject(fetch(base)), new JSONObject(fetch(base + "?fixture=true")), w.optString("team"), now, zone);
    }

    /**
     * A team's matches from ESPN's schedules: one being played now with its score, the next three, and the last
     * result. past: the season so far; next: the fixtures to come.
     */
    static JSONArray schedule(JSONObject past, JSONObject next, String team, long now, ZoneId zone) {
        JSONArray out = new JSONArray();
        String last = null;
        long lastAt = Long.MIN_VALUE;
        List<String> coming = new ArrayList<>();
        for (JSONObject sched : new JSONObject[]{past, next}) {
            JSONArray events = sched == null ? null : sched.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i), c = e == null ? null : first(e.optJSONArray("competitions"));
                if (c == null) continue;
                JSONObject type = c.optJSONObject("status") == null ? new JSONObject() : c.optJSONObject("status").optJSONObject("type");
                String state = type == null ? "pre" : type.optString("state", "pre");
                JSONObject home = null, away = null;
                JSONArray cs = c.optJSONArray("competitors");
                for (int k = 0; cs != null && k < cs.length(); k++) {
                    JSONObject t = cs.optJSONObject(k);
                    if ("home".equals(t.optString("homeAway"))) home = t; else away = t;
                }
                if (home == null || away == null) continue;
                String h = FanHub.display(teamName(home)), a = FanHub.display(teamName(away));
                long at = time(e.optString("date"));
                if (state.equals("post")) {
                    // ESPN lists a season newest first; the latest by date is the last result.
                    if (at < lastAt) continue;
                    lastAt = at;
                    last = h + " " + score(home) + "–" + score(away) + " " + a;
                } else if (state.equals("in")) {
                    String clock = type.optString("shortDetail", "");
                    out.put("LIVE  " + h + " " + score(home) + "–" + score(away) + " " + a + (clock.isEmpty() ? "" : "  ·  " + clock));
                } else if (at > now - 3 * 3_600_000L && coming.size() < 3 && !coming.contains(h + a + at)) {
                    coming.add(h + a + at);
                    out.put(when(at, now, zone) + "  ·  " + h + " v " + a);
                }
            }
        }
        if (last != null) out.put("Last: " + last);
        return out;
    }

    private static JSONObject first(JSONArray a) { return a == null || a.length() == 0 ? null : a.optJSONObject(0); }

    private static String teamName(JSONObject competitor) {
        JSONObject t = competitor.optJSONObject("team");
        return t == null ? "" : t.optString("shortDisplayName", t.optString("displayName"));
    }

    private static String score(JSONObject competitor) {
        Object s = competitor.opt("score");
        if (s instanceof JSONObject) return ((JSONObject) s).optString("displayValue", "");
        return s == null ? "" : String.valueOf(s);
    }

    /** A cricket team's matches on now, or just played or about to start, from ESPN's cricket scoreboard. */
    static JSONArray cricket(JSONObject header, String team, long now, ZoneId zone) {
        JSONArray out = new JSONArray();
        JSONArray sports = header.optJSONArray("sports");
        for (int s = 0; sports != null && s < sports.length(); s++) {
            JSONArray leagues = sports.optJSONObject(s).optJSONArray("leagues");
            for (int l = 0; leagues != null && l < leagues.length(); l++) {
                JSONArray events = leagues.optJSONObject(l).optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    JSONArray cs = e.optJSONArray("competitors");
                    boolean ours = false;
                    List<String> sides = new ArrayList<>(), scored = new ArrayList<>();
                    for (int k = 0; cs != null && k < cs.length(); k++) {
                        JSONObject c = cs.optJSONObject(k);
                        String name = c.optString("displayName");
                        if (name.equalsIgnoreCase(team)) ours = true;
                        sides.add(name);
                        String sc = c.optString("score", "");
                        scored.add(sc.isEmpty() ? name : name + " " + sc);
                    }
                    if (!ours || sides.size() < 2) continue;
                    String what = e.optString("title", "").replaceAll("\\s*\\(.*\\)$", "");
                    String status = e.optString("status");
                    if (status.equals("in")) out.put("LIVE  " + String.join("  ·  ", scored));
                    else if (status.equals("post")) out.put(e.optString("summary", String.join(" v ", sides)).replaceAll("\\s+", " ").trim());
                    else out.put(when(time(e.optString("date")), now, zone) + "  ·  " + String.join(" v ", sides) + (what.isEmpty() ? "" : " (" + what + ")"));
                }
            }
        }
        return out;
    }

    /** "Mexico City GP  ·  Sun 2 Nov, 1:30 AM" from Jolpica's next race. */
    static JSONArray f1(JSONObject r, ZoneId zone) {
        JSONArray out = new JSONArray();
        JSONObject race = first(r.optJSONObject("MRData") == null ? null : r.optJSONObject("MRData").optJSONObject("RaceTable").optJSONArray("Races"));
        if (race == null) return out;
        String name = race.optString("raceName").replace("Grand Prix", "GP");
        String time = race.optString("time", "12:00:00Z");
        long at = time(race.optString("date") + "T" + (time.endsWith("Z") ? time : time + "Z"));
        out.put(name + (at < 0 ? "" : "  ·  " + when(at, System.currentTimeMillis(), zone)));
        return out;
    }

    private static JSONArray weather(JSONObject w) throws IOException, JSONException {
        JSONObject r = new JSONObject(fetch("https://api.open-meteo.com/v1/forecast?latitude=" + w.getDouble("lat") + "&longitude=" + w.getDouble("lon")
                + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"));
        JSONObject cur = r.getJSONObject("current");
        JSONObject d = r.getJSONObject("daily");
        String line = w.optString("place") + "  " + Math.round(cur.getDouble("temperature_2m")) + "°  " + FanHub.sky(cur.optInt("weather_code"))
                + "  ·  " + Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)) + "° / " + Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
        return new JSONArray().put(line.trim());
    }

    private static JSONArray air(JSONObject w) throws IOException, JSONException {
        JSONObject cur = new JSONObject(fetch("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + w.getDouble("lat") + "&longitude="
                + w.getDouble("lon") + "&current=us_aqi,pm2_5")).getJSONObject("current");
        return new JSONArray().put(airLine(w.optString("place"), cur.optInt("us_aqi", -1), cur.optDouble("pm2_5", Double.NaN)));
    }

    /** "Delhi air 176, unhealthy  ·  PM2.5 98" on the US AQI scale. */
    static String airLine(String place, int aqi, double pm25) {
        if (aqi < 0) return place + " air: no reading now";
        String says = aqi <= 50 ? "good" : aqi <= 100 ? "moderate" : aqi <= 150 ? "unhealthy for some" : aqi <= 200 ? "unhealthy" : aqi <= 300 ? "very unhealthy" : "hazardous";
        return (place + " air " + aqi + ", " + says + (Double.isNaN(pm25) ? "" : "  ·  PM2.5 " + Math.round(pm25))).trim();
    }

    /** Up to three of Wikipedia's "on this day" events, shortest first, so each fits a line. */
    static JSONArray onThisDay(long now) throws IOException, JSONException {
        LocalDate d = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        return parseOnThisDay(new JSONObject(fetch(String.format(Locale.ROOT, "https://api.wikimedia.org/feed/v1/wikipedia/en/onthisday/selected/%02d/%02d",
                d.getMonthValue(), d.getDayOfMonth()))));
    }

    static JSONArray parseOnThisDay(JSONObject r) {
        List<String> all = new ArrayList<>();
        JSONArray sel = r.optJSONArray("selected");
        for (int i = 0; sel != null && i < sel.length(); i++) {
            JSONObject x = sel.optJSONObject(i);
            String text = x.optString("text").trim();
            if (!text.isEmpty() && text.length() <= 150) all.add(x.optInt("year") + ": " + text);
        }
        all.sort((a, b) -> a.length() - b.length());
        JSONArray out = new JSONArray();
        for (int i = 0; i < all.size() && i < 3; i++) out.put(all.get(i));
        return out;
    }

    // ---- calendars (iCalendar) -----------------------------------------------------------------------------------

    /**
     * The coming events within 60 days (up to 12, soonest first) from a calendar's .ics text: {at, day (all day),
     * what}. Repeating events (yearly birthdays, weekly classes) repeat; their one-off changes are not followed.
     */
    static JSONArray events(String ics, long now, ZoneId zone) {
        String text = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "");
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), end = today.plusDays(60);
        List<Object[]> found = new ArrayList<>();   // {start ms, all day, summary}
        for (String block : text.split("BEGIN:VEVENT")) {
            int stop = block.indexOf("END:VEVENT");
            if (stop < 0) continue;
            String summary = "", start = "", tzid = "", rrule = "";
            for (String line : block.substring(0, stop).split("\n")) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String name = line.substring(0, colon), value = line.substring(colon + 1).trim();
                if (name.equals("SUMMARY") || name.startsWith("SUMMARY;")) summary = value.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ");
                else if (name.equals("DTSTART") || name.startsWith("DTSTART;")) {
                    start = value;
                    Matcher tz = Pattern.compile("TZID=([^;:]+)").matcher(name);
                    if (tz.find()) tzid = tz.group(1);
                } else if (name.equals("RRULE")) rrule = value;
                else if (name.equals("STATUS") && value.equals("CANCELLED")) summary = "";
            }
            if (summary.isEmpty() || start.length() < 8) continue;
            boolean allDay = start.length() == 8;
            ZonedDateTime first;
            try {
                LocalDate date = LocalDate.parse(start.substring(0, 4) + "-" + start.substring(4, 6) + "-" + start.substring(6, 8));
                if (allDay) first = date.atStartOfDay(zone);
                else {
                    java.time.LocalTime t = java.time.LocalTime.of(Integer.parseInt(start.substring(9, 11)), Integer.parseInt(start.substring(11, 13)));
                    ZoneId from = start.endsWith("Z") ? java.time.ZoneOffset.UTC : tzid.isEmpty() ? zone : ZoneId.of(tzid);
                    first = date.atTime(t).atZone(from).withZoneSameInstant(zone);
                }
            } catch (RuntimeException bad) {
                continue;
            }
            ZonedDateTime next = nextOccurrence(first, rrule, today, allDay ? today.atStartOfDay(zone).toInstant().toEpochMilli() : now);
            if (next == null || next.toLocalDate().isAfter(end)) continue;
            found.add(new Object[]{next.toInstant().toEpochMilli(), allDay, summary});
        }
        found.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
        JSONArray out = new JSONArray();
        for (int i = 0; i < found.size() && out.length() < 12; i++) {
            try { out.put(new JSONObject().put("at", found.get(i)[0]).put("day", found.get(i)[1]).put("what", found.get(i)[2])); }
            catch (JSONException ignored) { }
        }
        return out;
    }

    /** The next three of them as lines, "Diwali  ·  Sun 8 Nov" or "Dentist  ·  Tomorrow 10:00 AM". */
    static List<String> upcoming(JSONArray events, long now, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < events.length() && out.size() < 3; i++) {
            JSONObject e = events.optJSONObject(i);
            long at = e.optLong("at");
            ZonedDateTime t = Instant.ofEpochMilli(at).atZone(zone);
            String day;
            if (e.optBoolean("day")) {
                if (t.toLocalDate().isBefore(today)) continue;
                long days = ChronoUnit.DAYS.between(today, t.toLocalDate());
                day = days == 0 ? "Today" : days == 1 ? "Tomorrow" : t.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH));
            } else {
                if (at < now) continue;
                day = when(at, now, zone);
            }
            String line = e.optString("what") + "  ·  " + day;
            if (!out.contains(line)) out.add(line);
        }
        return out;
    }

    /** The first time a (maybe repeating) event starts at or after `from`, or null. */
    static ZonedDateTime nextOccurrence(ZonedDateTime first, String rrule, LocalDate today, long from) {
        if (first.toInstant().toEpochMilli() >= from) return first;
        if (rrule.isEmpty()) return null;
        String freq = "", until = "";
        int interval = 1, count = -1;
        for (String part : rrule.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length < 2) continue;
            switch (kv[0]) {
                case "FREQ": freq = kv[1]; break;
                case "INTERVAL": try { interval = Math.max(1, Integer.parseInt(kv[1])); } catch (NumberFormatException ignored) { } break;
                case "COUNT": try { count = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) { } break;
                case "UNTIL": until = kv[1]; break;
                default:
            }
        }
        ChronoUnit unit = freq.equals("YEARLY") ? ChronoUnit.YEARS : freq.equals("MONTHLY") ? ChronoUnit.MONTHS : freq.equals("WEEKLY") ? ChronoUnit.WEEKS
                : freq.equals("DAILY") ? ChronoUnit.DAYS : null;
        if (unit == null) return null;
        // Jump close to today, then step.
        long steps = Math.max(0, unit.between(first, Instant.ofEpochMilli(from).atZone(first.getZone())) / interval - 1);
        if (count > 0 && steps >= count) return null;
        ZonedDateTime t = first.plus(steps * interval, unit);
        for (int i = 0; i < 400; i++, steps++) {
            if (count > 0 && steps >= count) return null;
            if (until.length() >= 8 && t.toLocalDate().isAfter(LocalDate.parse(until.substring(0, 4) + "-" + until.substring(4, 6) + "-" + until.substring(6, 8)))) return null;
            if (t.toInstant().toEpochMilli() >= from) return t;
            t = first.plus((steps + 1) * interval, unit);
        }
        return null;
    }

    private static final Pattern ITEM_TITLE = Pattern.compile("<item>.*?<title>(.*?)</title>", Pattern.DOTALL);

    /** The top five headlines on a topic, each its own line ("Sensex ends higher… - The Hindu"). */
    static JSONArray headlines(String topic) throws IOException {
        String rss = fetch("https://news.google.com/rss/search?q=" + URLEncoder.encode(topic, "UTF-8") + "&hl=en-IN&gl=IN&ceid=IN:en");
        return parseHeadlines(rss);
    }

    static JSONArray parseHeadlines(String rss) {
        JSONArray out = new JSONArray();
        Matcher m = ITEM_TITLE.matcher(rss);
        while (m.find() && out.length() < 5) {
            String t = m.group(1).replace("<![CDATA[", "").replace("]]>", "");
            t = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString().trim();
            if (!t.isEmpty()) out.put(t);
        }
        return out;
    }

    private static String fetch(String url) throws IOException { return fetch(url, 2_000_000); }

    private static String fetch(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        // Yahoo turns away requests that don't look like a browser's.
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) den-tv");
        try {
            if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode() + " from " + c.getURL().getHost());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); if (b.size() > max) throw new IOException("too large"); }
                return b.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- what Home and the Fan Hub show --------------------------------------------------------------------------

    /** Every line for a place (home or hub): {label, text}; clocks and countdowns worked out now. */
    static List<String[]> lines(Context ctx, String place, long now) {
        List<String[]> out = new ArrayList<>();
        JSONArray all = list(ctx);
        for (int i = 0; i < all.length(); i++) {
            JSONObject w = all.optJSONObject(i);
            String where = w.optString("where", "both");
            if (!where.equals("both") && !where.equals(place)) continue;
            String kind = w.optString("kind");
            String label = label(w);
            List<String> texts = texts(ctx, w, now);
            // Home's line runs a market's prices together, as a ticker; the Fan Hub's card has room for one a line.
            if ((kind.equals("markets") || kind.equals("crypto")) && place.equals("home") && !texts.isEmpty()) texts = java.util.Collections.singletonList(String.join("     ", texts));
            for (String text : texts) out.add(new String[]{label, text});
        }
        return out;
    }

    static String label(JSONObject w) {
        switch (w.optString("kind")) {
            case "markets": return "MARKETS";
            case "crypto": return "CRYPTO";
            case "fixtures": return w.optString("name").toUpperCase(Locale.ROOT);
            case "f1": return "FORMULA 1";
            case "weather": return "WEATHER";
            case "air": return "AIR QUALITY";
            case "clocks": return "WORLD CLOCK";
            case "countdown": return w.optBoolean("yearly") ? "COMING UP" : "COUNTDOWN";
            case "calendar": return w.optString("label").toUpperCase(Locale.ROOT);
            case "headlines": return w.optString("topic").toUpperCase(Locale.ROOT);
            case "onthisday": return "ON THIS DAY";
            default: return "NOTE";
        }
    }

    static List<String> texts(Context ctx, JSONObject w, long now) {
        List<String> out = new ArrayList<>();
        switch (w.optString("kind")) {
            case "clocks": {
                List<String> parts = new ArrayList<>();
                JSONArray zones = w.optJSONArray("zones");
                for (int i = 0; zones != null && i < zones.length(); i++) {
                    JSONObject z = zones.optJSONObject(i);
                    ZonedDateTime t = Instant.ofEpochMilli(now).atZone(ZoneId.of(z.optString("tz")));
                    parts.add(z.optString("label") + " " + t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
                }
                out.add(String.join("   ·   ", parts));
                break;
            }
            case "countdown": {
                LocalDate when = LocalDate.parse(w.optString("date")), today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
                if (w.optBoolean("yearly")) {
                    when = when.withYear(today.getYear());
                    if (when.isBefore(today)) when = when.plusYears(1);
                }
                long days = ChronoUnit.DAYS.between(today, when);
                if (days >= 0) out.add(w.optString("label") + (days == 0 ? ": today!" : days == 1 ? ": tomorrow" : " in " + days + " days"));
                break;
            }
            case "note": out.add(w.optString("text")); break;
            case "calendar": {
                // The coming events are kept as fetched; how they read is worked out now, so "Tomorrow" stays right.
                try { out.addAll(upcoming(new JSONArray(prefs(ctx).getString("data:" + w.optString("id"), "[]")), now, ZoneId.systemDefault())); }
                catch (JSONException ignored) { }
                break;
            }
            default: {
                try {
                    JSONArray data = new JSONArray(prefs(ctx).getString("data:" + w.optString("id"), "[]"));
                    for (int i = 0; i < data.length(); i++) out.add(data.optString(i));
                } catch (JSONException ignored) { }
            }
        }
        return out;
    }

    /** For the phone: the widgets with what each shows now. */
    static JSONObject status(Context ctx) throws JSONException {
        JSONArray all = list(ctx), out = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < all.length(); i++) {
            JSONObject w = new JSONObject(all.optJSONObject(i).toString());
            w.remove("url");   // a private calendar's address stays on the TV
            w.put("shows", new JSONArray(texts(ctx, all.optJSONObject(i), now)));
            out.put(w);
        }
        return new JSONObject().put("widgets", out).put("max", MAX);
    }

    // ---- searching, for the phone --------------------------------------------------------------------------------

    /** Shares, indices, funds and currencies matching a name ("tata motors": TMCV.NS ...). */
    static JSONArray symbols(String q) throws IOException, JSONException {
        JSONObject r = new JSONObject(fetch("https://query1.finance.yahoo.com/v1/finance/search?q=" + URLEncoder.encode(q, "UTF-8") + "&quotesCount=8&newsCount=0"));
        JSONArray out = new JSONArray(), quotes = r.optJSONArray("quotes");
        for (int i = 0; quotes != null && i < quotes.length(); i++) {
            JSONObject x = quotes.getJSONObject(i);
            if (x.optString("symbol").isEmpty()) continue;
            out.put(new JSONObject().put("symbol", x.optString("symbol")).put("name", x.optString("shortname", x.optString("longname")))
                    .put("exchange", x.optString("exchDisp", x.optString("exchange"))).put("type", x.optString("quoteType")));
        }
        return out;
    }

    /** Teams matching a name across ESPN's sports (football, cricket, basketball, American football, hockey, baseball). */
    static JSONArray teams(String q) throws IOException, JSONException {
        JSONObject r = new JSONObject(fetch("https://site.web.api.espn.com/apis/search/v2?limit=10&type=team&query=" + URLEncoder.encode(q, "UTF-8")));
        JSONArray out = new JSONArray(), results = r.optJSONArray("results");
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONArray contents = results.getJSONObject(i).optJSONArray("contents");
            for (int k = 0; contents != null && k < contents.length() && out.length() < 10; k++) {
                JSONObject t = contents.getJSONObject(k);
                Matcher m = Pattern.compile("^s:(\\d+)~(?:l:\\d+~)?t:(\\d+)$").matcher(t.optString("uid"));
                if (!m.find()) continue;
                String sport = null;
                for (String[] sp : SPORTS) if (sp[0].equals(m.group(1))) sport = sp[1];
                if (sport == null || (!sport.equals("cricket") && t.optString("defaultLeagueSlug").isEmpty())) continue;
                out.put(new JSONObject().put("name", t.optString("displayName")).put("sport", sport).put("league", t.optString("defaultLeagueSlug"))
                        .put("team", m.group(2)).put("where", t.optString("subtitle", t.optString("description", ""))));
            }
        }
        return out;
    }

    /** Places matching a name, for the weather (Open-Meteo's geocoding). */
    static JSONArray places(String q) throws IOException, JSONException {
        JSONObject r = new JSONObject(fetch("https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&name=" + URLEncoder.encode(q, "UTF-8")));
        JSONArray out = new JSONArray(), res = r.optJSONArray("results");
        for (int i = 0; res != null && i < res.length(); i++) {
            JSONObject x = res.getJSONObject(i);
            out.put(new JSONObject().put("place", x.optString("name")).put("lat", x.optDouble("latitude")).put("lon", x.optDouble("longitude"))
                    .put("where", (x.optString("admin1") + ", " + x.optString("country")).replaceAll("^, ", "")).put("tz", x.optString("timezone")));
        }
        return out;
    }
}
