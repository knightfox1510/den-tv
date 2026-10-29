package tv.den.fanhub;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The phone app: a small web server on the home Wi-Fi, run by PhoneService. A phone pairs once with the 4-digit
 * code shown on the TV, and only while the Fan Hub or setup screen is open (the pairing window); it then keeps its
 * own token (a cookie). Without one a phone can only load the page. The API is in Api.
 */
final class PhoneServer {
    private static final String TAG = "FanHubPhone";
    static final int PORT = 8765;
    private static final Uri LOOKS = Uri.parse("content://tv.den.launcher.looks/looks");
    private static final Uri CURRENT = Uri.parse("content://tv.den.launcher.looks/current");
    private static final Uri SETTINGS = Uri.parse("content://tv.den.launcher.looks/settings");

    /** The TV's setup screen, while it runs: the phone's progress, so the TV can follow along. */
    interface Setup {
        /** A paired phone opened the page, or moved to a step ("name", "interests", "home", "look"). */
        void step(String step);
        /** The phone finished the setup. */
        void done();
    }

    private volatile Setup setup;

    /** Puts the page in setup mode (a step-by-step wizard on the phone) while the TV's setup screen is open. */
    void setSetup(Setup s) { setup = s; }
    private static final int MAX_BODY = 64 * 1024, MAX_PHOTO = 15 * 1024 * 1024, MAX_TRIES = 5;

    private static PhoneServer instance;

    /** The one server, shared by the service and the screens. */
    static synchronized PhoneServer get(Context ctx) {
        if (instance == null) instance = new PhoneServer(ctx, () -> { });
        return instance;
    }

    private volatile Runnable pairing;   // set while the Fan Hub or setup screen is open
    private Api api;

    /** Opens pairing while a screen shows the code; onChange runs when the code changes or a phone pairs. */
    void openPairing(Runnable onChange) { pairing = onChange; }

    void closePairing() { pairing = null; setup = null; }

    boolean pairingOpen() { return pairing != null; }

    private void changed() {
        Runnable r = pairing;
        if (r != null) r.run();
        onChange.run();
    }

    private final Context ctx;
    private final SecureRandom random = new SecureRandom();
    private final Runnable onChange;
    private ServerSocket socket;
    // A few connections at once; more wait in the queue, past that they are turned away.
    private final java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
            4, 4, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16));
    private Thread thread;
    private volatile String code;
    private int wrong;

    PhoneServer(Context ctx, Runnable onChange) {
        pool.allowCoreThreadTimeOut(true);
        this.ctx = ctx.getApplicationContext();
        this.onChange = onChange;
        this.api = new Api(this.ctx, this);
        newCode();
    }

    String code() { return code; }

    synchronized boolean running() { return socket != null && !socket.isClosed(); }

    private void newCode() {
        code = String.format(Locale.ROOT, "%04d", random.nextInt(10000));
        wrong = 0;
    }

    /**
     * The TV's address on the home network, for the screen to show; null when it has none. Android's own idea of
     * the network it is using comes first. Interfaces are the fallback, and never Wi-Fi Direct's p2p0
     * (192.168.49.x, used for casting), a hotspot, mobile data or a VPN: a phone at home can't reach those.
     */
    static String address(Context ctx) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.LinkProperties lp = cm == null ? null : cm.getLinkProperties(cm.getActiveNetwork());
            if (lp != null && usable(lp.getInterfaceName())) {
                for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                    InetAddress a = la.getAddress();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return "http://" + a.getHostAddress() + ":" + PORT;
                }
            }
        } catch (RuntimeException ignored) { }
        java.util.Map<String, String> found = new java.util.LinkedHashMap<>();
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) found.put(n.getName(), a.getHostAddress());
                }
            }
        } catch (SocketException ignored) { }
        String ip = choose(found);
        return ip == null ? null : "http://" + ip + ":" + PORT;
    }

    /** Interfaces a phone on the home network can't reach the TV through. */
    static boolean usable(String name) {
        if (name == null) return true;
        String n = name.toLowerCase(Locale.ROOT);
        return !(n.startsWith("p2p") || n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("rmnet")
                || n.startsWith("ccmni") || n.startsWith("tun") || n.startsWith("dummy") || n.startsWith("v4-"));
    }

    /** From interface name to IPv4 address: Wi-Fi (wlan) or Ethernet (eth) first, then anything usable. */
    static String choose(java.util.Map<String, String> found) {
        for (String prefix : new String[]{"wlan", "eth"}) {
            for (java.util.Map.Entry<String, String> e : found.entrySet()) if (e.getKey().startsWith(prefix)) return e.getValue();
        }
        for (java.util.Map.Entry<String, String> e : found.entrySet()) if (usable(e.getKey())) return e.getValue();
        return null;
    }

    synchronized void start() {
        if (thread != null) return;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);   // reopening the screen right away must not find the port still taken
            socket.bind(new java.net.InetSocketAddress(PORT));
        } catch (IOException e) {
            socket = null;
            Log.w(TAG, "port " + PORT + " is taken", e);
            return;
        }
        thread = new Thread(this::serve, "phone-settings");
        thread.start();
    }

    synchronized void stop() {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        socket = null;
        thread = null;
    }

    /**
     * Each connection on its own thread: phone browsers open spare connections that send nothing, and one of those
     * must not hold up the real request behind it (that left the page blank on a phone).
     */
    private void serve() {
        ServerSocket s = socket;
        while (s != null && !s.isClosed()) {
            Socket c;
            try {
                c = s.accept();
            } catch (IOException e) {
                if (s.isClosed()) return;
                continue;
            }
            try {
                pool.execute(() -> answer(c));
            } catch (java.util.concurrent.RejectedExecutionException busy) {
                try { c.close(); } catch (IOException ignored) { }
            }
        }
    }

    private void answer(Socket socket) {
        try (Socket c = socket) {
            // Only phones on the same network: a link- or site-local address, never a public one.
            InetAddress from = c.getInetAddress();
            if (!(from.isSiteLocalAddress() || from.isLinkLocalAddress() || from.isLoopbackAddress())) return;
            c.setSoTimeout(10_000);
            handle(c);
        } catch (IOException e) {
            // A spare connection that never sent a request, or a phone that went away.
        } catch (RuntimeException e) {
            Log.w(TAG, "request failed", e);
        }
    }

    // ---- HTTP ----------------------------------------------------------------------------------------------------

    private static final class Request {
        String method = "", path = "", query = "", cookie = "";
        byte[] body = new byte[0];
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1 && ch != '\n') {
            if (ch != '\r') b.write(ch);
            if (b.size() > 8192) throw new IOException("header too long");
        }
        return b.toString("UTF-8");
    }

    private void handle(Socket c) throws IOException {
        InputStream in = new BufferedInputStream(c.getInputStream());
        Request r = new Request();
        String[] first = line(in).split(" ");
        if (first.length < 2) return;
        r.method = first[0];
        int q = first[1].indexOf('?');
        r.path = q < 0 ? first[1] : first[1].substring(0, q);
        r.query = q < 0 ? "" : first[1].substring(q + 1);
        int length = 0;
        for (String h; !(h = line(in)).isEmpty(); ) {
            int colon = h.indexOf(':');
            if (colon < 0) continue;
            String k = h.substring(0, colon).trim().toLowerCase(Locale.ROOT), v = h.substring(colon + 1).trim();
            if (k.equals("content-length")) try { length = Integer.parseInt(v); } catch (NumberFormatException ignored) { }
            if (k.equals("cookie")) r.cookie = v;
        }
        // Only a paired phone may send a photo; anyone else is turned away before 15 MB is read.
        if ((r.path.equals("/api/photo") || r.path.equals("/api/character")) && length > MAX_BODY && !paired(token(r.cookie))) {
            send(c, 401, "application/json", "{\"error\":\"pair\"}".getBytes(StandardCharsets.UTF_8), null);
            return;
        }
        if (length < 0 || length > (r.path.equals("/api/photo") || r.path.equals("/api/character") ? MAX_PHOTO : MAX_BODY)) { send(c, 413, "text/plain", "Too large".getBytes(StandardCharsets.UTF_8), null); return; }
        r.body = new byte[length];
        int n = 0, got;
        while (n < length && (got = in.read(r.body, n, length - n)) > 0) n += got;
        route(c, r);
    }

    private void route(Socket c, Request r) throws IOException {
        if (r.method.equals("GET") && r.path.equals("/")) {
            send(c, 200, "text/html; charset=utf-8", page(), null);
            return;
        }
        try {
            if (r.method.equals("POST") && r.path.equals("/api/pair")) {
                pair(c, new JSONObject(new String(r.body, StandardCharsets.UTF_8)));
                return;
            }
            String token = token(r.cookie);
            if (!paired(token)) { json(c, 401, new JSONObject().put("error", "pair").put("open", pairingOpen())); return; }
            if (r.method.equals("GET") && r.path.equals("/api/state")) {
                Setup su = setup;
                if (su != null) su.step("phone");
                json(c, 200, state());
                return;
            }
            if (r.method.equals("GET") && (r.path.equals("/api/icon") || r.path.equals("/api/photo") || r.path.equals("/api/charimg"))) {
                byte[] img = r.path.equals("/api/icon") ? api.icon(param(r.query, "pkg"))
                        : r.path.equals("/api/photo") ? api.photoFile(param(r.query, "name"))
                        : api.characterFile(param(r.query, "id"), "1".equals(param(r.query, "closed")));
                if (img == null) send(c, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8), null);
                else send(c, 200, r.path.equals("/api/photo") ? "image/jpeg" : "image/png", img, null);
                return;
            }
            if (r.method.equals("POST") && r.path.equals("/api/photo")) { json(c, 200, api.savePhoto(r.body)); return; }
            if (r.method.equals("POST") && r.path.equals("/api/character")) {
                json(c, 200, api.saveCharacter(r.body, param(r.query, "id"), param(r.query, "name"), "1".equals(param(r.query, "closed"))));
                return;
            }
            if (r.path.startsWith("/api/") && api.handles(r.path)) {
                JSONObject in = r.method.equals("POST") && r.body.length > 0 ? new JSONObject(new String(r.body, StandardCharsets.UTF_8)) : new JSONObject();
                json(c, 200, api.call(r.path, in, token));
                return;
            }
            JSONObject body = new JSONObject(new String(r.body, StandardCharsets.UTF_8));
            if (r.method.equals("POST") && r.path.equals("/api/look")) { json(c, 200, look(body.optString("id"))); return; }
            if (r.method.equals("POST") && r.path.equals("/api/fanhub")) { json(c, 200, fanhub(body)); return; }
            if (r.method.equals("POST") && r.path.equals("/api/settings")) { json(c, 200, settings(body)); return; }
            if (r.method.equals("POST") && r.path.equals("/api/setup")) { json(c, 200, setupStep(body.optString("step"))); return; }
            json(c, 404, new JSONObject().put("error", "not found"));
        } catch (JSONException e) {
            send(c, 400, "text/plain", "Bad request".getBytes(StandardCharsets.UTF_8), null);
        }
    }

    private byte[] page() throws IOException {
        try (InputStream in = ctx.getAssets().open("phone.html"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    private void json(Socket c, int status, JSONObject j) throws IOException {
        send(c, status, "application/json", j.toString().getBytes(StandardCharsets.UTF_8), null);
    }

    private static void send(Socket c, int status, String type, byte[] body, String cookie) throws IOException {
        String head = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\n"
                + "Content-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\n"
                + "X-Frame-Options: DENY\r\nConnection: close\r\n"
                + (cookie == null ? "" : "Set-Cookie: den=" + cookie + "; Path=/; Max-Age=31536000; HttpOnly; SameSite=Strict\r\n") + "\r\n";
        OutputStream o = c.getOutputStream();
        o.write(head.getBytes(StandardCharsets.UTF_8));
        o.write(body);
        o.flush();
    }

    // ---- pairing -------------------------------------------------------------------------------------------------

    private SharedPreferences prefs() { return ctx.getSharedPreferences("phones", Context.MODE_PRIVATE); }

    private static String token(String cookieHeader) {
        String token = null;
        for (String part : cookieHeader.split(";")) {
            String p = part.trim();
            if (p.startsWith("den=")) token = p.substring(4);
        }
        return token;
    }

    /** A query parameter, URL-decoded; "" when missing. */
    static String param(String query, String key) {
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(key)) {
                try { return java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8"); } catch (java.io.UnsupportedEncodingException | IllegalArgumentException e) { return ""; }
            }
        }
        return "";
    }

    private boolean paired(String token) {
        return token != null && token.length() == 32 && phones().has(token);
    }

    /** Paired phones: token -> {name, when}. Older versions kept a bare set of tokens; those become "A phone". */
    synchronized JSONObject phones() {
        JSONObject j;
        try { j = new JSONObject(prefs().getString("phones", "{}")); } catch (JSONException e) { j = new JSONObject(); }
        Set<String> old = prefs().getStringSet("tokens", null);
        if (old != null) {
            for (String t : old) try { if (!j.has(t)) j.put(t, new JSONObject().put("name", "A phone").put("when", 0)); } catch (JSONException ignored) { }
            prefs().edit().putString("phones", j.toString()).remove("tokens").apply();
        }
        return j;
    }

    synchronized void forget(String token) {
        JSONObject j = phones();
        j.remove(token);
        prefs().edit().putString("phones", j.toString()).apply();
    }

    private synchronized void pair(Socket c, JSONObject body) throws IOException, JSONException {
        if (!pairingOpen()) {
            json(c, 403, new JSONObject().put("error", "Open the Fan Hub on the TV to see a code, then try again."));
            return;
        }
        if (!code.equals(body.optString("code"))) {
            // A few tries per code; then the TV shows a new one, so guessing all 10,000 is not practical.
            if (++wrong >= MAX_TRIES) { newCode(); changed(); }
            json(c, 403, new JSONObject().put("error", "That code doesn't match. Check the TV."));
            return;
        }
        byte[] b = new byte[16];
        random.nextBytes(b);
        StringBuilder token = new StringBuilder();
        for (byte x : b) token.append(String.format(Locale.ROOT, "%02x", x));
        JSONObject list = phones();
        String name = body.optString("name", "").trim();
        list.put(token.toString(), new JSONObject().put("name", name.isEmpty() ? "A phone" : name.length() > 30 ? name.substring(0, 30) : name)
                .put("when", System.currentTimeMillis()));
        prefs().edit().putString("phones", list.toString()).apply();
        newCode();
        changed();
        send(c, 200, "application/json", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), token.toString());
    }

    int pairedPhones() { return phones().length(); }

    void forgetPhones() { prefs().edit().remove("phones").remove("tokens").apply(); }

    // ---- what the page reads and changes -------------------------------------------------------------------------

    private JSONObject state() throws JSONException {
        JSONObject s = new JSONObject();
        JSONArray looks = new JSONArray();
        try (Cursor c = ctx.getContentResolver().query(LOOKS, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                looks.put(new JSONObject().put("id", c.getString(0)).put("name", c.getString(1)).put("category", c.getString(2)).put("on", c.getInt(3) == 1));
            }
        } catch (RuntimeException e) {
            s.put("looksError", "The den-tv launcher isn't installed, or was signed with a different key.");
        }
        s.put("looks", looks);
        FanHub.Config cfg = Store.config(ctx);
        JSONArray teams = new JSONArray(), shows = new JSONArray();
        for (FanHub.Team t : cfg.teams) teams.put(t.name);
        for (String sh : cfg.shows) shows.put(sh);
        s.put("teams", teams).put("shows", shows);
        JSONObject home = new JSONObject();
        try (Cursor c = ctx.getContentResolver().query(SETTINGS, null, null, null, null)) {
            while (c != null && c.moveToNext()) home.put(c.getString(0), c.getString(1));
        } catch (RuntimeException ignored) {
            // An older launcher without settings: the page leaves those out.
        }
        s.put("settings", home);
        s.put("setup", setup != null);
        return s;
    }

    /** Your name and what Home shows, saved in the launcher (DenLooksProvider). */
    private JSONObject settings(JSONObject body) throws JSONException {
        ContentValues v = new ContentValues();
        if (body.has("greeting_name")) v.put("greeting_name", body.optString("greeting_name"));
        for (String k : new String[]{"show_greeting", "fan_panel", "watcher", "wake", "reduced_motion", "animations", "facts", "stats"}) if (body.has(k)) v.put(k, body.optBoolean(k) ? "1" : "0");
        if (body.has("idle_clock")) v.put("idle_clock", String.valueOf(body.optInt("idle_clock", -1)));
        try {
            ctx.getContentResolver().update(SETTINGS, v, null, null);
            return new JSONObject().put("ok", true);
        } catch (RuntimeException e) {
            return new JSONObject().put("ok", false).put("error", "The launcher didn't accept it. Update den-tv.");
        }
    }

    /** The phone moved on in the setup; "done" also marks the setup finished in the launcher. */
    private JSONObject setupStep(String step) throws JSONException {
        Setup su = setup;
        if ("done".equals(step)) {
            ContentValues v = new ContentValues();
            v.put("setup_done", "1");
            try { ctx.getContentResolver().update(SETTINGS, v, null, null); } catch (RuntimeException ignored) { }
            if (su != null) su.done();
        } else if (su != null && step.matches("[a-z]{1,16}")) {
            su.step(step);
        }
        return new JSONObject().put("ok", true);
    }

    private JSONObject look(String id) throws JSONException {
        ContentValues v = new ContentValues();
        v.put("look", id);
        try {
            int n = ctx.getContentResolver().update(CURRENT, v, null, null);
            return new JSONObject().put("ok", n == 1);
        } catch (RuntimeException e) {
            return new JSONObject().put("ok", false).put("error", "The launcher didn't accept it.");
        }
    }

    /**
     * New teams and shows. A team name is looked up on TheSportsDB (searchteams.php) for its id; one it can't
     * find is reported back and left out. Teams kept from before keep their ids without a lookup.
     */
    private JSONObject fanhub(JSONObject body) throws JSONException {
        FanHub.Config old = Store.config(ctx);
        JSONArray names = body.optJSONArray("teams"), shows = body.optJSONArray("shows");
        JSONArray teams = new JSONArray(), missing = new JSONArray();
        if (names != null) for (int i = 0; i < Math.min(names.length(), 8); i++) {
            String name = names.optString(i).trim();
            if (name.isEmpty()) continue;
            FanHub.Team known = null;
            for (FanHub.Team t : old.teams) if (t.name.equalsIgnoreCase(name)) known = t;
            if (known != null) {
                teams.put(new JSONObject().put("name", known.name).put("sport", known.sport).put("sportsdb", known.sportsdb)
                        .put("colour", String.format(Locale.ROOT, "#%06x", known.colour & 0xffffff)));
                continue;
            }
            JSONObject found = search(name, old.sportsdbKey);
            if (found == null) missing.put(name); else teams.put(found);
        }
        JSONArray showList = new JSONArray();
        if (shows != null) for (int i = 0; i < Math.min(shows.length(), 8); i++) if (!shows.optString(i).trim().isEmpty()) showList.put(shows.optString(i).trim());
        JSONObject cfg = new JSONObject().put("teams", teams).put("shows", showList)
                .put("keys", new JSONObject().put("sportsdb", old.sportsdbKey));
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) return new JSONObject().put("ok", false).put("error", "The TV's storage isn't ready.");
        try (FileOutputStream o = new FileOutputStream(new File(dir, "config.json"))) {
            o.write(cfg.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new JSONObject().put("ok", false).put("error", "Couldn't save on the TV.");
        }
        Store.refresh(ctx, true);
        return new JSONObject().put("ok", true).put("missing", missing);
    }

    private static JSONObject search(String name, String key) {
        try {
            JSONObject r = FanHub.get("https://www.thesportsdb.com/api/v1/json/" + URLEncoder.encode(key, "UTF-8")
                    + "/searchteams.php?t=" + URLEncoder.encode(name, "UTF-8"));
            JSONArray list = r.optJSONArray("teams");
            if (list == null || list.length() == 0) return null;
            JSONObject t = list.getJSONObject(0);
            String sport = t.optString("strSport").equalsIgnoreCase("Cricket") ? "cricket" : "football";
            String colour = t.optString("strColour1", "");
            JSONObject out = new JSONObject().put("name", t.optString("strTeam", name)).put("sport", sport).put("sportsdb", t.optString("idTeam"));
            if (colour.matches("#[0-9a-fA-F]{6}")) out.put("colour", colour);
            return out;
        } catch (IOException | JSONException e) {
            return null;
        }
    }
}
