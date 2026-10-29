package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Shows that Home's one long row goes on: the row fades out at an edge with more apps past it, a chevron pill there
 * says how many ("‹ 4" / "12 ›"), and a slim track under the row marks where the focused app is in the whole row.
 */
final class DenRowHints extends RecyclerView.ItemDecoration {
    private final int accent;
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float height = Float.MAX_VALUE;
    private final Path path = new Path();

    private DenRowHints(int accent, float dp) {
        this.accent = accent;
        this.dp = dp;
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Only on the sideways row; a grid that scrolls down has its own rows to show it goes on. */
    static void attach(View grid, int accent) {
        if (!(grid instanceof RecyclerView)) return;
        // Home sets its layout manager after DenLayer attaches, so whether the grid is a sideways row is asked when
        // drawing, not here (checking here found no row yet, and the hints never showed).
        RecyclerView rv = (RecyclerView) grid;
        rv.addItemDecoration(new DenRowHints(accent, rv.getResources().getDisplayMetrics().density));
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) { r.invalidate(); }
        });
        rv.getViewTreeObserver().addOnGlobalFocusChangeListener((a, b) -> rv.invalidate());
    }

    @Override public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.Adapter<?> a = parent.getAdapter();
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (a == null || parent.getChildCount() == 0 || !(lm instanceof LinearLayoutManager)
                || ((LinearLayoutManager) lm).getOrientation() != RecyclerView.HORIZONTAL) return;
        int total = a.getItemCount(), padL = parent.getPaddingLeft(), padR = parent.getWidth() - parent.getPaddingRight();
        int before = 0, after = 0, top = Integer.MAX_VALUE, bottom = 0, first = Integer.MAX_VALUE, last = -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(v);
            if (pos == RecyclerView.NO_POSITION) continue;
            boolean whole = v.getLeft() >= padL - 1 && v.getRight() <= padR + 1;
            if (whole) { first = Math.min(first, pos); last = Math.max(last, pos); }
            // Where the tile is drawn, after an icon layout has lifted or dropped it (DenLayouts).
            top = Math.min(top, Math.round(v.getTop() + v.getTranslationY()));
            bottom = Math.max(bottom, Math.round(v.getBottom() + v.getTranslationY()));
        }
        if (last < 0) return;
        before = first;
        after = total - 1 - last;
        View focused = parent.getFocusedChild();
        int at = focused == null ? first : parent.getChildAdapterPosition(focused);
        height = parent.getHeight();
        draw(c, parent.getWidth(), top, bottom, before, after, at, total);
    }

    /** The hints for a row between top and bottom with before / after apps off screen; at is the focused app. */
    void draw(Canvas c, int width, float top, float bottom, int before, int after, int at, int total) {
        float h = bottom - top, edge = 150 * dp;
        if (before > 0) {
            fill.setShader(new LinearGradient(0, 0, edge, 0, 0xd9000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, top - 12 * dp, edge, bottom + 12 * dp, fill);
            fill.setShader(null);
            pill(c, 34 * dp, top + h / 2, before, true);
        }
        if (after > 0) {
            fill.setShader(new LinearGradient(width - edge, 0, width, 0, 0x00000000, 0xd9000000, Shader.TileMode.CLAMP));
            c.drawRect(width - edge, top - 12 * dp, width, bottom + 12 * dp, fill);
            fill.setShader(null);
            pill(c, width - 34 * dp, top + h / 2, after, false);
        }
        if (total > 1 && (before > 0 || after > 0)) track(c, width / 2f, Math.min(bottom + 14 * dp, height - 8 * dp), Math.max(0, Math.min(total - 1, at)), total);
    }

    /** A dark pill on the edge: a chevron and the number of apps that way. */
    private void pill(Canvas c, float cx, float cy, int count, boolean left) {
        String n = String.valueOf(count);
        text.setTextSize(15 * dp);
        float w = 30 * dp + text.measureText(n) + 10 * dp, hh = 34 * dp;
        float x0 = left ? cx - 22 * dp : cx + 22 * dp - w;
        r.set(x0, cy - hh / 2, x0 + w, cy + hh / 2);
        fill.setColor(0xcc0b0c12);
        c.drawRoundRect(r, hh / 2, hh / 2, fill);
        line.setColor((accent & 0xffffff) | 0x99000000);
        line.setStrokeWidth(1.5f * dp);
        c.drawRoundRect(r, hh / 2, hh / 2, line);
        float chev = left ? r.left + 16 * dp : r.right - 16 * dp, s = 6 * dp;
        line.setColor(0xffffffff);
        line.setStrokeWidth(2.5f * dp);
        path.reset();
        if (left) { path.moveTo(chev + s / 2, cy - s); path.lineTo(chev - s / 2, cy); path.lineTo(chev + s / 2, cy + s); }
        else { path.moveTo(chev - s / 2, cy - s); path.lineTo(chev + s / 2, cy); path.lineTo(chev - s / 2, cy + s); }
        c.drawPath(path, line);
        text.setColor(0xffffffff);
        float tx = left ? r.right - 10 * dp - text.measureText(n) / 2 : r.left + 10 * dp + text.measureText(n) / 2;
        c.drawText(n, tx, cy - (text.ascent() + text.descent()) / 2, text);
    }

    /** The slim track under the row, with a thumb where the focused app sits among them all. */
    private void track(Canvas c, float cx, float y, int at, int total) {
        float w = Math.min(320 * dp, total * 14 * dp), x0 = cx - w / 2, th = 4 * dp;
        r.set(x0, y - th / 2, x0 + w, y + th / 2);
        fill.setColor(0x40ffffff);
        c.drawRoundRect(r, th, th, fill);
        float thumb = Math.max(18 * dp, w / total), tx = x0 + (w - thumb) * at / (float) (total - 1);
        r.set(tx, y - th / 2, tx + thumb, y + th / 2);
        fill.setColor(accent | 0xff000000);
        c.drawRoundRect(r, th, th, fill);
    }

    /** For the screen renders. */
    static DenRowHints forTest(int accent, float dp) { return new DenRowHints(accent, dp); }
}
