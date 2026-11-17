package com.biblereader.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

/** The scrolling contents of one chapter: its title, headings and verses. */
final class VerseAdapter extends RecyclerView.Adapter<VerseAdapter.Holder> {

    private static final int TYPE_TITLE = 0;
    private static final int TYPE_HEADING = 1;
    private static final int TYPE_VERSE = 2;

    private Bible.Chapter chapter;
    private float textSizeSp;

    void setChapter(Bible.Chapter chapter, float textSizeSp) {
        this.chapter = chapter;
        this.textSizeSp = textSizeSp;
        notifyDataSetChanged();
    }

    void setTextSize(float textSizeSp) {
        if (this.textSizeSp != textSizeSp) {
            this.textSizeSp = textSizeSp;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return chapter == null ? 0 : chapter.items.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_TITLE;
        }
        return chapter.items.get(position - 1).isHeading() ? TYPE_HEADING : TYPE_VERSE;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_TITLE ? R.layout.item_chapter_title
                : viewType == TYPE_HEADING ? R.layout.item_heading
                : R.layout.item_verse;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TextView text = holder.text;
        Context context = text.getContext();
        switch (getItemViewType(position)) {
            case TYPE_TITLE:
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp * 1.45f);
                text.setText(chapter.title());
                break;
            case TYPE_HEADING:
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp * 0.9f);
                text.setText(chapter.items.get(position - 1).text);
                break;
            default:
                Bible.Item item = chapter.items.get(position - 1);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                text.setText(verseText(context, item));
                int top = item.startsParagraph && position > 1
                        ? context.getResources().getDimensionPixelSize(R.dimen.paragraph_gap)
                        : 0;
                text.setPadding(text.getPaddingLeft(), top,
                        text.getPaddingRight(), text.getPaddingBottom());
                break;
        }
    }

    /** The verse number as a small raised label, followed by the verse text. */
    private static CharSequence verseText(Context context, Bible.Item item) {
        String number = String.valueOf(item.verse);
        SpannableStringBuilder out = new SpannableStringBuilder(number);
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        int accent = MaterialColors.getColor(context,
                androidx.appcompat.R.attr.colorPrimary, 0xFF7A4B1E);
        out.setSpan(new SuperscriptSpan(), 0, number.length(), flags);
        out.setSpan(new RelativeSizeSpan(0.65f), 0, number.length(), flags);
        out.setSpan(new StyleSpan(Typeface.BOLD), 0, number.length(), flags);
        out.setSpan(new ForegroundColorSpan(accent), 0, number.length(), flags);
        out.append(' ').append(item.text);
        return out;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(View view) {
            super(view);
            text = (TextView) view;
        }
    }
}
