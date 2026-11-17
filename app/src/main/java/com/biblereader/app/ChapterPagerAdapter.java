package com.biblereader.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** One swipeable page per chapter, across the whole Bible. */
final class ChapterPagerAdapter extends RecyclerView.Adapter<ChapterPagerAdapter.Page> {

    private final Bible bible;
    private final Set<Page> boundPages = new HashSet<>();
    private float textSizeSp;
    private int restorePage = RecyclerView.NO_POSITION;
    private int restoreItem;

    ChapterPagerAdapter(Bible bible, float textSizeSp) {
        this.bible = bible;
        this.textSizeSp = textSizeSp;
    }

    /** Opens the given chapter scrolled to the given row the next time it is shown. */
    void restoreScroll(int page, int item) {
        restorePage = page;
        restoreItem = item;
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
                .inflate(R.layout.page_chapter, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Page page, int position) {
        boundPages.add(page);
        try {
            page.verses.setChapter(bible.chapterAt(position), textSizeSp);
            page.error.setVisibility(View.GONE);
            page.list.setVisibility(View.VISIBLE);
        } catch (IOException e) {
            page.verses.setChapter(null, textSizeSp);
            page.list.setVisibility(View.GONE);
            page.error.setVisibility(View.VISIBLE);
            page.error.setText(page.error.getContext()
                    .getString(R.string.chapter_load_failed, bible.titleAt(position)));
            return;
        }
        int item = 0;
        if (position == restorePage) {
            item = restoreItem;
            restorePage = RecyclerView.NO_POSITION;
        }
        page.layout.scrollToPositionWithOffset(item, 0);
    }

    @Override
    public void onViewRecycled(@NonNull Page page) {
        boundPages.remove(page);
    }

    static final class Page extends RecyclerView.ViewHolder {
        final RecyclerView list;
        final TextView error;
        final LinearLayoutManager layout;
        final VerseAdapter verses = new VerseAdapter();

        Page(View view) {
            super(view);
            list = view.findViewById(R.id.verses);
            error = view.findViewById(R.id.error);
            layout = new LinearLayoutManager(view.getContext());
            list.setLayoutManager(layout);
            list.setAdapter(verses);
        }

        /** Index of the topmost visible row, for saving the reading position. */
        int firstVisibleItem() {
            return Math.max(0, layout.findFirstVisibleItemPosition());
        }
    }
}
