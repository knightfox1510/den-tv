package tv.den.fanhub;

import android.app.Activity;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

/**
 * The Fan Hub's own screen: what Home will show next, when it was fetched and anything that failed. OK fetches
 * again now. Home shows the same games and episodes in the panel under the clock.
 */
public final class FanHubActivity extends Activity {
    private LinearLayout list;
    private TextView status, phone;
    private PhoneServer server;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver changed = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean self) { show(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xff050507);
        int pad = Math.round(48 * dp);
        page.setPadding(pad, Math.round(28 * dp), pad, Math.round(20 * dp));
        page.addView(text("Fan Hub", 30, Color.WHITE, true));
        status = text("", 14, 0xffa09dac, false);
        page.addView(status);
        phone = text("", 16, 0xffffd98a, false);
        phone.setPadding(0, Math.round(12 * dp), 0, 0);
        page.addView(phone);
        server = new PhoneServer(this, () -> main.post(this::showPhone));
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Math.round(16 * dp), 0, 0);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(Store.TURNS, false, changed);
        show();
        Store.refresh(this, false);
        server.start();
        showPhone();
    }

    @Override protected void onPause() {
        server.stop();
        getContentResolver().unregisterContentObserver(changed);
        super.onPause();
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            status.setText("Fetching…");
            Store.refresh(this, true);
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            server.forgetPhones();
            showPhone();
            return true;
        }
        return super.onKeyUp(code, e);
    }

    /** Where the phone page is, its pairing code, and how many phones are paired. Only while this screen is open. */
    private void showPhone() {
        String at = PhoneServer.address();
        int n = server.pairedPhones();
        if (!server.running()) {
            phone.setText("Phone settings couldn't start: port " + PhoneServer.PORT + " is in use. Close Fan Hub and open it again.");
            return;
        }
        phone.setText(at == null ? "Phone settings: connect the TV to Wi-Fi first."
                : "Phone settings: on a phone on this Wi-Fi, open " + at + " and enter " + server.code() + ". It works while this screen is open."
                + (n > 0 ? " " + n + (n == 1 ? " phone is" : " phones are") + " paired; Menu forgets them." : ""));
    }

    private void show() {
        List<FanHub.Turn> turns = Store.turns(this);
        long at = Store.fetchedAt(this);
        String err = Store.errors(this);
        status.setText((at == 0 ? "Not fetched yet." : "Updated " + DateFormat.getTimeFormat(this).format(new Date(at)) + ".")
                + " OK fetches again. Home shows these under the clock." + (err.isEmpty() ? "" : "\nCouldn't reach: " + err.replace('\n', ';')));
        list.removeAllViews();
        if (turns.isEmpty()) list.addView(text("Nothing in the next week yet.", 18, Color.WHITE, false));
        long now = System.currentTimeMillis();
        for (FanHub.Turn t : turns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            float dp = getResources().getDisplayMetrics().density;
            row.setPadding(0, Math.round(8 * dp), 0, Math.round(8 * dp));
            View bar = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(t.colour);
            g.setCornerRadius(2 * dp);
            bar.setBackground(g);
            row.addView(bar, new LinearLayout.LayoutParams(Math.round(4 * dp), Math.round(40 * dp)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Math.round(14 * dp), 0, 0, 0);
            col.addView(text(t.topLine, 18, Color.WHITE, true));
            boolean live = now >= t.start && now < t.end;
            col.addView(text(live ? "● Live now" : t.word + " " + DateFormat.format("EEE d MMM, " + (DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a"), t.start),
                    14, live ? 0xffff4d4d : 0xffa09dac, false));
            row.addView(col);
            list.addView(row);
        }
    }

    private TextView text(String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colour);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }
}
