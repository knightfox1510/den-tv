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
 * (a card): market prices (indices, shares, gold, currencies, crypto), the weather now, world clocks, a countdown,
 * headlines on a topic, or a note. Set up on the phone (Tweak, Widgets). The Fan Hub fetches what needs the
 * internet while the TV is on; Home reads the lines from WidgetsProvider and stays offline.
 *
 * Market prices come from Yahoo Finance's public chart data (no key; can be delayed, and it isn't an official API, so
 * it may change); the weather from Open-Meteo; headlines from Google News' RSS.
 */
final class Widgets {
    private static final String PREFS = "widgets";
    static final int MAX = 8;
    static final List<String> KINDS = java.util.Arrays.asList("markets", "weather", "clocks", "countdown", "headlines", "note");
    private static final long MARKETS_EVERY = 5 * 60_000L, SLOW_EVERY = 30 * 60_000L;
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
            case "weather": {
                double lat = in.optDouble("lat", Double.NaN), lon = in.optDouble("lon", Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
                return w.put("place", Gemini.clean(in.optString("place"), 30)).put("lat", lat).put("lon", lon);
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
                return w.put("label", label).put("date", date);
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
            String kind = w.optString("kind");
            long every = kind.equals("markets") ? MARKETS_EVERY : kind.equals("weather") || kind.equals("headlines") ? SLOW_EVERY : -1;
            if (every < 0) continue;
            if (now - prefs(ctx).getLong("at:" + w.optString("id"), 0) >= every) refresh(ctx, w.optString("id"));
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
                    case "weather": lines = weather(w); break;
                    case "headlines": lines = headlines(w.optString("topic")); break;
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
    static String quote(String sym, JSONObject meta) {
        double price = meta.optDouble("regularMarketPrice", Double.NaN), prev = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN));
        String name = null;
        for (String[] n : NAMES) if (n[0].equalsIgnoreCase(sym)) name = n[1];
        if (name == null) {
            String s = meta.optString("shortName", "");
            name = !s.isEmpty() && s.length() <= 18 ? s : sym.replaceAll("\\.(NS|BO)$", "");
        }
        // ICU's, which knows Indian grouping (1,23,456) for rupees.
        android.icu.text.NumberFormat f = android.icu.text.NumberFormat.getNumberInstance("INR".equals(meta.optString("currency")) ? new Locale("en", "IN") : Locale.US);
        int digits = price < 10000 ? 2 : 0;   // a share's paise and cents; an index's points
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        String out = name.toUpperCase(Locale.ROOT) + " " + (Double.isNaN(price) ? "–" : f.format(price));
        if (!Double.isNaN(price) && !Double.isNaN(prev) && prev != 0) {
            double ch = (price - prev) / prev * 100;
            out += " " + (ch >= 0 ? "▲" : "▼") + String.format(Locale.ROOT, "%.2f%%", Math.abs(ch));
        }
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

    private static String fetch(String url) throws IOException {
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
                while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); if (b.size() > 2_000_000) throw new IOException("too large"); }
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
            String label = kind.equals("markets") ? "MARKETS" : kind.equals("weather") ? "WEATHER" : kind.equals("clocks") ? "WORLD CLOCK"
                    : kind.equals("countdown") ? "COUNTDOWN" : kind.equals("headlines") ? w.optString("topic").toUpperCase(Locale.ROOT) : "NOTE";
            List<String> texts = texts(ctx, w, now);
            // Home's line runs a market's prices together, as a ticker; the Fan Hub's card has room for one a line.
            if (kind.equals("markets") && place.equals("home") && !texts.isEmpty()) texts = java.util.Collections.singletonList(String.join("     ", texts));
            for (String text : texts) out.add(new String[]{label, text});
        }
        return out;
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
                LocalDate when = LocalDate.parse(w.optString("date"));
                long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(), when);
                if (days >= 0) out.add(w.optString("label") + (days == 0 ? ": today!" : days == 1 ? ": tomorrow" : " in " + days + " days"));
                break;
            }
            case "note": out.add(w.optString("text")); break;
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
            w.put("shows", new JSONArray(texts(ctx, w, now)));
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
