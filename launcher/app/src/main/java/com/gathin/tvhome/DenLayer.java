package com.gathin.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.List;

/**
 * den-tv's layer over Home: the look's watcher (v0.1: the eyes) following the focused tile, and the wake
 * animation when the TV switches on. MainActivity calls attach, onResume and onDestroy; nothing here takes
 * focus or keys, so the launcher's navigation is untouched.
 */
final class DenLayer {
    private final Activity activity;
    private final ViewGroup content;
    private final boolean motion;
    private Watcher eyes;

    /** What follows the focused tile: DenEyesView or DenWatcherView. x, y in screen pixels; label is the tile's name. */
    interface Watcher { void lookAt(float x, float y, String label); }
    private boolean wakePending = true;   // the first start counts as a wake
    private final int[] where = new int[2];

    private final ViewTreeObserver.OnGlobalFocusChangeListener focus = (old, now) -> {
        if (eyes == null || now == null) return;
        now.getLocationOnScreen(where);
        int x = where[0] + now.getWidth() / 2, y = where[1] + now.getHeight() / 2;
        View v = (View) eyes;
        v.getLocationOnScreen(where);
        eyes.lookAt(x - where[0], y - where[1], label(now));
    };

    private final List<View> covers = new java.util.ArrayList<>();   // the wake's flash and fade, removed when done

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            wakePending = true;
            // The broadcast can land after Home has already resumed; then play it now.
            if (activity.hasWindowFocus()) onResume();
        }
    };

    private DenLayer(Activity activity, boolean motion) {
        this.activity = activity;
        this.content = activity.findViewById(android.R.id.content);
        this.motion = motion;
    }

    /** Call after setContentView. motion: false with reduced motion or animations off (Launcher settings). */
    static DenLayer attach(Activity activity, boolean motion) {
        DenLayer layer = new DenLayer(activity, motion);
        List<DenLooks.Look> looks = DenLooks.load(activity);
        DenLooks.Look look = DenLooks.current(activity, looks);
        View watcher = look == null ? null
                : look.hasEyes() ? new DenEyesView(activity, look.watcher, motion)
                : DenWatcherView.draws(look.watcher) ? new DenWatcherView(activity, look.watcher, look.accent, motion) : null;
        if (watcher != null) {
            layer.eyes = (Watcher) watcher;
            layer.content.addView(watcher, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        layer.content.getViewTreeObserver().addOnGlobalFocusChangeListener(layer.focus);
        layer.content.post(layer::followFocus);   // Home may have focused a tile before the watcher was there
        IntentFilter on = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 33) activity.registerReceiver(layer.screenOn, on, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(layer.screenOn, on);
        return layer;
    }

    /** A tile's name for the watchers that show it: its description, else the first text inside it. */
    static String label(View v) {
        CharSequence d = v.getContentDescription();
        if (d != null && d.length() > 0) return d.toString();
        if (v instanceof android.widget.TextView) {
            CharSequence t = ((android.widget.TextView) v).getText();
            if (t != null && t.length() > 0) return t.toString();
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                String s = label(g.getChildAt(i));
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private void followFocus() {
        View f = content.findFocus();
        if (f != null) focus.onGlobalFocusChanged(null, f);
    }

    void onResume() {
        content.post(this::followFocus);
        if (!wakePending) return;
        wakePending = false;
        if (motion) wake();
    }

    void onDestroy() {
        content.getViewTreeObserver().removeOnGlobalFocusChangeListener(focus);
        try { activity.unregisterReceiver(screenOn); } catch (IllegalArgumentException ignored) { }
    }

    /**
     * "Light bloom": Home comes up from black through a soft white flash and settles from a slight zoom. Home itself
     * stays opaque; only covers on top fade, so an interrupted animation can never leave Home invisible, and the
     * next wake clears any cover left behind.
     */
    private void wake() {
        View home = content.getChildAt(0);
        if (home == null) return;
        for (View v : covers) { v.animate().cancel(); content.removeView(v); }
        covers.clear();
        View dark = cover(Color.BLACK, 1f), flash = cover(Color.WHITE, 0.5f);
        dark.animate().alpha(0f).setDuration(700).setInterpolator(new DecelerateInterpolator(1.4f)).withEndAction(() -> uncover(dark)).start();
        flash.animate().alpha(0f).setStartDelay(150).setDuration(1000).withEndAction(() -> uncover(flash)).start();
        home.animate().cancel();
        home.setScaleX(1.05f);
        home.setScaleY(1.05f);
        home.animate().scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private View cover(int colour, float alpha) {
        View v = new View(activity);
        v.setBackgroundColor(colour);
        v.setAlpha(alpha);
        v.setFocusable(false);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        covers.add(v);
        return v;
    }

    private void uncover(View v) {
        content.removeView(v);
        covers.remove(v);
    }
}
