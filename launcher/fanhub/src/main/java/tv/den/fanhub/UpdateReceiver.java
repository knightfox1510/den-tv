package tv.den.fanhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Hears how an update install went (Updater.install): goes on to the next, or asks for OK when Android wants it. */
public final class UpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String pkg = intent.getStringExtra("pkg");
        boolean user = intent.getBooleanExtra("user", false);
        PendingResult async = goAsync();
        Store.worker().post(() -> {
            try {
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    // Someone pressed OK on the Fan Hub: Android's own "Update this app?" goes on screen now.
                    if (user && confirm != null) {
                        try { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                        catch (RuntimeException ignored) { }
                    }
                    Updater.needsYou(ctx);
                } else if (status == PackageInstaller.STATUS_SUCCESS) {
                    Updater.installedOne(ctx, pkg, user);
                } else {
                    String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    Updater.failed(ctx, pkg, why == null ? "status " + status : why);
                }
            } finally {
                async.finish();
            }
        });
    }
}
