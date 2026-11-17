package com.biblereader.app;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The book menu: every book in order, grouped under Old and New Testament. */
final class BookListAdapter extends RecyclerView.Adapter<BookListAdapter.Holder> {

    interface Listener {
        void onBookChosen(Bible.Book book);
    }

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_BOOK = 1;

    /** Each row is either a section title (String) or a Bible.Book. */
    private final List<Object> rows = new ArrayList<>();
    private final Listener listener;
    private Bible.Book current;

    BookListAdapter(List<Bible.Book> books, String oldTestament, String newTestament,
            Listener listener) {
        this.listener = listener;
        Boolean section = null;
        for (Bible.Book book : books) {
            if (section == null || section != book.newTestament) {
                section = book.newTestament;
                rows.add(section ? newTestament : oldTestament);
            }
            rows.add(book);
        }
    }

    /** Highlights the book being read and returns its row, or -1. */
    int setCurrent(Bible.Book book) {
        int oldRow = rows.indexOf(current);
        int newRow = rows.indexOf(book);
        current = book;
        if (oldRow != newRow) {
            if (oldRow >= 0) {
                notifyItemChanged(oldRow);
            }
            if (newRow >= 0) {
                notifyItemChanged(newRow);
            }
        }
        return newRow;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof Bible.Book ? TYPE_BOOK : TYPE_SECTION;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_BOOK ? R.layout.item_book : R.layout.item_book_section;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Object row = rows.get(position);
        if (row instanceof Bible.Book) {
            Bible.Book book = (Bible.Book) row;
            boolean selected = book == current;
            holder.text.setText(book.name);
            holder.text.setSelected(selected);
            holder.text.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            holder.text.setOnClickListener(v -> listener.onBookChosen(book));
        } else {
            holder.text.setText((String) row);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(View view) {
            super(view);
            text = (TextView) view;
        }
    }
}
