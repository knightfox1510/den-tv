package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Icon layouts (looks/README.md, "icon_layout") on Home's one long row. The row stays the launcher's own, so the
 * D-pad moves left and right as always; each tile is only lifted, tilted or scaled where the layout puts it, and
 * the layout's ground is drawn behind: a pitch for the formation, the boundary and pitch for the cricket field, the
 * dotted route of the Grand Line arc, the plank of the manga shelf, the big middle tile of the carousel.
 */
final class DenLayouts {
    static final List<String> LAYOUTS = List.of("row", "formation", "field", "arc", "shelf", "carousel");

    private DenLayouts() {}

    /** Puts the layout on the row; "row", empty or unknown leave it as it is. */
    private static final java.util.Map<RecyclerView, String> ON = new java.util.WeakHashMap<>();

    static void attach(RecyclerView grid, String layout, int accent) {
        if (grid == null || !LAYOUTS.contains(layout) || "row".equals(layout)) return;
        ON.put(grid, layout);
        Shaper s = new Shaper(grid, layout, accent);
        grid.addItemDecoration(s, 0);   // drawn first, under the launcher's own band and the tiles
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { s.shape(); }
        });
        grid.getViewTreeObserver().addOnGlobalLayoutListener(s::shape);
        grid.getViewTreeObserver().addOnGlobalFocusChangeListener((a, b) -> s.shape());
    }

    /** Whether a layout other than the plain row shapes this grid. */
    static boolean active(View grid) { return grid instanceof RecyclerView && ON.containsKey(grid); }

    /**
     * Where the layout puts a tile when it is focused: {lift (px, + is down), scale, rotation}. MainActivity sizes and
     * places the focus glass with it, so the glass sits on the tile wherever the layout has moved it. {0, 1, 0} for
     * the plain row.
     */
    static float[] focused(View grid, View item) {
        String layout = grid instanceof RecyclerView ? ON.get(grid) : null;
        if (layout == null || item == null) return new float[]{0, 1, 0};
        RecyclerView rv = (RecyclerView) grid;
        float[] t = transform(layout, rv.getChildAdapterPosition(item), u(rv, item), item.getHeight(), true);
        return new float[]{t[0], t[1], t[3]};
    }

    /** -1 at the left edge of the row's view, 0 in the middle, 1 at the right edge. */
    private static float u(RecyclerView grid, View v) {
        float w = grid.getWidth();
        return w == 0 ? 0 : Math.max(-1.2f, Math.min(1.2f, ((v.getLeft() + v.getRight()) / 2f - w / 2f) / (w / 2f)));
    }

    /** One tile's shape in the layout: {translationY, scaleX, scaleY, rotation, alpha}. */
    private static float[] transform(String layout, int pos, float u, float h, boolean on) {
        float ty = 0, sx = 1, sy = 1, rot = 0, alpha = 1;
        switch (layout) {
            case "formation": ty = (pos % 2 == 0 ? -0.22f : 0.16f) * h; break;   // two lines, like players on a pitch
            case "field": ty = 0.26f * h * (1 - u * u) - 0.08f * h; break;          // around the boundary
            case "arc": ty = -0.38f * h * (1 - u * u) + 0.14f * h; rot = u * 6; break;   // islands along the route
            case "shelf":
                if (!on) { sx = 0.62f; rot = (pos % 3 - 1) * 2.5f; } else ty = -0.18f * h;   // spines; the picked one slides out
                break;
            case "carousel": {
                float d = Math.min(1, Math.abs(u));
                sx = sy = on ? 1.3f : 1 - 0.28f * d;
                alpha = on ? 1f : 1 - 0.45f * d;
                break;
            }
            default: break;
        }
        return new float[]{ty, sx, sy, rot, alpha};
    }

    private static final class Shaper extends RecyclerView.ItemDecoration {
        private final RecyclerView grid;
        private final String layout;
        private final int accent;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();

        Shaper(RecyclerView grid, String layout, int accent) {
            this.grid = grid; this.layout = layout; this.accent = accent;
            line.setStyle(Paint.Style.STROKE);
        }

        void shape() {
            View focused = grid.getFocusedChild();
            for (int i = 0; i < grid.getChildCount(); i++) {
                View v = grid.getChildAt(i);
                boolean on = v == focused;
                float[] t = transform(layout, grid.getChildAdapterPosition(v), u(grid, v), v.getHeight(), on);
                float ty = t[0], sx = t[1], sy = t[2], rot = t[3];
                if (v.getAlpha() != t[4] && layout.equals("carousel")) v.setAlpha(t[4]);
                if (v.getTranslationY() != ty) v.setTranslationY(ty);
                if (v.getScaleX() != sx) v.setScaleX(sx);
                if (v.getScaleY() != sy) v.setScaleY(sy);
                if (v.getRotation() != rot) v.setRotation(rot);
                v.setTranslationZ(on ? 4 : 0);
            }
            grid.invalidate();   // the ground follows the tiles
        }

        @Override public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            if (parent.getChildCount() == 0) return;
            View first = parent.getChildAt(0);
            float h = first.getHeight(), mid = first.getTop() + h / 2f, w = parent.getWidth();
            float dp = parent.getResources().getDisplayMetrics().density;
            fill.setPathEffect(null);
            line.setPathEffect(null);
            switch (layout) {
                case "formation": {
                    fill.setColor(0x2a2e8b3a);
                    r.set(w * 0.03f, mid - h * 0.62f, w * 0.97f, mid + h * 0.55f);
                    c.drawRoundRect(r, 12 * dp, 12 * dp, fill);
                    line.setColor(0x66ffffff);
                    line.setStrokeWidth(2 * dp);
                    c.drawRoundRect(r, 12 * dp, 12 * dp, line);
                    c.drawLine(w / 2, r.top, w / 2, r.bottom, line);
                    c.drawCircle(w / 2, mid - h * 0.04f, h * 0.3f, line);
                    c.drawRect(r.left, mid - h * 0.35f, r.left + w * 0.06f, mid + h * 0.28f, line);
                    c.drawRect(r.right - w * 0.06f, mid - h * 0.35f, r.right, mid + h * 0.28f, line);
                    break;
                }
                case "field": {
                    fill.setColor(0x222e8b3a);
                    r.set(w * 0.02f, mid - h * 0.75f, w * 0.98f, mid + h * 0.7f);
                    c.drawOval(r, fill);
                    line.setColor(0x88ffffff);
                    line.setStrokeWidth(2.5f * dp);
                    line.setPathEffect(new DashPathEffect(new float[]{10 * dp, 7 * dp}, 0));
                    c.drawOval(r, line);
                    fill.setColor(0x66d9c48a);   // the pitch, in the middle
                    r.set(w / 2 - h * 0.1f, mid - h * 0.62f, w / 2 + h * 0.1f, mid - h * 0.2f);
                    c.drawRect(r, fill);
                    break;
                }
                case "arc": {
                    path.reset();
                    boolean started = false;
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        View v = parent.getChildAt(i);
                        float x = (v.getLeft() + v.getRight()) / 2f, y = (v.getTop() + v.getBottom()) / 2f + v.getTranslationY();
                        if (!started) { path.moveTo(x, y); started = true; } else path.lineTo(x, y);
                    }
                    line.setColor((accent & 0xffffff) | 0xaa000000);
                    line.setStrokeWidth(3 * dp);
                    line.setPathEffect(new DashPathEffect(new float[]{4 * dp, 10 * dp}, 0));
                    line.setStrokeCap(Paint.Cap.ROUND);
                    c.drawPath(path, line);
                    break;
                }
                case "shelf": {
                    fill.setColor(0xcc5a3a1e);
                    r.set(w * 0.02f, mid + h * 0.34f, w * 0.98f, mid + h * 0.42f);
                    c.drawRoundRect(r, 4 * dp, 4 * dp, fill);
                    fill.setColor(0x55000000);
                    r.set(w * 0.02f, mid + h * 0.42f, w * 0.98f, mid + h * 0.47f);
                    c.drawRect(r, fill);
                    break;
                }
                default: break;
            }
        }
    }
}
