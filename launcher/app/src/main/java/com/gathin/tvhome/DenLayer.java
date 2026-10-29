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
    private Watcher figure;   // your character, when one is on Home
    private final List<Watcher> others = new java.util.ArrayList<>();   // focus effects
    private DenLooks.Look look;
    private final DenMoments.Player moments = this::play;

    /** What follows the focused tile: DenEyesView or DenWatcherView. x, y in screen pixels; label is the tile's name. */
    interface Watcher { void lookAt(float x, float y, String label); }
    private boolean wakePending = true;   // the first start counts as a wake
    private final int[] where = new int[2];

    private final ViewTreeObserver.OnGlobalFocusChangeListener focus = (old, now) -> {
        if ((eyes == null && figure == null && others.isEmpty()) || now == null) return;
        // The row is scrolling the new tile into place: where it is now isn't where it will stop. Wait for the
        // row to settle (the scroll listener below calls back), or a ball flies to a spot past the screen's edge.
        View grid = DenLayer.this.activity.findViewById(R.id.grid);
        if (grid instanceof androidx.recyclerview.widget.RecyclerView && ((androidx.recyclerview.widget.RecyclerView) grid).getScrollState() != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return;
        now.getLocationOnScreen(where);
        int x = where[0] + now.getWidth() / 2, y = where[1] + now.getHeight() / 2;
        View v = (View) (eyes != null ? eyes : figure != null ? figure : others.get(0));
        v.getLocationOnScreen(where);
        if (eyes != null) eyes.lookAt(x - where[0], y - where[1], label(now));
        if (figure != null) figure.lookAt(x - where[0], y - where[1], label(now));
        for (Watcher w : others) w.lookAt(x - where[0], y - where[1], label(now));
    };

    private final List<View> covers = new java.util.ArrayList<>();   // the wake's flash and fade, removed when done

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                DenPeople.endGuest(ctx);   // a guest's turn ends when the TV sleeps
                return;
            }
            wakePending = true;
            // The broadcast can land after Home has already resumed; then play it now.
            if (activity.hasWindowFocus()) onResume();
        }
    };

    private final Runnable rebuild = () -> {
        Activity a = DenLayer.this.activity;
        if (a.isFinishing() || !a.hasWindowFocus()) return;   // otherwise MainActivity rebuilds on resume
        a.recreate();
    };

    /** A change from the phone (DenLooksProvider bumps the settings revision): rebuild Home now if it is in front. */
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener revision = (prefs, key) -> {
        if (!Config.SETTINGS_REVISION_KEY.equals(key)) return;
        DenLayer.this.content.removeCallbacks(rebuild);
        DenLayer.this.content.postDelayed(rebuild, 400);   // several changes in a row rebuild once
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
        DenLooks.Look look = DenLooks.tweaked(activity, DenLooks.current(activity, looks));
        View watcher = look == null || !DenSettings.watcher(activity) ? null
                : look.hasEyes() ? new DenEyesView(activity, look.watcher, motion)
                : DenWatcherView.draws(look.watcher) ? new DenWatcherView(activity, look.watcher, look.accent, motion).number(DenWatcherView.numberFor(look.hero))
                : DenBallWatcher.KINDS.contains(look.watcher) ? new DenBallWatcher(activity, look.watcher, look.accent, motion) : null;
        // Your character: a walker takes the watcher's place; a figure stands beside it.
        String charId = DenCharacters.inUse(activity);
        boolean walker = "walker".equals(DenCharacters.mode(activity));
        if (!charId.isEmpty() && DenSettings.watcher(activity)) {
            android.graphics.Bitmap body = DenCharacters.picture(activity, charId, false, walker ? 400 : 800);
            if (body != null) {
                DenFigureView f = new DenFigureView(activity, body, DenCharacters.picture(activity, charId, true, walker ? 400 : 800), walker, motion);
                layer.figure = f;
                layer.content.addView(f, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                if (walker) watcher = null;
            }
        }
        // The look's own figure (Luffy, Gear 5, Zoro), drawn from the mockup, when you haven't put up your own.
        if (layer.figure == null && look != null && !look.figure.isEmpty() && DenSettings.watcher(activity)) {
            android.graphics.Bitmap body = DenFigureView.asset(activity, "figure-" + look.figure);
            if (body != null) {
                DenFigureView f = new DenFigureView(activity, body, DenFigureView.asset(activity, "figure-" + look.figure + "-closed"), false, motion);
                layer.figure = f;
                layer.content.addView(f, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
        if (look != null && DenSettings.hud(activity, look)) {
            layer.content.addView(new DenHudView(activity, look.accent), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (watcher != null) {
            layer.eyes = (Watcher) watcher;
            layer.content.addView(watcher, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (look != null) {
            int accent;
            try { accent = android.graphics.Color.parseColor(look.accent); } catch (RuntimeException e) { accent = 0xffff4d6d; }
            DenExtras extras = DenExtras.make(activity, look, motion);
            if (extras != null) layer.content.addView(extras, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (DenFocusFx.draws(look.focus)) {
                DenFocusFx fx = new DenFocusFx(activity, look.focus, accent, motion);
                layer.others.add(fx);
                layer.content.addView(fx, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
        if (DenSettings.facts(activity) && look != null) {
            DenFactsView facts = DenFactsView.make(activity, look, motion);
            if (facts != null) layer.content.addView(facts, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        layer.look = look;
        DenMoments.attach(layer.moments);
        DenIcons.use(look == null ? "" : look.icons, look == null ? "" : look.accent, look == null ? "" : look.id);
        if (look != null) clock(activity, look);
        int accent = 0xffff4d6d;
        if (look != null) {
            try { accent = android.graphics.Color.parseColor(look.accent); } catch (RuntimeException ignored) { }
            DenLayouts.attach(activity.findViewById(R.id.grid), look.iconLayout, accent);
        }
        DenRowHints.attach(activity.findViewById(R.id.grid), accent);
        View rowView = activity.findViewById(R.id.grid);
        if (rowView instanceof androidx.recyclerview.widget.RecyclerView) {
            ((androidx.recyclerview.widget.RecyclerView) rowView).addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override public void onScrollStateChanged(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView rv, int state) {
                    // The row has stopped: the watchers go to the focused tile where it ended up (after the layout's shape too).
                    if (state == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) rv.post(layer::followFocus);
                }
            });
        }
        layer.content.getViewTreeObserver().addOnGlobalFocusChangeListener(layer.focus);
        layer.content.post(layer::followFocus);
        activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(layer.revision);   // Home may have focused a tile before the watcher was there
        IntentFilter on = new IntentFilter(Intent.ACTION_SCREEN_ON);
        on.addAction(Intent.ACTION_SCREEN_OFF);
        if (android.os.Build.VERSION.SDK_INT >= 33) activity.registerReceiver(layer.screenOn, on, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(layer.screenOn, on);
        return layer;
    }

    /** Puts the look's clock style in place of Home's clock (AM/PM, the date and the rest of the row stay). */
    static void clock(Activity activity, DenLooks.Look look) {
        if (!DenClockView.STYLES.contains(look.clock)) return;
        View clock = activity.findViewById(R.id.clock);
        if (clock == null || !(clock.getParent() instanceof android.widget.LinearLayout)) return;
        android.widget.LinearLayout row = (android.widget.LinearLayout) clock.getParent();
        DenClockView styled = new DenClockView(activity, look.clock, look.accent);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.BOTTOM;
        row.addView(styled, row.indexOfChild(clock), lp);
        clock.setVisibility(View.GONE);
    }

    /**
     * Hero Wall shows the next hero each time Home comes back, at most every five minutes, so the picture never
     * changes under you while you browse. The change rebuilds Home, like any wallpaper change.
     */
    private void heroWall() {
        if (look == null || !"rotate".equals(look.hero)) return;
        android.content.SharedPreferences den = DenSettings.prefs(activity);
        long now = System.currentTimeMillis();
        if (now - den.getLong("hero_at", 0) < 5 * 60_000L) return;
        String next = DenLooks.nextHero(activity);
        if (next == null) return;
        den.edit().putLong("hero_at", now).apply();
        android.content.SharedPreferences choices = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (next.equals(choices.getString(Config.WALLPAPER_CHOICE_KEY, ""))) return;
        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, next)
                .putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    /**
     * Plays a moment over Home; false when Home isn't in front (it waits for later). A goal plays the look's own
     * moment when it has a special one (the beam, arise, the punch, Gear 5).
     */
    private boolean play(String type, String title, String line, int colour) {
        if (activity.isFinishing() || !activity.hasWindowFocus()) return false;
        String t = type;
        if ("goal".equals(type) && look != null && java.util.Arrays.asList("beam", "arise", "punch", "gear5").contains(look.moment)) {
            t = look.moment;
            title = java.util.Map.of("beam", "Kamehameha!", "arise", "Arise", "punch", "One punch", "gear5", "Gear 5").get(t);
        }
        DenMomentView v = new DenMomentView(activity, t, title, line, colour, motion);
        content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
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
        heroWall();
        DenBadges.refresh(activity, () -> {
            View grid = activity.findViewById(R.id.grid);
            if (grid instanceof androidx.recyclerview.widget.RecyclerView) {
                androidx.recyclerview.widget.RecyclerView.Adapter<?> a = ((androidx.recyclerview.widget.RecyclerView) grid).getAdapter();
                if (a != null) a.notifyItemRangeChanged(0, a.getItemCount());
            }
        });
        content.post(this::followFocus);
        DenMoments.attach(moments);
        content.post(DenMoments::replay);
        DenSettings.offerSetupOnce(activity);
        if (!wakePending) return;
        wakePending = false;
        if (motion && DenSettings.wake(activity)) wake();
    }

    void onDestroy() {
        DenMoments.detach(moments);
        content.removeCallbacks(rebuild);
        activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(revision);
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
        if (look != null && !"bloom".equals(look.wake)) {
            int accent;
            try { accent = android.graphics.Color.parseColor(look.accent); } catch (RuntimeException e) { accent = 0xffff4d6d; }
            if (DenWake.play(content, home, look.wake, accent)) return;
        }
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
