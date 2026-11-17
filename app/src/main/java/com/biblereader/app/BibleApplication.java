package com.biblereader.app;

import android.app.Application;

/** Applies the chosen theme before any screen is created. */
public class BibleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Settings.applyTheme(this);
    }
}
