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
            "/api/timers", "/api/screentime", "/api/rules", "/api/phones", "/api/people", "/api/characters", "/api/hike", "/api/live", "/api/moment", "/api/mine", "/api/order", "/api/nowplaying", "/api/describe", "/api/fav", "/api/surprise", "/api/keep", "/api/update", "/api/homesetup", "/api/speedup", "/api/lookart", "/api/widgets", "/api/symbols", "/api/places"));

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
            case "/api/people": return in.length() == 0 ? people() : person(in);
            case "/api/characters": return in.length() == 0 ? characters() : characterAction(in);
            case "/api/hike": return in.length() == 0 ? Store.hike(ctx) : Store.setHike(ctx, in);
            case "/api/live": return in.length() == 0 ? Store.live(ctx) : in.optBoolean("refresh") ? refreshNow() : Store.setLive(ctx, in);
            case "/api/moment": return testMoment(in.optString("type"));
            case "/api/mine": return in.length() == 0 ? mine() : mineAction(in);
            case "/api/order": return order(in);
            case "/api/nowplaying": return nowPlaying();
            case "/api/describe": return describe(in.optString("text"));
            case "/api/fav": return fav(in.optString("id"), in.optBoolean("on"));
            case "/api/surprise": return surprise();
            case "/api/keep": return keep(in);
            case "/api/update": return in.length() == 0 ? Updater.status(ctx) : Updater.set(ctx, in);
            case "/api/homesetup": return Timers.open(ctx, new Intent(ctx, HomeSetupActivity.class).putExtra(HomeSetupActivity.EXTRA_SPEED, in.optBoolean("speed")))
                    ? ok(true) : fail(helperNeeded());
            case "/api/speedup": return Speedup.status(ctx);
            case "/api/lookart": return lookArt(in);
            case "/api/widgets": return widgets(in);
            case "/api/symbols":
                try { return ok(true).put("results", Widgets.symbols(in.optString("q"))); }
                catch (IOException e) { return fail("Couldn't search the markets: " + e.getMessage()); }
            case "/api/places":
                try { return ok(true).put("results", Widgets.places(in.optString("q"))); }
                catch (IOException e) { return fail("Couldn't search places: " + e.getMessage()); }
            default: return new JSONObject().put("error", "not found");
        }
    }

    private static JSONObject ok(boolean ok) throws JSONException { return new JSONObject().put("ok", ok); }

    private static JSONObject fail(String why) throws JSONException { return new JSONObject().put("ok", false).put("error", why); }

    // ---- remote --------------------------------------------------------------------------------------------------

    /**
     * What the TV is doing, for the Remote tab's readout: the app in front and for how long (from the helper), the
     * person watching, and the volume. Home counts as Home, not as an app.
     */
    private JSONObject nowPlaying() throws JSONException {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        String pkg = Helper.front();
        boolean home = pkg.isEmpty() || pkg.equals("tv.den.launcher") || pkg.equals(ctx.getPackageName());
        String label = "";
        if (!pkg.isEmpty()) {
            try { label = String.valueOf(ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(pkg, 0))); }
            catch (PackageManager.NameNotFoundException e) { label = pkg; }
        }
        return new JSONObject().put("helper", Helper.on()).put("pkg", pkg).put("label", label).put("home", home)
                .put("since", Helper.since()).put("now", System.currentTimeMillis()).put("who", Timers.who(ctx))
                .put("volume", am.getStreamVolume(AudioManager.STREAM_MUSIC)).put("max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                .put("muted", am.isStreamMute(AudioManager.STREAM_MUSIC));
    }

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
                        .put("renamed", c.getString(3)).put("folder", c.getColumnCount() > 4 && "1".equals(c.getString(4)))
                        .put("picture", c.getColumnCount() > 5 && "1".equals(c.getString(5))).put("hiddenMe", c.getColumnCount() > 6 && "1".equals(c.getString(6))));
            }
        } catch (RuntimeException e) {
            PackageManager pm = ctx.getPackageManager();
            for (ResolveInfo ri : tvApps(pm)) list.put(new JSONObject().put("pkg", ri.activityInfo.packageName).put("label", ri.loadLabel(pm)).put("hidden", false));
        }
        return new JSONObject().put("apps", list).put("helper", Helper.on()).put("who", Timers.who(ctx));
    }

    private static List<ResolveInfo> tvApps(PackageManager pm) {
        return pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
    }

    private JSONObject app(JSONObject in) throws JSONException {
        ContentValues v = new ContentValues();
        v.put("package", in.optString("pkg"));
        if (in.has("hidden")) v.put("hidden", in.optBoolean("hidden") ? "1" : "0");
        if (in.has("label")) v.put("label", in.optString("label"));
        if (in.has("folder")) v.put("folder", in.optBoolean("folder") ? "1" : "0");
        if (in.has("hideMe")) v.put("hide_me", in.optBoolean("hideMe") ? "1" : "0");
        if (in.has("picture") && !in.optBoolean("picture")) v.put("picture", "0");
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
        if (in.optBoolean("reset")) { v.put("reset", "1"); if (in.has("id")) v.put("id", in.optString("id")); }
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
        int sample = sample(o.outWidth, o.outHeight, 1920, 1080);
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

    /**
     * How much to shrink a picture while decoding it: halves while both sides stay at least minW by minH, and until it
     * is at most 8 million pixels whatever its shape (a 60000 by 1100 strip would otherwise need 260 MB).
     */
    static int sample(int w, int h, int minW, int minH) {
        int s = 1;
        while ((w / (s * 2) >= minW && h / (s * 2) >= minH) || (long) (w / s) * (h / s) > 8_000_000L) s *= 2;
        return s;
    }

    /** A small copy of a saved photo for the phone's list. */
    byte[] photoFile(String name) {
        if (!name.matches("photo-\\d+(-p)?\\.jpg")) return null;
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
        skip.addAll(kept());   // the keep-running list: apps you want left alone (a music app, a VPN)
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

    private Set<String> kept() {
        Set<String> out = new HashSet<>();
        try {
            JSONArray a = new JSONArray(Timers.prefs(ctx).getString("keep", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    /** The keep-running list: GET lists the TV's apps with whether each is kept; {pkg, on} keeps one or lets it go. */
    private JSONObject keep(JSONObject in) throws JSONException {
        Set<String> keep = kept();
        if (in.has("pkg")) {
            String pkg = in.optString("pkg");
            if (!pkg.matches("[A-Za-z0-9_.]{1,200}")) return fail("Which app?");
            if (in.optBoolean("on")) keep.add(pkg); else keep.remove(pkg);
            Timers.prefs(ctx).edit().putString("keep", new JSONArray(keep).toString()).apply();
        }
        PackageManager pm = ctx.getPackageManager();
        JSONArray list = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : tvApps(pm)) {
            String pkg = ri.activityInfo.packageName;
            if (!seen.add(pkg) || pkg.equals(ctx.getPackageName()) || pkg.equals("tv.den.launcher")) continue;
            list.put(new JSONObject().put("pkg", pkg).put("label", String.valueOf(ri.loadLabel(pm))).put("kept", keep.contains(pkg)));
        }
        return ok(true).put("apps", list);
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
        JSONObject by = Timers.byPerson(ctx);
        JSONArray people = new JSONArray();
        for (Iterator<String> it = by.keys(); it.hasNext(); ) { String n = it.next(); people.put(new JSONObject().put("name", n).put("minutes", by.optLong(n) / 60_000)); }
        return new JSONObject().put("apps", list).put("people", people).put("minutes", total / 60_000).put("helper", Helper.on())
                .put("smartOrder", Timers.prefs(ctx).getBoolean("smart_order", false));
    }

    // ---- people ----------------------------------------------------------------------------------------------------

    private static final Uri PEOPLE = Uri.parse(L + "people"), CHARACTERS = Uri.parse(L + "characters");

    private JSONObject people() throws JSONException {
        JSONArray list = new JSONArray();
        try (Cursor c = ctx.getContentResolver().query(PEOPLE, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                list.put(new JSONObject().put("id", c.getString(0)).put("name", c.getString(1)).put("colour", c.getString(2)).put("look", c.getString(3))
                        .put("guest", "1".equals(c.getString(4))).put("on", "1".equals(c.getString(5)))
                        .put("private", c.getColumnCount() > 6 && "1".equals(c.getString(6))));
            }
        } catch (RuntimeException e) {
            return new JSONObject().put("people", list).put("error", "Update the den-tv launcher to use people.");
        }
        return new JSONObject().put("people", list);
    }

    private int wrongPins;
    private long wrongSince;

    private JSONObject person(JSONObject in) throws JSONException {
        if (in.has("pin")) {
            // A few wrong PINs, then a pause: guessing 10,000 PINs from a phone is not practical.
            long now = System.currentTimeMillis();
            if (now - wrongSince > 10 * 60_000L) { wrongPins = 0; wrongSince = now; }
            if (wrongPins >= 5) return fail("Too many wrong PINs. Try again in a few minutes.");
        }
        ContentValues v = new ContentValues();
        for (String k : new String[]{"use", "remove", "id", "name", "colour", "pin", "old"}) if (in.has(k)) v.put(k, in.optString(k));
        if (in.optBoolean("private")) v.put("private", "1");
        if (in.has("guest")) v.put("guest", in.optBoolean("guest") ? "1" : "0");
        try {
            boolean done = ctx.getContentResolver().update(PEOPLE, v, null, null) > 0;
            if (!done && in.has("pin") && in.has("use")) { wrongPins++; return fail("That PIN isn't right."); }
            if (!done && in.optBoolean("private") && in.has("old")) { wrongPins++; return fail("The current PIN isn't right."); }
            return ok(done);
        } catch (RuntimeException e) { return fail("The launcher didn't accept it."); }
    }

    // ---- characters ----------------------------------------------------------------------------------------------

    private JSONObject characters() throws JSONException {
        JSONArray list = new JSONArray();
        try (Cursor c = ctx.getContentResolver().query(CHARACTERS, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                list.put(new JSONObject().put("id", c.getString(0)).put("name", c.getString(1)).put("closed", "1".equals(c.getString(2)))
                        .put("on", "1".equals(c.getString(3))).put("mode", c.getString(4)));
            }
        } catch (RuntimeException e) {
            return new JSONObject().put("characters", list).put("error", "Update the den-tv launcher to use characters.");
        }
        return new JSONObject().put("characters", list);
    }

    private JSONObject characterAction(JSONObject in) throws JSONException {
        ContentValues v = new ContentValues();
        if (in.has("use")) { v.put("use", in.optString("use")); v.put("mode", in.optString("mode", "figure")); }
        else if (in.has("delete")) v.put("delete", in.optString("delete"));
        else return fail("Nothing to do");
        try { return ok(ctx.getContentResolver().update(CHARACTERS, v, null, null) > 0); }
        catch (RuntimeException e) { return fail("The launcher didn't accept it."); }
    }

    /**
     * A character's picture from the phone, kept as PNG (so a cut-out keeps its see-through background) at most
     * 900 pixels tall. The body makes a new character (id empty); closed adds the eyes-closed picture to one.
     */
    JSONObject saveCharacter(byte[] data, String id, String name, boolean closed) throws JSONException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return fail("That isn't a picture the TV can read (PNG or JPEG).");
        int sample = sample(o.outWidth, o.outHeight, 1, 900);
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (src == null) return fail("That picture couldn't be read.");
        if (src.getHeight() > 900) src = Bitmap.createScaledBitmap(src, Math.round(src.getWidth() * 900f / src.getHeight()), 900, true);
        boolean fresh = id == null || id.isEmpty();
        String cid = fresh ? "c" + System.currentTimeMillis() : id;
        if (!cid.matches("c\\d{1,16}")) return fail("Unknown character.");
        try (OutputStream os = ctx.getContentResolver().openOutputStream(Uri.parse(L + "characters/" + cid + (closed ? "-closed" : "") + ".png"), "w")) {
            if (os == null) return fail("The launcher didn't accept it.");
            src.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (IOException | RuntimeException e) {
            return fail("Update the den-tv launcher to use characters.");
        }
        if (fresh) {
            ContentValues v = new ContentValues();
            v.put("add", cid);
            v.put("name", name == null || name.trim().isEmpty() ? "My character" : name.trim());
            try { if (ctx.getContentResolver().update(CHARACTERS, v, null, null) == 0) return fail("That's the most characters this TV keeps (8). Delete one first."); }
            catch (RuntimeException e) { return fail("The launcher didn't accept it."); }
        }
        return ok(true).put("id", cid).put("transparent", src.hasAlpha());
    }

    byte[] characterFile(String id, boolean closed) {
        if (!id.matches("c\\d{1,16}")) return null;
        try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(L + "characters/" + id + (closed ? "-closed" : "") + ".png"))) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- live data and moments -----------------------------------------------------------------------------------

    private JSONObject refreshNow() throws JSONException {
        Store.refresh(ctx, true);
        return Store.live(ctx).put("ok", true);
    }

    /** Plays a moment on Home to try it out. */
    private JSONObject testMoment(String type) throws JSONException {
        String[][] samples = {{"goal", "Goal!", "BAR 2\u20131 RMA \u00B7 67'", "-5963708"}, {"six", "100 up", "IND 100/2 (11.4)", "-14721071"},
                {"drop", "New episode", "ONE PIECE \u00B7 EPISODE 1181", "-34304"}, {"beam", "Kamehameha!", "Goal for your team", "-11534"},
                {"arise", "Arise", "Goal for your team", "-8758785"}, {"punch", "One punch", "Goal for your team", "-11476"},
                {"gear5", "Gear 5", "Goal for your team", "-1"}};
        for (String[] x : samples) {
            if (!x[0].equals(type)) continue;
            return Moments.play(ctx, x[0], x[1], x[2], Integer.parseInt(x[3])) ? ok(true) : fail("Update the den-tv launcher to play moments.");
        }
        return fail("Unknown moment");
    }

    // ---- your own looks and smart order --------------------------------------------------------------------------

    private static final Uri MINE = Uri.parse(L + "mine");

    private JSONObject mine() throws JSONException {
        JSONArray list = new JSONArray();
        JSONObject parts = new JSONObject();
        try (Cursor c = ctx.getContentResolver().query(MINE, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if ("parts".equals(c.getString(0))) parts = new JSONObject(c.getString(1));
                else list.put(new JSONObject(c.getString(1)));
            }
        } catch (RuntimeException e) {
            return new JSONObject().put("mine", list).put("error", "Update the den-tv launcher to make looks.");
        }
        return new JSONObject().put("mine", list).put("parts", parts);
    }

    private static final Uri LOOKS = Uri.parse(L + "looks");

    /** Stars a look (or takes the star off): favourites come first on the TV's Looks screen and in Surprise me. */
    private JSONObject fav(String id, boolean on) throws JSONException {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("on", on ? "1" : "0");
        try { return ok(ctx.getContentResolver().update(Uri.parse(L + "fav"), v, null, null) > 0); }
        catch (RuntimeException e) { return fail("Update the den-tv launcher for favourites."); }
    }

    /** Surprise me: a random look other than the one on Home, from your favourites when you have two or more. */
    private JSONObject surprise() throws JSONException {
        java.util.List<String[]> all = new java.util.ArrayList<>(), favs = new java.util.ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(LOOKS, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0);
                if (id.equals("rotate") || c.getInt(3) == 1) continue;
                String[] l = {id, c.getString(1)};
                all.add(l);
                if (c.getColumnCount() > 5 && c.getInt(5) == 1) favs.add(l);
            }
        } catch (RuntimeException e) {
            return fail("Update the den-tv launcher for Surprise me.");
        }
        java.util.List<String[]> from = favs.size() >= 2 ? favs : all;
        if (from.isEmpty()) return fail("No other look to pick.");
        String[] pick = from.get(new java.util.Random().nextInt(from.size()));
        ContentValues v = new ContentValues();
        v.put("look", pick[0]);
        try { return ok(ctx.getContentResolver().update(Uri.parse(L + "current"), v, null, null) == 1).put("id", pick[0]).put("name", pick[1]); }
        catch (RuntimeException e) { return fail("The launcher didn't accept it."); }
    }

    /** Your picture for an app's tile (POST /api/appicon?pkg=): written into the launcher, which shows it next time. */
    JSONObject saveAppPicture(String pkg, byte[] body) throws JSONException {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}")) return fail("Which app?");
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(body, 0, body.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return fail("That isn't a picture the TV can read. Use a JPEG or PNG.");
        // Shrunk here to tile size (at most 640 by 360), so Home only ever reads a small picture.
        android.graphics.BitmapFactory.Options d = new android.graphics.BitmapFactory.Options();
        d.inSampleSize = sample(o.outWidth, o.outHeight, 640, 360);
        Bitmap src = android.graphics.BitmapFactory.decodeByteArray(body, 0, body.length, d);
        if (src == null) return fail("That picture couldn't be read.");
        float k = Math.min(1f, Math.min(640f / src.getWidth(), 360f / src.getHeight()));
        if (k < 1f) src = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * k)), Math.max(1, Math.round(src.getHeight() * k)), true);
        try (java.io.OutputStream out = ctx.getContentResolver().openOutputStream(Uri.parse(L + "icons/" + pkg), "w")) {
            if (out == null) return fail("The launcher didn't accept it.");
            src.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (java.io.IOException | RuntimeException e) {
            return fail("Update the den-tv launcher for app pictures.");
        }
        ContentValues v = new ContentValues();
        v.put("package", pkg);
        v.put("picture", "1");
        try { ctx.getContentResolver().update(APPS, v, null, null); } catch (RuntimeException ignored) { }
        return ok(true);
    }

    /** A QR code of some text (a look code to share), as a PNG; null when it doesn't fit. */
    byte[] qr(String text) {
        if (text == null || text.isEmpty() || text.length() > 1800) return null;
        android.graphics.drawable.BitmapDrawable d = Qr.of(ctx.getResources(), text);
        if (d == null) return null;
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        d.getBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, b);
        return b.toByteArray();
    }

    /**
     * "Describe a look": Gemini (with a key) or word matching picks a base look and parts for a sentence. Nothing is
     * saved here: the phone shows it, and Save sends it to /api/mine like any look of your own.
     */
    private JSONObject describe(String text) throws JSONException {
        text = text == null ? "" : text.trim();
        if (text.isEmpty() || text.length() > 300) return fail("Describe the look in a sentence (300 letters at most).");
        JSONArray looks = new JSONArray();
        try (Cursor c = ctx.getContentResolver().query(LOOKS, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0);
                if (id.equals("rotate") || id.startsWith("mine-")) continue;
                looks.put(new JSONObject().put("id", id).put("name", c.getString(1)).put("category", c.getString(2))
                        .put("words", c.getColumnCount() > 6 ? c.getString(6) : ""));
            }
        } catch (RuntimeException e) {
            return fail("Update the den-tv launcher to make looks.");
        }
        JSONObject parts = mine().optJSONObject("parts");
        if (parts == null) parts = new JSONObject();
        String key = Store.geminiKey(ctx), note = "";
        if (!key.isEmpty()) {
            try { return ok(true).put("look", Gemini.ask(key, Store.geminiModel(ctx), text, looks, parts)).put("by", "gemini"); }
            catch (java.io.IOException e) { note = e.getMessage() + ". "; }
        }
        JSONObject w = Gemini.words(text, looks);
        if (w == null) return fail(note + (key.isEmpty() ? "No look matched those words. Add a Gemini key (TV → Live data) to describe looks freely." : "No look matched those words."));
        return ok(true).put("look", w).put("by", "words").put("note", note);
    }

    /**
     * A described look's wallpaper (LookArt): painted by Gemini from its scene when the key allows it, or drawn here
     * in its colours; with its title, subtitle and number. Saved as one of the TV's photos (not put on Home) for the
     * look to use; the phone deletes it again if the look isn't kept.
     */
    private JSONObject lookArt(JSONObject in) throws JSONException {
        String key = Store.geminiKey(ctx), scene = in.optString("scene", ""), note = "";
        byte[] picture = null;
        if (!key.isEmpty() && !scene.isEmpty()) {
            try { picture = Gemini.picture(key, scene, in.optJSONArray("colours")); }
            catch (IOException e) { note = "Gemini couldn't paint it (" + e.getMessage() + "), so den-tv drew a poster in its colours."; }
        }
        Bitmap art;
        try {
            art = LookArt.make(ctx, picture, in.optJSONArray("colours"), in.optString("number", "").replaceAll("\\D", ""),
                    Gemini.clean(in.optString("title"), 14).toUpperCase(Locale.ROOT), Gemini.clean(in.optString("subtitle"), 40).toUpperCase(Locale.ROOT), System.nanoTime());
        } catch (OutOfMemoryError e) {
            return fail("The TV ran out of memory making the wallpaper");
        }
        try (OutputStream os = ctx.getContentResolver().openOutputStream(NEW_PHOTO, "w")) {
            if (os == null) return fail("The launcher didn't accept it");
            art.compress(Bitmap.CompressFormat.JPEG, 90, os);
        } catch (IOException | RuntimeException e) {
            return fail("Update the den-tv launcher to save wallpapers");
        }
        JSONArray list = photos().optJSONArray("photos");
        if (list == null || list.length() == 0) return fail("The wallpaper wasn't saved");
        return ok(true).put("photo", list.getJSONObject(0).optString("name")).put("by", picture == null ? "drawn" : "gemini").put("note", note);
    }

    /** GET lists the widgets with what each shows; {add: {...}} adds one; {remove: id} removes one. */
    private JSONObject widgets(JSONObject in) throws JSONException {
        if (in.has("add")) {
            JSONObject w = Widgets.add(ctx, in.getJSONObject("add"));
            if (w == null) return fail(Widgets.list(ctx).length() >= Widgets.MAX ? "That's the most widgets (" + Widgets.MAX + "). Remove one first."
                    : "That widget is missing something.");
        }
        if (in.has("remove")) Widgets.remove(ctx, in.optString("remove"));
        return Widgets.status(ctx).put("ok", true);
    }

    private JSONObject mineAction(JSONObject in) throws JSONException {
        ContentValues v = new ContentValues();
        if (in.has("delete")) v.put("delete", in.optString("delete"));
        else v.put("json", in.toString());
        try { return ok(ctx.getContentResolver().update(MINE, v, null, null) > 0); }
        catch (RuntimeException e) { return fail("The launcher didn't accept it."); }
    }

    /** Sort now, or switch the daily sort on or off. */
    private JSONObject order(JSONObject in) throws JSONException {
        if (in.has("daily")) Timers.prefs(ctx).edit().putBoolean("smart_order", in.optBoolean("daily")).apply();
        boolean sorted = in.optBoolean("now") && Timers.sortApps(ctx);
        return ok(true).put("sorted", sorted).put("daily", Timers.prefs(ctx).getBoolean("smart_order", false));
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
