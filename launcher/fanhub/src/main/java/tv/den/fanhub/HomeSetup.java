package tv.den.fanhub;

import java.io.IOException;

/**
 * Making den-tv the TV's home screen, and giving Home back, as shell commands: the same steps and the same safety
 * order as install/install.sh. den-tv is started first, Google's home screen is switched off only once it has, and
 * if Home doesn't end up on den-tv the TV's own is put back, so the TV is never left without a home screen.
 * HomeSetupActivity runs these over Adb on the TV itself; tests run them against a pretend TV.
 */
final class HomeSetup {
    static final String LAUNCHER = "tv.den.launcher", COMPONENT = LAUNCHER + "/com.gathin.tvhome.MainActivity";
    static final String FANHUB = "tv.den.fanhub", HELPER = FANHUB + "/tv.den.fanhub.Helper";
    static final String DREAM = LAUNCHER + "/com.gathin.tvhome.DenDream";
    /** Home screens that come with the TV: Google TV, Android TV, older Android TV. */
    static final String[] STOCK = {"com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher"};
    /** Google TV's setup wizard also claims Home, and takes it back unless it is switched off too. */
    static final String WIZARD = "com.google.android.tungsten.setupwraith";
    static final String HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME";
    /** Brought back to the front after den-tv has been started, so the progress stays on screen. */
    static final String ACTIVITY = FANHUB + "/tv.den.fanhub.HomeSetupActivity";

    interface Shell { String run(String command) throws IOException; }

    /** One line of progress for the screen. */
    interface Progress { void step(String line); }

    private HomeSetup() {}

    static String homeNow(Shell sh) throws IOException {
        String[] lines = sh.run(HOME_QUERY).trim().split("\n");
        return lines[lines.length - 1].trim();
    }

    static boolean enabled(Shell sh, String pkg) throws IOException {
        for (String l : sh.run("pm list packages -e " + pkg).split("\n")) if (l.trim().equals("package:" + pkg)) return true;
        return false;
    }

    static boolean installed(Shell sh, String pkg) throws IOException {
        for (String l : sh.run("pm list packages " + pkg).split("\n")) if (l.trim().equals("package:" + pkg)) return true;
        return false;
    }

    /** Null when den-tv is the home screen now; otherwise what went wrong (and the TV's own home screen is back). */
    static String makeHome(Shell sh, Progress p) throws IOException {
        String before = homeNow(sh);
        String beforePkg = before.contains("/") ? before.substring(0, before.indexOf('/')) : before;
        p.step("Starting den-tv");
        String out = sh.run("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n " + COMPONENT);
        if (!out.contains("Status: ok") || out.matches("(?s)(.*\n)?Error.*")) {
            return "den-tv didn't start, so the TV's own home screen was left as it is.";
        }
        sh.run("am start -n " + ACTIVITY);
        p.step("Switching off the TV's own home screen");
        java.util.List<String> off = new java.util.ArrayList<>();
        for (String pkg : STOCK) if (enabled(sh, pkg)) { sh.run("pm disable-user --user 0 " + pkg); off.add(pkg); }
        if (enabled(sh, WIZARD)) { sh.run("pm disable-user --user 0 " + WIZARD); off.add(WIZARD); }
        sh.run("cmd package set-home-activity --user 0 " + COMPONENT);
        String now = homeNow(sh);
        if (!now.startsWith(LAUNCHER + "/")) {
            for (String pkg : off) sh.run("pm enable --user 0 " + pkg);
            if (!beforePkg.isEmpty() && !beforePkg.equals("android") && !beforePkg.equals(LAUNCHER)) homeTo(sh, beforePkg);
            return "Home went to " + (now.isEmpty() ? "nothing" : now) + " instead of den-tv, so the TV's own home screen was put back."
                    + " This TV's maker may lock its home screen.";
        }
        p.step("den-tv is the home screen");
        // Lets Home show the Wi-Fi name; lets the Fan Hub install den-tv's updates and switch its helper back on
        // after one (Android takes an accessibility service off its list when the app is replaced).
        sh.run("pm grant " + LAUNCHER + " android.permission.ACCESS_FINE_LOCATION");
        sh.run("appops set " + FANHUB + " REQUEST_INSTALL_PACKAGES allow");
        sh.run("pm grant " + FANHUB + " android.permission.WRITE_SECURE_SETTINGS");
        return null;
    }

    /** Switches the den-tv helper on, keeping any other accessibility service that is on. */
    static void helperOn(Shell sh) throws IOException {
        String list = sh.run("settings get secure enabled_accessibility_services").trim();
        String next = list.contains(HELPER) ? list : list.isEmpty() || list.equals("null") ? HELPER : list + ":" + HELPER;
        // Cleared first: after an update Android binds the helper again only when the list changes.
        sh.run("settings delete secure enabled_accessibility_services");
        sh.run("settings put secure enabled_accessibility_services '" + next + "'");
        sh.run("settings put secure accessibility_enabled 1");
    }

    static void screensaverOn(Shell sh) throws IOException {
        sh.run("settings put secure screensaver_components " + DREAM);
    }

    /** Last of all: the connection ends with it. */
    static void debuggingOff(Shell sh) throws IOException {
        sh.run("settings put global adb_enabled 0");
    }

    /** Puts the TV's own home screen back; den-tv and the Fan Hub stay installed (Settings, Apps removes them). */
    static String giveBack(Shell sh, Progress p) throws IOException {
        p.step("Switching the TV's own home screen back on");
        String stock = null;
        for (String pkg : STOCK) if (installed(sh, pkg)) { stock = pkg; break; }
        if (stock == null) return "This TV has no Google home screen to go back to.";
        sh.run("pm enable --user 0 " + stock);
        homeTo(sh, stock);
        if (installed(sh, WIZARD)) sh.run("pm enable --user 0 " + WIZARD);
        if (sh.run("settings get secure screensaver_components").trim().equals(DREAM)) sh.run("settings delete secure screensaver_components");
        String now = homeNow(sh);
        if (now.startsWith(LAUNCHER + "/")) return "Home still opens den-tv. Restart the TV and try again.";
        p.step("The TV's own home screen is back");
        return null;
    }

    /** Home to a package's own home activity, worked out on the TV. */
    private static void homeTo(Shell sh, String pkg) throws IOException {
        sh.run("cmd package set-home-activity --user 0 $(" + HOME_QUERY + " " + pkg + " | tail -n 1)");
    }
}
