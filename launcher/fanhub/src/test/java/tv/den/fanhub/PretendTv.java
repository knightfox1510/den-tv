package tv.den.fanhub;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pretend Google TV for tests: the shell commands HomeSetup sends (packages, Home, settings), and optionally its
 * adbd, speaking real ADB with key auth (a key is accepted when it signs the token and was allowed before, or is
 * allowed now when allow is true, as a person choosing Allow would).
 */
final class PretendTv implements HomeSetup.Shell {
    final Set<String> packages = new HashSet<>(), disabled = new HashSet<>();
    final Map<String, String> settings = new HashMap<>();
    final List<String> log = new ArrayList<>();
    String home = "com.google.android.apps.tv.launcherx";
    boolean startFails, homeLocked;
    /** Google TV: the setup wizard answers Home. */
    boolean wizardClaimsHome = true;
    /** Who owns the saved Wi-Fi network, as dumpsys wifi names it; null for no saved network. */
    String wifiOwner = "android.uid.system:1000";

    PretendTv() {
        packages.add("com.google.android.apps.tv.launcherx");
        packages.add(HomeSetup.WIZARD);
        packages.add(HomeSetup.LAUNCHER);
        packages.add(HomeSetup.FANHUB);
    }

    private boolean on(String pkg) { return packages.contains(pkg) && !disabled.contains(pkg); }

    @Override public synchronized String run(String c) {
        log.add(c);
        if (c.equals(HomeSetup.HOME_QUERY)) {
            String h = on(home) ? home : on(HomeSetup.WIZARD) ? HomeSetup.WIZARD : "android";
            return "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\r\n" + h + "/.Home\r\n";
        }
        if (c.equals(HomeSetup.CLAIMS_QUERY)) {
            StringBuilder b = new StringBuilder();
            for (String p : new String[]{home, HomeSetup.LAUNCHER, "com.google.android.apps.tv.launcherx"}) if (on(p)) b.append(p).append("/.Home\n");
            if (wizardClaimsHome && on(HomeSetup.WIZARD)) b.append(HomeSetup.WIZARD).append("/.RecoveryActivity\n");
            return b.toString();
        }
        if (c.equals("dumpsys wifi")) return wifiOwner == null ? "Wi-Fi is enabled\n" : "WifiConfigManager\n  ID: 0 SSID: \"Home\" cuid=10031 cname=" + wifiOwner + " allowAutojoin=true\n";
        if (c.startsWith("cmd package set-home-activity --user 0 $(")) {
            String pkg = c.substring(c.indexOf("HOME ") + 5, c.indexOf(" | tail"));
            home = pkg;
            return "";
        }
        if (c.startsWith("cmd package set-home-activity --user 0 " + HomeSetup.LAUNCHER)) {
            if (!homeLocked) home = HomeSetup.LAUNCHER;
            return "";
        }
        if (c.startsWith("am start -W")) return startFails ? "Starting: Intent\nError: Activity not started\n" : "Starting: Intent\nStatus: ok\nLaunchState: COLD\n";
        if (c.startsWith("pm list packages -e ")) { String p = c.substring(20); return on(p) ? "package:" + p + "\n" : ""; }
        if (c.startsWith("pm list packages ")) { String p = c.substring(17); return packages.contains(p) ? "package:" + p + "\n" : ""; }
        if (c.startsWith("pm disable-user --user 0 ")) { disabled.add(c.substring(25)); return "Package new state: disabled-user\n"; }
        if (c.startsWith("pm enable --user 0 ")) { disabled.remove(c.substring(19)); return "Package new state: enabled\n"; }
        if (c.startsWith("settings get ")) { String v = settings.get(c.substring(13)); return (v == null ? "null" : v) + "\n"; }
        if (c.startsWith("settings delete ")) { settings.remove(c.substring(16)); return ""; }
        if (c.startsWith("settings put ")) {
            String[] p = c.substring(13).split(" ", 3);
            settings.put(p[0] + " " + p[1], p[2].replace("'", ""));
            return "";
        }
        return "";
    }

    // ---- adbd ----------------------------------------------------------------------------------------------------

    final Set<BigInteger> allowed = new HashSet<>();
    boolean allow = true;
    int prompts;
    private ServerSocket server;

    /** Starts the pretend adbd; returns its port. */
    int listen() throws IOException {
        server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) { serve(s); } catch (IOException | RuntimeException ignored) { }
            }
        }, "pretend-adbd");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    void stop() throws IOException { server.close(); }

    private void serve(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(s.getInputStream());
        OutputStream out = s.getOutputStream();
        Adb.Message m = Adb.read(in);
        if (m.command != Adb.CNXN) return;
        byte[] token = new byte[20];
        new SecureRandom().nextBytes(token);
        out.write(Adb.encode(Adb.AUTH, Adb.AUTH_TOKEN, 0, token));
        boolean in_ = false;
        while (!in_) {
            m = Adb.read(in);
            if (m.command != Adb.AUTH) return;
            if (m.arg0 == Adb.AUTH_SIGNATURE) {
                for (BigInteger n : allowed) if (verifies(n, token, m.data)) in_ = true;
                if (!in_) out.write(Adb.encode(Adb.AUTH, Adb.AUTH_TOKEN, 0, token));
            } else if (m.arg0 == Adb.AUTH_PUBLIC_KEY) {
                prompts++;
                if (!allow) return;   // "Deny": adbd hangs up
                allowed.add(Adb.modulusOf(m.data));
                in_ = true;
            }
        }
        out.write(Adb.encode(Adb.CNXN, Adb.VERSION, Adb.MAX_DATA, "device::ro.product.model=Pretend TV;\0".getBytes(StandardCharsets.UTF_8)));
        int remote = 100;
        while (true) {
            m = Adb.read(in);
            if (m.command != Adb.OPEN) continue;
            String dest = new String(m.data, StandardCharsets.UTF_8).replace("\0", "");
            int local = m.arg0, id = remote++;
            out.write(Adb.encode(Adb.OKAY, id, local, new byte[0]));
            String reply = dest.startsWith("shell:") ? run(dest.substring(6)) : "";
            if (!reply.isEmpty()) {
                out.write(Adb.encode(Adb.WRTE, id, local, reply.getBytes(StandardCharsets.UTF_8)));
                Adb.Message ok = Adb.read(in);
                if (ok.command != Adb.OKAY) throw new IOException("expected OKAY");
            }
            out.write(Adb.encode(Adb.CLSE, id, local, new byte[0]));
            Adb.Message close = Adb.read(in);
            if (close.command != Adb.CLSE) throw new IOException("expected CLSE");
            if (dest.equals("shell:settings put global adb_enabled 0")) return;   // debugging off: the connection ends
        }
    }

    /** The signature checks out with the public key adbd was given (modulus from adbd's format, exponent 65537). */
    private static boolean verifies(BigInteger n, byte[] token, byte[] sig) {
        try {
            PublicKey k = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, BigInteger.valueOf(65537)));
            Signature v = Signature.getInstance("NONEwithRSA");
            v.initVerify(k);
            v.update(new byte[]{0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14});
            v.update(token);
            return v.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }
}
