package com.biblereader.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Checks how bookmarks are stored, ordered and recovered. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class BookmarksTest {

    private Context context;
    private Bookmarks bookmarks;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        bookmarks = new Bookmarks(context);
    }

    @Test
    public void startsEmpty() {
        assertTrue(bookmarks.all().isEmpty());
        assertFalse(bookmarks.contains("john", 3, 16));
        assertTrue(bookmarks.versesIn("john", 3).isEmpty());
    }

    @Test
    public void togglingAddsThenRemoves() {
        assertTrue(bookmarks.toggle("john", 3, 16));
        assertTrue(bookmarks.contains("john", 3, 16));
        assertFalse(bookmarks.contains("john", 3, 17));
        assertFalse(bookmarks.contains("1john", 3, 16));
        assertFalse(bookmarks.toggle("john", 3, 16));
        assertFalse(bookmarks.contains("john", 3, 16));
        assertTrue(bookmarks.all().isEmpty());
    }

    @Test
    public void listsNewestFirstAndSurvivesReopening() {
        bookmarks.toggle("genesis", 1, 1);
        bookmarks.toggle("psalms", 23, 1);
        bookmarks.toggle("john", 3, 16);
        List<Bookmarks.Entry> all = new Bookmarks(context).all();
        assertEquals(3, all.size());
        assertTrue(all.get(0).is("john", 3, 16));
        assertTrue(all.get(1).is("psalms", 23, 1));
        assertTrue(all.get(2).is("genesis", 1, 1));
    }

    @Test
    public void reportsTheBookmarkedVersesOfAChapter() {
        bookmarks.toggle("john", 3, 16);
        bookmarks.toggle("john", 3, 3);
        bookmarks.toggle("john", 4, 24);
        bookmarks.toggle("1john", 3, 1);
        assertEquals(new HashSet<>(Arrays.asList(3, 16)), bookmarks.versesIn("john", 3));
    }

    @Test
    public void replacingTheListRestoresAnEarlierState() {
        bookmarks.toggle("genesis", 1, 1);
        bookmarks.toggle("john", 3, 16);
        List<Bookmarks.Entry> before = bookmarks.all();
        bookmarks.toggle("john", 3, 16);
        assertEquals(1, bookmarks.all().size());
        bookmarks.set(before);
        List<Bookmarks.Entry> restored = bookmarks.all();
        assertEquals(2, restored.size());
        assertTrue(restored.get(0).is("john", 3, 16));
    }

    @Test
    public void ignoresStoredTextThatIsNotABookmark() {
        context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE).edit()
                .putString("verses", "junk\njohn:3:16\n:1:1\njohn:x:1\njohn:0:5\n\njohn:3:16:9")
                .commit();
        List<Bookmarks.Entry> all = bookmarks.all();
        assertEquals(1, all.size());
        assertTrue(all.get(0).is("john", 3, 16));
    }
}
