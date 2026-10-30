package tv.den.fanhub;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * A small ADB client, so the Fan Hub can do on its own TV what a computer does over USB debugging: it connects to
 * the TV's own debugging port (5555, which USB debugging opens on most Android TVs), and the TV asks "Allow USB
 * debugging?" for this app's key the first time, exactly as it does for a computer. Only shell commands are used.
 * The protocol is ADB's own (system/core/adb/protocol.txt): 24-byte little-endian headers, RSA key auth.
 */
final class Adb implements Closeable {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    static final int VERSION = 0x01000000, MAX_DATA = 256 * 1024;
    static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_PUBLIC_KEY = 3;
    static final int PORT = 5555;
    /** ASN.1 DigestInfo prefix for SHA-1: adbd's token is signed as if it were a SHA-1 hash. */
    private static final byte[] SHA1_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    /** The TV said no to this app's key, or nobody answered its Allow prompt in time. */
    static final class NotAllowed extends IOException {
        NotAllowed(String why) { super(why); }
    }

    static final class Message {
        final int command, arg0, arg1;
        final byte[] data;
        Message(int command, int arg0, int arg1, byte[] data) { this.command = command; this.arg0 = arg0; this.arg1 = arg1; this.data = data; }
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextId = 1;

    private Adb(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new java.io.BufferedInputStream(socket.getInputStream()));
        this.out = socket.getOutputStream();
    }

    /**
     * Connects and signs in with this app's key. When the TV doesn't know the key yet it shows its Allow prompt:
     * asking runs then, and up to waitMs is given for someone to answer it.
     */
    static Adb connect(String host, int port, KeyPair key, Runnable asking, int waitMs) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 3000);
        Adb adb = new Adb(s);
        try {
            adb.signIn(key, asking, waitMs);
            return adb;
        } catch (IOException | RuntimeException e) {
            adb.close();
            throw e;
        }
    }

    private void signIn(KeyPair key, Runnable asking, int waitMs) throws IOException {
        socket.setSoTimeout(10_000);
        write(CNXN, VERSION, MAX_DATA, "host::den-tv\0".getBytes(StandardCharsets.UTF_8));
        boolean signed = false, offered = false;
        while (true) {
            Message m;
            try {
                m = read();
            } catch (SocketTimeoutException | EOFException e) {
                if (offered) throw new NotAllowed("The TV didn't allow it.");
                throw e;
            }
            if (m.command == CNXN) return;
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (!signed) {
                write(AUTH, AUTH_SIGNATURE, 0, sign(key, m.data));
                signed = true;
            } else if (!offered) {
                // The key isn't known: offering it makes the TV ask, and the answer can take a while.
                write(AUTH, AUTH_PUBLIC_KEY, 0, publicKey((RSAPublicKey) key.getPublic(), "den-tv@fanhub"));
                offered = true;
                if (asking != null) asking.run();
                socket.setSoTimeout(waitMs);
            } else {
                throw new NotAllowed("The TV didn't allow it.");
            }
        }
    }

    /** Runs a command in the TV's shell and returns what it printed (with Android's \r\n made \n). */
    String shell(String command) throws IOException {
        int local = nextId++, remote = 0;
        socket.setSoTimeout(60_000);
        write(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        while (true) {
            Message m = read();
            if (m.arg1 != local) continue;   // something for another stream
            if (m.command == OKAY) remote = m.arg0;
            else if (m.command == WRTE) {
                text.write(m.data);
                write(OKAY, local, m.arg0, new byte[0]);
            } else if (m.command == CLSE) {
                if (remote != 0) write(CLSE, local, remote, new byte[0]);
                return text.toString("UTF-8").replace("\r\n", "\n");
            }
        }
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }

    // ---- messages ------------------------------------------------------------------------------------------------

    private void write(int command, int arg0, int arg1, byte[] data) throws IOException {
        out.write(encode(command, arg0, arg1, data));
        out.flush();
    }

    static byte[] encode(int command, int arg0, int arg1, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~command).put(data);
        return b.array();
    }

    private Message read() throws IOException {
        return read(in);
    }

    static Message read(DataInputStream in) throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int command = b.getInt(), arg0 = b.getInt(), arg1 = b.getInt(), length = b.getInt();
        b.getInt();   // checksum: adbd sends 0 from protocol version 0x01000001 on, so it isn't checked
        if (b.getInt() != ~command) throw new IOException("Not an ADB message");
        if (length < 0 || length > 1024 * 1024) throw new IOException("ADB message too big");
        byte[] data = new byte[length];
        in.readFully(data);
        return new Message(command, arg0, arg1, data);
    }

    // ---- keys ----------------------------------------------------------------------------------------------------

    /** adbd's token signed with the private key, PKCS#1 v1.5 over a SHA-1 DigestInfo of the token as given. */
    static byte[] sign(KeyPair key, byte[] token) throws IOException {
        try {
            Signature s = Signature.getInstance("NONEwithRSA");
            s.initSign(key.getPrivate());
            s.update(SHA1_INFO);
            s.update(token);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IOException("Couldn't sign: " + e.getMessage(), e);
        }
    }

    /**
     * The public key in adbd's own format (the one in ~/.android/adbkey.pub): little-endian words of the modulus
     * with the Montgomery values adbd's RSA code wants, base64, then a name, NUL-terminated.
     */
    static byte[] publicKey(RSAPublicKey k, String name) {
        int words = (k.getModulus().bitLength() + 31) / 32;
        BigInteger n = k.getModulus(), r32 = BigInteger.ONE.shiftLeft(32);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n.mod(r32).modInverse(r32).negate().mod(r32).intValue());   // -1 / n[0] mod 2^32
        for (int i = 0; i < words; i++) b.putInt(n.shiftRight(32 * i).intValue());
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 32 * 2).mod(n);     // R^2 mod n, R = 2^(32 * words)
        for (int i = 0; i < words; i++) b.putInt(rr.shiftRight(32 * i).intValue());
        b.putInt(k.getPublicExponent().intValue());
        return (Base64.getEncoder().encodeToString(b.array()) + " " + name + "\0").getBytes(StandardCharsets.UTF_8);
    }

    /** The modulus back from adbd's format (for tests: a pretend TV checks signatures with it). */
    static BigInteger modulusOf(byte[] encoded) {
        String s = new String(encoded, StandardCharsets.UTF_8);
        byte[] raw = Base64.getDecoder().decode(s.substring(0, s.indexOf(' ')));
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int words = b.getInt();
        b.getInt();
        BigInteger n = BigInteger.ZERO;
        for (int i = 0; i < words; i++) n = n.or(BigInteger.valueOf(b.getInt() & 0xffffffffL).shiftLeft(32 * i));
        return n;
    }

    /** This app's key, made the first time (2048-bit RSA, as adb's own), kept in the app's private files. */
    static KeyPair key(Context ctx) throws IOException {
        File priv = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        try {
            KeyFactory f = KeyFactory.getInstance("RSA");
            if (priv.isFile() && pub.isFile()) {
                return new KeyPair(f.generatePublic(new X509EncodedKeySpec(readAll(pub))), f.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair k = g.generateKeyPair();
            try (OutputStream o = new FileOutputStream(priv)) { o.write(k.getPrivate().getEncoded()); }
            try (OutputStream o = new FileOutputStream(pub)) { o.write(k.getPublic().getEncoded()); }
            return k;
        } catch (GeneralSecurityException e) {
            throw new IOException("No RSA keys: " + e.getMessage(), e);
        }
    }

    /** Throws the key away: the TV's "always allow" for it is then of no use to anyone. */
    static void forgetKey(Context ctx) {
        new File(ctx.getFilesDir(), "adbkey").delete();
        new File(ctx.getFilesDir(), "adbkey.pub").delete();
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream i = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }
}
