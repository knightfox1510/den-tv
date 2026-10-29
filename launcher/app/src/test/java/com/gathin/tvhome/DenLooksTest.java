package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DenLooksTest {
    @Test public void wallpaperNamesGiveTheLookId() {
        assertEquals("camp-nou", DenLooks.wallpaperLookId("01-camp-nou.jpg"));
        assertEquals("king-of-curses", DenLooks.wallpaperLookId("42-king-of-curses.jpg"));
        assertEquals("hero-messi", DenLooks.wallpaperLookId("55-hero-messi.JPG"));
    }

    @Test public void otherFilesAreNotLooks() {
        assertNull(DenLooks.wallpaperLookId("captions.txt"));
        assertNull(DenLooks.wallpaperLookId("camp-nou.jpg"));
        assertNull(DenLooks.wallpaperLookId("a1-camp-nou.jpg"));
        assertNull(DenLooks.wallpaperLookId("01-.jpg"));
        assertNull(DenLooks.wallpaperLookId("01-camp-nou.gif"));
    }

    @Test public void onlyEyeWatchersDrawEyes() {
        assertEquals(true, new DenLooks.Look("a", "A", "", "sharingan", "bloom", "#fff", "01-a.jpg").hasEyes());
        assertEquals(false, new DenLooks.Look("b", "B", "", "dragonballs", "bloom", "#fff", "02-b.jpg").hasEyes());
    }
}
