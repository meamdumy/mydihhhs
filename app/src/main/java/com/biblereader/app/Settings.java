package com.biblereader.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** The reader's saved preferences. */
final class Settings {

    private static final String FILE = "reader";

    /** Theme choices, in the order they are offered. */
    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

    private static final float DEFAULT_TEXT_SP = 18f;
    static final float MIN_TEXT_SP = 12f;
    static final float MAX_TEXT_SP = 34f;

    private static final String KEY_CHAPTER = "chapter";
    private static final String KEY_ROW = "item";
    private static final String KEY_TEXT_SIZE = "textSize";
    private static final String KEY_THEME = "theme";
    private static final String KEY_KEEP_SCREEN_ON = "keepScreenOn";

    private Settings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Global index of the chapter last read; 0, Genesis 1, on first use. */
    static int chapter(Context context) {
        return prefs(context).getInt(KEY_CHAPTER, 0);
    }

    /** Row that was at the top of the screen in the chapter last read. */
    static int row(Context context) {
        return prefs(context).getInt(KEY_ROW, 0);
    }

    static void savePosition(Context context, int chapter, int row) {
        prefs(context).edit().putInt(KEY_CHAPTER, chapter).putInt(KEY_ROW, row).apply();
    }

    /** Text size in sp, within the range the reader offers. */
    static float textSize(Context context) {
        return clampTextSize(prefs(context).getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SP));
    }

    static void setTextSize(Context context, float sizeSp) {
        prefs(context).edit().putFloat(KEY_TEXT_SIZE, clampTextSize(sizeSp)).apply();
    }

    static float clampTextSize(float sizeSp) {
        return Math.max(MIN_TEXT_SP, Math.min(MAX_TEXT_SP, sizeSp));
    }

    static int theme(Context context) {
        int theme = prefs(context).getInt(KEY_THEME, THEME_SYSTEM);
        return theme == THEME_LIGHT || theme == THEME_DARK ? theme : THEME_SYSTEM;
    }

    /** Saves the theme choice and applies it to every screen straight away. */
    static void setTheme(Context context, int theme) {
        prefs(context).edit().putInt(KEY_THEME, theme).apply();
        applyTheme(context);
    }

    /** Applies the saved theme. Open screens rebuild themselves if it changed. */
    static void applyTheme(Context context) {
        int mode;
        switch (theme(context)) {
            case THEME_LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case THEME_DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    static boolean keepScreenOn(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    static void setKeepScreenOn(Context context, boolean keepOn) {
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply();
    }
}
