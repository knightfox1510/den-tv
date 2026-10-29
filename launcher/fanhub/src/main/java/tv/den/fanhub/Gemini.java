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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * "Describe a look" on the phone: a sentence ("rainy Tokyo night with Itachi's crows") becomes a look of your own,
 * a base look's wallpaper with parts picked to match. With a Gemini API key (entered on the phone, kept in the Fan
 * Hub's own storage on the TV, sent only to Google in a request header) Gemini picks from den-tv's real looks and
 * parts; every answer is checked against those lists before it goes anywhere. Without a key, or when Gemini can't
 * answer, the words are matched against the looks' names instead.
 */
final class Gemini {
    static final String DEFAULT_MODEL = "gemini-flash-latest";
    private static final int TIMEOUT_MS = 25_000;
    static final String[] PARTS = {"watcher", "clock", "icons", "icon_layout", "focus", "wake", "extras"};

    private Gemini() {}

    /** What Gemini is asked: the description, the looks to start from and the parts each slot may take. */
    static String prompt(String text, JSONArray looks, JSONObject parts) throws JSONException {
        StringBuilder b = new StringBuilder();
        b.append("You design looks for den-tv, an Android TV home screen. A look is a base look (its painted wallpaper) ")
                .append("plus parts: watcher (what follows the selected app), clock style, icons (app icon style), icon_layout, ")
                .append("focus (effect when moving between apps), wake (animation when the TV turns on), extras (small moving things) ")
                .append("and accent (a #RRGGBB highlight colour).\n\nThe person wants: \"").append(text.replace('"', '\'')).append("\"\n\nBase looks (id: name, category):\n");
        for (int i = 0; i < looks.length(); i++) {
            JSONObject l = looks.getJSONObject(i);
            b.append("- ").append(l.optString("id")).append(": ").append(l.optString("name")).append(", ").append(l.optString("category")).append('\n');
        }
        b.append("\nAllowed values for each part:\n");
        for (String p : PARTS) {
            JSONArray a = parts.optJSONArray(p);
            if (a != null) b.append("- ").append(p).append(": ").append(join(a)).append('\n');
        }
        b.append("\nAnswer with JSON only: {\"name\": a short name for the look (2-4 words), \"base\": a base look id, ")
                .append("one value for each part from its list, \"accent\": \"#RRGGBB\", \"why\": one short sentence}. ")
                .append("Use only ids and values from the lists above.");
        return b.toString();
    }

    private static String join(JSONArray a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length(); i++) { if (i > 0) b.append(", "); b.append(a.optString(i)); }
        return b.toString();
    }

    /** Gemini's answer cut down to what den-tv has: an unknown base fails, an unknown part is left out. Null when unusable. */
    static JSONObject check(JSONObject answer, JSONArray looks, JSONObject parts) throws JSONException {
        if (answer == null) return null;
        String base = answer.optString("base");
        boolean known = false;
        for (int i = 0; i < looks.length(); i++) if (looks.getJSONObject(i).optString("id").equals(base)) known = true;
        if (!known) return null;
        String name = answer.optString("name", "My look").replaceAll("[\\p{Cntrl}]", "").trim();
        JSONObject out = new JSONObject().put("base", base).put("name", name.isEmpty() ? "My look" : name.length() > 40 ? name.substring(0, 40) : name);
        for (String p : PARTS) {
            JSONArray allowed = parts.optJSONArray(p);
            String v = answer.optString(p, "");
            if (allowed != null) for (int i = 0; i < allowed.length(); i++) if (allowed.optString(i).equals(v)) out.put(p, v);
        }
        String accent = answer.optString("accent", "");
        if (accent.matches("#[0-9a-fA-F]{6}")) out.put("accent", accent.toLowerCase(Locale.ROOT));
        String why = answer.optString("why", "").replaceAll("[\\p{Cntrl}]", "").trim();
        if (!why.isEmpty()) out.put("why", why.length() > 160 ? why.substring(0, 160) : why);
        return out;
    }

    /** The look whose name, category or id shares the most words with the description; parts left as the look's own. */
    static JSONObject words(String text, JSONArray looks) throws JSONException {
        String[] ws = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        JSONObject best = null;
        int score = 0;
        for (int i = 0; i < looks.length(); i++) {
            JSONObject l = looks.getJSONObject(i);
            String hay = (l.optString("id") + " " + l.optString("name") + " " + l.optString("category")).toLowerCase(Locale.ROOT).replace('-', ' ');
            int s = 0;
            for (String w : ws) if (w.length() > 2 && hay.contains(w)) s += w.length();
            if (s > score) { score = s; best = l; }
        }
        if (best == null) return null;
        return new JSONObject().put("base", best.optString("id")).put("name", best.optString("name") + " (mine)")
                .put("why", "Matched your words to " + best.optString("name") + ".");
    }

    /** Asks Gemini; the checked look, or IOException with a reason a person can act on. */
    static JSONObject ask(String key, String model, String text, JSONArray looks, JSONObject parts) throws IOException, JSONException {
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt(text, looks, parts))))))
                .put("generationConfig", new JSONObject().put("responseMimeType", "application/json").put("temperature", 0.8));
        JSONObject r = post("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent", body.toString(), key);
        JSONArray cands = r.optJSONArray("candidates");
        JSONObject content = cands == null || cands.length() == 0 ? null : cands.getJSONObject(0).optJSONObject("content");
        JSONArray ps = content == null ? null : content.optJSONArray("parts");
        String answer = ps == null || ps.length() == 0 ? "" : ps.getJSONObject(0).optString("text");
        JSONObject look;
        try { look = check(new JSONObject(answer.trim().replaceAll("^```(json)?|```$", "")), looks, parts); }
        catch (JSONException e) { look = null; }
        if (look == null) throw new IOException("Gemini's answer didn't fit den-tv's looks");
        return look;
    }

    private static JSONObject post(String url, String body, String key) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", key);   // in a header, never in the address, so it stays out of logs
        try {
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            if (code == 400 || code == 401 || code == 403) throw new IOException("Gemini refused the key (HTTP " + code + "). Check it on the phone: TV → Live data.");
            if (code == 404) throw new IOException("Gemini doesn't know that model (HTTP 404)");
            if (code == 429) throw new IOException("Gemini's free limit is used up for now. Try again in a minute.");
            if (code / 100 != 2) throw new IOException("Gemini answered HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); if (b.size() > 1_000_000) throw new IOException("Gemini's answer was too large"); }
                return new JSONObject(b.toString("UTF-8"));
            }
        } finally {
            c.disconnect();
        }
    }

}
