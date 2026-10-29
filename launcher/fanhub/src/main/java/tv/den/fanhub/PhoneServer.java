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
 * The phone settings page: a small web server on the home Wi-Fi that runs only while the Fan Hub screen is open
 * on the TV. A phone pairs once with the 4-digit code shown on the TV and keeps a token (a cookie); without one it
 * can only load the page. It changes the look (through the launcher's DenLooksProvider) and the Fan Hub's teams
 * and shows (config.json).
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
    private static final int MAX_BODY = 64 * 1024, MAX_TRIES = 5;

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
        String method = "", path = "", cookie = "";
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
        r.path = first[1];
        int length = 0;
        for (String h; !(h = line(in)).isEmpty(); ) {
            int colon = h.indexOf(':');
            if (colon < 0) continue;
            String k = h.substring(0, colon).trim().toLowerCase(Locale.ROOT), v = h.substring(colon + 1).trim();
            if (k.equals("content-length")) try { length = Integer.parseInt(v); } catch (NumberFormatException ignored) { }
            if (k.equals("cookie")) r.cookie = v;
        }
        if (length < 0 || length > MAX_BODY) { send(c, 413, "text/plain", "Too large".getBytes(StandardCharsets.UTF_8), null); return; }
        r.body = new byte[length];
        int n = 0, got;
        while (n < length && (got = in.read(r.body, n, length - n)) > 0) n += got;
        route(c, r);
    }

    private void route(Socket c, Request r) throws IOException {
        if (r.method.equals("GET") && (r.path.equals("/") || r.path.startsWith("/?"))) {
            send(c, 200, "text/html; charset=utf-8", page(), null);
            return;
        }
        try {
            if (r.method.equals("POST") && r.path.equals("/api/pair")) {
                pair(c, new JSONObject(new String(r.body, StandardCharsets.UTF_8)));
                return;
            }
            if (!paired(r.cookie)) { json(c, 401, new JSONObject().put("error", "pair")); return; }
            if (r.method.equals("GET") && r.path.equals("/api/state")) {
                Setup su = setup;
                if (su != null) su.step("phone");
                json(c, 200, state());
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

    private boolean paired(String cookieHeader) {
        String token = null;
        for (String part : cookieHeader.split(";")) {
            String p = part.trim();
            if (p.startsWith("den=")) token = p.substring(4);
        }
        return token != null && token.length() == 32 && prefs().getStringSet("tokens", Collections.emptySet()).contains(token);
    }

    private synchronized void pair(Socket c, JSONObject body) throws IOException, JSONException {
        if (!code.equals(body.optString("code"))) {
            // A few tries per code; then the TV shows a new one, so guessing all 10,000 is not practical.
            if (++wrong >= MAX_TRIES) { newCode(); onChange.run(); }
            json(c, 403, new JSONObject().put("error", "That code doesn't match. Check the TV."));
            return;
        }
        byte[] b = new byte[16];
        random.nextBytes(b);
        StringBuilder token = new StringBuilder();
        for (byte x : b) token.append(String.format(Locale.ROOT, "%02x", x));
        Set<String> tokens = new HashSet<>(prefs().getStringSet("tokens", Collections.emptySet()));
        tokens.add(token.toString());
        prefs().edit().putStringSet("tokens", tokens).apply();
        newCode();
        onChange.run();
        send(c, 200, "application/json", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), token.toString());
    }

    int pairedPhones() { return prefs().getStringSet("tokens", Collections.emptySet()).size(); }

    void forgetPhones() { prefs().edit().remove("tokens").apply(); }

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
        for (String k : new String[]{"show_greeting", "fan_panel", "watcher"}) if (body.has(k)) v.put(k, body.optBoolean(k) ? "1" : "0");
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
