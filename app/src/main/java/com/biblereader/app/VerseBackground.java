package com.biblereader.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

/**
 * Drawn behind a verse: a tint when the verse is highlighted, and a bar in
 * the margin beside it when it is bookmarked.
 */
final class VerseBackground extends Drawable {

    /** Opacity, out of 255, of the highlight tint. */
    private static final int TINT_ALPHA = 46;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private final float barInset;
    private final float barWidth;
    private boolean highlighted;
    private boolean bookmarked;

    /**
     * @param accent colour of the bar, and of the tint once made translucent
     * @param density pixels per dp, for sizing the bar
     */
    VerseBackground(int accent, float density) {
        this.accent = accent;
        barInset = 8 * density;
        barWidth = 3 * density;
    }

    void setState(boolean highlighted, boolean bookmarked) {
        if (this.highlighted != highlighted || this.bookmarked != bookmarked) {
            this.highlighted = highlighted;
            this.bookmarked = bookmarked;
            invalidateSelf();
        }
    }

    boolean isHighlighted() {
        return highlighted;
    }

    boolean isBookmarked() {
        return bookmarked;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (highlighted) {
            paint.setColor(ColorUtils.setAlphaComponent(accent, TINT_ALPHA));
            canvas.drawRect(bounds, paint);
        }
        if (bookmarked) {
            paint.setColor(accent);
            boolean rtl = DrawableCompat.getLayoutDirection(this) == View.LAYOUT_DIRECTION_RTL;
            float left = rtl ? bounds.right - barInset - barWidth : bounds.left + barInset;
            float gap = barWidth;
            canvas.drawRoundRect(left, bounds.top + gap, left + barWidth, bounds.bottom - gap,
                    barWidth / 2, barWidth / 2, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        // Always drawn at its own opacity.
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
