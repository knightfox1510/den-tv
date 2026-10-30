package tv.den.fanhub;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Speeding the TV up with cleanyourtv's tested lists (assets/cleanyourtv.json, made by tools/cleanyourtv/import.py):
 * each one is the background apps (ads, tracking, demos, auto-installs, preloads) an owner switched off on one TV
 * model and checked. The TV is matched to a list by its brand and model or device name, and the person confirms it.
 * Nothing is guessed for a TV with no list. On top of each list, cleanyourtv's own safety checks: never core Android
 * or TV middleware, never what drives the HDMI inputs, opens Settings or is the keyboard, never a home screen. Apps
 * with a tile of their own (that someone may watch) are left on unless picked. Apps are switched off, not removed,
 * and come back with Settings, Apps, or from the Fan Hub.
 */
final class Speedup {
    private static final String PREFS = "speedup";

    static final class TvList {
        final String folder, tv, brand;
        final List<String> models = new ArrayList<>(), devices = new ArrayList<>();
        final List<String[]> entries = new ArrayList<>();
        TvList(String folder, String tv, String brand) { this.folder = folder; this.tv = tv; this.brand = brand; }
    }

    /** One app on the list, on this TV: its name, the list's note, whether it has a tile, and whether it is picked. */
    static final class Item {
        final String pkg, label, note;
        final boolean app;
        boolean on;
        Item(String pkg, String label, String note, boolean app, boolean on) { this.pkg = pkg; this.label = label; this.note = note; this.app = app; this.on = on; }
    }

    private Speedup() {}

    // ---- the lists and which one is this TV ----------------------------------------------------------------------

    static List<TvList> load(Context ctx) {
        try (InputStream in = ctx.getAssets().open("cleanyourtv.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return parse(new JSONObject(b.toString("UTF-8")));
        } catch (IOException | JSONException e) {
            return new ArrayList<>();
        }
    }

    static List<TvList> parse(JSONObject root) throws JSONException {
        List<TvList> out = new ArrayList<>();
        JSONArray lists = root.getJSONArray("lists");
        for (int i = 0; i < lists.length(); i++) {
            JSONObject l = lists.getJSONObject(i);
            TvList t = new TvList(l.getString("folder"), l.getString("tv"), l.optString("brand"));
            JSONArray m = l.optJSONArray("models"), d = l.optJSONArray("devices"), e = l.getJSONArray("entries");
            for (int k = 0; m != null && k < m.length(); k++) t.models.add(m.getString(k));
            for (int k = 0; d != null && k < d.length(); k++) t.devices.add(d.getString(k));
            for (int k = 0; k < e.length(); k++) t.entries.add(new String[]{e.getJSONArray(k).getString(0), e.getJSONArray(k).optString(1)});
            out.add(t);
        }
        return out;
    }

    /** The lists for this TV, from Build (brand, maker, model, and the device, product, board and hardware names). */
    static List<TvList> forThisTv(Context ctx) {
        return match(load(ctx), Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.HARDWARE);
    }

    /**
     * The best-matching lists: the brand must match, then the model named in the list, the device's codename, or the
     * model in the TV's name, best first. Several at the same level are all returned, for the person to pick theirs.
     */
    static List<TvList> match(List<TvList> all, String brand, String maker, String model, String... devices) {
        String who = (brand + " " + maker + " " + model).toLowerCase(Locale.ROOT);
        Set<String> names = new HashSet<>();
        for (String d : devices) if (d != null && d.length() >= 3) names.add(d.toLowerCase(Locale.ROOT));
        String mdl = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
        List<TvList> out = new ArrayList<>();
        int best = 0;
        for (TvList l : all) {
            if (l.brand.isEmpty() || !word(who, l.brand.toLowerCase(Locale.ROOT))) continue;
            // The model as the list names it, then the device's codename (unique to a board), then the model in the
            // TV's name (weakest: "Chromecast" is in two lists' names).
            int score = 0;
            for (String m : l.models) if (m.equalsIgnoreCase(mdl)) score = 3;
            if (score == 0) for (String d : l.devices) if (names.contains(d)) score = 2;
            if (score == 0 && mdl.length() >= 4 && word(l.tv.toLowerCase(Locale.ROOT), mdl)) score = 1;
            if (score == 0 || score < best) continue;
            if (score > best) { best = score; out.clear(); }
            out.add(l);
        }
        return out;
    }

    /** text has phrase as whole words ("vu" in "vu 4k", not in "vuzix"). */
    private static boolean word(String text, String phrase) {
        return Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(phrase) + "($|[^a-z0-9])").matcher(text).find();
    }

    // ---- what's safe to switch off on this TV --------------------------------------------------------------------

    /**
     * Core Android and TV middleware: with one of these off the TV may not boot again (cleanyourtv's CORE_RE, from
     * a Mi TV Stick that never came back and a Haier that restarted in a loop). Never switched off, whatever a list says.
     */
    static final Pattern CORE = Pattern.compile("^(android|android\\.ext\\.(services|shared)|com\\.android\\.systemui|com\\.android\\.shell"
            + "|com\\.android\\.phone|com\\.android\\.settings|com\\.android\\.tv\\.settings|com\\.(google\\.)?android\\.(permissioncontroller"
            + "|packageinstaller|ext\\.services|ext\\.shared|modulemetadata)|com\\.google\\.android\\.overlay\\.modules.*"
            + "|com\\.android\\.providers\\..*|com\\.cltv\\.mal|com\\.mediatek\\.(tis|tv\\.service|tv\\.agent))$");
    static final Pattern CORE_OK = Pattern.compile("^com\\.android\\.providers\\.(calendar|contacts|userdictionary|blockednumber)$");

    /** The list's apps that are on this TV, switched on, and safe to switch off; tiled apps and "by choice" ones unpicked. */
    static List<Item> plan(Context ctx, TvList list) {
        PackageManager pm = ctx.getPackageManager();
        Set<String> keep = keepers(ctx);
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String[] e : list.entries) {
            String pkg = e[0], note = e[1];
            if (!seen.add(pkg) || keep.contains(pkg) || (CORE.matcher(pkg).matches() && !CORE_OK.matcher(pkg).matches())) continue;
            ApplicationInfo ai;
            PackageInfo pi;
            try {
                ai = pm.getApplicationInfo(pkg, 0);
                pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            } catch (PackageManager.NameNotFoundException ex) {
                continue;   // not on this TV
            }
            int state = pm.getApplicationEnabledSetting(pkg);
            if (!ai.enabled || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) continue;
            // The software under the HDMI, AV and tuner inputs: off, the ports stay black for good (cleanyourtv #321).
            if (pi.requestedPermissions != null) {
                boolean input = false;
                for (String p : pi.requestedPermissions) if ("android.permission.TV_INPUT_HARDWARE".equals(p)) input = true;
                if (input) continue;
            }
            boolean app = pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
            String n = note.toLowerCase(Locale.ROOT);
            boolean choice = n.contains("choice") || n.contains("optional") || n.contains("if you") || n.contains("by owner");
            out.add(new Item(pkg, String.valueOf(ai.loadLabel(pm)), note, app, !app && !choice));
        }
        return out;
    }

    /** Never switched off: home screens and the setup wizard (HomeSetup's), den-tv, the inputs, Settings, the keyboard. */
    private static Set<String> keepers(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Set<String> keep = new HashSet<>();
        keep.add(HomeSetup.LAUNCHER);
        keep.add(HomeSetup.FANHUB);
        keep.add(HomeSetup.WIZARD);
        for (String s : HomeSetup.STOCK) keep.add(s);
        addAll(keep, pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DISABLED_COMPONENTS));
        // What switches to an HDMI input, and what opens Settings: without them, no inputs and no way back to
        // Settings on a remote with no Settings button (cleanyourtv: a Sony lost its inputs, another its Settings).
        addAll(keep, pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.item/channel"), 0));
        addAll(keep, pm.queryIntentActivities(new Intent(Settings.ACTION_SETTINGS), 0));
        for (ResolveInfo ri : pm.queryIntentServices(new Intent("android.media.tv.TvInputService"), 0)) {
            if (ri.serviceInfo != null) keep.add(ri.serviceInfo.packageName);
        }
        String ime = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null && ime.contains("/")) keep.add(ime.substring(0, ime.indexOf('/')));
        return keep;
    }

    private static void addAll(Set<String> keep, List<ResolveInfo> found) {
        for (ResolveInfo ri : found) if (ri.activityInfo != null) keep.add(ri.activityInfo.packageName);
    }

    // ---- switching off and back on (over Adb) --------------------------------------------------------------------

    /**
     * Switches the picked apps off; returns the ones that went. If Home stopped opening den-tv afterwards they all
     * come back on, and an empty list is returned.
     */
    static List<String> apply(Context ctx, HomeSetup.Shell sh, List<Item> items, HomeSetup.Progress p) throws IOException {
        List<String> off = new ArrayList<>();
        for (Item i : items) {
            if (!i.on) continue;
            String out = sh.run("pm disable-user --user 0 " + i.pkg);
            if (out.contains("disabled")) off.add(i.pkg);
        }
        if (!off.isEmpty() && !HomeSetup.homeNow(sh).startsWith(HomeSetup.LAUNCHER + "/")) {
            for (String pkg : off) sh.run("pm enable --user 0 " + pkg);
            return new ArrayList<>();
        }
        remember(ctx, off);
        if (!off.isEmpty()) p.step(off.size() + (off.size() == 1 ? " background app" : " background apps") + " switched off");
        return off;
    }

    /** Switches back on everything the Fan Hub switched off. */
    static void restore(Context ctx, HomeSetup.Shell sh, HomeSetup.Progress p) throws IOException {
        Set<String> off = switchedOff(ctx);
        for (String pkg : off) sh.run("pm enable --user 0 " + pkg);
        if (!off.isEmpty()) p.step(off.size() + (off.size() == 1 ? " app" : " apps") + " switched back on");
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("off").apply();
    }

    /** Which tested list this TV's owner confirmed (its TV name), for the phone; "" before one was. */
    static void rememberTv(Context ctx, TvList l) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tv", l.folder).apply();
    }

    /**
     * For the phone's TV tab: the confirmed list (or the best match), each of its apps on this TV with whether it is
     * switched off now and whether den-tv did it; and anything den-tv switched off that isn't on the list.
     */
    static JSONObject status(Context ctx) throws JSONException {
        PackageManager pm = ctx.getPackageManager();
        Set<String> ours = switchedOff(ctx);
        String chosen = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tv", "");
        List<TvList> all = load(ctx);
        TvList list = null;
        for (TvList l : all) if (l.folder.equals(chosen)) list = l;
        if (list == null) {
            List<TvList> m = match(all, Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.HARDWARE);
            if (m.size() == 1) list = m.get(0);
        }
        JSONArray apps = new JSONArray();
        Set<String> listed = new HashSet<>();
        int off = 0;
        if (list != null) for (String[] e : list.entries) {
            if (!listed.add(e[0])) continue;
            JSONObject a = app(pm, e[0], e[1], ours);
            if (a != null) { apps.put(a); if (a.getBoolean("off")) off++; }
        }
        for (String pkg : ours) if (!listed.contains(pkg)) { JSONObject a = app(pm, pkg, "", ours); if (a != null) apps.put(a); }
        return new JSONObject().put("tv", list == null ? "" : list.tv).put("confirmed", list != null && list.folder.equals(chosen))
                .put("apps", apps).put("off", off).put("byDen", ours.size())
                .put("model", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")");
    }

    private static JSONObject app(PackageManager pm, String pkg, String note, Set<String> ours) throws JSONException {
        String label;
        try { label = String.valueOf(pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).loadLabel(pm)); }
        catch (PackageManager.NameNotFoundException e) { return null; }   // not on this TV
        int state = pm.getApplicationEnabledSetting(pkg);
        boolean off = state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        return new JSONObject().put("pkg", pkg).put("label", label).put("note", note).put("off", off).put("byDen", ours.contains(pkg));
    }

    static Set<String> switchedOff(Context ctx) {
        Set<String> out = new HashSet<>();
        try {
            JSONArray a = new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("off", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    private static void remember(Context ctx, List<String> more) {
        Set<String> all = switchedOff(ctx);
        all.addAll(more);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("off", new JSONArray(all).toString()).apply();
    }
}
