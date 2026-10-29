package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PhoneServerTest {
    private PhoneServer server;
    private Socket idle;

    @Before public void start() {
        server = new PhoneServer(RuntimeEnvironment.getApplication(), () -> { });
        server.start();
        assertTrue(server.running());
    }

    @After public void stop() throws IOException {
        if (idle != null) idle.close();
        server.stop();
    }

    private static final class Reply { int status; String body = "", cookie; }

    private static Reply call(String method, String path, String body, String cookie) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + PhoneServer.PORT + path).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        c.setRequestMethod(method);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        Reply r = new Reply();
        r.status = c.getResponseCode();
        r.cookie = c.getHeaderField("Set-Cookie");
        InputStream in = r.status < 400 ? c.getInputStream() : c.getErrorStream();
        if (in != null) try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            r.body = b.toString("UTF-8");
        }
        c.disconnect();
        return r;
    }

    /** A phone browser's spare connection that sends nothing must not hold up the page. */
    @Test public void pageLoadsPastAnIdleConnection() throws IOException {
        idle = new Socket("127.0.0.1", PhoneServer.PORT);
        Reply r = call("GET", "/", null, null);
        assertEquals(200, r.status);
        assertTrue(r.body.contains("Settings for the TV"));
    }

    @Test public void pairingGuardsTheSettings() throws IOException {
        assertEquals(401, call("GET", "/api/state", null, null).status);
        String code = server.code();
        String wrong = code.equals("0000") ? "1111" : "0000";
        assertEquals(403, call("POST", "/api/pair", "{\"code\":\"" + wrong + "\"}", null).status);
        Reply paired = call("POST", "/api/pair", "{\"code\":\"" + server.code() + "\"}", null);
        assertEquals(200, paired.status);
        String cookie = paired.cookie.split(";")[0];
        Reply state = call("GET", "/api/state", null, cookie);
        assertEquals(200, state.status);
        assertTrue(state.body.contains("\"teams\""));
        assertEquals(1, server.pairedPhones());
    }

    @Test public void tooManyWrongCodesMakeANewOne() throws IOException {
        String first = server.code();
        String wrong = first.equals("0000") ? "1111" : "0000";
        for (int i = 0; i < 5; i++) call("POST", "/api/pair", "{\"code\":\"" + wrong + "\"}", null);
        assertTrue(!first.equals(server.code()) || first.equals(wrong));
    }
}
