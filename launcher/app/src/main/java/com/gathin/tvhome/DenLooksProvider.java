package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.List;

/**
 * den-tv: the looks for the phone settings page, which the Fan Hub add-on serves. Only apps signed with den-tv's
 * own key may use it (the tv.den.permission.LOOKS signature permission in the manifest). query looks: every look,
 * the one on Home marked; update current with look = an id or "rotate": puts it on, as the Looks screen does.
 */
public final class DenLooksProvider extends ContentProvider {
    static final String[] COLUMNS = {"id", "name", "category", "on"};

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if ("settings".equals(uri.getLastPathSegment())) return settings();
        if (!"looks".equals(uri.getLastPathSegment())) return null;
        DenTheme.ensure(getContext());
        List<DenLooks.Look> looks = DenLooks.load(getContext());
        DenLooks.Look on = DenLooks.current(getContext(), looks);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        c.addRow(new Object[]{DenLooks.ROTATE, "Rotate every look", "", on == null ? 1 : 0});
        for (DenLooks.Look l : looks) c.addRow(new Object[]{l.id, l.name, l.category, l == on ? 1 : 0});
        return c;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        if ("settings".equals(uri.getLastPathSegment()) && values != null) return saveSettings(values);
        if (!"current".equals(uri.getLastPathSegment()) || values == null) return 0;
        String id = values.getAsString("look");
        if (id == null) return 0;
        if (DenLooks.ROTATE.equals(id)) { DenLooks.apply(getContext(), null); return 1; }
        DenLooks.Look look = DenLooks.byId(DenLooks.load(getContext()), id);
        if (look == null) return 0;
        DenLooks.apply(getContext(), look);
        return 1;
    }

    /** settings: one row per choice (key, value), the values as text; booleans are "1" or "0". */
    private Cursor settings() {
        android.content.SharedPreferences p = DenSettings.prefs(getContext());
        MatrixCursor c = new MatrixCursor(new String[]{"key", "value"});
        c.addRow(new Object[]{DenSettings.NAME, p.getString(DenSettings.NAME, "")});
        c.addRow(new Object[]{DenSettings.GREETING, p.getBoolean(DenSettings.GREETING, true) ? "1" : "0"});
        c.addRow(new Object[]{DenSettings.PANEL, p.getBoolean(DenSettings.PANEL, true) ? "1" : "0"});
        c.addRow(new Object[]{DenSettings.WATCHER, p.getBoolean(DenSettings.WATCHER, true) ? "1" : "0"});
        c.addRow(new Object[]{DenSettings.SETUP_DONE, p.getBoolean(DenSettings.SETUP_DONE, false) ? "1" : "0"});
        return c;
    }

    /** Saves the choices it knows and asks Home to rebuild (Home picks the change up when it is back in front). */
    private int saveSettings(ContentValues v) {
        android.content.SharedPreferences.Editor e = DenSettings.prefs(getContext()).edit();
        int n = 0;
        if (v.containsKey(DenSettings.NAME)) {
            String name = v.getAsString(DenSettings.NAME);
            name = name == null ? "" : name.trim();
            e.putString(DenSettings.NAME, name.length() > 24 ? name.substring(0, 24) : name);
            n++;
        }
        for (String k : new String[]{DenSettings.GREETING, DenSettings.PANEL, DenSettings.WATCHER, DenSettings.SETUP_DONE}) {
            if (!v.containsKey(k)) continue;
            e.putBoolean(k, "1".equals(v.getAsString(k)) || Boolean.TRUE.equals(v.getAsBoolean(k)));
            n++;
        }
        e.apply();
        android.content.SharedPreferences choices = getContext().getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        return n;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
