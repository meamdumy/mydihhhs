package com.biblereader.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Checks the defaults, limits and persistence of the reader's preferences. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class SettingsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        // The night mode is process-wide; leave it as other tests expect.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test
    public void startsAtGenesisWithDefaultSettings() {
        assertEquals(0, Settings.chapter(context));
        assertEquals(0, Settings.row(context));
        assertEquals(18f, Settings.textSize(context), 0f);
        assertEquals(Settings.THEME_SYSTEM, Settings.theme(context));
        assertFalse(Settings.keepScreenOn(context));
    }

    @Test
    public void remembersThePositionAndOptions() {
        Settings.savePosition(context, 500, 4);
        Settings.setTextSize(context, 24f);
        Settings.setKeepScreenOn(context, true);
        assertEquals(500, Settings.chapter(context));
        assertEquals(4, Settings.row(context));
        assertEquals(24f, Settings.textSize(context), 0f);
        assertTrue(Settings.keepScreenOn(context));
    }

    @Test
    public void keepsTheTextSizeWithinTheRangeOffered() {
        Settings.setTextSize(context, 500f);
        assertEquals(Settings.MAX_TEXT_SP, Settings.textSize(context), 0f);
        Settings.setTextSize(context, 1f);
        assertEquals(Settings.MIN_TEXT_SP, Settings.textSize(context), 0f);
    }

    @Test
    public void appliesTheChosenTheme() {
        Settings.setTheme(context, Settings.THEME_DARK);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
        Settings.setTheme(context, Settings.THEME_LIGHT);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
        Settings.setTheme(context, Settings.THEME_SYSTEM);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
        // A value this version does not know falls back to following the device.
        Settings.setTheme(context, 7);
        assertEquals(Settings.THEME_SYSTEM, Settings.theme(context));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }
}
