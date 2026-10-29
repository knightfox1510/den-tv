package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * den-tv's look picker, opened from the Looks tile: every look with its wallpaper, four to a row. OK puts the
 * look on Home (DenLooks.apply) and closes; Back keeps what is on. The first tile goes back to rotating looks.
 */
public final class DenLooksActivity extends Activity {
    private static final int COLUMNS = 4;
    private List<DenLooks.Look> looks;
    private DenLooks.Look current;
    private File walls;
    private float dp;
    private Typeface font;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        try { font = getResources().getFont(R.font.outfit); } catch (Exception e) { font = Typeface.SANS_SERIF; }
        DenTheme.ensure(this);   // opened before Home ever ran: set the theme up here too
        looks = DenLooks.load(this);
        current = DenLooks.current(this, looks);
        File dir = getExternalFilesDir(null);
        walls = dir == null ? null : new File(dir, "wallpapers");

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xff050507);
        page.setPadding(px(48), px(28), px(48), 0);
        page.addView(text("Looks", 30, Color.WHITE, Typeface.BOLD));
        page.addView(text(looks.isEmpty() ? "No looks yet: the den-mix theme isn't on this TV. Restart Home once and try again."
                : "OK puts a look on Home. Back keeps " + (current == null ? "the looks rotating." : current.name + "."), 14, 0xffa09dac, Typeface.NORMAL));

        RecyclerView grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setClipToPadding(false);
        grid.setPadding(0, px(18), 0, px(40));
        grid.setAdapter(new Tiles());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(grid, lp);
        setContentView(page);

        int start = current == null ? 0 : looks.indexOf(current) + 1;
        grid.scrollToPosition(start);
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(start);
            if (h != null) h.itemView.requestFocus();
        });
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private int px(float v) { return Math.round(v * dp); }

    private TextView text(String s, float sp, int colour, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        // Outfit is a variable font: ask for the weight on Android 9+, a synthetic bold before that.
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setTypeface(Typeface.create(font, style == Typeface.BOLD ? 650 : 350, false));
        else t.setTypeface(font, style);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private void choose(DenLooks.Look look) {
        DenLooks.apply(this, look);
        Toast.makeText(this, look == null ? "Looks rotate every 5 minutes" : look.name + " is on", Toast.LENGTH_SHORT).show();
        finish();
    }

    /** A 16:9 picture box: its height follows the column width. */
    private static final class Picture extends ImageView {
        Picture(Context ctx) { super(ctx); }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            setMeasuredDimension(width, width * 9 / 16);
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final Picture picture;
        final TextView name, meta;
        final GradientDrawable frame;
        String want;

        Holder(FrameLayout card, Picture picture, TextView name, TextView meta, GradientDrawable frame) {
            super(card);
            this.picture = picture; this.name = name; this.meta = meta; this.frame = frame;
        }
    }

    private final class Tiles extends RecyclerView.Adapter<Holder> {
        @Override public int getItemCount() { return looks.size() + 1; }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout card = new FrameLayout(DenLooksActivity.this);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(px(9), px(9), px(9), px(9));
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            GradientDrawable frame = new GradientDrawable();
            frame.setCornerRadius(px(12));
            frame.setColor(0xff16161d);
            card.setBackground(frame);
            card.setClipToOutline(true);
            LinearLayout column = new LinearLayout(DenLooksActivity.this);
            column.setOrientation(LinearLayout.VERTICAL);
            Picture picture = new Picture(DenLooksActivity.this);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            column.addView(picture);
            TextView name = text("", 15, Color.WHITE, Typeface.BOLD);
            TextView meta = text("", 12, 0xffa09dac, Typeface.NORMAL);
            name.setPadding(px(10), px(7), px(10), 0);
            meta.setPadding(px(10), 0, px(10), px(9));
            column.addView(name);
            column.addView(meta);
            card.addView(column);
            card.setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(150).start();
                v.setElevation(focused ? px(8) : 0);
            });
            return new Holder(card, picture, name, meta, frame);
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            DenLooks.Look look = position == 0 ? null : looks.get(position - 1);
            boolean on = look == current;
            h.name.setText(look == null ? "Rotate every look" : look.name);
            h.meta.setText((look == null ? "A new look every 5 minutes" : look.category) + (on ? " · On now" : ""));
            h.frame.setStroke(on ? px(3) : 0, accent(look));
            h.itemView.setOnClickListener(v -> choose(look));
            h.picture.setImageBitmap(null);
            if (look == null) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff241c46, 0xffff4d6d});
                h.picture.setBackground(g);
                h.want = null;
                return;
            }
            h.picture.setBackgroundColor(0xff000000);
            h.want = look.wallpaper;
            Bitmap cached = thumbs.get(look.wallpaper);
            if (cached != null) { h.picture.setImageBitmap(cached); return; }
            if (walls == null) return;
            String file = look.wallpaper;
            io.execute(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 8;   // 1920x1080 -> 240x135
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeFile(new File(walls, file).getPath(), o);
                if (b == null) return;
                main.post(() -> {
                    thumbs.put(file, b);
                    if (file.equals(h.want)) h.picture.setImageBitmap(b);
                });
            });
        }

        private int accent(DenLooks.Look look) {
            if (look == null) return 0xffff4d6d;
            try { return Color.parseColor(look.accent); } catch (IllegalArgumentException e) { return 0xffff4d6d; }
        }
    }
}
