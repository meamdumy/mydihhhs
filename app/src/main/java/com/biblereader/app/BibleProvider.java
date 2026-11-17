package com.biblereader.app;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;

/** Shares one {@link Bible}, and so one cache of parsed books, between screens. */
final class BibleProvider {

    private static Context application;
    private static Bible bible;

    private BibleProvider() {
    }

    /** Reads the small book index on first use; the text itself loads lazily. */
    static synchronized Bible get(Context context) throws IOException {
        Context app = context.getApplicationContext();
        if (bible == null || application != app) {
            AssetManager assets = app.getAssets();
            bible = new Bible(assets::open);
            application = app;
        }
        return bible;
    }
}
