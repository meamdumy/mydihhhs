package com.biblereader.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bundled Bible text. Chapters are addressed by a single index running
 * from Genesis 1 (0) to Revelation 22, so the reader can page straight across
 * book boundaries. Books are parsed on first use and a few are kept in memory.
 */
public final class Bible {

    /** Opens a bundled data file by its path relative to the assets root. */
    public interface Source {
        InputStream open(String path) throws IOException;
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
            return book.chapterCount == 1 ? book.name : book.name + " " + number;
        }
    }

    private static final String DIR = "bible/";
    private static final int CACHED_BOOKS = 4;

    private final Source source;
    private final List<Book> books;
    private final int chapterCount;
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
        int number = chapterIndex - book.firstChapter + 1;
        return book.chapterCount == 1 ? book.name : book.name + " " + number;
    }

    public synchronized Chapter chapterAt(int chapterIndex) throws IOException {
        Book book = bookAt(chapterIndex);
        List<List<Item>> chapters = cache.get(book.id);
        if (chapters == null) {
            chapters = load(book);
            cache.put(book.id, chapters);
        }
        int number = chapterIndex - book.firstChapter + 1;
        return new Chapter(book, number, chapters.get(number - 1));
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
                    int a = line.indexOf('\t');
                    int b = line.indexOf('\t', a + 1);
                    if (a < 0 || b < 0) {
                        throw new IOException("Malformed verse in " + book.id + ": " + line);
                    }
                    current.add(new Item(
                            Integer.parseInt(line.substring(1, a)),
                            line.charAt(a + 1) == 'p',
                            line.substring(b + 1).replace("\\n", "\n")));
                }
            }
        }
        if (chapters.size() != book.chapterCount) {
            throw new IOException(book.id + " has " + chapters.size()
                    + " chapters, expected " + book.chapterCount);
        }
        return chapters;
    }

    private BufferedReader reader(String file) throws IOException {
        return new BufferedReader(
                new InputStreamReader(source.open(DIR + file), StandardCharsets.UTF_8));
    }
}
