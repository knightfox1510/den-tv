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
        String path = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
        if ("settings".equals(uri.getLastPathSegment())) return settings();
        if ("apps".equals(path)) return apps();
        if ("override".equals(path)) return override();
        if ("photos".equals(path)) return photos();
        if ("people".equals(path)) return people();
        if ("characters".equals(path)) return characters();
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
        String path = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
        if ("apps".equals(path) && values != null) return saveApp(values);
        if ("override".equals(path) && values != null) return saveOverride(values);
        if ("photos".equals(path) && values != null) return photo(values);
        if ("people".equals(path) && values != null) return person(values);
        if ("characters".equals(path) && values != null) return character(values);
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
        c.addRow(new Object[]{DenSettings.WAKE, p.getBoolean(DenSettings.WAKE, true) ? "1" : "0"});
        c.addRow(new Object[]{DenSettings.REDUCED_MOTION, p.getBoolean(DenSettings.REDUCED_MOTION, false) ? "1" : "0"});
        c.addRow(new Object[]{DenSettings.IDLE_CLOCK, String.valueOf(p.getInt(DenSettings.IDLE_CLOCK, -1))});
        android.content.SharedPreferences choices = getContext().getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
        c.addRow(new Object[]{"animations", choices.getBoolean("animations", true) ? "1" : "0"});
        c.addRow(new Object[]{"facts", p.getBoolean("facts", false) ? "1" : "0"});
        c.addRow(new Object[]{"stats", p.getBoolean("stats", false) ? "1" : "0"});
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
        if (v.containsKey(DenSettings.IDLE_CLOCK)) {
            try {
                int m = Integer.parseInt(v.getAsString(DenSettings.IDLE_CLOCK));
                if (m < 0) e.remove(DenSettings.IDLE_CLOCK); else e.putInt(DenSettings.IDLE_CLOCK, Math.min(240, m));
                n++;
            } catch (NumberFormatException ignored) { }
        }
        if (v.containsKey("animations")) {
            getContext().getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE).edit()
                    .putBoolean("animations", "1".equals(v.getAsString("animations"))).apply();
            n++;
        }
        for (String k : new String[]{DenSettings.GREETING, DenSettings.PANEL, DenSettings.WATCHER, DenSettings.SETUP_DONE,
                DenSettings.WAKE, DenSettings.REDUCED_MOTION, "facts", "stats"}) {
            if (!v.containsKey(k)) continue;
            e.putBoolean(k, "1".equals(v.getAsString(k)) || Boolean.TRUE.equals(v.getAsBoolean(k)));
            n++;
        }
        e.apply();
        android.content.SharedPreferences choices = getContext().getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        return n;
    }

    private void bump() {
        android.content.SharedPreferences choices = getContext().getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    // ---- apps: hide, show and rename ------------------------------------------------------------------------------

    /** Every TV app (package, its name on Home, hidden "1"/"0", the phone's own name for it or ""). */
    private Cursor apps() {
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        java.util.Set<String> removed = LibraryApps.loadRemoved(getContext());
        org.json.JSONObject names = labelsJson();
        MatrixCursor c = new MatrixCursor(new String[]{"package", "label", "hidden", "renamed"});
        java.util.Set<String> seen = new java.util.HashSet<>();
        android.content.Intent tv = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(tv, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!seen.add(pkg) || pkg.equals(getContext().getPackageName())) continue;
            String renamed = names.optString(pkg, "");
            c.addRow(new Object[]{pkg, renamed.isEmpty() ? String.valueOf(ri.loadLabel(pm)) : renamed, removed.contains(pkg) ? "1" : "0", renamed});
        }
        return c;
    }

    private org.json.JSONObject labelsJson() {
        try { return new org.json.JSONObject(DenSettings.prefs(getContext()).getString(DenSettings.LABELS, "{}")); }
        catch (org.json.JSONException e) { return new org.json.JSONObject(); }
    }

    /** package plus hidden "1"/"0" and/or label (empty puts the app's own name back). */
    private int saveApp(ContentValues v) {
        String pkg = v.getAsString("package");
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}")) return 0;
        int n = 0;
        if (v.containsKey("hidden") && LibraryApps.save(getContext(), pkg, !"1".equals(v.getAsString("hidden")))) n++;
        if (v.containsKey("label")) {
            org.json.JSONObject names = labelsJson();
            String label = v.getAsString("label");
            label = label == null ? "" : label.trim();
            try {
                if (label.isEmpty()) names.remove(pkg); else names.put(pkg, label.length() > 30 ? label.substring(0, 30) : label);
            } catch (org.json.JSONException ignored) { }
            DenSettings.prefs(getContext()).edit().putString(DenSettings.LABELS, names.toString()).apply();
            n++;
        }
        bump();
        return n;
    }

    // ---- the current look's watcher, clock, icons and colour -----------------------------------------------------

    /**
     * One row per part: part, the look's own value, the phone's change ("" for none), and the choices. The look is
     * the one on Home; none while looks rotate or a photo is on.
     */
    private Cursor override() {
        MatrixCursor c = new MatrixCursor(new String[]{"part", "look", "value", "choices", "look_id", "look_name"});
        java.util.List<DenLooks.Look> looks = DenLooks.load(getContext());
        DenLooks.Look look = DenLooks.current(getContext(), looks);
        if (look == null) return c;
        org.json.JSONObject o = overrideJson(look.id);
        java.util.List<String> watchers = new java.util.ArrayList<>(DenLooks.EYES);
        watchers.addAll(DenWatcherView.KINDS);
        watchers.add(0, "none");
        c.addRow(new Object[]{"watcher", look.watcher, o.optString("watcher"), String.join(",", watchers), look.id, look.name});
        c.addRow(new Object[]{"clock", look.clock, o.optString("clock"), String.join(",", DenClockView.STYLES), look.id, look.name});
        java.util.List<String> icons = new java.util.ArrayList<>(DenIcons.STYLES);
        icons.add(0, "tiles");
        c.addRow(new Object[]{"icons", look.icons, o.optString("icons"), String.join(",", icons), look.id, look.name});
        c.addRow(new Object[]{"accent", look.accent, o.optString("accent"), "", look.id, look.name});
        return c;
    }

    private org.json.JSONObject overrideJson(String id) {
        try { return new org.json.JSONObject(DenSettings.prefs(getContext()).getString(DenLooks.OVERRIDE + id, "{}")); }
        catch (org.json.JSONException e) { return new org.json.JSONObject(); }
    }

    /** part and value for the look on Home ("" puts the look's own back); reset=1 drops all of its changes. */
    private int saveOverride(ContentValues v) {
        DenLooks.Look look = DenLooks.current(getContext(), DenLooks.load(getContext()));
        if (look == null) return 0;
        String key = DenLooks.OVERRIDE + look.id;
        if ("1".equals(v.getAsString("reset"))) {
            DenSettings.prefs(getContext()).edit().remove(key).apply();
            bump();
            return 1;
        }
        String part = v.getAsString("part"), value = v.getAsString("value");
        if (part == null || !part.matches("watcher|clock|icons|accent")) return 0;
        value = value == null ? "" : value.trim();
        if (part.equals("accent") && !value.isEmpty() && !value.matches("#[0-9a-fA-F]{6}")) return 0;
        if (!part.equals("accent") && !value.matches("[a-z0-9]{0,20}")) return 0;
        org.json.JSONObject o = overrideJson(look.id);
        try { if (value.isEmpty()) o.remove(part); else o.put(part, value); } catch (org.json.JSONException ignored) { }
        DenSettings.prefs(getContext()).edit().putString(key, o.toString()).apply();
        bump();
        return 1;
    }

    // ---- photos --------------------------------------------------------------------------------------------------

    private Cursor photos() {
        MatrixCursor c = new MatrixCursor(new String[]{"name", "on"});
        String on = DenSettings.prefs(getContext()).getString("photo_name", "");
        for (java.io.File f : DenPhotos.list(getContext())) c.addRow(new Object[]{f.getName(), f.getName().equals(on) ? 1 : 0});
        return c;
    }

    /** use=name puts a photo on Home; delete=name removes it. */
    private int photo(ContentValues v) {
        try {
            if (v.containsKey("use")) return DenPhotos.use(getContext(), v.getAsString("use")) ? 1 : 0;
            if (v.containsKey("delete")) { boolean ok = DenPhotos.delete(getContext(), v.getAsString("delete")); bump(); return ok ? 1 : 0; }
        } catch (java.io.IOException e) {
            return 0;
        }
        return 0;
    }

    /** photos/new opens a fresh photo file to write (the Fan Hub saves a resized JPEG); photos/NAME reads one. */
    @Override public android.os.ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        java.util.List<String> seg = uri.getPathSegments();
        if (seg.size() == 2 && "characters".equals(seg.get(0))) return characterFile(seg.get(1), mode);
        if (seg.size() != 2 || !"photos".equals(seg.get(0))) throw new java.io.FileNotFoundException(uri.toString());
        try {
            if ("new".equals(seg.get(1)) && mode.startsWith("w")) {
                java.io.File f = DenPhotos.create(getContext());
                return android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_WRITE_ONLY
                        | android.os.ParcelFileDescriptor.MODE_CREATE | android.os.ParcelFileDescriptor.MODE_TRUNCATE);
            }
            String name = seg.get(1);
            java.io.File d = DenPhotos.dir(getContext());
            if (d != null && name.matches("photo-\\d+\\.jpg") && "r".equals(mode)) {
                return android.os.ParcelFileDescriptor.open(new java.io.File(d, name), android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            }
        } catch (java.io.IOException e) {
            throw new java.io.FileNotFoundException(e.getMessage());
        }
        throw new java.io.FileNotFoundException(uri.toString());
    }

    // ---- people ----------------------------------------------------------------------------------------------------

    private Cursor people() {
        MatrixCursor c = new MatrixCursor(new String[]{"id", "name", "colour", "look", "guest", "on"});
        DenPeople.Person cur = DenPeople.current(getContext());
        for (DenPeople.Person p : DenPeople.all(getContext())) {
            c.addRow(new Object[]{p.id, p.name, p.colour, p.look, p.guest ? "1" : "0", cur != null && cur.id.equals(p.id) ? "1" : "0"});
        }
        return c;
    }

    /** use=id switches; id with name/colour/guest edits (a new id adds); remove=id removes. */
    private int person(ContentValues v) {
        if (v.containsKey("use")) return DenPeople.use(getContext(), v.getAsString("use")) ? 1 : 0;
        if (v.containsKey("remove")) return DenPeople.edit(getContext(), v.getAsString("remove"), null, null, null, true) ? 1 : 0;
        String id = v.getAsString("id");
        Boolean guest = v.containsKey("guest") ? "1".equals(v.getAsString("guest")) : null;
        return DenPeople.edit(getContext(), id == null ? "" : id, v.getAsString("name"), v.getAsString("colour"), guest, false) ? 1 : 0;
    }

    // ---- characters ----------------------------------------------------------------------------------------------

    private Cursor characters() {
        MatrixCursor c = new MatrixCursor(new String[]{"id", "name", "closed", "on", "mode"});
        String on = DenCharacters.inUse(getContext()), mode = DenCharacters.mode(getContext());
        for (DenCharacters.Character ch : DenCharacters.all(getContext())) c.addRow(new Object[]{ch.id, ch.name, ch.closed ? "1" : "0", ch.id.equals(on) ? "1" : "0", mode});
        return c;
    }

    /** add=id with name (after its picture was written); use=id with mode figure/walker/off; delete=id. */
    private int character(ContentValues v) {
        int n = 0;
        if (v.containsKey("add")) n = DenCharacters.add(getContext(), v.getAsString("add"), v.getAsString("name") == null ? "My character" : v.getAsString("name")) ? 1 : 0;
        else if (v.containsKey("use")) { DenCharacters.use(getContext(), v.getAsString("use"), v.getAsString("mode")); n = 1; }
        else if (v.containsKey("delete")) n = DenCharacters.delete(getContext(), v.getAsString("delete")) ? 1 : 0;
        bump();
        return n;
    }

    /** characters/c123.png or c123-closed.png: written by the Fan Hub ("w"), read back for the phone ("r"). */
    private android.os.ParcelFileDescriptor characterFile(String name, String mode) throws java.io.FileNotFoundException {
        if (!name.matches("c\\d{1,16}(-closed)?\\.png")) throw new java.io.FileNotFoundException(name);
        java.io.File d = DenCharacters.dir(getContext());
        if (d == null || (!d.isDirectory() && !d.mkdirs())) throw new java.io.FileNotFoundException("storage not ready");
        java.io.File f = new java.io.File(d, name);
        if (mode.startsWith("w")) return android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_WRITE_ONLY
                | android.os.ParcelFileDescriptor.MODE_CREATE | android.os.ParcelFileDescriptor.MODE_TRUNCATE);
        return android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
