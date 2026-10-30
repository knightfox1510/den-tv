package tv.den.fanhub;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Keeps den-tv and the Fan Hub up to date from the repo's GitHub releases. Twice a day it reads the newest release's
 * version.json; anything newer is downloaded, checked (right app, newer, signed with the same key as the one on the
 * TV) and installed while nobody is watching Home or the Fan Hub: the screen off, another app in front, or 3 to 5 in
 * the morning. On Android 12 and later the Fan Hub, as the apps' installer, updates them without asking; on older
 * TVs (or before it is their installer) Android asks once, so the Fan Hub screen offers "OK installs it".
 */
final class Updater {
    static final String REPO = "knightfox1510/den-tv";
    private static final String PREFS = "updates";
    private static final long EVERY = 12 * 3_600_000L;

    /** An app this keeps up to date: its package, its key in version.json, its release file and its name. */
    static final class App {
        final String pkg, key, asset, label;
        App(String pkg, String key, String asset, String label) { this.pkg = pkg; this.key = key; this.asset = asset; this.label = label; }
    }

    /** The launcher first: a Fan Hub update ends the Fan Hub's own process. */
    static final List<App> APPS = Arrays.asList(
            new App(HomeSetup.LAUNCHER, "launcher", "den-tv-launcher.apk", "den-tv"),
            new App(HomeSetup.FANHUB, "fanhub", "den-tv-fanhub.apk", "Fan Hub"));

    private static long lastTick;

    private Updater() {}

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static boolean auto(Context ctx) { return prefs(ctx).getBoolean("auto", true); }

    /** The version code on the TV, -1 when the app isn't installed. */
    static long installed(Context ctx, String pkg) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private static String installedName(Context ctx, String pkg) {
        try { return ctx.getPackageManager().getPackageInfo(pkg, 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return ""; }
    }

    /** The apps a release has newer versions of, given what's installed (an app that isn't installed is left alone). */
    static List<App> newer(JSONObject release, long launcher, long fanhub) {
        List<App> out = new ArrayList<>();
        for (App a : APPS) {
            JSONObject v = release.optJSONObject(a.key);
            long have = a.pkg.equals(HomeSetup.LAUNCHER) ? launcher : fanhub;
            if (v != null && have >= 0 && v.optLong("code", 0) > have) out.add(a);
        }
        return out;
    }

    // ---- when to check and install -------------------------------------------------------------------------------

    /** PhoneService's tick: check twice a day, and install what's waiting once nobody is looking. */
    static void tick(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 5 * 60_000L) return;   // PhoneService ticks every 30 seconds; this needs far less
        lastTick = now;
        claim(ctx);
        if (!auto(ctx)) return;
        if (System.currentTimeMillis() - prefs(ctx).getLong("checked_at", 0) >= EVERY) check(ctx, false);
        else if (!prefs(ctx).getBoolean("needs_you", false) && quiet(ctx)) installNext(ctx, false);
    }

    /** Nobody would see Home or the Fan Hub restart: the screen is off, another app is in front, or it's 3 to 5 am. */
    static boolean quiet(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return true;
        String front = Helper.front();
        if (Helper.on() && !front.isEmpty() && !front.equals(HomeSetup.LAUNCHER) && !front.equals(ctx.getPackageName())) return true;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 3 && h < 5;
    }

    /**
     * Reads the newest release and downloads what's newer. now: someone asked (the phone, or OK on the Fan Hub), so
     * it installs straight away rather than waiting for a quiet moment.
     */
    static synchronized JSONObject check(Context ctx, boolean now) {
        SharedPreferences p = prefs(ctx);
        try {
            JSONObject release = FanHub.get("https://github.com/" + REPO + "/releases/latest/download/version.json");
            p.edit().putLong("checked_at", System.currentTimeMillis()).putString("latest", release.toString()).remove("error").apply();
            String tag = release.optString("tag");
            for (App a : newer(release, installed(ctx, HomeSetup.LAUNCHER), installed(ctx, HomeSetup.FANHUB))) {
                long code = release.getJSONObject(a.key).optLong("code");
                File f = file(ctx, a);
                if (verify(ctx, f, a.pkg, code) != null) {
                    p.edit().putString("state", "Downloading " + a.label).apply();
                    download("https://github.com/" + REPO + "/releases/download/" + tag + "/" + a.asset, f);
                    String bad = verify(ctx, f, a.pkg, code);
                    if (bad != null) { f.delete(); throw new IOException(bad); }
                }
            }
            p.edit().remove("state").apply();
            if (now || quiet(ctx)) installNext(ctx, now);
        } catch (IOException | JSONException | RuntimeException e) {
            p.edit().putLong("checked_at", System.currentTimeMillis()).putString("error", String.valueOf(e.getMessage())).apply();
        }
        return status(ctx);
    }

    static File file(Context ctx, App a) {
        File dir = new File(ctx.getFilesDir(), "updates");
        dir.mkdirs();
        return new File(dir, a.asset);
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "den-tv-fanhub");
        try {
            if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode() + " downloading " + to.getName());
            File part = new File(to.getPath() + ".part");
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] b = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    if ((total += n) > 64L * 1024 * 1024) throw new IOException("download too large");
                }
            }
            if (!part.renameTo(to)) throw new IOException("couldn't save " + to.getName());
        } finally {
            c.disconnect();
        }
    }

    /**
     * Null when the file is the app's update: the same package, the version the release names, newer than the one on
     * the TV, and signed with the same key. Android checks the key again when installing; this keeps a wrong file
     * from ever being offered.
     */
    static String verify(Context ctx, File apk, String pkg, long code) {
        if (!apk.isFile()) return "not downloaded";
        PackageManager pm = ctx.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo got = pm.getPackageArchiveInfo(apk.getPath(), flags);
        if (got == null) return "not an app";
        long gotCode = Build.VERSION.SDK_INT >= 28 ? got.getLongVersionCode() : got.versionCode;
        if (!pkg.equals(got.packageName)) return "the wrong app";
        if (gotCode != code || gotCode <= installed(ctx, pkg)) return "not the newer version";
        try {
            PackageInfo have = pm.getPackageInfo(pkg, flags);
            if (!Arrays.equals(signatures(have), signatures(got))) return "signed with a different key";
        } catch (PackageManager.NameNotFoundException e) {
            return "not installed";
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo pi) {
        if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) return pi.signingInfo.getApkContentsSigners();
        return pi.signatures;
    }

    // ---- installing ----------------------------------------------------------------------------------------------

    /** Installs the next waiting update (one at a time; UpdateReceiver goes on to the next). user: someone is here. */
    static synchronized void installNext(Context ctx, boolean user) {
        String latest = prefs(ctx).getString("latest", "");
        JSONObject release;
        try { release = new JSONObject(latest); } catch (JSONException e) { return; }
        for (App a : newer(release, installed(ctx, HomeSetup.LAUNCHER), installed(ctx, HomeSetup.FANHUB))) {
            File f = file(ctx, a);
            if (verify(ctx, f, a.pkg, release.optJSONObject(a.key).optLong("code")) != null) continue;
            try {
                install(ctx, f, a, user);
            } catch (IOException | RuntimeException e) {
                prefs(ctx).edit().putString("error", "Couldn't install " + a.label + ": " + e.getMessage()).apply();
            }
            return;
        }
    }

    private static void install(Context ctx, File apk, App a, boolean user) throws IOException {
        if (a.pkg.equals(ctx.getPackageName())) rememberHelper(ctx);
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(a.pkg);
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("base.apk", 0, apk.length())) {
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                s.fsync(out);
            }
            Intent done = new Intent(ctx, UpdateReceiver.class).putExtra("pkg", a.pkg).putExtra("user", user);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(ctx, id, done, flags).getIntentSender());
        }
        prefs(ctx).edit().putString("state", "Installing " + a.label).apply();
    }

    /** UpdateReceiver: Android wants someone to confirm. With nobody at the TV, the Fan Hub screen asks next time. */
    static void needsYou(Context ctx) {
        prefs(ctx).edit().putBoolean("needs_you", true).putString("state", "Waiting for OK on the TV").apply();
    }

    static void installedOne(Context ctx, String pkg, boolean user) {
        for (App a : APPS) if (a.pkg.equals(pkg)) file(ctx, a).delete();
        prefs(ctx).edit().remove("needs_you").remove("state").remove("error").apply();
        installNext(ctx, user);
    }

    static void failed(Context ctx, String pkg, String why) {
        prefs(ctx).edit().remove("state").putString("error", "Update failed: " + why).apply();
    }

    /** OK on the Fan Hub, or Install on the phone: install what's waiting now. */
    static void installNow(Context ctx, boolean user) {
        prefs(ctx).edit().remove("needs_you").apply();
        Store.worker().post(() -> installNext(ctx, user));
    }

    private static String waitingFor = "", waitingWas = "";

    /** What's waiting, for the Fan Hub footer: "" when nothing is. Remembered until the release or the files change. */
    static synchronized String waiting(Context ctx) {
        String latest = prefs(ctx).getString("latest", "");
        StringBuilder key = new StringBuilder(latest);
        for (App a : APPS) key.append('|').append(file(ctx, a).lastModified()).append('|').append(installed(ctx, a.pkg));
        if (key.toString().equals(waitingFor)) return waitingWas;
        waitingWas = findWaiting(ctx, latest);
        waitingFor = key.toString();
        return waitingWas;
    }

    private static String findWaiting(Context ctx, String latest) {
        JSONObject release;
        try { release = new JSONObject(latest); } catch (JSONException e) { return ""; }
        List<String> names = new ArrayList<>();
        for (App a : newer(release, installed(ctx, HomeSetup.LAUNCHER), installed(ctx, HomeSetup.FANHUB))) {
            if (verify(ctx, file(ctx, a), a.pkg, release.optJSONObject(a.key).optLong("code")) == null) {
                names.add(a.label + " " + release.optJSONObject(a.key).optString("name"));
            }
        }
        return String.join(" and ", names);
    }

    /** For the phone's TV tab. */
    static JSONObject status(Context ctx) {
        SharedPreferences p = prefs(ctx);
        JSONObject s = new JSONObject();
        try {
            s.put("auto", auto(ctx)).put("checkedAt", p.getLong("checked_at", 0)).put("state", p.getString("state", ""))
                    .put("error", p.getString("error", "")).put("needsYou", p.getBoolean("needs_you", false))
                    .put("waiting", waiting(ctx)).put("silent", Build.VERSION.SDK_INT >= 31).put("repo", REPO);
            for (App a : APPS) s.put(a.key, installedName(ctx, a.pkg));
            JSONObject latest = new JSONObject(p.getString("latest", "{}"));
            s.put("latest", latest.optString("tag"));
        } catch (JSONException ignored) { }
        return s;
    }

    static JSONObject set(Context ctx, JSONObject in) {
        if (in.has("auto")) prefs(ctx).edit().putBoolean("auto", in.optBoolean("auto")).apply();
        if (in.optBoolean("install")) { installNow(ctx, false); return status(ctx); }
        if (in.optBoolean("check")) {
            // Downloads can take a minute: the phone gets the state now and asks again.
            prefs(ctx).edit().putString("state", "Checking for updates").apply();
            Store.worker().post(() -> { check(ctx, false); prefs(ctx).edit().remove("state").apply(); });
        }
        return status(ctx);
    }

    // ---- being the apps' installer, and the helper across an update ----------------------------------------------

    /**
     * Makes the Fan Hub the installer of record of both apps (Android allows it for apps with the same key that no
     * other store installed). Then Android 12 and later let it update them without asking each time.
     */
    @SuppressWarnings("deprecation")
    static void claim(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (App a : APPS) {
            try {
                if (installed(ctx, a.pkg) < 0 || ctx.getPackageName().equals(pm.getInstallerPackageName(a.pkg))) continue;
                pm.setInstallerPackageName(a.pkg, ctx.getPackageName());
            } catch (RuntimeException ignored) {
                // Another installer owns it, or this Android won't allow it: updates then ask once.
            }
        }
    }

    /** Before the Fan Hub replaces itself: whether its helper is on, so it can be put back after. */
    private static void rememberHelper(Context ctx) {
        String list = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        prefs(ctx).edit().putBoolean("helper_was_on", list != null && list.contains(HomeSetup.HELPER)).apply();
    }

    /**
     * After the Fan Hub was replaced: Android takes an accessibility service off its list when its app is replaced,
     * so the helper is put back if it was on (with WRITE_SECURE_SETTINGS, which the setup grants over USB debugging).
     */
    static void afterUpdate(Context ctx) {
        for (App a : APPS) {
            // A file whose version is now on the TV (or older) has done its job.
            File f = file(ctx, a);
            PackageInfo got = f.isFile() ? ctx.getPackageManager().getPackageArchiveInfo(f.getPath(), 0) : null;
            long code = got == null ? -1 : Build.VERSION.SDK_INT >= 28 ? got.getLongVersionCode() : got.versionCode;
            if (f.isFile() && code <= installed(ctx, a.pkg)) f.delete();
        }
        prefs(ctx).edit().remove("state").remove("needs_you").apply();
        if (!prefs(ctx).getBoolean("helper_was_on", false)) return;
        prefs(ctx).edit().remove("helper_was_on").apply();
        if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
        try {
            String list = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list != null && list.contains(HomeSetup.HELPER)) return;
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    list == null || list.isEmpty() ? HomeSetup.HELPER : list + ":" + HomeSetup.HELPER);
            Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (RuntimeException ignored) { }
    }
}
