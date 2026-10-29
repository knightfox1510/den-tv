package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * The Shows folder: one tile on Home that opens the streaming apps you put in it from the phone, so the row stays
 * short. The apps in it are hidden from the row (Remove from Home) and come back when taken out. Its tile shows only
 * while it holds an app.
 */
public final class DenFolderActivity extends Activity {
    static final String KEY = "folder";

    static List<String> members(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(DenSettings.prefs(ctx).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    /** Puts an app in the folder or takes it out; the folder's tile appears with its first app and goes with its last. */
    static void set(Context ctx, String pkg, boolean in) {
        List<String> m = members(ctx);
        m.remove(pkg);
        if (in) m.add(pkg);
        DenSettings.prefs(ctx).edit().putString(KEY, new JSONArray(m).toString()).apply();
        LibraryApps.save(ctx, pkg, !in);
        ctx.getPackageManager().setComponentEnabledSetting(new ComponentName(ctx, DenFolderActivity.class),
                m.isEmpty() ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        PackageManager pm = getPackageManager();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xf0050507);
        page.setPadding(Math.round(48 * dp), Math.round(28 * dp), Math.round(48 * dp), Math.round(20 * dp));
        TextView title = new TextView(this);
        title.setText("Shows");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        page.addView(title);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(0, Math.round(16 * dp), 0, 0);
        ImageView first = null;
        for (String pkg : members(this)) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            Drawable banner;
            try { banner = pm.getApplicationBanner(pkg); if (banner == null) banner = pm.getApplicationIcon(pkg); }
            catch (PackageManager.NameNotFoundException e) { continue; }
            ImageView tile = new ImageView(this);
            tile.setImageDrawable(banner);
            tile.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xff16161d);
            bg.setCornerRadius(12 * dp);
            tile.setBackground(bg);
            tile.setClipToOutline(true);
            tile.setFocusable(true);
            tile.setClickable(true);
            try { tile.setContentDescription(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (PackageManager.NameNotFoundException ignored) { }
            Intent go = launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            tile.setOnClickListener(v -> { startActivity(go); finish(); });
            tile.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(150).start());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Math.round(200 * dp);
            lp.height = Math.round(112 * dp);
            lp.setMargins(Math.round(10 * dp), Math.round(10 * dp), Math.round(10 * dp), Math.round(10 * dp));
            grid.addView(tile, lp);
            if (first == null) first = tile;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        if (first != null) first.requestFocus();
    }
}
