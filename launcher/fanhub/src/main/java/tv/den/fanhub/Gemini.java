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
    /** Describing a look: the model reads the whole library and may think first, which takes longer than a key check. */
    private static final int ASK_TIMEOUT_MS = 75_000;
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
            b.append("- ").append(l.optString("id")).append(": ").append(l.optString("name")).append(", ").append(l.optString("category"))
                    .append(l.optString("words").isEmpty() ? "" : " (" + l.optString("words") + ")").append('\n');
        }
        b.append("\nAllowed values for each part:\n");
        for (String p : PARTS) {
            JSONArray a = parts.optJSONArray(p);
            if (a != null) b.append("- ").append(p).append(": ").append(join(a)).append('\n');
        }
        b.append("\nAnswer with JSON only: {\"name\": a short name for the look (2-4 words), \"base\": the closest base look id, ")
                .append("one value for each part from its list, \"accent\": \"#RRGGBB\", \"why\": one short sentence, ")
                .append("\"title\": the big word on the wallpaper (a name or one word, at most 14 letters, e.g. RAPHINHA), ")
                .append("\"subtitle\": a short line under it (at most 40 characters, e.g. BARÇA · BRAZIL · NO. 11), ")
                .append("\"number\": a shirt number if there is one (digits only) or \"\", ")
                .append("\"colours\": two or three #RRGGBB colours that belong to the subject (club or team colours, a show's palette), ")
                .append("\"scene\": one or two sentences describing a wallpaper picture for it (a place, a moment, symbols, the colours), ")
                .append("\"facts\": three to five short, true, family-friendly facts about the subject (each under 120 characters)}. ")
                .append("Use only ids and values from the lists above. The scene must not show anyone's face up close or any ")
                .append("text, letters or logos: people only as distant figures or silhouettes. Keep everything family-friendly.");
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
        // The new look's own wallpaper: its words, number and colours, and the picture to paint (LookArt).
        String title = clean(answer.optString("title"), 14).toUpperCase(Locale.ROOT);
        if (!title.isEmpty()) out.put("title", title);
        String sub = clean(answer.optString("subtitle"), 40).toUpperCase(Locale.ROOT);
        if (!sub.isEmpty()) out.put("subtitle", sub);
        String number = answer.optString("number", "").replaceAll("\\D", "");
        if (!number.isEmpty() && number.length() <= 3) out.put("number", number);
        JSONArray colours = new JSONArray();
        JSONArray cs = answer.optJSONArray("colours");
        for (int i = 0; cs != null && i < cs.length() && colours.length() < 3; i++) {
            if (cs.optString(i).matches("#[0-9a-fA-F]{6}")) colours.put(cs.optString(i).toLowerCase(Locale.ROOT));
        }
        if (colours.length() > 0) out.put("colours", colours);
        String scene = clean(answer.optString("scene"), 400);
        if (!scene.isEmpty()) out.put("scene", scene);
        JSONArray facts = new JSONArray();
        JSONArray fs = answer.optJSONArray("facts");
        for (int i = 0; fs != null && i < fs.length() && facts.length() < 6; i++) {
            String f = clean(fs.optString(i), 160);
            if (!f.isEmpty()) facts.put(f);
        }
        if (facts.length() > 0) out.put("facts", facts);
        return out;
    }

    /** Plain text: no control characters, trimmed, at most max characters. */
    static String clean(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    // ---- painting a wallpaper (with a key that allows Gemini's image model) -----------------------------------------

    /** Image models to try, newest first; a key may only be allowed one of them (or none: Google can charge for it). */
    static final String[] IMAGE_MODELS = {"gemini-2.5-flash-image", "gemini-2.0-flash-preview-image-generation"};

    /**
     * A wallpaper picture for a look (PNG or JPEG bytes), from a scene described in words. The picture leaves room for
     * den-tv's clock (left) and title (bottom right) and has no text, no close-up faces, nothing unsuitable.
     */
    static byte[] picture(String key, String scene, JSONArray colours) throws IOException, JSONException {
        String prompt = "A wallpaper for a TV home screen, wide 16:9 landscape: " + clean(scene, 400)
                + (colours != null && colours.length() > 0 ? " Main colours: " + join(colours) + "." : "")
                + " Bold, cinematic digital illustration with depth and soft light. Keep the left third and the bottom fifth calm and"
                + " fairly dark (a clock and app tiles go there), with the subject toward the right. No text, letters, numbers,"
                + " logos or watermarks. No close-up or realistic faces of real people: people only as distant figures or"
                + " silhouettes. Family friendly.";
        IOException last = null;
        for (String model : IMAGE_MODELS) {
            JSONObject config = new JSONObject().put("responseModalities", new JSONArray().put("TEXT").put("IMAGE"));
            if (model.startsWith("gemini-2.5")) config.put("imageConfig", new JSONObject().put("aspectRatio", "16:9"));
            JSONObject body = new JSONObject()
                    .put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))))
                    .put("generationConfig", config);
            try {
                JSONObject r = post("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent", body.toString(), key, ASK_TIMEOUT_MS);
                JSONArray cands = r.optJSONArray("candidates");
                JSONObject content = cands == null || cands.length() == 0 ? null : cands.getJSONObject(0).optJSONObject("content");
                JSONArray ps = content == null ? null : content.optJSONArray("parts");
                for (int i = 0; ps != null && i < ps.length(); i++) {
                    JSONObject data = ps.getJSONObject(i).optJSONObject("inlineData");
                    if (data != null && data.optString("mimeType").startsWith("image/")) return java.util.Base64.getDecoder().decode(data.getString("data"));
                }
                last = new IOException("Gemini didn't paint a picture for that (it may have declined the subject)");
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** Words that say nothing about which look ("I want a theme for ..."). */
    private static final java.util.Set<String> FILLER = new java.util.HashSet<>(java.util.Arrays.asList(("a an and any are as at be "
            + "but by can cool do for from get give have i in into is it its like look looks make me mine my new nice of on "
            + "one or please player players show some something style team that the theme themed this to tv want wants "
            + "with would you your").split(" ")));
    /** What people call things, to the words the looks use. */
    private static final String[][] ALSO = {{"barcelona", "barca"}, {"fcb", "barca"}, {"blaugrana", "barca"}, {"tendulkar", "sachin"},
            {"virat", "kohli"}, {"rohit", "hitman"}, {"messi", "goat"}, {"argentina", "albiceleste"}, {"madrid", "clasico"},
            {"ipl", "wankhede"}, {"mumbai", "wankhede"}, {"cricket", "cricket"}, {"anime", "anime"}, {"dbz", "saiyan"},
            {"goku", "saiyan"}, {"naruto", "naruto"}, {"luffy", "straw"}, {"onepiece", "piece"}, {"hiking", "trail"},
            {"diwali", "diwali"}, {"coding", "coder"}, {"programming", "coder"}, {"rain", "monsoon"}};

    /**
     * Without Gemini: the look whose name, category, hero, scene and texts share the most words with the description,
     * ignoring filler words, forgiving a letter or two in longer words (Raphina, Raphinha), and knowing a few
     * nicknames (Barcelona, Barça). Parts are left as the look's own.
     */
    static JSONObject words(String text, JSONArray looks) throws JSONException {
        java.util.List<String> ws = new java.util.ArrayList<>();
        for (String w : tokens(text)) {
            if (w.length() < 3 || FILLER.contains(w)) continue;
            ws.add(w);
            for (String[] a : ALSO) if (a[0].equals(w)) ws.add(a[1]);
        }
        JSONObject best = null;
        int score = 0;
        for (int i = 0; i < looks.length(); i++) {
            JSONObject l = looks.getJSONObject(i);
            java.util.Set<String> hay = new java.util.HashSet<>(tokens(l.optString("id") + " " + l.optString("name") + " "
                    + l.optString("category") + " " + l.optString("words")));
            int s = 0;
            for (String w : ws) {
                if (hay.contains(w)) { s += 2 * w.length(); continue; }
                for (String h : hay) if (w.length() >= 5 && h.length() >= 5 && near(w, h)) { s += w.length(); break; }
            }
            if (s > score) { score = s; best = l; }
        }
        if (best == null) return null;
        return new JSONObject().put("base", best.optString("id")).put("name", best.optString("name") + " (mine)")
                .put("why", "Matched your words to " + best.optString("name") + ".");
    }

    /** Lower-case words, accents dropped (Barça: barca). */
    static java.util.List<String> tokens(String text) {
        String plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String w : plain.split("[^\\p{L}\\p{N}]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** At most one letter added, dropped or changed (two for words of eight letters or more). */
    static boolean near(String a, String b) {
        int max = Math.max(a.length(), b.length()) >= 8 ? 2 : 1;
        if (Math.abs(a.length() - b.length()) > max) return false;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()] <= max;
    }

    static JSONObject ask(String key, String model, String text, JSONArray looks, JSONObject parts) throws IOException, JSONException {
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt(text, looks, parts))))))
                .put("generationConfig", new JSONObject().put("responseMimeType", "application/json").put("temperature", 0.8));
        JSONObject r = post("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent", body.toString(), key, ASK_TIMEOUT_MS);
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

    /** What trying a key said: ok, the model that answered (it may be a fallback), and a sentence for the phone. */
    static final class Check {
        final boolean ok;
        final String model, message;
        Check(boolean ok, String model, String message) { this.ok = ok; this.model = model; this.message = message; }
    }

    /** Models tried, in order, when the chosen one isn't known to this key's API version. */
    static final String[] FALLBACK_MODELS = {"gemini-2.5-flash", "gemini-2.0-flash"};

    /** Asks Gemini about the model (no tokens used): does the key work, and does the model exist? */
    static Check test(String key, String model) {
        java.util.List<String> models = new java.util.ArrayList<>();
        models.add(model);
        for (String m : FALLBACK_MODELS) if (!models.contains(m)) models.add(m);
        int last = 0;
        for (String m : models) {
            int code;
            try { code = status("https://generativelanguage.googleapis.com/v1beta/models/" + m, key); }
            catch (IOException e) { return new Check(false, null, "the TV couldn't reach Gemini (" + e.getMessage() + "). Check its internet and try again."); }
            if (code / 100 == 2) return new Check(true, m, "Gemini answered: the key works" + (m.equals(model) ? "." : " (with " + m + ")."));
            if (code == 400 || code == 401 || code == 403) return new Check(false, null, "Gemini refused this key (HTTP " + code + "). Make a new one at aistudio.google.com and paste it again.");
            if (code == 429) return new Check(true, model, "Gemini's limit is used up right now, but the key was accepted.");
            last = code;
            if (code != 404) break;
        }
        return new Check(false, null, "Gemini answered HTTP " + last + ".");
    }

    private static int status(String url, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("x-goog-api-key", key);
        try { return c.getResponseCode(); } finally { c.disconnect(); }
    }

    private static JSONObject post(String url, String body, String key) throws IOException, JSONException {
        return post(url, body, key, TIMEOUT_MS);
    }

    static JSONObject post(String url, String body, String key, int timeoutMs) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(timeoutMs);
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
