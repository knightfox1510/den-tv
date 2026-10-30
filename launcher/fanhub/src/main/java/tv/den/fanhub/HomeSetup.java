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
    /**
     * Google TV's setup wizard. Where it answers Home it has to go with Google's home screen, or Home falls to its
     * RecoveryActivity, a black screen system_server can crash-loop on. But it also owns the Wi-Fi network saved
     * during the TV's first setup (Android deletes that network when it is switched off) and, on some Google TVs,
     * the casting credentials; so it is switched off only where it answers Home, and never while it owns the Wi-Fi.
     * (cleanyourtv's tools/disable.sh learned these on many TVs.)
     */
    static final String WIZARD = "com.google.android.tungsten.setupwraith";
    static final String CLAIMS_QUERY = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME";
    /** makeHome's answer when the setup wizard owns the Wi-Fi: the screen offers Wi-Fi settings for it. */
    static final String WIFI_OWNED = "The Wi-Fi network this TV is on was saved by Google's setup, and switching that off"
            + " (which den-tv needs here) would delete it and take the TV off Wi-Fi. In Settings → Network & Internet, choose"
            + " your network, Forget it, join it again with its password, and then try again.";
    static final String WIFI_UNKNOWN = "The TV didn't say which app owns its saved Wi-Fi, so it isn't safe to switch off"
            + " Google's setup, which den-tv needs here. Try again in a minute; if this TV uses a network cable, that's fine and"
            + " the check is skipped.";
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

    /** Whether the setup wizard answers Home on this TV (it is listed among the HOME activities). */
    static boolean wizardClaimsHome(Shell sh) throws IOException {
        return sh.run(CLAIMS_QUERY).contains(WIZARD + "/");
    }

    /**
     * Null when it is safe to switch the setup wizard off; otherwise why not. ethernet: the TV is on a cable, so a
     * Wi-Fi network it might delete doesn't matter.
     */
    static String wizardWifiCheck(Shell sh, boolean ethernet) throws IOException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("cname=([^ \n]*)").matcher(sh.run("dumpsys wifi"));
        boolean any = false;
        while (m.find()) {
            any = true;
            if (m.group(1).startsWith(WIZARD)) return WIFI_OWNED;
        }
        return any || ethernet ? null : WIFI_UNKNOWN;
    }

    /** Null when den-tv is the home screen now; otherwise what went wrong (and the TV's own home screen is back). */
    static String makeHome(Shell sh, Progress p) throws IOException { return makeHome(sh, p, false); }

    static String makeHome(Shell sh, Progress p, boolean ethernet) throws IOException {
        // Decided before anything changes: if the wizard has to go and can't, nothing is touched.
        boolean wizardOff = enabled(sh, WIZARD) && wizardClaimsHome(sh);
        if (wizardOff) {
            String why = wizardWifiCheck(sh, ethernet);
            if (why != null) return why;
        }
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
        if (wizardOff) { sh.run("pm disable-user --user 0 " + WIZARD); off.add(WIZARD); }
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
        // TCL firmware won't bind an app's accessibility service (the helper) or let it start after a reboot
        // without its auto-start app-op; other TVs don't know the op and ignore it (cleanyourtv's install script).
        sh.run("cmd appops set " + FANHUB + " AUTO_START allow; cmd appops set " + FANHUB + " APP_AUTO_START allow; appops write-settings");
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
