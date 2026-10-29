package com.gathin.tvhome;

import android.service.dreams.DreamService;

/**
 * den-tv's screensaver (DenSaverView): the look on Home with its clock, what's next from the Fan Hub and a fact.
 * Google TV's settings only offer its own screensavers, so this one is chosen over ADB, once:
 * {@code settings put secure screensaver_components tv.den.launcher/com.gathin.tvhome.DenDream}.
 * Without the den-mix theme it falls back to the launcher's plain clock (ClockDream's).
 */
public class DenDream extends DreamService {
    private ClockScene scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        DenSaverView saver = DenSaverView.make(this, !DenSettings.reducedMotion(this, false));
        if (saver != null) { setContentView(saver); return; }
        setContentView(R.layout.dream_clock);
        scene = new ClockScene(getWindow().getDecorView());
        scene.useHomeWallpaper(this);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene != null) scene.start();
    }

    @Override
    public void onDreamingStopped() {
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }
}
