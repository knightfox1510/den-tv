package tv.den.fanhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the TV boots, the phone app and the update checks come back on (once a phone is paired, or while updates
 * are automatic). After the Fan Hub was updated, its helper is put back and the phone app restarted.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Updater.afterUpdate(ctx);
            PhoneService.start(ctx);
            return;
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) return;
        if (PhoneServer.get(ctx).pairedPhones() > 0 || Updater.auto(ctx)) PhoneService.start(ctx);
    }
}
