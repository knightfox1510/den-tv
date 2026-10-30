package tv.den.fanhub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes den-tv the home screen from the TV itself, with no computer or phone: with USB debugging on, the Fan Hub
 * connects to the TV's own debugging port (Adb), the TV asks "Allow USB debugging?" once, and HomeSetup does the
 * rest; USB debugging goes off again at the end and the key is thrown away. It also gives Home back to Google.
 * The Fan Hub opens it while den-tv isn't Home; holding OK on the Fan Hub opens it any time.
 */
public final class HomeSetupActivity extends Activity {
    static final String EXTRA_GIVE_BACK = "give_back";
    private static final String PREFS = "home_setup", SKIP = "skip";
    private enum Step { NO_LAUNCHER, IS_HOME, INTRO, ASK_HELPER, ASK_DREAM, DEBUG, CONNECTING, ALLOW, WORKING, DONE, FAILED }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> done = new ArrayList<>();
    private FrameLayout root;
    private float dp;
    private Step step;
    private boolean giveBack, helper = true, dream = true;
    private String error = "";
    private static volatile boolean running;

    /** Whether the Fan Hub should open this by itself: den-tv is installed but isn't Home, and no one said not now. */
    static boolean wanted(Context ctx) {
        return launcherInstalled(ctx) && !isHome(ctx) && !ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SKIP, false);
    }

    static boolean launcherInstalled(Context ctx) {
        try { ctx.getPackageManager().getPackageInfo(HomeSetup.LAUNCHER, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    static boolean isHome(Context ctx) {
        ResolveInfo ri = ctx.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && HomeSetup.LAUNCHER.equals(ri.activityInfo.packageName);
    }

    private static boolean debuggingOn(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    private void skip(boolean on) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(SKIP, on).apply(); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff120a1e, 0xff050507, 0xff0a1620}));
        setContentView(root);
        giveBack = getIntent().getBooleanExtra(EXTRA_GIVE_BACK, false);
        if (running) step = Step.WORKING;
        else if (!launcherInstalled(this)) step = Step.NO_LAUNCHER;
        else if (giveBack) step = Step.DEBUG;
        else step = isHome(this) ? Step.IS_HOME : Step.INTRO;
        show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (step == Step.DEBUG) show();   // back from Settings: maybe it's on now
    }

    @Override public void onBackPressed() {
        if (step == Step.CONNECTING || step == Step.ALLOW || step == Step.WORKING) return;   // let it finish
        super.onBackPressed();
    }

    private void go(Step s) {
        step = s;
        show();
    }

    // ---- the work, off the main thread ---------------------------------------------------------------------------

    private void start() {
        if (running) return;
        running = true;
        done.clear();
        go(Step.CONNECTING);
        Context app = getApplicationContext();
        boolean back = giveBack, wantHelper = helper, wantDream = dream;
        new Thread(() -> {
            String failed = null;
            try {
                failed = work(app, back, wantHelper, wantDream);
            } catch (Adb.NotAllowed e) {
                failed = "The TV didn't allow it. Try again, and choose Allow when the TV asks (tick \"Always allow\").";
            } catch (IOException | RuntimeException e) {
                failed = "The connection to the TV broke off (" + e.getMessage() + "). Try again.";
            } finally {
                running = false;
            }
            String f = failed;
            main.post(() -> {
                if (f == null) { skip(back); go(Step.DONE); }
                else { error = f; go(Step.FAILED); }
            });
        }, "home-setup").start();
    }

    /** Null when it all went through; otherwise what to tell the person. */
    private String work(Context app, boolean back, boolean wantHelper, boolean wantDream) throws IOException {
        KeyPair key = Adb.key(app);
        Adb adb = null;
        for (String host : hosts(app)) {
            try {
                adb = Adb.connect(host, Adb.PORT, key, () -> main.post(() -> go(Step.ALLOW)), 90_000);
                break;
            } catch (Adb.NotAllowed e) {
                throw e;
            } catch (IOException e) {
                // Nothing on this address; try the next.
            }
        }
        if (adb == null) {
            return "This TV doesn't let apps on it use USB debugging (nothing answers on port 5555). Use a computer or an"
                    + " Android phone instead: see INSTALL.md on github.com/" + Updater.REPO + ".";
        }
        main.post(() -> go(Step.WORKING));
        HomeSetup.Progress p = line -> main.post(() -> { done.add(line); if (step == Step.WORKING) show(); });
        try {
            Adb a = adb;
            HomeSetup.Shell sh = a::shell;
            String failed = back ? HomeSetup.giveBack(sh, p) : HomeSetup.makeHome(sh, p);
            if (failed != null) return failed + " USB debugging is still on, so you can try again or use a computer.";
            if (!back && wantHelper) { HomeSetup.helperOn(sh); p.step("den-tv helper on"); }
            if (!back && wantDream) { HomeSetup.screensaverOn(sh); p.step("den-tv screensaver on"); }
            if (!back) Updater.claim(app);
            p.step("USB debugging off");
            try { HomeSetup.debuggingOff(sh); } catch (IOException ignored) { }   // the connection ends with it
        } finally {
            adb.close();
            Adb.forgetKey(app);
        }
        return null;
    }

    /** The TV's own debugging port: on the loopback address first, then on its home-network address. */
    private static List<String> hosts(Context ctx) {
        List<String> out = new ArrayList<>();
        out.add("127.0.0.1");
        String at = PhoneServer.address(ctx);
        if (at != null) out.add(at.replace("http://", "").replaceAll(":\\d+$", ""));
        return out;
    }

    // ---- screens -------------------------------------------------------------------------------------------------

    private void show() {
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(96 * dp);
        col.setPadding(pad, Math.round(48 * dp), pad, Math.round(48 * dp));
        root.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        col.addView(text("den·tv", 22, 0xffff4d6d, true));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, Math.round(32 * dp), 0, 0);
        switch (step) {
            case NO_LAUNCHER:
                col.addView(text("Install den-tv first", 44, Color.WHITE, true));
                col.addView(body("The Fan Hub makes den-tv your home screen, but den-tv itself isn't on this TV yet. Download"
                        + " den-tv-launcher.apk the same way you got the Fan Hub (in Downloader, or from github.com/" + Updater.REPO
                        + "/releases), install it, then open the Fan Hub again."));
                buttons.addView(button("Close", this::finish));
                break;
            case IS_HOME:
                col.addView(text("den-tv is your home screen", 44, Color.WHITE, true));
                col.addView(body("You can make it again (if something took Home back), or give Home back to the TV's own home"
                        + " screen. Either way needs USB debugging on for a minute."));
                buttons.addView(button("Give Home back to Google", () -> { giveBack = true; go(Step.DEBUG); }));
                buttons.addView(button("Set it up again", () -> { giveBack = false; go(Step.ASK_HELPER); }));
                buttons.addView(button("Close", this::finish));   // last, so it has the focus
                break;
            case INTRO:
                col.addView(text("Make den-tv your home screen", 44, Color.WHITE, true));
                col.addView(body("Then the Home button opens den-tv. This TV does it itself: it needs USB debugging on for a"
                        + " minute and asks you to allow it once. Google's home screen is switched off, not removed, and you"
                        + " can bring it back here any time."));
                buttons.addView(button("Not now", () -> { skip(true); finish(); }));
                buttons.addView(button("Make it my home screen", () -> go(Step.ASK_HELPER)));
                break;
            case ASK_HELPER:
                col.addView(text("Turn on the den-tv helper?", 44, Color.WHITE, true));
                col.addView(body("It lets your phone press Back and Home, type into search boxes, open apps and count screen"
                        + " time. It's an accessibility service that only notes which app is in front; it reads nothing else"
                        + " on screen. Volume and play/pause work without it."));
                buttons.addView(button("No", () -> { helper = false; go(Step.ASK_DREAM); }));
                buttons.addView(button("Yes", () -> { helper = true; go(Step.ASK_DREAM); }));
                break;
            case ASK_DREAM:
                col.addView(text("Use den-tv's screensaver?", 44, Color.WHITE, true));
                col.addView(body("The look on Home drifting behind its clock, with what's next from the Fan Hub, instead of the"
                        + " TV's own screensaver."));
                buttons.addView(button("No", () -> { dream = false; go(Step.DEBUG); }));
                buttons.addView(button("Yes", () -> { dream = true; go(Step.DEBUG); }));
                break;
            case DEBUG:
                if (debuggingOn(this)) {
                    col.addView(text("USB debugging is on", 44, Color.WHITE, true));
                    col.addView(body("Next the TV asks \"Allow USB debugging?\". Tick \"Always allow from this computer\" and choose"
                            + " Allow. It's the Fan Hub asking, and USB debugging goes off again when it's done."));
                    buttons.addView(button("Cancel", this::finish));
                    buttons.addView(button("Go", this::start));
                } else {
                    col.addView(text("Turn on USB debugging", 44, Color.WHITE, true));
                    col.addView(body("1.  Settings → System → About → press OK on \"Android TV OS build\" 7 times.\n"
                            + "2.  Back in System → Developer options → turn on USB debugging.\n"
                            + "3.  Press Back to come back here.\n"
                            + "On older TVs these are under Device Preferences, and the build is called Build."));
                    buttons.addView(button("Cancel", this::finish));
                    buttons.addView(button("Open Settings", this::openSettings));
                }
                break;
            case CONNECTING:
                col.addView(text("Connecting…", 44, Color.WHITE, true));
                break;
            case ALLOW:
                col.addView(text("Choose Allow on the TV", 44, Color.WHITE, true));
                col.addView(body("The TV asks \"Allow USB debugging?\". Tick \"Always allow from this computer\", then choose Allow."));
                break;
            case WORKING:
                col.addView(text(giveBack ? "Giving Home back…" : "Setting up…", 44, Color.WHITE, true));
                for (String d : done) col.addView(text("✓  " + d, 22, 0xffc9c6d3, false));
                break;
            case DONE:
                if (giveBack) {
                    col.addView(text("The TV's own home screen is back", 44, Color.WHITE, true));
                    col.addView(body("den-tv and the Fan Hub are still installed: Settings → Apps removes them. USB debugging is off."));
                    buttons.addView(button("Close", this::home));
                } else {
                    col.addView(text("den-tv is your home screen ✓", 44, Color.WHITE, true));
                    // What was switched on, not every step on the way.
                    for (String d : done) if (!d.startsWith("Starting") && !d.startsWith("Switching")) col.addView(text("✓  " + d, 20, 0xffc9c6d3, false));
                    col.addView(body("The Fan Hub keeps den-tv up to date by itself. Next: pair your phone."));
                    buttons.addView(button("Go Home", this::home));
                }
                break;
            default:
                col.addView(text("That didn't work", 44, Color.WHITE, true));
                col.addView(body(error));
                buttons.addView(button("Close", this::finish));
                buttons.addView(button("Try again", () -> go(Step.DEBUG)));
                break;
        }
        if (buttons.getChildCount() > 0) {
            col.addView(buttons);
            buttons.getChildAt(buttons.getChildCount() - 1).requestFocus();
        }
    }

    private void openSettings() {
        boolean dev = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        for (String action : dev ? new String[]{Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_SETTINGS}
                : new String[]{Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS}) {
            try { startActivity(new Intent(action)); return; } catch (ActivityNotFoundException ignored) { }
        }
    }

    private void home() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) { }
        finish();
    }

    private Button button(String label, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(0xffff4d6d));
        bg.addState(new int[]{}, pill(0x26ffffff));
        b.setBackground(bg);
        b.setPadding(Math.round(28 * dp), Math.round(12 * dp), Math.round(28 * dp), Math.round(12 * dp));
        b.setFocusable(true);
        b.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Math.round(20 * dp));
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable pill(int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(28 * dp);
        g.setColor(colour);
        return g;
    }

    private TextView body(String s) { return text(s, 20, 0xffc9c6d3, false); }

    private TextView text(String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colour);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setPadding(0, Math.round(6 * dp), 0, Math.round(6 * dp));
        v.setMaxWidth(Math.round(1000 * dp));
        v.setLineSpacing(0, 1.15f);
        return v;
    }

    /** For the screen renders. */
    void showForTest(String name, List<String> lines) {
        step = Step.valueOf(name);
        done.clear();
        done.addAll(lines);
        show();
    }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, MODE_PRIVATE); }
}
