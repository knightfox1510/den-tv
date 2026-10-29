package tv.den.fanhub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * den-tv's first-time setup, as in the mockup: the TV owns welcome, Wi-Fi and pairing; once a phone is paired it
 * drives the rest (name, teams and shows, what Home shows, the look) on the phone page, and this screen follows
 * along until the phone says it is done. The launcher opens it once after install, and its Looks screen has a
 * tile to run it again. Back steps back; Back on the first screen, or Menu, leaves without changing anything.
 */
public final class SetupActivity extends Activity {
    private static final int WELCOME = 0, WIFI = 1, PAIR = 2, PHONE = 3, DONE = 4;
    private static final Uri SETTINGS = Uri.parse("content://tv.den.launcher.looks/settings");
    private static final Uri LOOKS = Uri.parse("content://tv.den.launcher.looks/looks");

    private final Handler main = new Handler(Looper.getMainLooper());
    private PhoneServer server;
    private FrameLayout root;
    private int step = WELCOME;
    private String phoneStep = "phone";
    private float dp;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff120a1e, 0xff050507, 0xff0a1620}));
        setContentView(root);
        server = new PhoneServer(this, () -> main.post(() -> { if (step == PAIR) show(); }));
        server.setSetup(new PhoneServer.Setup() {
            @Override public void step(String s) {
                main.post(() -> {
                    if (step == PAIR || step == PHONE) { phoneStep = s; go(PHONE); }
                });
            }
            @Override public void done() { main.post(() -> go(DONE)); }
        });
        show();
    }

    @Override protected void onResume() {
        super.onResume();
        server.start();
        if (step == WIFI || step == PAIR) show();   // back from Wi-Fi settings, or the address changed
    }

    @Override protected void onPause() {
        server.stop();
        super.onPause();
    }

    private void go(int s) {
        step = s;
        show();
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (step == WELCOME) go(WIFI);
            else if (step == WIFI) {
                if (online()) go(PAIR);
                else try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); } catch (ActivityNotFoundException ignored) { }
            } else if (step == DONE) home();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) { finish(); return true; }
        return super.onKeyUp(code, e);
    }

    @Override public void onBackPressed() {
        if (step == WIFI) go(WELCOME);
        else if (step == PAIR) go(WIFI);
        else if (step == PHONE) go(PAIR);
        else if (step == DONE) home();
        else super.onBackPressed();
    }

    private void home() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) { }
        finish();
    }

    private boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
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
        col.addView(dots());
        switch (step) {
            case WELCOME:
                col.addView(text("den·tv", 22, 0xffff4d6d, true));
                col.addView(text("Let's make this TV yours", 44, Color.WHITE, true));
                col.addView(text("Your name, your teams and shows, and a look for Home, set up from your phone in about two minutes.", 20, 0xffc9c6d3, false));
                col.addView(hint("Press OK to start  ·  Back to skip"));
                break;
            case WIFI:
                if (online()) {
                    col.addView(text("Connected ✓", 44, Color.WHITE, true));
                    col.addView(text("Keep your phone on the same Wi-Fi as this TV. The settings page runs on the TV and stays on your home network.", 20, 0xffc9c6d3, false));
                    col.addView(hint("Press OK to pair your phone"));
                } else {
                    col.addView(text("Connect to Wi-Fi first", 44, Color.WHITE, true));
                    col.addView(text("Your phone talks to the TV over your home Wi-Fi, and the Fan Hub needs it for scores and episodes.", 20, 0xffc9c6d3, false));
                    col.addView(hint("Press OK to open Wi-Fi settings"));
                }
                break;
            case PAIR: pair(col); break;
            case PHONE:
                col.addView(text("Carry on on your phone", 44, Color.WHITE, true));
                col.addView(text(phoneLabel(phoneStep), 24, 0xffffd98a, false));
                col.addView(text("This screen follows along. Keep it open until your phone says you're done.", 20, 0xffc9c6d3, false));
                col.addView(hint("Back shows the code again"));
                break;
            default: done(col); break;
        }
    }

    private static String phoneLabel(String s) {
        switch (s) {
            case "name": return "Step 1 of 4 · Your name";
            case "interests": return "Step 2 of 4 · Teams and shows";
            case "home": return "Step 3 of 4 · What Home shows";
            case "look": return "Step 4 of 4 · Pick a look";
            default: return "Your phone is connected";
        }
    }

    private void pair(LinearLayout col) {
        String at = PhoneServer.address(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        BitmapDrawable code = at == null ? null : Qr.of(getResources(), at);
        if (code != null) {
            ImageView qr = new ImageView(this);
            qr.setImageDrawable(code);
            qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(qr, new LinearLayout.LayoutParams(Math.round(220 * dp), Math.round(220 * dp)));
        }
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(code == null ? 0 : Math.round(40 * dp), 0, 0, 0);
        right.addView(text("Pair your phone", 40, Color.WHITE, true));
        if (!server.running()) {
            right.addView(text("The settings page couldn't start (port " + PhoneServer.PORT + " is in use). Press Back and try again.", 20, 0xffff7a7a, false));
        } else if (at == null) {
            right.addView(text("The TV has no address on your home network. Check the Wi-Fi.", 20, 0xffff7a7a, false));
        } else {
            right.addView(text("Scan the code with your phone's camera, or open", 20, 0xffc9c6d3, false));
            right.addView(text(at, 24, 0xff7fd8ff, true));
            right.addView(text("and enter", 20, 0xffc9c6d3, false));
            TextView digits = text(server.code(), 64, Color.WHITE, true);
            digits.setLetterSpacing(0.3f);
            right.addView(digits);
            right.addView(text(server.pairedPhones() > 0 ? "A phone that's paired already goes straight on: just open the page." : "Only phones on this Wi-Fi can open it.", 16, 0xffa09dac, false));
        }
        row.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(row);
        col.addView(hint("No phone? Menu leaves the setup. Run it again from the Looks screen."));
    }

    private void done(LinearLayout col) {
        String name = "";
        try (Cursor c = getContentResolver().query(SETTINGS, null, null, null, null)) {
            while (c != null && c.moveToNext()) if ("greeting_name".equals(c.getString(0))) name = c.getString(1);
        } catch (RuntimeException ignored) { }
        String look = "";
        try (Cursor c = getContentResolver().query(LOOKS, null, null, null, null)) {
            while (c != null && c.moveToNext()) if (c.getInt(3) == 1) look = c.getString(1);
        } catch (RuntimeException ignored) { }
        FanHub.Config cfg = Store.config(this);
        col.addView(text("You're all set" + (name == null || name.isEmpty() ? "" : ", " + name), 44, Color.WHITE, true));
        col.addView(text("•  " + cfg.teams.size() + (cfg.teams.size() == 1 ? " team" : " teams") + " and " + cfg.shows.size()
                + (cfg.shows.size() == 1 ? " show" : " shows") + " in the Fan Hub", 22, 0xffc9c6d3, false));
        if (!look.isEmpty()) col.addView(text("•  Look: " + look, 22, 0xffc9c6d3, false));
        col.addView(text("•  Your phone stays paired: open the Fan Hub on the TV to change things from it", 22, 0xffc9c6d3, false));
        col.addView(hint("Press OK to go Home"));
    }

    private View dots() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, Math.round(28 * dp));
        for (int i = 0; i <= DONE; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(4 * dp);
            g.setColor(i == step ? 0xffff4d6d : i < step ? 0x99ffffff : 0x33ffffff);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round((i == step ? 28 : 8) * dp), Math.round(8 * dp));
            lp.setMarginEnd(Math.round(8 * dp));
            row.addView(d, lp);
        }
        return row;
    }

    private TextView hint(String s) {
        TextView t = text(s, 18, 0xffffd98a, false);
        t.setPadding(0, Math.round(32 * dp), 0, 0);
        return t;
    }

    private TextView text(String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colour);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setPadding(0, Math.round(6 * dp), 0, Math.round(6 * dp));
        v.setMaxWidth(Math.round(900 * dp));
        return v;
    }
}
