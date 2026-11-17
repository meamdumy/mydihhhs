package com.biblereader.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** A dialog showing a book's chapter numbers as a grid to choose from. */
final class ChapterPicker {

    interface Listener {
        void onChapterChosen(int number);
    }

    private static final int COLUMNS = 5;

    private ChapterPicker() {
    }

    /**
     * @param currentNumber the chapter being read, shown as selected and
     *     scrolled into view, or 0 if the reader is in another book
     */
    static AlertDialog create(Context context, Bible.Book book, int currentNumber,
            Listener listener) {
        RecyclerView grid = (RecyclerView) LayoutInflater.from(context)
                .inflate(R.layout.dialog_chapters, null);
        grid.setLayoutManager(new GridLayoutManager(context, COLUMNS));
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(book.name)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                View cell = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_chapter_number, parent, false);
                return new RecyclerView.ViewHolder(cell) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                int number = position + 1;
                TextView cell = (TextView) holder.itemView;
                cell.setText(String.valueOf(number));
                cell.setContentDescription(
                        context.getString(R.string.chapter_number, number));
                cell.setSelected(number == currentNumber);
                cell.setOnClickListener(v -> {
                    dialog.dismiss();
                    listener.onChapterChosen(number);
                });
            }

            @Override
            public int getItemCount() {
                return book.chapterCount;
            }
        });
        if (currentNumber > 0) {
            grid.scrollToPosition(currentNumber - 1);
        }
        return dialog;
    }
}
