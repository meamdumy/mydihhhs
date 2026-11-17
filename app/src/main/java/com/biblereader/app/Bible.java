package com.biblereader.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bundled Bible text. Chapters are addressed by a single index running
 * from Genesis 1 (0) to Revelation 22, so the reader can page straight across
 * book boundaries. Books are parsed on first use and a few are kept in memory.
 *
 * <p>Safe to use from several threads. Loading and searching read from
 * storage, so call them off the UI thread.
 */
public final class Bible {

    /** Opens a bundled data file by its path relative to the assets root. */
    public interface Source {
        InputStream open(String path) throws IOException;
    }

    /** Asked during a long operation whether its result is still wanted. */
    public interface Cancellation {
        boolean isCancelled();
    }

    public static final class Book {
        public final String id;
        public final String name;
        public final boolean newTestament;
        public final int chapterCount;
        /** Global index of this book's first chapter. */
        public final int firstChapter;

        Book(String id, String name, boolean newTestament, int chapterCount, int firstChapter) {
            this.id = id;
            this.name = name;
            this.newTestament = newTestament;
            this.chapterCount = chapterCount;
            this.firstChapter = firstChapter;
        }

        /** Title of a chapter, such as "John 3", "Psalm 23" or "Jude". */
        public String chapterTitle(int number) {
            if (chapterCount == 1) {
                return name;
            }
            return ("psalms".equals(id) ? "Psalm" : name) + " " + number;
        }

        /** Citation for a verse, such as "John 3:16" or "Jude 5". */
        public String verseTitle(int chapter, int verse) {
            return chapterCount == 1 ? name + " " + verse : name + " " + chapter + ":" + verse;
        }
    }

    /** A heading or a verse within a chapter. */
    public static final class Item {
        /** Verse number, or 0 for a heading. */
        public final int verse;
        /** True when the verse opens a paragraph or stanza. */
        public final boolean startsParagraph;
        public final String text;

        Item(int verse, boolean startsParagraph, String text) {
            this.verse = verse;
            this.startsParagraph = startsParagraph;
            this.text = text;
        }

        public boolean isHeading() {
            return verse == 0;
        }
    }

    public static final class Chapter {
        public final Book book;
        public final int number;
        public final List<Item> items;

        Chapter(Book book, int number, List<Item> items) {
            this.book = book;
            this.number = number;
            this.items = items;
        }

        public String title() {
            return book.chapterTitle(number);
        }

        /**
         * Index in {@link #items} of the given verse. A verse the translation
         * omits resolves to the verse before it; returns -1 if there is none.
         */
        public int indexOfVerse(int verse) {
            int found = -1;
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (!item.isHeading() && item.verse <= verse) {
                    found = i;
                }
            }
            return found;
        }
    }

    /** A place in the text: a chapter and, optionally, a verse within it. */
    public static final class Reference {
        public final Book book;
        public final int chapter;
        /** Verse number, or 0 for the chapter as a whole. */
        public final int verse;

        Reference(Book book, int chapter, int verse) {
            this.book = book;
            this.chapter = chapter;
            this.verse = verse;
        }

        public String title() {
            return verse == 0 ? book.chapterTitle(chapter) : book.verseTitle(chapter, verse);
        }
    }

    /** A verse matching a search. */
    public static final class Hit {
        public final Reference reference;
        public final String text;

        Hit(Reference reference, String text) {
            this.reference = reference;
            this.text = text;
        }
    }

    /**
     * A parsed search. Normally every word must begin a word of the verse, so
     * "love" finds "loved" but "the" does not find "other". Wrapping the query
     * in double quotes finds that exact phrase instead. Case is ignored, and
     * straight and curly quotation marks are treated alike.
     */
    public static final class Query {
        private final List<String> terms;

        private Query(List<String> terms) {
            this.terms = terms;
        }

        public static Query parse(String text) {
            String folded = fold(text).trim();
            List<String> terms = new ArrayList<>();
            if (folded.length() >= 2 && folded.startsWith("\"") && folded.endsWith("\"")) {
                String phrase = folded.substring(1, folded.length() - 1)
                        .trim().replaceAll("\\s+", " ");
                if (!phrase.isEmpty()) {
                    terms.add(phrase);
                }
            } else {
                for (String word : folded.replace('"', ' ').split("\\s+")) {
                    if (!word.isEmpty()) {
                        terms.add(word);
                    }
                }
            }
            return new Query(terms);
        }

        public boolean isEmpty() {
            return terms.isEmpty();
        }

        /** Whether the text contains every term. Line breaks should be spaces. */
        public boolean matches(String text) {
            for (String term : terms) {
                if (indexOf(text, term, 0) < 0) {
                    return false;
                }
            }
            return !terms.isEmpty();
        }

        /** The {start, end} positions of every occurrence of every term. */
        public List<int[]> ranges(String text) {
            List<int[]> ranges = new ArrayList<>();
            for (String term : terms) {
                int at = indexOf(text, term, 0);
                while (at >= 0) {
                    ranges.add(new int[] {at, at + term.length()});
                    at = indexOf(text, term, at + term.length());
                }
            }
            return ranges;
        }

        /** Finds the term at the start of a word, at or after {@code from}. */
        private static int indexOf(String text, String term, int from) {
            int last = text.length() - term.length();
            char first = term.charAt(0);
            for (int i = from; i <= last; i++) {
                if (fold(text.charAt(i)) != first
                        || (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1)))) {
                    continue;
                }
                int k = 1;
                while (k < term.length() && fold(text.charAt(i + k)) == term.charAt(k)) {
                    k++;
                }
                if (k == term.length()) {
                    return i;
                }
            }
            return -1;
        }

        private static String fold(String text) {
            StringBuilder out = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                out.append(fold(text.charAt(i)));
            }
            return out.toString();
        }

        /** Lower-cases a character and straightens curly quotation marks. */
        private static char fold(char c) {
            switch (c) {
                case '\u2018':
                case '\u2019':
                    return '\'';
                case '\u201c':
                case '\u201d':
                    return '"';
                default:
                    return Character.toLowerCase(c);
            }
        }
    }

    public static final class SearchResult {
        public final List<Hit> hits;
        /** True when more verses matched than were returned. */
        public final boolean truncated;

        SearchResult(List<Hit> hits, boolean truncated) {
            this.hits = hits;
            this.truncated = truncated;
        }
    }

    private static final String DIR = "bible/";
    private static final int CACHED_BOOKS = 4;

    /**
     * Book, then optional chapter, then optional verse: "1 John 2:3", "ps 23",
     * "jude 5". A range such as "John 3:16-18" is read as its first verse.
     */
    private static final Pattern REFERENCE = Pattern.compile(
            "^\\s*((?:[1-3]\\s*)?[a-z][a-z .]*?)\\.?\\s*"
                    + "(?:(\\d{1,3})(?:\\s*[:.,]\\s*(\\d{1,3}))?(?:\\s*[-\u2013]\\s*\\d{1,3})?)?\\s*$");

    /** Common abbreviations that are not simply the start of the book's name. */
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        String[][] aliases = {
                {"gn", "genesis"}, {"dt", "deuteronomy"}, {"jdg", "judges"}, {"jgs", "judges"},
                {"psalm", "psalms"}, {"pss", "psalms"}, {"prv", "proverbs"},
                {"songofsongs", "songofsolomon"}, {"sos", "songofsolomon"},
                {"canticles", "songofsolomon"}, {"ezk", "ezekiel"}, {"dn", "daniel"},
                {"jl", "joel"}, {"ob", "obadiah"}, {"jon", "jonah"}, {"nah", "nahum"},
                {"hg", "haggai"}, {"zec", "zechariah"}, {"mt", "matthew"}, {"mk", "mark"},
                {"mrk", "mark"}, {"lk", "luke"}, {"jn", "john"}, {"jhn", "john"},
                {"rm", "romans"}, {"php", "philippians"}, {"phm", "philemon"},
                {"phlm", "philemon"}, {"jas", "james"}, {"jm", "james"}, {"rv", "revelation"},
                {"1sm", "1samuel"}, {"2sm", "2samuel"}, {"1kgs", "1kings"}, {"2kgs", "2kings"},
                {"1jn", "1john"}, {"2jn", "2john"}, {"3jn", "3john"},
                {"1pt", "1peter"}, {"2pt", "2peter"}, {"1th", "1thessalonians"},
                {"2th", "2thessalonians"}, {"1tm", "1timothy"}, {"2tm", "2timothy"},
        };
        for (String[] alias : aliases) {
            ALIASES.put(alias[0], alias[1]);
        }
    }

    private final Source source;
    private final List<Book> books;
    private final int chapterCount;
    /** Parsed books, least recently used first. Guarded by itself. */
    private final Map<String, List<List<Item>>> cache =
            new LinkedHashMap<String, List<List<Item>>>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<List<Item>>> eldest) {
                    return size() > CACHED_BOOKS;
                }
            };

    public Bible(Source source) throws IOException {
        this.source = source;
        List<Book> list = new ArrayList<>();
        int next = 0;
        try (BufferedReader in = reader("books.tsv")) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] f = line.split("\t");
                int chapters = Integer.parseInt(f[3]);
                list.add(new Book(f[0], f[1], "NT".equals(f[2]), chapters, next));
                next += chapters;
            }
        }
        books = Collections.unmodifiableList(list);
        chapterCount = next;
    }

    public List<Book> books() {
        return books;
    }

    /** Total number of chapters across all books. */
    public int chapterCount() {
        return chapterCount;
    }

    /** The book containing the chapter at the given global index. */
    public Book bookAt(int chapterIndex) {
        int lo = 0;
        int hi = books.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (books.get(mid).firstChapter <= chapterIndex) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return books.get(lo);
    }

    /** Global index of a chapter, given its book and 1-based chapter number. */
    public int indexOf(Book book, int chapterNumber) {
        return book.firstChapter + chapterNumber - 1;
    }

    /** Short title such as "John 3" for the chapter at the given global index. */
    public String titleAt(int chapterIndex) {
        Book book = bookAt(chapterIndex);
        return book.chapterTitle(chapterIndex - book.firstChapter + 1);
    }

    /** The chapter at the given index, reading its book from storage if needed. */
    public Chapter chapterAt(int chapterIndex) throws IOException {
        Book book = bookAt(chapterIndex);
        List<List<Item>> chapters;
        synchronized (cache) {
            chapters = cache.get(book.id);
        }
        if (chapters == null) {
            // Parsed outside the lock so the UI thread never waits on storage.
            chapters = load(book);
            synchronized (cache) {
                cache.put(book.id, chapters);
            }
        }
        return chapter(book, chapterIndex, chapters);
    }

    /** The chapter at the given index if its book is in memory, otherwise null. */
    public Chapter chapterIfLoaded(int chapterIndex) {
        Book book = bookAt(chapterIndex);
        List<List<Item>> chapters;
        synchronized (cache) {
            chapters = cache.get(book.id);
        }
        return chapters == null ? null : chapter(book, chapterIndex, chapters);
    }

    private static Chapter chapter(Book book, int chapterIndex, List<List<Item>> chapters) {
        int number = chapterIndex - book.firstChapter + 1;
        return new Chapter(book, number, chapters.get(number - 1));
    }

    /**
     * Reads a reference such as "John 3:16", "1 cor 13", "ps 23" or "Jude 5".
     * Returns null if the text is not a reference to a chapter that exists.
     * A book on its own must be named in full, so that ordinary words such as
     * "is" or "judge" are not taken for Isaiah or Judges; followed by a
     * chapter number, the start of the name is enough.
     */
    public Reference parseReference(String text) {
        Matcher m = REFERENCE.matcher(text.toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return null;
        }
        Book book = findBook(m.group(1), m.group(2) != null);
        if (book == null) {
            return null;
        }
        int first = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        int second = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        int chapter;
        int verse;
        if (book.chapterCount == 1 && second == 0) {
            // "Jude 5" means verse 5 of the only chapter.
            chapter = 1;
            verse = first;
        } else {
            chapter = first == 0 ? 1 : first;
            verse = second;
        }
        if (chapter < 1 || chapter > book.chapterCount) {
            return null;
        }
        return new Reference(book, chapter, verse);
    }

    /** Finds a book by its name, an abbreviation or, if allowed, the start of its name. */
    private Book findBook(String name, boolean allowPrefix) {
        String key = name.replaceAll("[ .]", "");
        if (key.isEmpty()) {
            return null;
        }
        String alias = ALIASES.get(key);
        if (alias != null) {
            key = alias;
        }
        Book prefixMatch = null;
        for (Book book : books) {
            if (book.id.equals(key)) {
                return book;
            }
            if (allowPrefix && prefixMatch == null && key.length() >= 2
                    && book.id.startsWith(key)) {
                prefixMatch = book;
            }
        }
        return prefixMatch;
    }

    /**
     * Points a reference at a verse the text actually has: a verse past the
     * end of its chapter becomes the last one, and a verse this translation
     * omits becomes the one before it. Reads the book from storage if needed.
     */
    public Reference resolve(Reference reference) throws IOException {
        if (reference.verse == 0) {
            return reference;
        }
        Chapter chapter = chapterAt(indexOf(reference.book, reference.chapter));
        int index = chapter.indexOfVerse(reference.verse);
        int verse = index < 0 ? 0 : chapter.items.get(index).verse;
        return verse == reference.verse
                ? reference : new Reference(reference.book, reference.chapter, verse);
    }

    /**
     * Finds the verses matching the query, in Bible order. Stops early,
     * returning what it has, once {@code cancelled} reports true.
     */
    public SearchResult search(Query query, int limit, Cancellation cancelled)
            throws IOException {
        List<Hit> hits = new ArrayList<>();
        if (query.isEmpty()) {
            return new SearchResult(hits, false);
        }
        for (Book book : books) {
            if (cancelled.isCancelled()) {
                break;
            }
            try (BufferedReader in = reader(book.id + ".txt")) {
                int chapter = 0;
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    char tag = line.charAt(0);
                    if (tag == 'C') {
                        chapter++;
                    } else if (tag == 'V') {
                        int textStart = line.indexOf('\t', line.indexOf('\t') + 1) + 1;
                        // Poetry line breaks become spaces so phrases can span them.
                        String text = line.substring(textStart).replace("\\n", " ");
                        if (query.matches(text)) {
                            if (hits.size() == limit) {
                                return new SearchResult(hits, true);
                            }
                            int verse = parseVerse(book, line).verse;
                            hits.add(new Hit(new Reference(book, chapter, verse), text));
                        }
                    }
                }
            }
        }
        return new SearchResult(hits, false);
    }

    private List<List<Item>> load(Book book) throws IOException {
        List<List<Item>> chapters = new ArrayList<>(book.chapterCount);
        List<Item> current = null;
        try (BufferedReader in = reader(book.id + ".txt")) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                char tag = line.charAt(0);
                if (tag == 'C') {
                    current = new ArrayList<>();
                    chapters.add(current);
                } else if (current == null) {
                    throw new IOException("Text before the first chapter in " + book.id);
                } else if (tag == 'H') {
                    current.add(new Item(0, true, line.substring(1)));
                } else if (tag == 'V') {
                    current.add(parseVerse(book, line));
                }
            }
        }
        if (chapters.size() != book.chapterCount) {
            throw new IOException(book.id + " has " + chapters.size()
                    + " chapters, expected " + book.chapterCount);
        }
        return chapters;
    }

    /** Parses a verse record: {@code V<number>\t<p|->\t<text>}. */
    private static Item parseVerse(Book book, String line) throws IOException {
        int a = line.indexOf('\t');
        int b = line.indexOf('\t', a + 1);
        if (a < 0 || b < 0) {
            throw new IOException("Malformed verse in " + book.id + ": " + line);
        }
        try {
            return new Item(
                    Integer.parseInt(line.substring(1, a)),
                    line.charAt(a + 1) == 'p',
                    line.substring(b + 1).replace("\\n", "\n"));
        } catch (NumberFormatException e) {
            throw new IOException("Malformed verse in " + book.id + ": " + line, e);
        }
    }

    private BufferedReader reader(String file) throws IOException {
        return new BufferedReader(
                new InputStreamReader(source.open(DIR + file), StandardCharsets.UTF_8));
    }
}
