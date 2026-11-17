package com.biblereader.app;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps content clear of the status bar, navigation bar and display cutout. */
final class SystemBars {

    private SystemBars() {
    }

    /**
     * Adds the system bar insets to the view's own padding, so that it can
     * extend edge to edge without its content being covered.
     *
     * @param avoidKeyboard also stay clear of the on-screen keyboard
     */
    static void pad(View view, boolean avoidKeyboard) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                | (avoidKeyboard ? WindowInsetsCompat.Type.ime() : 0);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(types);
            v.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + bars.bottom);
            return insets;
        });
    }
}
