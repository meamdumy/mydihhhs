package com.biblereader.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/** Checks the bundled text and the chapter indexing against known facts. */
public class BibleTest {

    private Bible bible;

    @Before
    public void setUp() throws IOException {
        File assets = new File("src/main/assets");
        bible = new Bible(path -> new FileInputStream(new File(assets, path)));
    }

    @Test
    public void hasEveryBookAndChapter() {
        assertEquals(66, bible.books().size());
        assertEquals(1189, bible.chapterCount());
        assertEquals("Genesis", bible.books().get(0).name);
        assertEquals("Revelation", bible.books().get(65).name);
        assertFalse(bible.books().get(38).newTestament);
        assertEquals("Matthew", bible.books().get(39).name);
        assertTrue(bible.books().get(39).newTestament);
    }

    @Test
    public void indexesRunAcrossBookBoundaries() {
        assertEquals("Genesis 1", bible.titleAt(0));
        assertEquals("Genesis 50", bible.titleAt(49));
        assertEquals("Exodus 1", bible.titleAt(50));
        assertEquals("Revelation 22", bible.titleAt(1188));
        Bible.Book psalms = bible.books().get(18);
        assertEquals(150, psalms.chapterCount);
        assertSame(psalms, bible.bookAt(bible.indexOf(psalms, 150)));
        assertEquals("Proverbs 1", bible.titleAt(bible.indexOf(psalms, 150) + 1));
    }

    @Test
    public void singleChapterBooksOmitTheChapterNumber() {
        Bible.Book jude = bible.books().get(64);
        assertEquals("Jude", bible.titleAt(jude.firstChapter));
    }

    @Test
    public void loadsVersesInOrder() throws IOException {
        Bible.Book john = bible.books().get(42);
        Bible.Chapter chapter = bible.chapterAt(bible.indexOf(john, 3));
        assertEquals("John 3", chapter.title());
        assertEquals(36, chapter.items.size());
        Bible.Item verse16 = chapter.items.get(15);
        assertEquals(16, verse16.verse);
        assertTrue(verse16.text.startsWith("For God so loved the world"));
    }

    @Test
    public void keepsPoetryLinesAndHeadings() throws IOException {
        Bible.Book psalms = bible.books().get(18);
        List<Bible.Item> psalm23 = bible.chapterAt(bible.indexOf(psalms, 23)).items;
        assertTrue(psalm23.get(0).isHeading());
        assertEquals("A Psalm by David.", psalm23.get(0).text);
        assertEquals(1, psalm23.get(1).verse);
        assertTrue(psalm23.get(1).text.contains("\n"));
        assertFalse(psalm23.get(1).text.contains("\\n"));
    }

    @Test
    public void everyChapterLoadsWithAscendingVerses() throws IOException {
        int verses = 0;
        for (int i = 0; i < bible.chapterCount(); i++) {
            Bible.Chapter chapter = bible.chapterAt(i);
            int last = 0;
            for (Bible.Item item : chapter.items) {
                if (item.isHeading()) {
                    continue;
                }
                assertTrue(chapter.title() + " verse " + item.verse, item.verse > last);
                assertFalse(chapter.title(), item.text.trim().isEmpty());
                last = item.verse;
                verses++;
            }
            assertTrue(chapter.title() + " is empty", last > 0);
        }
        assertEquals(31098, verses);
    }
}
