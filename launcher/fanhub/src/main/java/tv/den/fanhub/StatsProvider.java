package tv.den.fanhub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Screen time for the den-tv launcher's tile badges (CONTINUE, today's time): query apps gives one row per app
 * with today's milliseconds and when it was last left. Only an app signed with den-tv's own key may read it (the
 * launcher); anything else gets nothing.
 */
public final class StatsProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!"apps".equals(uri.getLastPathSegment()) || !ours()) return null;
        MatrixCursor c = new MatrixCursor(new String[]{"package", "ms_today", "last_left"});
        JSONObject st = Timers.screenTime(getContext()), last = Timers.lastLeft(getContext());
        Set<String> all = new HashSet<>();
        for (Iterator<String> it = st.keys(); it.hasNext(); ) all.add(it.next());
        for (Iterator<String> it = last.keys(); it.hasNext(); ) all.add(it.next());
        for (String pkg : all) c.addRow(new Object[]{pkg, st.optLong(pkg), last.optLong(pkg)});
        return c;
    }

    /** The caller is signed with the same key as this app (den-tv's launcher). */
    private boolean ours() {
        int uid = Binder.getCallingUid();
        return uid == Process.myUid() || getContext().getPackageManager().checkSignatures(uid, Process.myUid()) == PackageManager.SIGNATURE_MATCH;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
