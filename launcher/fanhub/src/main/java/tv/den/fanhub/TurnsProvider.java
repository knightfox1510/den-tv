package tv.den.fanhub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * The launcher's game_day contract (GameDay.java in the launcher): content://tv.den.fanhub.games/turns, one row
 * per match or episode. Read-only; a query also starts a refresh when the data is due one.
 */
public final class TurnsProvider extends ContentProvider {
    static final String[] COLUMNS = {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"};
    static final int TURN_SECONDS = 15;

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!"turns".equals(uri.getLastPathSegment())) return null;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (FanHub.Turn t : Store.turns(getContext())) {
            c.addRow(new Object[]{t.topLine, t.word, t.start, t.end, t.colour, null, TURN_SECONDS});
        }
        c.setNotificationUri(getContext().getContentResolver(), uri);
        Store.refresh(getContext(), false);
        return c;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.den.turn"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
