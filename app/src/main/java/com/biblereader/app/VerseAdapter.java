package com.biblereader.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

/** The scrolling contents of one chapter: its title, headings and verses. */
final class VerseAdapter extends RecyclerView.Adapter<VerseAdapter.Holder> {

    interface Listener {
        void onVerseLongPressed(View anchor, Bible.Chapter chapter, Bible.Item verse);
    }

    private static final int TYPE_TITLE = 0;
    private static final int TYPE_HEADING = 1;
    private static final int TYPE_VERSE = 2;
    /** Opacity, out of 255, of the tint behind a highlighted verse. */
    private static final int HIGHLIGHT_ALPHA = 46;

    private final Listener listener;
    private Bible.Chapter chapter;
    private float textSizeSp;
    private int highlightedVerse;

    VerseAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Shows a chapter, or nothing if null. Row 0 is the title; items follow. */
    @SuppressLint("NotifyDataSetChanged") // The whole chapter is replaced.
    void setChapter(Bible.Chapter chapter, float textSizeSp, int highlightedVerse) {
        this.chapter = chapter;
        this.textSizeSp = textSizeSp;
        this.highlightedVerse = highlightedVerse;
        notifyDataSetChanged();
    }

    void setTextSize(float textSizeSp) {
        if (this.textSizeSp != textSizeSp) {
            this.textSizeSp = textSizeSp;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    /** Tints the given verse, or none if 0. */
    void setHighlightedVerse(int verse) {
        if (highlightedVerse == verse) {
            return;
        }
        int before = rowOfVerse(highlightedVerse);
        highlightedVerse = verse;
        int after = rowOfVerse(verse);
        if (before >= 0) {
            notifyItemChanged(before);
        }
        if (after >= 0) {
            notifyItemChanged(after);
        }
    }

    /** Adapter row showing the given verse, or -1. */
    int rowOfVerse(int verse) {
        if (chapter == null || verse <= 0) {
            return -1;
        }
        int index = chapter.indexOfVerse(verse);
        return index < 0 ? -1 : index + 1;
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
                Bible.Chapter shown = chapter;
                Bible.Item item = shown.items.get(position - 1);
                int accent = MaterialColors.getColor(context,
                        androidx.appcompat.R.attr.colorPrimary, 0xFF7A4B1E);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                text.setText(verseText(item, accent, Math.round(text.getTextSize() * 1.2f)));
                int gap = item.startsParagraph && position > 1
                        ? context.getResources().getDimensionPixelSize(R.dimen.paragraph_gap)
                        : 0;
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) text.getLayoutParams();
                if (params.topMargin != gap) {
                    params.topMargin = gap;
                    text.setLayoutParams(params);
                }
                text.setBackgroundColor(item.verse == highlightedVerse
                        ? ColorUtils.setAlphaComponent(accent, HIGHLIGHT_ALPHA)
                        : Color.TRANSPARENT);
                text.setOnLongClickListener(v -> {
                    listener.onVerseLongPressed(v, shown, item);
                    return true;
                });
                break;
        }
    }

    /**
     * The verse number as a small raised label, followed by the verse text.
     * In poetry, where the text has line breaks, the continuation of a line
     * too long for the screen is indented to set it apart from a new line.
     */
    private static CharSequence verseText(Bible.Item item, int accent, int wrapIndent) {
        String number = String.valueOf(item.verse);
        SpannableStringBuilder out = new SpannableStringBuilder(number);
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        out.setSpan(new SuperscriptSpan(), 0, number.length(), flags);
        out.setSpan(new RelativeSizeSpan(0.65f), 0, number.length(), flags);
        out.setSpan(new StyleSpan(Typeface.BOLD), 0, number.length(), flags);
        out.setSpan(new ForegroundColorSpan(accent), 0, number.length(), flags);
        // A non-breaking space keeps the number attached to the first word.
        out.append('\u00a0').append(item.text);
        if (item.text.indexOf('\n') >= 0) {
            out.setSpan(new LeadingMarginSpan.Standard(0, wrapIndent), 0, out.length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
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
