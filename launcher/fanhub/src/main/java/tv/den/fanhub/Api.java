package tv.den.fanhub;

import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The phone app's features behind PhoneServer, one method per endpoint: the remote, apps, the look's parts, photos,
 * the TV's health (network, memory, storage), the timers, the Auto rules and the paired phones. The launcher's
 * parts go through its DenLooksProvider, which only den-tv's own apps may use.
 */
final class Api {
    private static final String L = "content://tv.den.launcher.looks/";
    private static final Uri APPS = Uri.parse(L + "apps"), OVERRIDE = Uri.parse(L + "override"), PHOTOS = Uri.parse(L + "photos"),
            NEW_PHOTO = Uri.parse(L + "photos/new");
    private static final Set<String> PATHS = new HashSet<>(Arrays.asList("/api/remote", "/api/apps", "/api/app", "/api/launch",
            "/api/inputs", "/api/input", "/api/watchnow", "/api/override", "/api/photos", "/api/tv", "/api/speedtest", "/api/clean",
            "/api/timers", "/api/screentime", "/api/rules", "/api/phones"));

    private final Context ctx;
    private final PhoneServer server;

    Api(Context ctx, PhoneServer server) { this.ctx = ctx; this.server = server; }

    boolean handles(String path) { return PATHS.contains(path); }

    JSONObject call(String path, JSONObject in, String token) throws JSONException {
        switch (path) {
            case "/api/remote": return remote(in.optString("action"), in.optString("text"));
            case "/api/apps": return apps();
            case "/api/app": return app(in);
            case "/api/launch": return launch(in.optString("pkg"));
            case "/api/inputs": return inputs();
            case "/api/input": return input(in.optString("id"));
            case "/api/watchnow": return watchNow();
            case "/api/override": return in.length() == 0 ? override() : setOverride(in);
            case "/api/photos": return in.length() == 0 ? photos() : photoAction(in);
            case "/api/tv": return tv();
            case "/api/speedtest": return speedTest();
            case "/api/clean": return clean();
            case "/api/timers": return in.length() == 0 ? Timers.get(ctx) : Timers.set(ctx, in);
            case "/api/screentime": return screenTime();
            case "/api/rules": return in.length() == 0 ? Rules.get(ctx) : Rules.set(ctx, in);
            case "/api/phones": return phones(in, token);
            default: return new JSONObject().put("error", "not found");
        }
    }

    private static JSONObject ok(boolean ok) throws JSONException { return new JSONObject().put("ok", ok); }

    private static JSONObject fail(String why) throws JSONException { return new JSONObject().put("ok", false).put("error", why); }

    // ---- remote --------------------------------------------------------------------------------------------------

    private JSONObject remote(String action, String text) throws JSONException {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        switch (action) {
            case "volume_up": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return ok(true);
            case "volume_down": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return ok(true);
            case "mute": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return ok(true);
            case "play_pause": return media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            case "next": return media(am, KeyEvent.KEYCODE_MEDIA_NEXT);
            case "previous": return media(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            case "rewind": return media(am, KeyEvent.KEYCODE_MEDIA_REWIND);
            case "forward": return media(am, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
            case "home":
                if (Helper.home()) return ok(true);
                return ok(Timers.open(ctx, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));
            case "back": return Helper.back() ? ok(true) : fail(helperNeeded());
            case "sleep": return Helper.sleep() ? ok(true) : fail(helperNeeded());
            case "type":
                if (!Helper.on()) return fail(helperNeeded());
                return Helper.type(text.length() > 200 ? text.substring(0, 200) : text) ? ok(true) : fail("Select a search box on the TV first, then send.");
            default: return fail("Unknown button");
        }
    }

    static String helperNeeded() {
        return "This needs the den-tv helper: on the TV open Settings, Accessibility, den-tv helper, and turn it on.";
    }

    private static JSONObject media(AudioManager am, int key) throws JSONException {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0));
        return ok(true);
    }

    // ---- apps ----------------------------------------------------------------------------------------------------

    /** TV apps with their names on Home and whether they are hidden; the launcher's list when it can give one. */
    private JSONObject apps() throws JSONException {
        JSONArray list = new JSONArray();
        try (Cursor c = ctx.getContentResolver().query(APPS, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                list.put(new JSONObject().put("pkg", c.getString(0)).put("label", c.getString(1)).put("hidden", "1".equals(c.getString(2)))
                        .put("renamed", c.getString(3)));
            }
        } catch (RuntimeException e) {
            PackageManager pm = ctx.getPackageManager();
            for (ResolveInfo ri : tvApps(pm)) list.put(new JSONObject().put("pkg", ri.activityInfo.packageName).put("label", ri.loadLabel(pm)).put("hidden", false));
        }
        return new JSONObject().put("apps", list).put("helper", Helper.on());
    }

    private static List<ResolveInfo> tvApps(PackageManager pm) {
        return pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
    }

    private JSONObject app(JSONObject in) throws JSONException {
        ContentValues v = new ContentValues();
        v.put("package", in.optString("pkg"));
        if (in.has("hidden")) v.put("hidden", in.optBoolean("hidden") ? "1" : "0");
        if (in.has("label")) v.put("label", in.optString("label"));
        try { return ok(ctx.getContentResolver().update(APPS, v, null, null) > 0); }
        catch (RuntimeException e) { return fail("The launcher didn't accept it. Update den-tv."); }
    }

    private JSONObject launch(String pkg) throws JSONException {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return fail("That app isn't on the TV.");
        if (Timers.open(ctx, i)) return ok(true);
        return fail(helperNeeded());
    }

    /** The app's banner (or icon) as a PNG for the phone; null when there is no such app. */
    byte[] icon(String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Drawable d;
        boolean banner = true;
        try {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            ResolveInfo ri = i == null ? null : pm.resolveActivity(i, 0);
            d = ri == null ? null : ri.activityInfo.loadBanner(pm);
            if (d == null) d = pm.getApplicationBanner(pkg);
            if (d == null) { d = pm.getApplicationIcon(pkg); banner = false; }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        int w = banner ? 240 : 120, h = banner ? 135 : 120;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(b));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    // ---- inputs and watch now ------------------------------------------------------------------------------------

    private JSONObject inputs() throws JSONException {
        JSONArray list = new JSONArray();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) for (TvInputInfo info : tim.getTvInputList()) {
            if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
            CharSequence label = info.loadCustomLabel(ctx);
            if (label == null) label = info.loadLabel(ctx);
            list.put(new JSONObject().put("id", info.getId()).put("name", String.valueOf(label)));
        }
        return new JSONObject().put("inputs", list);
    }

    private JSONObject input(String id) throws JSONException {
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        return Timers.open(ctx, i) ? ok(true) : fail(helperNeeded());
    }

    /** What's on now from the Fan Hub, with the app on this TV most likely to show it. */
    private JSONObject watchNow() throws JSONException {
        JSONArray list = new JSONArray();
        long now = System.currentTimeMillis();
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> apps = tvApps(pm);
        for (java.util.Map.Entry<String, List<FanHub.Turn>> e : Store.bySource(ctx).entrySet()) {
            for (FanHub.Turn t : e.getValue()) {
                if (now < t.start || now >= t.end) continue;
                boolean show = e.getKey().startsWith("show:"), cricket = !show && t.word.equals("First ball");
                String[] want = show ? new String[]{"crunchyroll"} : cricket ? new String[]{"hotstar", "fancode", "jiocinema"} : new String[]{"fancode", "jiocinema", "hotstar"};
                String pkg = null, label = null;
                for (String w : want) {
                    for (ResolveInfo ri : apps) {
                        String l = String.valueOf(ri.loadLabel(pm));
                        if (l.toLowerCase(Locale.ROOT).replace(" ", "").contains(w)) { pkg = ri.activityInfo.packageName; label = l; break; }
                    }
                    if (pkg != null) break;
                }
                list.put(new JSONObject().put("title", t.topLine).put("pkg", pkg == null ? JSONObject.NULL : pkg).put("app", label == null ? JSONObject.NULL : label));
            }
        }
        return new JSONObject().put("items", list);
    }

    // ---- the look's parts ----------------------------------------------------------------------------------------

    private JSONObject override() throws JSONException {
        JSONObject out = new JSONObject();
        try (Cursor c = ctx.getContentResolver().query(OVERRIDE, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                out.put(c.getString(0), new JSONObject().put("look", c.getString(1)).put("value", c.getString(2))
                        .put("choices", new JSONArray(Arrays.asList(c.getString(3).isEmpty() ? new String[0] : c.getString(3).split(",")))));
                out.put("lookId", c.getString(4)).put("lookName", c.getString(5));
            }
        } catch (RuntimeException e) {
            out.put("error", "Update the den-tv launcher to change these.");
        }
        return out;
    }

    private JSONObject setOverride(JSONObject in) throws JSONException {
        ContentValues v = new ContentValues();
        if (in.optBoolean("reset")) v.put("reset", "1");
        else { v.put("part", in.optString("part")); v.put("value", in.optString("value")); }
        try { return ok(ctx.getContentResolver().update(OVERRIDE, v, null, null) > 0); }
        catch (RuntimeException e) { return fail("The launcher didn't accept it. Update den-tv."); }
    }

    // ---- photos --------------------------------------------------------------------------------------------------

    private JSONObject photos() throws JSONException {
        JSONArray list = new JSONArray();
        try (Cursor c = ctx.getContentResolver().query(PHOTOS, null, null, null, null)) {
            while (c != null && c.moveToNext()) list.put(new JSONObject().put("name", c.getString(0)).put("on", c.getInt(1) == 1));
        } catch (RuntimeException e) {
            return new JSONObject().put("photos", list).put("error", "Update the den-tv launcher to use photos.");
        }
        return new JSONObject().put("photos", list);
    }

    private JSONObject photoAction(JSONObject in) throws JSONException {
        ContentValues v = new ContentValues();
        if (in.has("use")) v.put("use", in.optString("use"));
        else if (in.has("delete")) v.put("delete", in.optString("delete"));
        else return fail("Nothing to do");
        try { return ok(ctx.getContentResolver().update(PHOTOS, v, null, null) > 0); }
        catch (RuntimeException e) { return fail("The launcher didn't accept it."); }
    }

    /** A photo from the phone: cropped to 16:9 at 1920x1080, saved in the launcher and put on Home. */
    JSONObject savePhoto(byte[] data) throws JSONException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return fail("That isn't a photo the TV can read (JPEG or PNG).");
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1920 && o.outHeight / (sample * 2) >= 1080) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (src == null) return fail("That photo couldn't be read.");
        Bitmap out = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        float scale = Math.max(1920f / src.getWidth(), 1080f / src.getHeight());
        int cw = Math.round(1920 / scale), ch = Math.round(1080 / scale);
        Rect from = new Rect((src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2, (src.getWidth() + cw) / 2, (src.getHeight() + ch) / 2);
        new Canvas(out).drawBitmap(src, from, new Rect(0, 0, 1920, 1080), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        try (OutputStream os = ctx.getContentResolver().openOutputStream(NEW_PHOTO, "w")) {
            if (os == null) return fail("The launcher didn't accept it.");
            out.compress(Bitmap.CompressFormat.JPEG, 88, os);
        } catch (IOException | RuntimeException e) {
            return fail("Update the den-tv launcher to use photos.");
        }
        JSONArray list = photos().optJSONArray("photos");
        if (list != null && list.length() > 0) photoAction(new JSONObject().put("use", list.getJSONObject(0).optString("name")));
        return ok(true);
    }

    /** A small copy of a saved photo for the phone's list. */
    byte[] photoFile(String name) {
        if (!name.matches("photo-\\d+\\.jpg")) return null;
        try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(L + "photos/" + name))) {
            if (in == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 80, out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- the TV's health -----------------------------------------------------------------------------------------

    private JSONObject tv() throws JSONException {
        JSONObject out = new JSONObject();
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        JSONObject net = new JSONObject().put("address", PhoneServer.address(ctx) == null ? JSONObject.NULL : PhoneServer.address(ctx));
        if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiInfo w = ((WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo();
            net.put("type", "Wi-Fi").put("rssi", w.getRssi()).put("bars", WifiManager.calculateSignalLevel(w.getRssi(), 5))
                    .put("linkMbps", w.getLinkSpeed()).put("ghz", w.getFrequency() > 4900 ? "5 GHz" : "2.4 GHz");
        } else if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            net.put("type", "Ethernet");
        } else {
            net.put("type", "Offline");
        }
        out.put("network", net);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        out.put("memory", new JSONObject().put("total", mi.totalMem).put("free", mi.availMem));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        out.put("storage", new JSONObject().put("total", fs.getTotalBytes()).put("free", fs.getAvailableBytes()));
        out.put("helper", Helper.on());
        try { out.put("version", ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName); }
        catch (PackageManager.NameNotFoundException ignored) { }
        return out;
    }

    /** Download and upload speed and ping to Cloudflare's speed test, each part capped at a few seconds. */
    private JSONObject speedTest() throws JSONException {
        JSONObject out = new JSONObject();
        try {
            long best = Long.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                long t = System.nanoTime();
                transfer("https://speed.cloudflare.com/__down?bytes=0", 0, 3000);
                best = Math.min(best, (System.nanoTime() - t) / 1_000_000);
            }
            out.put("ping", best);
            long t = System.nanoTime();
            long got = transfer("https://speed.cloudflare.com/__down?bytes=25000000", 0, 8000);
            out.put("down", Math.round(got * 8 / ((System.nanoTime() - t) / 1e9) / 1e6 * 10) / 10.0);
            t = System.nanoTime();
            long sent = transfer("https://speed.cloudflare.com/__up", 5_000_000, 8000);
            out.put("up", Math.round(sent * 8 / ((System.nanoTime() - t) / 1e9) / 1e6 * 10) / 10.0);
            return out.put("ok", true);
        } catch (IOException e) {
            return fail("The speed test couldn't reach the internet.");
        }
    }

    /** Downloads (upload 0) or uploads that many bytes, stopping after limitMs; the bytes moved. */
    private static long transfer(String url, int upload, int limitMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(limitMs);
        long moved = 0, end = System.currentTimeMillis() + limitMs;
        try {
            if (upload > 0) {
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setFixedLengthStreamingMode(upload);
                byte[] chunk = new byte[64 * 1024];
                try (OutputStream o = c.getOutputStream()) {
                    while (moved < upload && System.currentTimeMillis() < end) {
                        int n = (int) Math.min(chunk.length, upload - moved);
                        o.write(chunk, 0, n);
                        moved += n;
                    }
                }
                c.getResponseCode();
                return moved;
            }
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = in.read(b)) > 0) {
                    moved += n;
                    if (System.currentTimeMillis() > end) break;
                }
            }
            return moved;
        } finally {
            c.disconnect();
        }
    }

    /** Stops apps sitting in the background (not the one on screen, the launcher, keyboards or this app). */
    private JSONObject clean() throws JSONException {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        PackageManager pm = ctx.getPackageManager();
        Set<String> skip = new HashSet<>(Arrays.asList(ctx.getPackageName(), "tv.den.launcher", Helper.front()));
        Set<String> done = new HashSet<>();
        for (ResolveInfo ri : tvApps(pm)) {
            String pkg = ri.activityInfo.packageName;
            if (skip.contains(pkg) || !done.add(pkg)) continue;
            am.killBackgroundProcesses(pkg);
        }
        ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(after);
        return ok(true).put("apps", done.size()).put("freed", Math.max(0, after.availMem - before.availMem)).put("free", after.availMem).put("total", after.totalMem);
    }

    private JSONObject screenTime() throws JSONException {
        JSONObject st = Timers.screenTime(ctx);
        PackageManager pm = ctx.getPackageManager();
        JSONArray list = new JSONArray();
        long total = 0;
        for (Iterator<String> it = st.keys(); it.hasNext(); ) {
            String pkg = it.next();
            long ms = st.optLong(pkg);
            if (ms < 60_000 || pkg.equals("tv.den.launcher")) continue;
            String label;
            try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (PackageManager.NameNotFoundException e) { label = pkg; }
            list.put(new JSONObject().put("pkg", pkg).put("label", label).put("minutes", ms / 60_000));
            total += ms;
        }
        return new JSONObject().put("apps", list).put("minutes", total / 60_000).put("helper", Helper.on());
    }

    // ---- paired phones -------------------------------------------------------------------------------------------

    /** Lists the phones (each by a short id, never its key); forget=id unpairs one, name renames this phone. */
    private JSONObject phones(JSONObject in, String token) throws JSONException {
        JSONObject all = server.phones();
        if (in.has("forget")) {
            for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                String t = it.next();
                if (id(t).equals(in.optString("forget"))) { server.forget(t); break; }
            }
            all = server.phones();
        }
        JSONArray list = new JSONArray();
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String t = it.next();
            JSONObject p = all.optJSONObject(t);
            list.put(new JSONObject().put("id", id(t)).put("name", p == null ? "A phone" : p.optString("name")).put("when", p == null ? 0 : p.optLong("when"))
                    .put("me", t.equals(token)));
        }
        return new JSONObject().put("phones", list);
    }

    private static String id(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) b.append(String.format(Locale.ROOT, "%02x", h[i]));
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
