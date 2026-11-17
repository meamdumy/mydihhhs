package com.biblereader.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The verses the reader has bookmarked, newest first. Each is stored by book,
 * chapter and verse, so bookmarks stay valid if the bundled text is replaced.
 */
final class Bookmarks {

    static final class Entry {
        final String bookId;
        final int chapter;
        final int verse;

        Entry(String bookId, int chapter, int verse) {
            this.bookId = bookId;
            this.chapter = chapter;
            this.verse = verse;
        }

        boolean is(String bookId, int chapter, int verse) {
            return this.chapter == chapter && this.verse == verse && this.bookId.equals(bookId);
        }

        private String encode() {
            return bookId + ":" + chapter + ":" + verse;
        }

        /** Returns null for text that is not a stored bookmark. */
        private static Entry decode(String text) {
            String[] parts = text.split(":");
            if (parts.length != 3 || parts[0].isEmpty()) {
                return null;
            }
            try {
                int chapter = Integer.parseInt(parts[1]);
                int verse = Integer.parseInt(parts[2]);
                return chapter > 0 && verse > 0 ? new Entry(parts[0], chapter, verse) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static final String FILE = "bookmarks";
    private static final String KEY = "verses";

    private final SharedPreferences prefs;

    Bookmarks(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Every bookmark, newest first. */
    List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        for (String line : prefs.getString(KEY, "").split("\n")) {
            Entry entry = Entry.decode(line);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** Replaces the whole list, for example to undo a removal. */
    void set(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(entry.encode());
        }
        prefs.edit().putString(KEY, out.toString()).apply();
    }

    boolean contains(String bookId, int chapter, int verse) {
        for (Entry entry : all()) {
            if (entry.is(bookId, chapter, verse)) {
                return true;
            }
        }
        return false;
    }

    /** Adds the bookmark if it is absent, otherwise removes it. Returns whether it is now set. */
    boolean toggle(String bookId, int chapter, int verse) {
        List<Entry> entries = all();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).is(bookId, chapter, verse)) {
                entries.remove(i);
                set(entries);
                return false;
            }
        }
        entries.add(0, new Entry(bookId, chapter, verse));
        set(entries);
        return true;
    }

    /** The bookmarked verse numbers within one chapter. */
    Set<Integer> versesIn(String bookId, int chapter) {
        Set<Integer> verses = new HashSet<>();
        for (Entry entry : all()) {
            if (entry.chapter == chapter && entry.bookId.equals(bookId)) {
                verses.add(entry.verse);
            }
        }
        return verses;
    }
}
