package com.biblereader.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    public void titlesReadNaturally() {
        Bible.Book psalms = bible.books().get(18);
        assertEquals("Psalm 23", bible.titleAt(bible.indexOf(psalms, 23)));
        assertEquals("Psalms 23:1", psalms.verseTitle(23, 1));
        Bible.Book jude = bible.books().get(64);
        assertEquals("Jude 5", jude.verseTitle(1, 5));
    }

    @Test
    public void booksLoadOnDemand() throws IOException {
        Bible.Book ruth = bible.books().get(7);
        assertNull(bible.chapterIfLoaded(ruth.firstChapter));
        bible.chapterAt(ruth.firstChapter);
        assertNotNull(bible.chapterIfLoaded(ruth.firstChapter + 3));
    }

    @Test
    public void findsVerseRowsIncludingOmittedVerses() throws IOException {
        Bible.Book luke = bible.books().get(41);
        Bible.Chapter chapter = bible.chapterAt(bible.indexOf(luke, 17));
        assertEquals(0, chapter.indexOfVerse(1));
        // This translation moves verse 36 to a footnote; land on verse 35.
        assertEquals(35, chapter.items.get(chapter.indexOfVerse(36)).verse);
        assertEquals(37, chapter.items.get(chapter.indexOfVerse(37)).verse);
        // Past the end resolves to the last verse.
        assertEquals(37, chapter.items.get(chapter.indexOfVerse(99)).verse);
    }

    private void assertReference(String text, String expectedTitle) {
        Bible.Reference reference = bible.parseReference(text);
        assertNotNull(text, reference);
        assertEquals(text, expectedTitle, reference.title());
    }

    @Test
    public void parsesReferences() {
        assertReference("John 3:16", "John 3:16");
        assertReference("john 3", "John 3");
        assertReference("  JN 3.16 ", "John 3:16");
        assertReference("1 cor 13", "1 Corinthians 13");
        assertReference("1cor 13:4", "1 Corinthians 13:4");
        assertReference("ps 23", "Psalm 23");
        assertReference("Psalm 119:105", "Psalms 119:105");
        assertReference("gen", "Genesis 1");
        assertReference("Song of Solomon 2", "Song of Solomon 2");
        assertReference("song of songs 2:1", "Song of Solomon 2:1");
        assertReference("jude 5", "Jude 5");
        assertReference("jude", "Jude");
        assertReference("phil 4:13", "Philippians 4:13");
        assertReference("rev. 22", "Revelation 22");
        assertReference("3 john", "3 John");
    }

    @Test
    public void rejectsTextThatIsNotAReference() {
        assertNull(bible.parseReference("love one another"));
        assertNull(bible.parseReference("john 99"));
        assertNull(bible.parseReference("j 3"));
        assertNull(bible.parseReference("3:16"));
        assertNull(bible.parseReference(""));
    }

    @Test
    public void searchFindsVersesContainingEveryWord() throws IOException {
        Bible.SearchResult result = bible.search("  Pastures  GREEN ", 50, () -> false);
        assertFalse(result.truncated);
        assertEquals(1, result.hits.size());
        Bible.Hit hit = result.hits.get(0);
        assertEquals("Psalms 23:2", hit.reference.title());
        // Poetry line breaks are flattened for a one-line preview.
        assertFalse(hit.text.contains("\n"));
        assertTrue(hit.text.contains("green pastures"));

        // Every word must be present, in any order and as part of longer words.
        Bible.SearchResult two = bible.search("shepherd lack", 50, () -> false);
        assertEquals(2, two.hits.size());
        assertEquals("Psalms 23:1", two.hits.get(0).reference.title());
        assertEquals("Jeremiah 23:4", two.hits.get(1).reference.title());
    }

    @Test
    public void searchReportsWhenThereAreMoreResults() throws IOException {
        Bible.SearchResult limited = bible.search("the", 10, () -> false);
        assertTrue(limited.truncated);
        assertEquals(10, limited.hits.size());
        assertEquals("Genesis 1:1", limited.hits.get(0).reference.title());
        assertTrue(bible.search("", 10, () -> false).hits.isEmpty());
        assertTrue(bible.search("zzzzqqq", 10, () -> false).hits.isEmpty());
    }

    @Test
    public void searchDoesNotMatchTheRecordPrefix() throws IOException {
        // "p" and "v" occur in every stored line's prefix but not in every verse.
        for (Bible.Hit hit : bible.search("v1", 20, () -> false).hits) {
            assertTrue(hit.text, hit.text.toLowerCase().contains("v1"));
        }
    }

    @Test
    public void searchStopsWhenCancelled() throws IOException {
        AtomicInteger checks = new AtomicInteger();
        Bible.SearchResult result = bible.search("the", 100000, () -> checks.incrementAndGet() > 2);
        // Only the first two books were read before the search was called off.
        for (Bible.Hit hit : result.hits) {
            assertFalse(hit.reference.title(), hit.reference.book.newTestament);
        }
        assertTrue(result.hits.size() < 5000);
    }
}
