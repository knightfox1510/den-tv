package tv.den.fanhub;

import android.Manifest;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.speech.RecognizerIntent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Voice, from the phone and on the TV. search() opens a search for words the phone sends (said into the phone
 * keyboard's mic, or typed): across the TV, as the remote's voice button would, or in YouTube. check() looks at why
 * the remote's own voice button might not listen: which app answers it, whether that app is on and may use the
 * microphone, and which apps that use the microphone are switched off (a debloat list can include the TV maker's
 * voice or remote-audio service). It only reads the TV's settings and changes nothing.
 */
final class Voice {
    static final String GOOGLE = "com.google.android.katniss", GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx";
    static final String YOUTUBE = "com.google.android.youtube.tv";

    private Voice() {}

    /** Opens a search for q: where "youtube" in YouTube, anything else across the TV. Returns the app's name, or null. */
    static String search(Context ctx, String q, String where) {
        PackageManager pm = ctx.getPackageManager();
        Intent[] tries = where.equals("youtube")
                ? new Intent[]{
                        new Intent(Intent.ACTION_SEARCH).setPackage(YOUTUBE).putExtra(SearchManager.QUERY, q),
                        new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))).setPackage(YOUTUBE)}
                : new Intent[]{
                        new Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, q),
                        new Intent(Intent.ACTION_SEARCH).setPackage(GOOGLE).putExtra(SearchManager.QUERY, q),
                        new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)};
        for (Intent i : tries) {
            ResolveInfo r = pm.resolveActivity(i, 0);
            if (r == null || r.activityInfo == null) continue;
            i.setComponent(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
            if (Timers.open(ctx, i)) return String.valueOf(r.loadLabel(pm));
        }
        return null;
    }

    /** What the remote's voice button needs, as the phone's Voice check shows it. */
    static JSONObject check(Context ctx) throws JSONException {
        PackageManager pm = ctx.getPackageManager();
        JSONObject out = new JSONObject();
        out.put("assistant", app(ctx, handler(pm, new Intent(Intent.ACTION_ASSIST))));
        out.put("recognizer", app(ctx, handler(pm, new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))));
        out.put("google", app(ctx, GOOGLE));
        out.put("googleTvHome", app(ctx, GOOGLE_TV_HOME));
        // Apps that ask for the microphone and are switched off: a maker's voice or remote-audio service among them
        // is the usual reason a remote's voice button opens but hears nothing.
        JSONArray off = new JSONArray();
        List<PackageInfo> all = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.MATCH_DISABLED_COMPONENTS);
        for (PackageInfo p : all) {
            if (p.requestedPermissions == null || p.applicationInfo == null || !disabled(pm, p.packageName)) continue;
            for (String perm : p.requestedPermissions) {
                if (!Manifest.permission.RECORD_AUDIO.equals(perm)) continue;
                off.put(new JSONObject().put("pkg", p.packageName).put("label", String.valueOf(p.applicationInfo.loadLabel(pm)))
                        .put("system", (p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
                break;
            }
        }
        return out.put("micAppsOff", off);
    }

    private static String handler(PackageManager pm, Intent i) {
        ResolveInfo r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return r == null || r.activityInfo == null || r.activityInfo.packageName.equals("android") ? "" : r.activityInfo.packageName;
    }

    private static boolean disabled(PackageManager pm, String pkg) {
        try {
            int s = pm.getApplicationEnabledSetting(pkg);
            return s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && !pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled);
        } catch (IllegalArgumentException | PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** {pkg, label, installed, on, mic} for an app; {} for none. */
    private static JSONObject app(Context ctx, String pkg) throws JSONException {
        JSONObject a = new JSONObject();
        if (pkg == null || pkg.isEmpty()) return a;
        PackageManager pm = ctx.getPackageManager();
        a.put("pkg", pkg);
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            a.put("label", String.valueOf(ai.loadLabel(pm))).put("installed", true).put("on", !disabled(pm, pkg))
                    .put("mic", pm.checkPermission(Manifest.permission.RECORD_AUDIO, pkg) == PackageManager.PERMISSION_GRANTED);
        } catch (PackageManager.NameNotFoundException e) {
            a.put("installed", false);
        }
        return a;
    }
}
