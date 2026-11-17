package com.biblereader.app;

import android.content.Intent;

/**
 * The result of a screen that lets the reader choose somewhere to go, such
 * as search or bookmarks: a chapter and, optionally, a verse within it.
 */
final class PlaceResult {

    private static final String EXTRA_CHAPTER_INDEX = "chapterIndex";
    private static final String EXTRA_VERSE = "verse";

    private PlaceResult() {
    }

    /**
     * @param chapterIndex global index of the chapter
     * @param verse verse number, or 0 for the top of the chapter
     */
    static Intent of(int chapterIndex, int verse) {
        return new Intent()
                .putExtra(EXTRA_CHAPTER_INDEX, chapterIndex)
                .putExtra(EXTRA_VERSE, verse);
    }

    /** Global index of the chosen chapter, or -1 if the result carries none. */
    static int chapterIndex(Intent result) {
        return result.getIntExtra(EXTRA_CHAPTER_INDEX, -1);
    }

    /** The chosen verse number, or 0 for the top of the chapter. */
    static int verse(Intent result) {
        return result.getIntExtra(EXTRA_VERSE, 0);
    }
}
