package tv.den.fanhub;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * The den-tv helper, an accessibility service the owner switches on once (Settings, Accessibility on the TV). It
 * lets the phone app do what an ordinary app can't: Back and Home, sleep, typing into the focused text box, and
 * opening apps while another app is in front. It also notes which app is in front, for screen time and the kids
 * timer. It reads nothing else on screen, and never keeps or sends anything it sees.
 */
public final class Helper extends AccessibilityService {
    private static volatile Helper running;
    private static volatile String front = "";

    static boolean on() { return running != null; }

    /** The package in front, as last seen; "" before the helper has seen one. */
    static String front() { return front; }

    @Override protected void onServiceConnected() {
        running = this;
        PhoneService.start(this);
    }

    @Override public void onDestroy() {
        running = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        // System overlays (the volume bar, the keyboard) come and go over an app; they don't count as switching.
        if (pkg.equals("android") || pkg.equals("com.android.systemui") || pkg.contains("inputmethod") || pkg.contains(".ime")) return;
        if (!pkg.equals(front)) {
            front = pkg;
            Timers.foreground(this, pkg);
        }
    }

    @Override public void onInterrupt() { }

    static boolean back() { Helper h = running; return h != null && h.performGlobalAction(GLOBAL_ACTION_BACK); }

    static boolean home() { Helper h = running; return h != null && h.performGlobalAction(GLOBAL_ACTION_HOME); }

    /** Sleep, where the TV treats locking the screen as standby (Android 9 and later). */
    static boolean sleep() { Helper h = running; return h != null && h.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); }

    /** Starts an activity from the helper, which Android allows while another app is in front. */
    static boolean start(Intent i) {
        Helper h = running;
        if (h == null) return false;
        try {
            h.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Puts text in the focused text box (a search field, say); false when there is none. */
    static boolean type(String text) {
        Helper h = running;
        if (h == null) return false;
        AccessibilityNodeInfo root = h.getRootInActiveWindow();
        AccessibilityNodeInfo box = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (box == null || !box.isEditable()) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
}
