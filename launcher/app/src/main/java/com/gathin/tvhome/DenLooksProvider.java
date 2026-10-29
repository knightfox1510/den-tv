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
        if (!"current".equals(uri.getLastPathSegment()) || values == null) return 0;
        String id = values.getAsString("look");
        if (id == null) return 0;
        if (DenLooks.ROTATE.equals(id)) { DenLooks.apply(getContext(), null); return 1; }
        DenLooks.Look look = DenLooks.byId(DenLooks.load(getContext()), id);
        if (look == null) return 0;
        DenLooks.apply(getContext(), look);
        return 1;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
