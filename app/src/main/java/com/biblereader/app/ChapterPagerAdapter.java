package com.biblereader.app;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * One swipeable page per chapter, across the whole Bible. A chapter whose
 * book is not yet in memory is read on the loader thread and shown when ready.
 */
final class ChapterPagerAdapter extends RecyclerView.Adapter<ChapterPagerAdapter.Page> {

    private final Bible bible;
    private final Executor loader;
    private final VerseAdapter.Listener verseListener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Page> boundPages = new HashSet<>();
    private float textSizeSp;

    /** A scroll position waiting for its page to be shown. */
    private int pendingPage = RecyclerView.NO_POSITION;
    private int pendingRow;
    private int pendingVerse;

    private int highlightPage = RecyclerView.NO_POSITION;
    private int highlightVerse;

    ChapterPagerAdapter(Bible bible, Executor loader, float textSizeSp,
            VerseAdapter.Listener verseListener) {
        this.bible = bible;
        this.loader = loader;
        this.textSizeSp = textSizeSp;
        this.verseListener = verseListener;
    }

    /** Opens the given chapter scrolled to the given row the next time it is shown. */
    void scrollToRow(int page, int row) {
        pendingPage = page;
        pendingRow = row;
        pendingVerse = 0;
        applyPendingScroll();
    }

    /** Scrolls the given chapter to a verse and highlights it. */
    void showVerse(int page, int verse) {
        pendingPage = page;
        pendingRow = 0;
        pendingVerse = verse;
        highlightPage = page;
        highlightVerse = verse;
        for (Page bound : boundPages) {
            bound.verses.setHighlightedVerse(
                    bound.getBindingAdapterPosition() == page ? verse : 0);
        }
        applyPendingScroll();
    }

    /** Drops the highlight once the reader moves on to another chapter. */
    void onPageSelected(int page) {
        if (highlightPage != RecyclerView.NO_POSITION && page != highlightPage) {
            clearHighlight();
        }
    }

    /** Removes the highlight left by {@link #showVerse}. */
    void clearHighlight() {
        highlightPage = RecyclerView.NO_POSITION;
        for (Page bound : boundPages) {
            bound.verses.setHighlightedVerse(0);
        }
    }

    void setTextSize(float textSizeSp) {
        this.textSizeSp = textSizeSp;
        for (Page page : boundPages) {
            page.verses.setTextSize(textSizeSp);
        }
    }

    @Override
    public int getItemCount() {
        return bible.chapterCount();
    }

    @NonNull
    @Override
    public Page onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Page(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.page_chapter, parent, false), verseListener);
    }

    @Override
    public void onBindViewHolder(@NonNull Page page, int position) {
        boundPages.add(page);
        Bible.Chapter loaded = bible.chapterIfLoaded(position);
        if (loaded != null) {
            display(page, position, loaded);
            return;
        }
        page.showLoading();
        loader.execute(() -> {
            Bible.Chapter chapter;
            try {
                chapter = bible.chapterAt(position);
            } catch (IOException e) {
                chapter = null;
            }
            Bible.Chapter result = chapter;
            main.post(() -> {
                // The page may have been recycled for another chapter meanwhile.
                if (!boundPages.contains(page) || page.getBindingAdapterPosition() != position) {
                    return;
                }
                if (result == null) {
                    page.showError(bible.titleAt(position));
                } else {
                    display(page, position, result);
                }
            });
        });
    }

    @Override
    public void onViewRecycled(@NonNull Page page) {
        boundPages.remove(page);
        page.chapter = null;
    }

    private void display(Page page, int position, Bible.Chapter chapter) {
        page.showChapter(chapter, textSizeSp, position == highlightPage ? highlightVerse : 0);
        if (position == pendingPage) {
            scrollToPending(page);
        } else {
            page.layout.scrollToPositionWithOffset(0, 0);
        }
    }

    /** Applies the pending scroll now if its page is already showing its chapter. */
    private void applyPendingScroll() {
        for (Page page : boundPages) {
            if (page.chapter != null && page.getBindingAdapterPosition() == pendingPage) {
                scrollToPending(page);
                return;
            }
        }
    }

    private void scrollToPending(Page page) {
        int row = pendingVerse > 0
                ? Math.max(0, page.verses.rowOfVerse(pendingVerse))
                : Math.min(pendingRow, page.verses.getItemCount() - 1);
        pendingPage = RecyclerView.NO_POSITION;
        page.layout.scrollToPositionWithOffset(Math.max(0, row), 0);
    }

    static final class Page extends RecyclerView.ViewHolder {
        final RecyclerView list;
        final TextView error;
        final CircularProgressIndicator progress;
        final LinearLayoutManager layout;
        final VerseAdapter verses;
        /** The chapter on show, or null while loading or after a failure. */
        Bible.Chapter chapter;

        Page(View view, VerseAdapter.Listener verseListener) {
            super(view);
            list = view.findViewById(R.id.verses);
            error = view.findViewById(R.id.error);
            progress = view.findViewById(R.id.progress);
            layout = new LinearLayoutManager(view.getContext());
            verses = new VerseAdapter(verseListener);
            list.setLayoutManager(layout);
            list.setAdapter(verses);
            // Rows change all at once (text size, highlight); fading each is distracting.
            list.setItemAnimator(null);
            ReadingWidth.apply(list);
        }

        void showLoading() {
            chapter = null;
            verses.setChapter(null, 0, 0);
            list.setVisibility(View.INVISIBLE);
            error.setVisibility(View.GONE);
            // Appears only if loading takes long enough to notice.
            progress.show();
        }

        void showChapter(Bible.Chapter chapter, float textSizeSp, int highlightedVerse) {
            this.chapter = chapter;
            verses.setChapter(chapter, textSizeSp, highlightedVerse);
            progress.hide();
            error.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
        }

        void showError(String title) {
            chapter = null;
            progress.hide();
            list.setVisibility(View.INVISIBLE);
            error.setText(error.getContext().getString(R.string.chapter_load_failed, title));
            error.setVisibility(View.VISIBLE);
        }

        /**
         * Row at the top of the screen, for saving the reading position, or
         * -1 if the chapter has not been loaded and laid out yet.
         */
        int firstVisibleRow() {
            return chapter == null ? -1 : layout.findFirstVisibleItemPosition();
        }
    }
}
