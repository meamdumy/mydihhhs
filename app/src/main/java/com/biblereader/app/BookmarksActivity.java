package com.biblereader.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lists the bookmarked verses, newest first. Tapping one finishes with that
 * place as a {@link PlaceResult}; each can be removed.
 */
public class BookmarksActivity extends AppCompatActivity {

    /** A bookmark and what is needed to show it. */
    private static final class Row {
        final Bookmarks.Entry entry;
        final Bible.Book book;
        /** The verse on one line; empty until it has been read from storage. */
        String text = "";

        Row(Bookmarks.Entry entry, Bible.Book book) {
            this.entry = entry;
            this.book = book;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RowAdapter rows = new RowAdapter();

    private Bible bible;
    private Bookmarks bookmarks;
    private RecyclerView list;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        SystemBars.pad(findViewById(R.id.content), false);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.bookmarks);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(rows);
        ReadingWidth.apply(list);

        try {
            bible = BibleProvider.get(this);
        } catch (IOException e) {
            finish();
            return;
        }
        bookmarks = new Bookmarks(this);
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    /** Lists the bookmarks at once, then fills in each verse's text as it is read. */
    private void load() {
        List<Row> listed = new ArrayList<>();
        for (Bookmarks.Entry entry : bookmarks.all()) {
            Bible.Book book = bible.bookById(entry.bookId);
            if (book != null && entry.chapter <= book.chapterCount) {
                listed.add(new Row(entry, book));
            }
            // Anything else is left over from a different text and cannot be opened.
        }
        rows.show(listed);
        showEmptyMessageIfNeeded();
        worker.execute(() -> {
            for (Row row : listed) {
                String text;
                try {
                    text = bible.verseText(row.book, row.entry.chapter, row.entry.verse);
                } catch (IOException e) {
                    // Listed without its text; opening it reports the problem.
                    text = null;
                }
                if (text != null) {
                    String read = text;
                    main.post(() -> rows.showText(row, read));
                }
            }
        });
    }

    private void showEmptyMessageIfNeeded() {
        boolean none = rows.getItemCount() == 0;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none ? View.GONE : View.VISIBLE);
    }

    private void open(Row row) {
        setResult(RESULT_OK, PlaceResult.of(
                bible.indexOf(row.book, row.entry.chapter), row.entry.verse));
        finish();
    }

    /** Deletes the bookmark, offering to undo it. */
    private void removeBookmark(Row row) {
        List<Bookmarks.Entry> before = bookmarks.all();
        List<Bookmarks.Entry> after = new ArrayList<>();
        for (Bookmarks.Entry entry : before) {
            if (!entry.is(row.entry.bookId, row.entry.chapter, row.entry.verse)) {
                after.add(entry);
            }
        }
        bookmarks.set(after);
        rows.removeRow(row);
        showEmptyMessageIfNeeded();
        Snackbar.make(findViewById(R.id.content), R.string.bookmark_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    bookmarks.set(before);
                    load();
                })
                .show();
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

        private final List<Row> shown = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged") // The whole list is replaced.
        void show(List<Row> loaded) {
            shown.clear();
            shown.addAll(loaded);
            notifyDataSetChanged();
        }

        /** Fills in a row's text, unless the row has since been replaced or removed. */
        void showText(Row row, String text) {
            int index = shown.indexOf(row);
            if (index >= 0) {
                row.text = text;
                notifyItemChanged(index);
            }
        }

        void removeRow(Row row) {
            int index = shown.indexOf(row);
            if (index >= 0) {
                shown.remove(index);
                notifyItemRemoved(index);
            }
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bookmark, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = shown.get(position);
            String title = row.book.verseTitle(row.entry.chapter, row.entry.verse);
            holder.title.setText(title);
            holder.text.setText(row.text);
            holder.text.setVisibility(row.text.isEmpty() ? View.GONE : View.VISIBLE);
            holder.itemView.setOnClickListener(v -> open(row));
            holder.remove.setContentDescription(getString(R.string.remove_bookmark_for, title));
            holder.remove.setOnClickListener(v -> removeBookmark(row));
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView text;
            final View remove;

            Holder(View view) {
                super(view);
                title = view.findViewById(R.id.result_title);
                text = view.findViewById(R.id.result_text);
                remove = view.findViewById(R.id.remove);
            }
        }
    }
}
