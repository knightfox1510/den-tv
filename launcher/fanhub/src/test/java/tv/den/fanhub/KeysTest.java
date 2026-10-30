package tv.den.fanhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/** Keys: the phone sees which are added, whether they work and why not, but never the key itself. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KeysTest {
    private final Application app = RuntimeEnvironment.getApplication();

    private void setKey(String id, String key) {
        app.getSharedPreferences("fanhub", Context.MODE_PRIVATE).edit().putString("key_" + id, key).commit();
    }

    private JSONObject row(String id) throws Exception {
        JSONArray keys = Keys.status(app).getJSONArray("keys");
        for (int i = 0; i < keys.length(); i++) if (keys.getJSONObject(i).getString("id").equals(id)) return keys.getJSONObject(i);
        throw new AssertionError(id);
    }

    @Test public void onlyTheLastFourShow() {
        assertEquals("••••wxyz", Keys.tail("abcdefghijklmnopqrstuvwxyz"));
        assertEquals("••••", Keys.tail("short"));
    }

    @Test public void errorsReadAsWhatToDo() {
        assertTrue(Keys.explain("football", "HTTP 403").contains("refused this key"));
        assertTrue(Keys.explain("cricket", "hits limit reached").contains("used up"));
        assertTrue(Keys.explain("gemini", "Unable to resolve host").contains("couldn't reach Gemini"));
    }

    @Test public void statesFollowWhatHappened() throws Exception {
        assertEquals("none", row("football").getString("state"));
        assertFalse(row("football").has("tail"));
        String key = "fd0123456789abcdef";
        setKey("football", key);
        setKey("cricket", "cd0123456789abcdef");
        Keys.afterFetch(app, Collections.singletonList("CricketData.org: HTTP 401"));
        JSONObject f = row("football"), c = row("cricket");
        assertEquals("ok", f.getString("state"));
        assertEquals("••••cdef", f.getString("tail"));
        assertFalse(f.toString().contains(key));
        assertEquals("problem", c.getString("state"));
        assertTrue(c.getString("message").contains("refused this key"));
        // A new key isn't judged by the old one's check.
        setKey("cricket", "cd0123456789zzzz");
        assertEquals("checking", row("cricket").getString("state"));
        Keys.remove(app, "football");
        assertEquals("none", row("football").getString("state"));
        Keys.afterFetch(app, Arrays.asList());
    }
}
