package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;

/** A described look (Describe a look with Gemini): its own wallpaper photo, its facts, and it shows as a look. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34)
public class DenMadeLookTest {
    private final Application app = RuntimeEnvironment.getApplication();

    @Test public void aDescribedLookKeepsItsFacts() throws Exception {
        DenTheme.ensure(app);
        File photo = DenPhotos.create(app);
        try (FileOutputStream o = new FileOutputStream(photo)) { o.write(new byte[]{1, 2, 3}); }
        String id = DenLooks.saveMine(app, new JSONObject().put("name", "Raphinha Wings").put("base", "barca-wings").put("photo", photo.getName())
                .put("made", true).put("watcher", "onetwo").put("facts", new org.json.JSONArray().put("He joined Barça in 2022.").put("Number 11.")));
        assertNotNull(id);
        DenLooks.Look l = DenLooks.byId(DenLooks.load(app), id);
        assertNotNull(l);
        assertEquals("My looks", l.category);
        assertEquals(2, l.facts.size());
        assertEquals(photo.getName(), l.photo);
    }
}
