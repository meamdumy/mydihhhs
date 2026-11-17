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
    public void readsASingleVerseWithoutLoadingItsBook() throws IOException {
        Bible.Book john = bible.books().get(42);
        assertTrue(bible.verseText(john, 3, 16).startsWith("For God so loved the world"));
        // Verse 1 is not confused with verses 10 to 19.
        assertTrue(bible.verseText(john, 3, 1).startsWith("Now there was a man"));
        assertNull(bible.chapterIfLoaded(john.firstChapter));

        Bible.Book psalms = bible.books().get(18);
        assertEquals("Yahweh is my shepherd: I shall lack nothing.",
                bible.verseText(psalms, 23, 1));

        // Absent: a verse this translation omits, and places past the end.
        assertNull(bible.verseText(bible.books().get(43), 8, 37));
        assertNull(bible.verseText(john, 3, 99));
        assertNull(bible.verseText(john, 99, 1));
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
        assertReference("genesis", "Genesis 1");
        assertReference("gen 1", "Genesis 1");
        assertReference("is 53", "Isaiah 53");
        assertReference("psalm", "Psalm 1");
        assertReference("Song of Solomon 2", "Song of Solomon 2");
        assertReference("song of songs 2:1", "Song of Solomon 2:1");
        assertReference("jude 5", "Jude 5");
        assertReference("jude", "Jude");
        assertReference("phil 4:13", "Philippians 4:13");
        assertReference("rev. 22", "Revelation 22");
        assertReference("3 john", "3 John");
        // A range opens at its first verse.
        assertReference("John 3:16-18", "John 3:16");
        assertReference("ps 23:1 \u2013 3", "Psalms 23:1");
    }

    @Test
    public void resolvesReferencesToVersesThatExist() throws IOException {
        Bible.Reference exact = bible.parseReference("John 3:16");
        assertSame(exact, bible.resolve(exact));
        Bible.Reference chapter = bible.parseReference("John 3");
        assertSame(chapter, bible.resolve(chapter));
        // Past the end of the chapter: the last verse.
        assertEquals("John 3:36", bible.resolve(bible.parseReference("John 3:99")).title());
        // Verses this translation moves to footnotes: the verse before.
        assertEquals("Acts 8:36", bible.resolve(bible.parseReference("Acts 8:37")).title());
        assertEquals("Luke 17:35", bible.resolve(bible.parseReference("Luke 17:36")).title());
    }

    @Test
    public void rejectsTextThatIsNotAReference() {
        assertNull(bible.parseReference("love one another"));
        assertNull(bible.parseReference("john 99"));
        assertNull(bible.parseReference("j 3"));
        // On their own these are ordinary words, not Genesis, Isaiah, Hebrews,
        // Song of Solomon or Judges.
        assertNull(bible.parseReference("gen"));
        assertNull(bible.parseReference("is"));
        assertNull(bible.parseReference("he"));
        assertNull(bible.parseReference("son"));
        assertNull(bible.parseReference("judge"));
        assertNull(bible.parseReference("3:16"));
        assertNull(bible.parseReference(""));
    }

    private Bible.SearchResult search(String query, int limit) throws IOException {
        return bible.search(Bible.Query.parse(query), limit, () -> false);
    }

    @Test
    public void searchFindsVersesContainingEveryWord() throws IOException {
        Bible.SearchResult result = search("  Pastures  GREEN ", 50);
        assertFalse(result.truncated);
        assertEquals(1, result.hits.size());
        Bible.Hit hit = result.hits.get(0);
        assertEquals("Psalms 23:2", hit.reference.title());
        // Poetry line breaks are flattened for a one-line preview.
        assertFalse(hit.text.contains("\n"));
        assertTrue(hit.text.contains("green pastures"));

        // Every word must be present, in any order; "lack" also finds "lacking".
        Bible.SearchResult two = search("shepherd lack", 50);
        assertEquals(2, two.hits.size());
        assertEquals("Psalms 23:1", two.hits.get(0).reference.title());
        assertEquals("Jeremiah 23:4", two.hits.get(1).reference.title());
    }

    @Test
    public void searchMatchesOnlyAtTheStartOfAWord() throws IOException {
        Bible.SearchResult result = search("ark", 100000);
        assertFalse(result.hits.isEmpty());
        java.util.regex.Pattern wordStart =
                java.util.regex.Pattern.compile("(?i)(^|[^\\p{L}\\p{N}])ark");
        for (Bible.Hit hit : result.hits) {
            // "dark" and "market" contain the letters but do not count.
            assertTrue(hit.text, wordStart.matcher(hit.text).find());
        }
        assertTrue(search("arkness", 10).hits.isEmpty());
    }

    @Test
    public void quotedSearchFindsTheExactPhrase() throws IOException {
        // Unquoted, the two words may be anywhere in the verse.
        assertEquals(2, search("still waters", 50).hits.size());
        Bible.SearchResult phrase = search("\"still waters\"", 50);
        assertEquals(1, phrase.hits.size());
        assertEquals("Psalms 23:2", phrase.hits.get(0).reference.title());
        // A phrase may run across a line break in poetry.
        Bible.SearchResult across = search("\u201cpastures. He leads\u201d", 50);
        assertEquals(1, across.hits.size());
        assertEquals("Psalms 23:2", across.hits.get(0).reference.title());
        assertTrue(search("\"\"", 50).hits.isEmpty());
    }

    @Test
    public void searchTreatsStraightAndCurlyApostrophesAlike() throws IOException {
        Bible.SearchResult result = search("\"doesn't walk\"", 50);
        // The text itself uses the curly form: "doesn’t walk".
        assertEquals(2, result.hits.size());
        assertEquals("Psalms 1:1", result.hits.get(0).reference.title());
        assertEquals("Revelation 16:15", result.hits.get(1).reference.title());
    }

    @Test
    public void queryReportsWhereItMatched() {
        String text = "The LORD is my shepherd; the end, and other things";
        List<int[]> ranges = Bible.Query.parse("the shepherd").ranges(text);
        assertEquals(3, ranges.size());
        assertEquals(0, ranges.get(0)[0]);
        assertEquals(3, ranges.get(0)[1]);
        assertEquals("the", text.substring(ranges.get(1)[0], ranges.get(1)[1]));
        assertEquals("shepherd", text.substring(ranges.get(2)[0], ranges.get(2)[1]));
        assertTrue(Bible.Query.parse("   ").isEmpty());
        assertFalse(Bible.Query.parse("the").matches("another"));
    }

    @Test
    public void searchReportsWhenThereAreMoreResults() throws IOException {
        Bible.SearchResult limited = search("the", 10);
        assertTrue(limited.truncated);
        assertEquals(10, limited.hits.size());
        assertEquals("Genesis 1:1", limited.hits.get(0).reference.title());
        assertTrue(search("", 10).hits.isEmpty());
        assertTrue(search("zzzzqqq", 10).hits.isEmpty());
    }

    @Test
    public void searchStopsWhenCancelled() throws IOException {
        AtomicInteger checks = new AtomicInteger();
        Bible.SearchResult result = bible.search(
                Bible.Query.parse("the"), 100000, () -> checks.incrementAndGet() > 2);
        // Only the first two books were read before the search was called off.
        for (Bible.Hit hit : result.hits) {
            assertFalse(hit.reference.title(), hit.reference.book.newTestament);
        }
        assertTrue(result.hits.size() < 5000);
    }
}
