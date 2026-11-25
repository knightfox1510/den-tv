package tv.den.fanhub;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Your remote's own buttons (the app buttons: Netflix, YouTube, Prime Video and the like). On Google TV the home
 * screen den-tv switches off was what answered them, so they go dead; here the phone teaches the den-tv helper a
 * button (it listens for the next one pressed) and says what it opens: any app, or Home, Back, Sleep, Settings,
 * Mute or Play/Pause. The arrows, OK, Back, Home, volume and power are never taken, so a mistake can't lock you out.
 * Some firmware sends a button only to its own home screen and never to an app; learning then hears nothing.
 */
final class RemoteKeys {
    private static final String PREFS = "remote_keys";
    static final long LEARN_MS = 20_000;
    /** Actions a button can do besides opening an app. */
    static final String[][] ACTIONS = {{"home", "Home"}, {"back", "Back"}, {"sleep", "Sleep"}, {"settings", "Settings"}, {"mute", "Mute"}, {"play_pause", "Play / pause"}};

    private static volatile long learnUntil;
    private static volatile JSONObject heard;
    /** The key whose press we answered, so its release (and repeats) are swallowed too. */
    private static int eaten = -1;

    private RemoteKeys() {}

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static JSONArray list(Context ctx) {
        try { return new JSONArray(prefs(ctx).getString("buttons", "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    /** Whether the helper should listen to the remote: a button is set up, or one is being learned. */
    static boolean wanted(Context ctx) { return list(ctx).length() > 0 || SystemClock.uptimeMillis() < learnUntil; }

    /** Keys that keep the TV usable whatever happens; never learned or remapped. */
    static boolean protectedKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_WAKEUP:
                return true;
            default:
                return false;
        }
    }

    /** "KEYCODE_BUTTON_3" → "Button 3"; a key Android has no name for is named by its scancode. */
    static String name(int code, int scan) {
        if (code == KeyEvent.KEYCODE_UNKNOWN) return "Button " + scan;
        String n = KeyEvent.keyCodeToString(code).replaceFirst("^KEYCODE_", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        // A keycode Android has no name for comes back as its number.
        return n.isEmpty() || n.matches("\\d+") ? "Button " + code : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Whether a stored button is this key: by keycode, or by scancode when Android gives it no keycode. */
    static boolean matches(JSONObject b, int code, int scan) {
        int c = b.optInt("code", -1);
        return c == KeyEvent.KEYCODE_UNKNOWN ? scan != 0 && b.optInt("scan", -1) == scan : c == code;
    }

    // ---- learning --------------------------------------------------------------------------------------------------

    static void learn() {
        heard = null;
        learnUntil = SystemClock.uptimeMillis() + LEARN_MS;
        Helper.post(Helper::listen);
        // Stop listening again if nothing is pressed and no button is set up.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(Helper::listen, LEARN_MS + 500);
    }

    /** {state: idle|listening|heard, code, scan, name, left (ms)}. */
    static JSONObject learning() throws JSONException {
        long left = learnUntil - SystemClock.uptimeMillis();
        JSONObject h = heard;
        if (h != null) return new JSONObject(h.toString()).put("state", "heard");
        return new JSONObject().put("state", left > 0 ? "listening" : "idle").put("left", Math.max(0, left));
    }

    // ---- the buttons -----------------------------------------------------------------------------------------------

    /** Sets what a key does (replacing what it did before); false for a target that's neither an app nor an action. */
    static boolean save(Context ctx, int code, int scan, String label, String target) throws JSONException {
        if (protectedKey(code) || (code == KeyEvent.KEYCODE_UNKNOWN && scan == 0)) return false;
        if (!isAction(target) && !installed(ctx, target)) return false;
        JSONArray all = list(ctx), out = new JSONArray();
        for (int i = 0; i < all.length(); i++) if (!matches(all.getJSONObject(i), code, scan)) out.put(all.getJSONObject(i));
        String l = label == null ? "" : label.trim();
        out.put(new JSONObject().put("id", "k" + code + "-" + scan).put("code", code).put("scan", scan)
                .put("label", l.isEmpty() ? name(code, scan) : (l.length() > 30 ? l.substring(0, 30) : l)).put("target", target));
        prefs(ctx).edit().putString("buttons", out.toString()).apply();
        heard = null;
        learnUntil = 0;
        Helper.post(Helper::listen);
        return true;
    }

    static void remove(Context ctx, String id) {
        JSONArray all = list(ctx), out = new JSONArray();
        for (int i = 0; i < all.length(); i++) if (!id.equals(all.optJSONObject(i).optString("id"))) out.put(all.optJSONObject(i));
        prefs(ctx).edit().putString("buttons", out.toString()).apply();
        Helper.post(Helper::listen);
    }

    static boolean isAction(String target) {
        for (String[] a : ACTIONS) if (a[0].equals(target)) return true;
        return false;
    }

    private static boolean installed(Context ctx, String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}")) return false;
        PackageManager pm = ctx.getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    /** For the phone: the buttons with what each opens, and whether the helper is on to answer them. */
    static JSONObject status(Context ctx) throws JSONException {
        JSONArray all = list(ctx), out = new JSONArray();
        PackageManager pm = ctx.getPackageManager();
        for (int i = 0; i < all.length(); i++) {
            JSONObject b = new JSONObject(all.getJSONObject(i).toString());
            String t = b.optString("target"), what = null;
            for (String[] a : ACTIONS) if (a[0].equals(t)) what = a[1];
            if (what == null) {
                try { what = String.valueOf(pm.getApplicationInfo(t, 0).loadLabel(pm)); } catch (PackageManager.NameNotFoundException e) { what = t + " (not installed)"; }
            }
            out.put(b.put("what", what));
        }
        JSONArray actions = new JSONArray();
        for (String[] a : ACTIONS) actions.put(new JSONObject().put("id", a[0]).put("name", a[1]));
        return new JSONObject().put("buttons", out).put("actions", actions).put("helper", Helper.on()).put("learning", learning());
    }

    // ---- the helper's key filter -----------------------------------------------------------------------------------

    /** Called with every key while the helper listens; true swallows it. Runs on the main thread. */
    static boolean onKey(Context ctx, KeyEvent e) {
        int code = e.getKeyCode(), scan = e.getScanCode();
        if (protectedKey(code)) return false;
        if (eaten == code && code != KeyEvent.KEYCODE_UNKNOWN) {
            if (e.getAction() == KeyEvent.ACTION_UP) eaten = -1;
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (SystemClock.uptimeMillis() < learnUntil) {
            try { heard = new JSONObject().put("code", code).put("scan", scan).put("name", name(code, scan)); } catch (JSONException ignored) { }
            learnUntil = 0;
            eaten = code;
            Helper.post(Helper::listen);
            return true;
        }
        JSONArray all = list(ctx);
        for (int i = 0; i < all.length(); i++) {
            JSONObject b = all.optJSONObject(i);
            if (b == null || !matches(b, code, scan)) continue;
            eaten = code;
            if (e.getRepeatCount() == 0) run(ctx, b.optString("target"));
            return true;
        }
        return false;
    }

    /** Does what a button is set to: opens its app, or runs its action. */
    static boolean run(Context ctx, String target) {
        switch (target) {
            case "home": return Helper.home();
            case "back": return Helper.back();
            case "sleep": return Helper.sleep();
            case "settings": return Helper.start(new Intent(Settings.ACTION_SETTINGS));
            case "mute": {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                return true;
            }
            case "play_pause": {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                long t = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                return true;
            }
            default: {
                PackageManager pm = ctx.getPackageManager();
                Intent i = pm.getLeanbackLaunchIntentForPackage(target);
                if (i == null) i = pm.getLaunchIntentForPackage(target);
                return i != null && Helper.start(i);
            }
        }
    }
}
