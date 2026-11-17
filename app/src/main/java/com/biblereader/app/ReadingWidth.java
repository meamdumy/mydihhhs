package com.biblereader.app;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;

/** Keeps lines of text a comfortable length on wide screens. */
final class ReadingWidth {

    private ReadingWidth() {
    }

    /**
     * Space to leave on each side of a full-width column so that it is no
     * wider than the reading width, in pixels. Zero on phones.
     */
    static int sideInset(Context context) {
        Resources resources = context.getResources();
        float density = resources.getDisplayMetrics().density;
        float screen = resources.getConfiguration().screenWidthDp * density;
        float column = resources.getDimension(R.dimen.reading_max_width);
        return Math.max(0, Math.round((screen - column) / 2f));
    }

    /** Insets the view's content on both sides by {@link #sideInset}. */
    static void apply(View view) {
        int inset = sideInset(view.getContext());
        view.setPadding(view.getPaddingLeft() + inset, view.getPaddingTop(),
                view.getPaddingRight() + inset, view.getPaddingBottom());
    }
}
