package tv.den.fanhub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Fan Hub's screen as one dashboard, drawn on a 1920x1080 grid scaled to the TV: the next match with a
 * countdown (or the live score), the matches after it, the anime week, the weekend's trail weather and the next 72
 * hours, over the look on Home (its wallpaper, darkened, and its colour). Phone pairing sits in the footer, or in
 * its own card while no phone is paired. The activity fills a Data; the view only draws it.
 */
final class HubView extends View {

    /** One match or episode, with what the screen needs about where it came from. */
    static final class Item {
        final FanHub.Turn turn;
        final String sport, name;   // sport is "football", "cricket", "show" or "hike"; name is the team or show
        Item(FanHub.Turn turn, String sport, String name) { this.turn = turn; this.sport = sport; this.name = name; }
    }

    static final class Data {
        String lookName = "", place = "", errors = "", address, code = "";
        int accent = 0xffff4d6d, paired;
        boolean h24 = true, running = true;
        Bitmap wallpaper, qr;
        long fetched;
        final List<Item> teams = new ArrayList<>(), shows = new ArrayList<>(), others = new ArrayList<>();
        List<FanHub.Day> weekend = new ArrayList<>();
        /** Store.extras: each team's recent form and league table, by team name. */
        org.json.JSONObject extras = new org.json.JSONObject();
    }

    private static final float W = 1920, H = 1080;
    private static final Pattern FOOTBALL_LIVE = Pattern.compile("^(.*?) (\\d+)–(\\d+) (.*?)(?: · (.*))?$");
    private static final Pattern EPISODE = Pattern.compile("EPISODE (\\d+)");

    private Data data = new Data();
    private LongSupplier clock = System::currentTimeMillis;
    private ZoneId zone = ZoneId.systemDefault();
    private final Typeface light, regular, semi, bold;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Runnable tick = new Runnable() {
        @Override public void run() { invalidate(); postDelayed(this, 1000 - clock.getAsLong() % 1000); }
    };

    HubView(Context ctx) {
        super(ctx);
        light = font(ctx, 300);
        regular = font(ctx, 400);
        semi = font(ctx, 600);
        bold = font(ctx, 700);
        setFocusable(false);
    }

    private static Typeface font(Context ctx, int weight) {
        try {
            return new Typeface.Builder(ctx.getAssets(), "fonts/outfit.ttf").setFontVariationSettings("'wght' " + weight).setWeight(weight).build();
        } catch (RuntimeException e) {
            return Typeface.create(Typeface.SANS_SERIF, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    void show(Data d) { data = d; invalidate(); }

    /** For the screen renders: a fixed time and zone. */
    void freeze(long at, ZoneId z) { clock = () -> at; zone = z; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }

    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (getWidth() == 0) return;
        long now = clock.getAsLong();
        c.save();
        c.scale(getWidth() / W, getHeight() / H);
        background(c);
        header(c, now);
        List<Item> teams = upcoming(data.teams, now);
        hero(c, 90, 200, 1010, 400, teams.isEmpty() ? null : teams.get(0), now);
        later(c, 1130, 200, 700, 400, teams.size() > 1 ? teams.subList(1, teams.size()) : new ArrayList<>(), now);
        week(c, 90, 630, 780, 330, now);
        trails(c, 900, 630, 500, 330, now);
        if (data.paired == 0 && data.address != null && data.running) pair(c, 1430, 630, 400, 330);
        else next72(c, 1430, 630, 400, 330, now);
        footer(c, now);
        c.restore();
    }

    private void background(Canvas c) {
        c.drawColor(0xff07080d);
        Bitmap wall = data.wallpaper;
        if (wall != null) {
            float k = Math.max(W / wall.getWidth(), H / wall.getHeight());
            float w = wall.getWidth() * k, h = wall.getHeight() * k;
            r.set((W - w) / 2, (H - h) / 2, (W + w) / 2, (H + h) / 2);
            c.drawBitmap(wall, null, r, img);
        }
        p.setColor(0xff000000);
        p.setShader(new LinearGradient(0, 0, 0, H, wall == null ? 0x00000000 : 0xb3060710, 0xeb060710, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(260, 140, 1000, a(data.accent, 0.16f), 0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    private void header(Canvas c, long now) {
        // The app's mark: a rounded square in the look's colour, a sun over a hill.
        r.set(90, 70, 166, 146);
        p.setColor(0xff000000);
        p.setShader(new LinearGradient(90, 70, 166, 146, data.accent | 0xff000000, mix(data.accent, 0xff9c8cff, 0.6f), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 20, 20, p);
        p.setShader(null);
        p.setColor(0xf2ffffff);
        c.drawCircle(145, 94, 9, p);
        path.reset();
        path.moveTo(102, 134); path.lineTo(124, 104); path.lineTo(138, 120); path.lineTo(146, 112); path.lineTo(156, 134); path.close();
        c.drawPath(path, p);
        text(c, "Fan Hub", 190, 120, 56, semi, 0xffffffff);
        ZonedDateTime z = Instant.ofEpochMilli(now).atZone(zone);
        String date = z.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH));
        text(c, date + "  ·  your teams, your shows, your trails", 192, 160, 25, light, 0xffb9b5c8);
        t.setTextAlign(Paint.Align.RIGHT);
        text(c, z.format(DateTimeFormatter.ofPattern(data.h24 ? "HH:mm" : "h:mm", Locale.ENGLISH)), 1830, 128, 76, light, 0xffffffff);
        text(c, (data.lookName.isEmpty() ? "" : data.lookName + "  ·  ") + "Back returns Home", 1830, 162, 21, regular, 0xffa7a3b5);
        t.setTextAlign(Paint.Align.LEFT);
    }

    /** The next match: its teams, and a countdown to it, or the score while it's on. */
    private void hero(Canvas c, float x, float y, float w, float h, Item it, long now) {
        card(c, x, y, w, h, true);
        if (it == null) {
            label(c, "MATCHES · NEXT UP", x + 36, y + 52, data.accent);
            text(c, "No matches in the next week", x + 36, y + 150, 46, semi, 0xffffffff);
            text(c, "Add your teams from the phone: Tweak → Fan Hub.", x + 36, y + 200, 25, light, 0xffb9b5c8);
            return;
        }
        FanHub.Turn tn = it.turn;
        boolean live = tn.live || now >= tn.start;
        String[] m = parts(tn.topLine);
        label(c, sport(it) + " · " + (live ? "LIVE" : "NEXT UP") + (m[2].isEmpty() ? "" : " · " + m[2].toUpperCase(Locale.ROOT)),
                x + 36, y + 52, live ? 0xffff3b4a : tn.colour);
        if (live) { heroLive(c, x, y, w, h, it); return; }
        // Teams.
        float ty = y + 118;
        badge(c, x + 76, ty, m[0], same(it.name, m[0]) ? tn.colour : 0xff3b3f55);
        float nx = x + 130;
        String a = fit(title(m[0]), 330, 50, bold);
        text(c, a, nx, ty + 17, 50, bold, 0xffffffff);
        float vx = nx + Math.max(200, t.measureText(a)) + 28;
        text(c, "vs", vx, ty + 12, 26, light, 0xffa7a3b5);
        float bx = vx + 92;
        badge(c, bx, ty, m[1], same(it.name, m[1]) ? tn.colour : 0xff3b3f55);
        text(c, fit(title(m[1]), x + w - bx - 90, 50, bold), bx + 54, ty + 17, 50, bold, 0xffffffff);
        // Our side's last results, under its name.
        org.json.JSONObject ex = data.extras.optJSONObject(it.name);
        String form = ex == null ? "" : ex.optString("form");
        if (!form.isEmpty()) formChips(c, same(it.name, m[1]) ? bx + 54 : nx, ty + 34, form);
        // Countdown.
        long left = Math.max(0, tn.start - now) / 1000;
        long[] v;
        String[] units;
        if (left >= 100 * 3600) { v = new long[]{left / 86400, left % 86400 / 3600, left % 3600 / 60}; units = new String[]{"DAYS", "HRS", "MIN"}; }
        else { v = new long[]{left / 3600, left % 3600 / 60, left % 60}; units = new String[]{"HRS", "MIN", "SEC"}; }
        float cx = x + 36, cy = y + 178;
        for (int i = 0; i < 3; i++) {
            float bx0 = cx + i * 190;
            r.set(bx0, cy, bx0 + 150, cy + 124);
            p.setColor(0xcc05060a);
            c.drawRoundRect(r, 16, 16, p);
            t.setTextAlign(Paint.Align.CENTER);
            text(c, String.format(Locale.ROOT, "%02d", v[i]), bx0 + 75, cy + 98, 92, semi, 0xffffffff);
            text(c, units[i], bx0 + 75, cy + 150, 18, regular, 0xffa7a3b5);
            t.setTextAlign(Paint.Align.LEFT);
            if (i < 2) { p.setColor(0x80ffffff); c.drawCircle(bx0 + 170, cy + 48, 4, p); c.drawCircle(bx0 + 170, cy + 76, 4, p); }
        }
        float ix = cx + 600;
        text(c, "until the " + ("Kick-off".equals(tn.word) ? "kick-off" : tn.word.toLowerCase(Locale.ROOT)), ix, cy + 32, 32, semi, lift(tn.colour));
        text(c, when(tn.start, now), ix, cy + 72, 26, regular, 0xffffffff);
        text(c, fit(m[2].isEmpty() ? it.name : title(m[2]), x + w - ix - 30, 24, light), ix, cy + 108, 24, light, 0xffb9b5c8);
        // What follows.
        int more = 0;
        for (Item o : data.teams) if (o != it && o.turn.end > now) more++;
        String foot = more == 0 ? "Home shows this in the panel under the clock." : more + " more " + (more == 1 ? "match" : "matches") + " in the next week, on the right.";
        text(c, foot, x + 36, y + h - 26, 22, light, 0xffb9b5c8);
    }

    private void heroLive(Canvas c, float x, float y, float w, float h, Item it) {
        FanHub.Turn tn = it.turn;
        Matcher f = FOOTBALL_LIVE.matcher(tn.topLine);
        if ("football".equals(it.sport) && f.matches()) {
            float mid = x + w / 2;
            badge(c, x + 140, y + 200, f.group(1), same(it.name, f.group(1)) ? tn.colour : 0xff3b3f55);
            badge(c, x + w - 140, y + 200, f.group(4), same(it.name, f.group(4)) ? tn.colour : 0xff3b3f55);
            t.setTextAlign(Paint.Align.CENTER);
            text(c, f.group(1), x + 140, y + 275, 28, bold, 0xffffffff);
            text(c, f.group(4), x + w - 140, y + 275, 28, bold, 0xffffffff);
            text(c, f.group(2) + " – " + f.group(3), mid, y + 245, 150, semi, 0xffffffff);
            if (f.group(5) != null) text(c, f.group(5), mid, y + 300, 30, semi, 0xffff5a66);
            t.setTextAlign(Paint.Align.LEFT);
        } else {
            String[] lines = tn.topLine.split(" · ");
            for (int i = 0; i < Math.min(2, lines.length); i++)
                text(c, fit(lines[i], w - 72, i == 0 ? 72 : 52, semi), x + 36, y + 170 + i * 84, i == 0 ? 72 : 52, semi, i == 0 ? 0xffffffff : 0xffd8d4e4);
        }
        text(c, "Live scores update every 2 minutes.", x + 36, y + h - 34, 22, light, 0xffb9b5c8);
    }

    /** The matches after the next one, as a fixture list; a second live match goes on top in red. */
    private void later(Canvas c, float x, float y, float w, float h, List<Item> items, long now) {
        card(c, x, y, w, h, false);
        label(c, "LATER · YOUR TEAMS", x + 32, y + 52, data.accent);
        if (items.isEmpty()) {
            text(c, "Nothing else this week.", x + 32, y + 130, 30, regular, 0xffffffff);
            text(c, "Each team's next match shows here once it's set.", x + 32, y + 172, 22, light, 0xffb9b5c8);
            return;
        }
        // A league table (football-data.org) takes the bottom of the card; the fixtures keep the top.
        org.json.JSONObject table = null;
        for (Item it : data.teams) {
            org.json.JSONObject ex = data.extras.optJSONObject(it.name);
            if (ex != null && ex.optJSONObject("table") != null) { table = ex.optJSONObject("table"); break; }
        }
        text(c, "WHEN", x + 32, y + 100, 16, regular, 0xff8e8aa0);
        text(c, "MATCH", x + 210, y + 100, 16, regular, 0xff8e8aa0);
        float ry = y + 120;
        if (table != null) standings(c, x, y + h - 152, w, table);
        for (int i = 0; i < Math.min(table == null ? 5 : 2, items.size()); i++) {
            FanHub.Turn tn = items.get(i).turn;
            boolean live = tn.live || now >= tn.start;
            if (i == 0) { r.set(x + 16, ry, x + w - 16, ry + 50); p.setColor(0x1affffff); c.drawRoundRect(r, 10, 10, p); }
            p.setColor(live ? 0xffff3b4a : tn.colour);
            c.drawCircle(x + 34, ry + 25, 6, p);
            text(c, live ? "LIVE" : shortWhen(tn.start, now), x + 50, ry + 33, 20, semi, live ? 0xffff5a66 : 0xffd8d4e4);
            String[] m = parts(tn.topLine);
            String line = live ? tn.topLine : title(m[0]) + " v " + title(m[1]);
            text(c, fit(line, w - 230 - 130, 22, regular), x + 210, ry + 33, 22, regular, 0xffffffff);
            t.setTextAlign(Paint.Align.RIGHT);
            text(c, fit(m[2].isEmpty() ? "" : title(m[2]), 120, 18, light), x + w - 32, ry + 33, 18, light, 0xffa7a3b5);
            t.setTextAlign(Paint.Align.LEFT);
            ry += 54;
        }
    }

    /** "W D L" chips, oldest first: green, grey, red. */
    private void formChips(Canvas c, float x, float y, String form) {
        text(c, "Form", x, y + 17, 18, light, 0xffb9b5c8);
        float cx = x + 52;
        Paint.Align al = t.getTextAlign();
        for (char ch : form.toCharArray()) {
            r.set(cx, y, cx + 24, y + 24);
            p.setColor(ch == 'W' ? 0xff2f9e5a : ch == 'L' ? 0xffd8434f : 0xff6b6f80);
            c.drawRoundRect(r, 6, 6, p);
            t.setTextAlign(Paint.Align.CENTER);
            text(c, String.valueOf(ch), cx + 12, y + 18, 15, bold, 0xffffffff);
            cx += 30;
        }
        t.setTextAlign(al);
    }

    /** Three rows of the league table, ours highlighted: position, team, played, goal difference, points. */
    private void standings(Canvas c, float x, float y, float w, org.json.JSONObject table) {
        org.json.JSONArray rows = table.optJSONArray("rows");
        if (rows == null) return;
        text(c, "#", x + 34, y + 16, 15, regular, 0xff8e8aa0);
        t.setLetterSpacing(0.14f);
        text(c, table.optString("league").toUpperCase(Locale.ROOT), x + 70, y + 16, 15, regular, 0xff8e8aa0);
        t.setLetterSpacing(0);
        t.setTextAlign(Paint.Align.RIGHT);
        text(c, "P", x + w - 170, y + 16, 15, regular, 0xff8e8aa0);
        text(c, "GD", x + w - 100, y + 16, 15, regular, 0xff8e8aa0);
        text(c, "PTS", x + w - 34, y + 16, 15, regular, 0xff8e8aa0);
        t.setTextAlign(Paint.Align.LEFT);
        float ry = y + 30;
        for (int i = 0; i < rows.length(); i++) {
            org.json.JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            boolean ours = row.optBoolean("ours");
            if (ours) { r.set(x + 16, ry, x + w - 16, ry + 36); p.setColor(0x26ffffff); c.drawRoundRect(r, 8, 8, p); }
            Typeface f = ours ? semi : regular;
            text(c, String.valueOf(row.optInt("pos")), x + 34, ry + 25, 19, f, 0xffffffff);
            text(c, fit(row.optString("name"), w - 330, 19, f), x + 70, ry + 25, 19, f, 0xffffffff);
            t.setTextAlign(Paint.Align.RIGHT);
            int gd = row.optInt("gd");
            text(c, String.valueOf(row.optInt("played")), x + w - 170, ry + 25, 19, f, 0xffd8d4e4);
            text(c, (gd > 0 ? "+" : "") + gd, x + w - 100, ry + 25, 19, f, 0xffd8d4e4);
            text(c, String.valueOf(row.optInt("pts")), x + w - 34, ry + 25, 19, f, 0xffffffff);
            t.setTextAlign(Paint.Align.LEFT);
            ry += 38;
        }
    }

    /** Monday to Sunday with each show's next episode in its day; today in the look's colour. */
    private void week(Canvas c, float x, float y, float w, float h, long now) {
        card(c, x, y, w, h, false);
        label(c, "ANIME · THIS WEEK", x + 32, y + 52, 0xffff6fa8);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        LocalDate mon = today.minusDays(today.getDayOfWeek().getValue() - 1);
        String[] names = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        float col = (w - 64) / 7, py = y + 104, ph = 150;
        for (int d = 0; d < 7; d++) {
            LocalDate day = mon.plusDays(d);
            boolean isToday = day.equals(today);
            float cx = x + 32 + d * col;
            t.setTextAlign(Paint.Align.CENTER);
            text(c, isToday ? "TODAY" : names[d], cx + col / 2, y + 92, 17, isToday ? bold : regular, isToday ? lift(data.accent) : 0xff8e8aa0);
            t.setTextAlign(Paint.Align.LEFT);
            r.set(cx + 4, py, cx + col - 4, py + ph);
            Item ep = null;
            for (Item it : data.shows) if (Instant.ofEpochMilli(it.turn.start).atZone(zone).toLocalDate().equals(day)) { ep = it; break; }
            if (ep == null) {
                p.setColor(0x14ffffff);
                c.drawRoundRect(r, 12, 12, p);
                continue;
            }
            int hue = hueFor(ep.name);
            p.setColor(0xff000000);
            p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, hue, mix(hue, 0xff101018, 0.4f), Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 12, 12, p);
            p.setShader(null);
            p.setColor(0x40ffffff);
            c.drawCircle(r.centerX() + 10, r.top + 46, 22, p);
            if (isToday) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(lift(data.accent)); c.drawRoundRect(r, 12, 12, p); p.setStyle(Paint.Style.FILL); }
            String[] words = ep.name.split(" ");
            String l1 = words[0], l2 = words.length > 1 ? TextUtils.join(" ", java.util.Arrays.copyOfRange(words, 1, words.length)) : "";
            text(c, fit(l1, col - 22, 16, bold), r.left + 9, r.bottom - 46, 16, bold, 0xffffffff);
            if (!l2.isEmpty()) text(c, fit(l2, col - 22, 16, bold), r.left + 9, r.bottom - 28, 16, bold, 0xffffffff);
            Matcher e = EPISODE.matcher(ep.turn.topLine);
            text(c, e.find() ? "Ep " + e.group(1) : "New", r.left + 9, r.bottom - 10, 14, regular, 0xe6ffffff);
        }
        Item next = null;
        for (Item it : data.shows) if (it.turn.end > now && (next == null || it.turn.start < next.turn.start)) next = it;
        if (next == null) {
            text(c, data.shows.isEmpty() ? "Add shows from the phone: Tweak → Fan Hub." : "No new episodes in the next week.", x + 32, y + h - 34, 22, light, 0xffb9b5c8);
            return;
        }
        Matcher e = EPISODE.matcher(next.turn.topLine);
        String when = now >= next.turn.start ? "Out now" : when(next.turn.start, now);
        text(c, when, x + 32, y + h - 34, 24, semi, lift(data.accent));
        float wx = x + 32 + t.measureText(when) + 16;
        text(c, fit(next.name + (e.find() ? " Ep " + e.group(1) : ""), x + w - wx - 30, 24, regular), wx, y + h - 34, 24, regular, 0xffffffff);
    }

    /** The weekend's two days at the hike place, the better one picked out. */
    private void trails(Canvas c, float x, float y, float w, float h, long now) {
        card(c, x, y, w, h, false);
        label(c, "TRAILS · THIS WEEKEND", x + 32, y + 52, 0xff3ddc84);
        if (data.weekend.isEmpty()) {
            text(c, data.place.isEmpty() ? "Weekend weather is off" : "No forecast yet", x + 32, y + 120, 34, semi, 0xffffffff);
            text(c, data.place.isEmpty() ? "Turn on the weekend hike from the" : "It comes with the next fetch.", x + 32, y + 162, 22, light, 0xffb9b5c8);
            if (data.place.isEmpty()) text(c, "phone: Tweak → Widgets.", x + 32, y + 192, 22, light, 0xffb9b5c8);
            return;
        }
        text(c, fit(data.place + " trail", w - 64, 38, semi), x + 32, y + 112, 38, semi, 0xffffffff);
        FanHub.Day first = data.weekend.get(0);
        text(c, "Saturday and Sunday" + (first.sunrise.isEmpty() ? "" : "  ·  sunrise " + first.sunrise), x + 32, y + 146, 20, light, 0xffb9b5c8);
        float bw = (w - 64 - 16) / 2;
        FanHub.Day best = null;
        for (int i = 0; i < Math.min(2, data.weekend.size()); i++) {
            FanHub.Day d = data.weekend.get(i);
            float bx = x + 32 + i * (bw + 16), by = y + 168;
            r.set(bx, by, bx + bw, by + 100);
            p.setColor(d.best ? 0x2e3ddc84 : 0x1affffff);
            c.drawRoundRect(r, 14, 14, p);
            if (d.best) { best = d; p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xb33ddc84); c.drawRoundRect(r, 14, 14, p); p.setStyle(Paint.Style.FILL); }
            sky(c, bx + 38, by + 48, d);
            text(c, d.label + " · " + d.max + "° / " + d.min + "°", bx + 72, by + 42, 22, semi, 0xffffffff);
            text(c, FanHub.sky(d.code) + ", " + d.rain + "%", bx + 72, by + 72, 18, light, 0xffb9b5c8);
        }
        if (best != null) {
            String go = "Go " + ("Sat".equals(best.label) ? "Saturday" : "Sunday") + ", 06:00 to 12:00.";
            text(c, go, x + 32, y + h - 34, 22, semi, 0xff3ddc84);
        }
    }

    /** Everything in the next three days, in order, with its colour. */
    private void next72(Canvas c, float x, float y, float w, float h, long now) {
        card(c, x, y, w, h, false);
        List<Item> all = new ArrayList<>();
        for (List<Item> l : List.of(data.teams, data.shows, data.others))
            for (Item it : l) if (it.turn.end > now && it.turn.start < now + 72 * 3_600_000L) all.add(it);
        all.sort((a, b) -> Long.compare(a.turn.start, b.turn.start));
        label(c, all.size() > 4 ? "NEXT 72 HOURS \u00B7 +" + (all.size() - 4) : "YOUR NEXT 72 HOURS", x + 32, y + 52, 0xff9c8cff);
        if (all.isEmpty()) { text(c, "A quiet three days.", x + 32, y + 120, 28, regular, 0xffffffff); return; }
        float ry = y + 92;
        int rows = Math.min(4, all.size());
        for (int i = 0; i < rows; i++) {
            Item it = all.get(i);
            boolean live = now >= it.turn.start;
            p.setColor(live ? 0xffff3b4a : it.turn.colour);
            c.drawCircle(x + 40, ry + 8, 7, p);
            if (i < rows - 1) { p.setColor(0x33ffffff); c.drawRect(x + 39, ry + 18, x + 41, ry + 52, p); }
            text(c, live ? "NOW" : shortWhen(it.turn.start, now), x + 60, ry + 13, 16, regular, live ? 0xffff5a66 : 0xff8e8aa0);
            String what = "show".equals(it.sport) ? it.name + episode(it.turn) : "hike".equals(it.sport) ? "Hike, " + data.place : line(it.turn.topLine);
            text(c, fit(what, w - 92, 21, regular), x + 60, ry + 40, 21, regular, 0xffffffff);
            ry += 58;
        }
    }

    /** No phone yet: a big code to scan, the address and the pairing code. */
    private void pair(Canvas c, float x, float y, float w, float h) {
        card(c, x, y, w, h, false);
        label(c, "PAIR YOUR PHONE", x + 32, y + 52, data.accent);
        if (data.qr != null) {
            r.set(x + 32, y + 78, x + 32 + 190, y + 78 + 190);
            p.setColor(0xffffffff);
            c.drawRoundRect(r, 10, 10, p);
            r.inset(8, 8);
            c.drawBitmap(data.qr, null, r, img);
        }
        float tx = x + 240;
        text(c, "Scan, or open", tx, y + 104, 20, light, 0xffb9b5c8);
        String[] host = data.address.replace("http://", "").split(":", 2);
        text(c, fit(host[0], x + w - tx - 16, 20, semi), tx, y + 132, 20, semi, 0xffffffff);
        if (host.length > 1) text(c, ":" + host[1], tx, y + 158, 20, semi, 0xffffffff);
        text(c, "and enter", tx, y + 196, 20, light, 0xffb9b5c8);
        text(c, fit(data.code, x + w - tx - 16, 44, semi), tx, y + 248, 44, semi, lift(data.accent));
        text(c, "Same Wi-Fi as the TV.", x + 32, y + h - 34, 20, light, 0xffb9b5c8);
    }

    private void footer(Canvas c, long now) {
        String upd = data.fetched == 0 ? "Not fetched yet" : "Updated " + Instant.ofEpochMilli(data.fetched).atZone(zone)
                .format(DateTimeFormatter.ofPattern(data.h24 ? "HH:mm" : "h:mm a", Locale.ENGLISH));
        text(c, upd + "  ·  OK fetches again  ·  Menu forgets paired phones", 92, 1012, 20, light, 0xff8e8aa0);
        if (!data.errors.isEmpty()) text(c, fit("Couldn't reach: " + data.errors.replace('\n', ';'), 900, 18, light), 92, 1042, 18, light, 0xffffc46b);
        t.setTextAlign(Paint.Align.RIGHT);
        if (!data.running) text(c, "Phone settings couldn't start: port " + PhoneServer.PORT + " is in use.", 1830, 1012, 20, regular, 0xffffc46b);
        else if (data.address == null) text(c, "Phone settings: connect the TV to Wi-Fi first.", 1830, 1012, 20, regular, 0xffb9b5c8);
        else if (data.paired > 0) {
            float qx = 1830;
            if (data.qr != null) {
                r.set(1830 - 76, 984, 1830, 1060);
                p.setColor(0xffffffff);
                c.drawRoundRect(r, 6, 6, p);
                r.inset(4, 4);
                c.drawBitmap(data.qr, null, r, img);
                qx = 1830 - 96;
            }
            text(c, "Phone: " + data.address.replace("http://", "") + "  ·  code " + data.code, qx, 1016, 20, regular, 0xffd8d4e4);
            text(c, data.paired + (data.paired == 1 ? " phone" : " phones") + " paired", qx, 1046, 18, light, 0xff8e8aa0);
        }
        t.setTextAlign(Paint.Align.LEFT);
    }

    // ---- pieces ---------------------------------------------------------------------------------------------------

    private void card(Canvas c, float x, float y, float w, float h, boolean hero) {
        r.set(x, y, x + w, y + h);
        if (hero) { p.setColor(0xff000000); p.setShader(new LinearGradient(x, y, x + w, y + h, a(data.accent, 0.20f), 0xb30b0c14, Shader.TileMode.CLAMP)); }
        else p.setColor(0xa60b0c14);
        c.drawRoundRect(r, 26, 26, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(hero ? 2 : 1.5f);
        p.setColor(hero ? a(data.accent, 0.55f) : 0x26ffffff);
        c.drawRoundRect(r, 26, 26, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void label(Canvas c, String s, float x, float y, int dot) {
        p.setColor(dot | 0xff000000);
        c.drawCircle(x + 6, y - 7, 6, p);
        t.setLetterSpacing(0.18f);
        text(c, s, x + 22, y, 19, semi, 0xffd8d4e4);
        t.setLetterSpacing(0);
    }

    private void badge(Canvas c, float cx, float cy, String name, int colour) {
        p.setColor(colour | 0xff000000);
        c.drawCircle(cx, cy, 40, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0x40ffffff);
        c.drawCircle(cx, cy, 40, p);
        p.setStyle(Paint.Style.FILL);
        Paint.Align al = t.getTextAlign();
        t.setTextAlign(Paint.Align.CENTER);
        text(c, initials(name), cx, cy + 9, 26, bold, 0xffffffff);
        t.setTextAlign(al);
    }

    /** A small drawn sky for a forecast: sun, cloud, or cloud with rain. */
    private void sky(Canvas c, float cx, float cy, FanHub.Day d) {
        boolean rain = d.rain >= 50 || d.code >= 51, sun = d.code <= 2 && d.rain < 50;
        if (sun || d.code <= 2) {
            p.setColor(0xffffc83a);
            c.drawCircle(cx - (sun ? 0 : 8), cy - (sun ? 0 : 8), sun ? 13 : 10, p);
            if (sun) {
                p.setStrokeWidth(3); p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4;
                    c.drawLine(cx + (float) Math.cos(ang) * 18, cy + (float) Math.sin(ang) * 18, cx + (float) Math.cos(ang) * 24, cy + (float) Math.sin(ang) * 24, p);
                }
                return;
            }
        }
        p.setColor(0xffe6ecf5);
        c.drawCircle(cx - 8, cy + 2, 10, p);
        c.drawCircle(cx + 4, cy - 4, 13, p);
        c.drawCircle(cx + 15, cy + 3, 9, p);
        r.set(cx - 18, cy + 2, cx + 24, cy + 12);
        c.drawRoundRect(r, 5, 5, p);
        if (rain) {
            p.setColor(0xff7fb6ff); p.setStrokeWidth(2.5f); p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 3; i++) c.drawLine(cx - 8 + i * 10, cy + 18, cx - 11 + i * 10, cy + 26, p);
        }
    }

    private void text(Canvas c, String s, float x, float y, float size, Typeface face, int colour) {
        t.setTypeface(face);
        t.setTextSize(size);
        t.setColor(colour);
        c.drawText(s, x, y, t);
    }

    private String fit(String s, float max, float size, Typeface face) {
        t.setTypeface(face);
        t.setTextSize(size);
        if (t.measureText(s) <= max) return s;
        while (s.length() > 1 && t.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s.trim() + "…";
    }

    // ---- words ----------------------------------------------------------------------------------------------------

    private List<Item> upcoming(List<Item> items, long now) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) if (it.turn.end > now) out.add(it);
        out.sort((a, b) -> {
            boolean la = a.turn.live || now >= a.turn.start, lb = b.turn.live || now >= b.turn.start;
            return la != lb ? (la ? -1 : 1) : Long.compare(a.turn.start, b.turn.start);
        });
        return out;
    }

    /** "BARÇA V REAL MADRID · LA LIGA" -> {"BARÇA", "REAL MADRID", "LA LIGA"}. */
    static String[] parts(String top) {
        String[] dot = top.split(" · ", 2);
        String[] v = dot[0].split(" (?i:v) ", 2);
        return new String[]{v[0].trim(), v.length > 1 ? v[1].trim() : "", dot.length > 1 ? dot[1].trim() : ""};
    }

    /** "NEW ZEALAND" -> "New Zealand"; short codes ("NZ", "IND", "T20I", "IPL") stay as they are. */
    static String title(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            if (w.length() <= 3 && !w.equals("DE") || w.matches(".*\\d.*")) b.append(w);
            else b.append(w.charAt(0)).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    /** "India" -> "IND", "Real Madrid" -> "RM", "NZ" -> "NZ". */
    static String initials(String name) {
        String[] w = name.trim().split("\\s+");
        if (w.length == 1) return w[0].substring(0, Math.min(3, w[0].length())).toUpperCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length && b.length() < 3; i++) if (!w[i].isEmpty()) b.append(Character.toUpperCase(w[i].charAt(0)));
        return b.toString();
    }

    /** Whether a side ("BARÇA", "BAR", "INDIA") is our team ("FC Barcelona", "India"); live lines use 3-letter names. */
    private static boolean same(String team, String side) {
        if (FanHub.same(team, title(side)) || FanHub.same(team, side)) return true;
        String t = team.replaceAll("(?i)^(fc|cf|afc) ", "").toUpperCase(Locale.ROOT);
        return side.length() >= 2 && side.length() <= 3 && t.startsWith(side.toUpperCase(Locale.ROOT));
    }

    private static String sport(Item it) { return "cricket".equals(it.sport) ? "CRICKET" : "football".equals(it.sport) ? "FOOTBALL" : "MATCH"; }

    private static String line(String top) {
        String[] m = parts(top);
        return m[1].isEmpty() ? title(top) : title(m[0]) + " v " + title(m[1]) + (m[2].isEmpty() ? "" : " · " + title(m[2]));
    }

    private static String episode(FanHub.Turn tn) {
        Matcher e = EPISODE.matcher(tn.topLine);
        return e.find() ? " Ep " + e.group(1) : "";
    }

    /** "Tonight 21:30", "Tomorrow 14:02", "Sat 1 Nov, 20:45". */
    private String when(long at, long now) {
        ZonedDateTime z = Instant.ofEpochMilli(at).atZone(zone);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String time = z.format(DateTimeFormatter.ofPattern(data.h24 ? "HH:mm" : "h:mm a", Locale.ENGLISH));
        if (z.toLocalDate().equals(today)) return (z.getHour() >= 17 ? "Tonight " : "Today ") + time;
        if (z.toLocalDate().equals(today.plusDays(1))) return "Tomorrow " + time;
        return z.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) + ", " + time;
    }

    /** "TODAY 13:32", "TOMORROW 09:00", "SAT 20:45". */
    private String shortWhen(long at, long now) {
        ZonedDateTime z = Instant.ofEpochMilli(at).atZone(zone);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String time = z.format(DateTimeFormatter.ofPattern(data.h24 ? "HH:mm" : "h:mm a", Locale.ENGLISH));
        String day = z.toLocalDate().equals(today) ? "TODAY" : z.toLocalDate().equals(today.plusDays(1)) ? "TMRW"
                : z.toLocalDate().isBefore(today.plusDays(7)) ? z.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).toUpperCase(Locale.ROOT)
                : z.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).toUpperCase(Locale.ROOT);
        return day + " " + time;
    }

    // ---- colour ---------------------------------------------------------------------------------------------------

    private static int a(int colour, float alpha) { return (Math.round(alpha * 255) << 24) | (colour & 0xffffff); }

    private static int mix(int x, int y, float k) {
        int r = Math.round(((x >> 16) & 255) * (1 - k) + ((y >> 16) & 255) * k), g = Math.round(((x >> 8) & 255) * (1 - k) + ((y >> 8) & 255) * k),
                b = Math.round((x & 255) * (1 - k) + (y & 255) * k);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /** A colour bright enough to read on the dark cards. */
    private static int lift(int colour) {
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        return 0.299 * r + 0.587 * g + 0.114 * b < 110 ? mix(colour, 0xffffffff, 0.45f) : colour | 0xff000000;
    }

    /** A steady colour per show, for its poster. */
    private static int hueFor(String name) {
        int[] hues = {0xff3f5bd8, 0xff2f8f6b, 0xffd8434f, 0xff8a4fd8, 0xffd87a2f, 0xff2f86b8};
        return hues[Math.abs(name.hashCode()) % hues.length];
    }

}
