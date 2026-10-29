package tv.den.fanhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After the TV boots, the phone app comes back on, but only once a phone has been paired. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (PhoneServer.get(ctx).pairedPhones() > 0) PhoneService.start(ctx);
    }
}
