package tv.den.fanhub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * The den-tv helper, an accessibility service the owner switches on once (Settings, Accessibility on the TV). It
 * lets the phone app do what an ordinary app can't: Back and Home, sleep, typing into the focused text box, and
 * opening apps while another app is in front, and the phone's arrows and OK (it finds the selected item and moves
 * the selection, on Android 12 and earlier, where an app can't press keys). It also notes which app is in front, for
 * screen time and the kids timer, and, once you have set some up, it answers your remote's own buttons (RemoteKeys).
 * It reads nothing else on screen, and never keeps or sends anything it sees.
 */
public final class Helper extends AccessibilityService {
    private static volatile Helper running;
    private static volatile String front = "";
    private static volatile long since;

    static boolean on() { return running != null; }

    /** The package in front, as last seen; "" before the helper has seen one. */
    static String front() { return front; }

    /** When the app in front came to the front (System.currentTimeMillis); 0 before the helper has seen one. */
    static long since() { return since; }

    @Override protected void onServiceConnected() {
        running = this;
        PhoneService.start(this);
        listen();
    }

    /**
     * Asks for the remote's key presses only while there's a button to answer or one being learned: a key-filtering
     * service can cost a TV its key repeat (holding volume), so a TV with no buttons set up never pays for it.
     */
    static void listen() {
        Helper h = running;
        if (h == null) return;
        AccessibilityServiceInfo info = h.getServiceInfo();
        if (info == null) return;
        boolean want = RemoteKeys.wanted(h);
        boolean has = (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
        if (want == has) return;
        info.flags = want ? info.flags | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS : info.flags & ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        h.setServiceInfo(info);
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        return RemoteKeys.onKey(this, e);
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
            since = System.currentTimeMillis();
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

    /**
     * The phone's arrows and OK: up, down, left, right or ok. On Android 13 and later the helper presses the key
     * itself. Earlier, it moves the selection to the next item that way (scrolling a list at its edge) and clicks
     * the selected item for OK; apps that draw their own screens without telling Android what's on them (some
     * players and games) don't answer this.
     */
    static boolean dpad(String dir) {
        Helper h = running;
        if (h == null) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            int a;
            switch (dir) {
                case "up": a = GLOBAL_ACTION_DPAD_UP; break;
                case "down": a = GLOBAL_ACTION_DPAD_DOWN; break;
                case "left": a = GLOBAL_ACTION_DPAD_LEFT; break;
                case "right": a = GLOBAL_ACTION_DPAD_RIGHT; break;
                default: a = GLOBAL_ACTION_DPAD_CENTER;
            }
            return h.performGlobalAction(a);
        }
        AccessibilityNodeInfo root = h.getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (dir.equals("ok")) {
            for (AccessibilityNodeInfo n = focus; n != null; n = n.getParent())
                if (n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return false;
        }
        if (focus == null) {
            // Nothing selected yet: select the first thing that can be.
            AccessibilityNodeInfo first = firstFocusable(root, 0);
            return first != null && first.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        }
        int d = dir.equals("up") ? View.FOCUS_UP : dir.equals("down") ? View.FOCUS_DOWN : dir.equals("left") ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
        AccessibilityNodeInfo next = focus.focusSearch(d);
        if (next != null && !next.equals(focus) && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return true;
        // At the edge of a row or list: scroll it, and the app moves its own selection along.
        int scroll = d == View.FOCUS_DOWN || d == View.FOCUS_RIGHT ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        for (AccessibilityNodeInfo n = focus.getParent(); n != null; n = n.getParent())
            if (n.isScrollable() && n.performAction(scroll)) return true;
        return false;
    }

    private static AccessibilityNodeInfo firstFocusable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30) return null;
        if (n.isFocusable() && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo f = firstFocusable(n.getChild(i), depth + 1);
            if (f != null) return f;
        }
        return null;
    }

    /** Runs on the helper's main thread (the phone's requests arrive on the web server's). */
    static void post(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }

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
