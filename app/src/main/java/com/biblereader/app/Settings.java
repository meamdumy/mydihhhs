package com.biblereader.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** The reader's saved preferences. */
final class Settings {

    /** Name of the preferences file shared by the reader's screens. */
    static final String FILE = "reader";

    /** Theme choices, in the order they are offered. */
    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

    private static final String KEY_THEME = "theme";
    private static final String KEY_KEEP_SCREEN_ON = "keepScreenOn";

    private Settings() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
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
