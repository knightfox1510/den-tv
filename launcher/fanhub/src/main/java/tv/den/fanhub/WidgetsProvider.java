package tv.den.fanhub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

/**
 * Your widgets' lines for den-tv's Home (Widgets): content://tv.den.fanhub.widgets/home, one row per line
 * (label, text), worked out now. Only an app signed with den-tv's own key may read it (the launcher).
 */
public final class WidgetsProvider extends ContentProvider {
    static final Uri HOME = Uri.parse("content://tv.den.fanhub.widgets/home");

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!"home".equals(uri.getLastPathSegment()) || !ours()) return null;
        MatrixCursor c = new MatrixCursor(new String[]{"label", "text"});
        for (String[] line : Widgets.lines(getContext(), "home", System.currentTimeMillis())) c.addRow(line);
        c.setNotificationUri(getContext().getContentResolver(), HOME);
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
