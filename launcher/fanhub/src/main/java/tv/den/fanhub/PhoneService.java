package tv.den.fanhub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps the phone app reachable while the TV is on, not only while the Fan Hub screen is open, and runs the
 * once-a-minute checks: the timers (sleep, kids, quiet hours, screen time) and the Auto rules. Started by the Fan
 * Hub and setup screens, the helper, and at boot once a phone has been paired.
 */
public final class PhoneService extends Service {
    private static final String CHANNEL = "phone";
    private static final long TICK_MS = 30_000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Store.worker().post(() -> {
                Timers.tick(PhoneService.this);
                Rules.tick(PhoneService.this);
                Updater.tick(PhoneService.this);
            });
            main.postDelayed(this, TICK_MS);
        }
    };

    static void start(Context ctx) {
        try {
            ctx.startForegroundService(new Intent(ctx, PhoneService.class));
        } catch (RuntimeException e) {
            // Not allowed from here right now (the app is in the background on a newer Android); the next screen starts it.
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Phone app", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("den-tv phone app")
                .setContentText("Your paired phone can control this TV")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .build();
        startForeground(1, n);
        PhoneServer.get(this).start();
        main.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        PhoneServer.get(this).start();   // again, in case the port was busy last time
        return START_STICKY;
    }

    @Override public void onDestroy() {
        main.removeCallbacks(tick);
        PhoneServer.get(this).stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
