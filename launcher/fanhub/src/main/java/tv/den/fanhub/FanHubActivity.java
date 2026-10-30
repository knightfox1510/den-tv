package tv.den.fanhub;

import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.KeyEvent;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * The Fan Hub's own screen: a dashboard (HubView) of the next match, the matches after it, the anime week, the
 * weekend's trail weather and the next 72 hours, in the colours of the look on Home. OK fetches again now (or
 * installs an update that is waiting); holding OK opens the home screen setup; Menu forgets paired phones. Home
 * shows the same games and episodes in the panel under the clock. While den-tv isn't the home screen, opening the
 * Fan Hub offers to make it so (HomeSetupActivity).
 */
public final class FanHubActivity extends Activity {
    private static final Uri CURRENT = Uri.parse("content://tv.den.launcher.looks/current");
    private static final Uri WALLPAPER = Uri.parse("content://tv.den.launcher.looks/wallpaper");

    private HubView hub;
    private boolean offered;
    private PhoneServer server;
    private String qrFor;
    private Bitmap qr;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver changed = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean self) { show(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        hub = new HubView(this);
        setContentView(hub);
        server = PhoneServer.get(this);
    }

    @Override protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(Store.TURNS, false, changed);
        show();
        Store.refresh(this, false);
        PhoneService.start(this);
        server.start();
        server.openPairing(() -> main.post(this::show));
        // Once per visit: Cancel on the setup comes back here, and mustn't open it again.
        if (!offered && HomeSetupActivity.wanted(this)) { offered = true; startActivity(new Intent(this, HomeSetupActivity.class)); }
    }

    @Override protected void onPause() {
        server.closePairing();
        getContentResolver().unregisterContentObserver(changed);
        super.onPause();
    }

    private static boolean ok(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (ok(code)) { e.startTracking(); return true; }   // so holding OK is told apart from a press
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (ok(code)) { startActivity(new Intent(this, HomeSetupActivity.class)); return true; }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (ok(code)) {
            if (e.isCanceled() || (e.getFlags() & KeyEvent.FLAG_CANCELED_LONG_PRESS) != 0) return true;   // the end of a long press
            if (!Updater.waiting(this).isEmpty()) Updater.installNow(this, true);
            else Store.refresh(this, true);
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            server.forgetPhones();
            show();
            return true;
        }
        return super.onKeyUp(code, e);
    }

    /** Fills the dashboard from the cache, the phone server and the look on Home. */
    private void show() {
        HubView.Data d = new HubView.Data();
        FanHub.Config cfg = Store.config(this);
        for (Map.Entry<String, List<FanHub.Turn>> e : Store.bySource(this).entrySet()) {
            String k = e.getKey();
            for (FanHub.Turn t : e.getValue()) {
                if (k.startsWith("team:")) {
                    FanHub.Team team = null;
                    for (FanHub.Team x : cfg.teams) if (k.equals("team:" + x.sportsdb)) team = x;
                    d.teams.add(new HubView.Item(t, team == null ? "" : team.sport, team == null ? "" : team.name));
                } else if (k.startsWith("show:")) d.shows.add(new HubView.Item(t, "show", k.substring(5)));
                else d.others.add(new HubView.Item(t, k, ""));
            }
        }
        d.fetched = Store.fetchedAt(this);
        d.errors = Store.errors(this);
        d.place = cfg.hike == null ? "" : cfg.hike.name;
        d.weekend = FanHub.weekend(Store.forecast(this), System.currentTimeMillis());
        d.extras = Store.extras(this);
        d.h24 = DateFormat.is24HourFormat(this);
        d.running = server.running();
        d.address = PhoneServer.address(this);
        d.code = server.code();
        d.paired = server.pairedPhones();
        d.qr = qr(d.address);
        String waiting = Updater.waiting(this);
        d.notice = waiting.isEmpty() ? "" : "Update ready: " + waiting + "  ·  OK installs it now";
        look(d);
        hub.show(d);
    }

    /** The look on Home: its name, colour and wallpaper (read once per look). */
    private String wallFor;
    private Bitmap wall;

    private void look(HubView.Data d) {
        String id = "";
        try (Cursor c = getContentResolver().query(CURRENT, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                id = c.getString(0);
                d.lookName = id.isEmpty() ? "" : c.getString(1);
                try { d.accent = Color.parseColor(c.getString(2)); } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }   // an older launcher, or none: the Fan Hub's own colours
        if (!id.equals(wallFor)) {
            wallFor = id;
            wall = null;
            if (!id.isEmpty()) {
                try (InputStream in = getContentResolver().openInputStream(WALLPAPER)) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;   // darkened behind cards: half size is plenty, and light on a 1.8 GB TV
                    wall = in == null ? null : BitmapFactory.decodeStream(in, null, o);
                } catch (Exception ignored) { }
            }
        }
        d.wallpaper = wall;
    }

    private Bitmap qr(String at) {
        if (at == null) return null;
        if (!at.equals(qrFor)) {
            BitmapDrawable b = Qr.of(getResources(), at);
            qr = b == null ? null : b.getBitmap();
            qrFor = qr == null ? null : at;
        }
        return qr;
    }
}
