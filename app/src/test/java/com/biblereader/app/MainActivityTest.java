package com.biblereader.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/** Drives the reader screen: paging, the book menu and the saved position. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static String title(MainActivity activity) {
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        return String.valueOf(toolbar.getTitle());
    }

    /** Finds the first descendant TextView showing exactly the given text. */
    private static TextView findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) {
            return (TextView) root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void opensAtGenesisAndShowsItsVerses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                assertEquals("Genesis 1", title(activity));
                ViewPager2 pager = activity.findViewById(R.id.pager);
                assertEquals(1189, pager.getAdapter().getItemCount());
                RecyclerView verses = pager.findViewById(R.id.verses);
                assertNotNull(verses);
                // Title row plus the 31 verses of Genesis 1.
                assertEquals(32, verses.getAdapter().getItemCount());
                TextView first = (TextView) verses.getLayoutManager().findViewByPosition(1);
                assertTrue(first.getText().toString(),
                        first.getText().toString().contains("In the beginning"));
            });
        }
    }

    @Test
    public void pagingMovesBetweenChaptersAndAcrossBooks() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.pager);
                pager.setCurrentItem(1, false);
                idle();
                assertEquals("Genesis 2", title(activity));
                pager.setCurrentItem(49, false);
                idle();
                assertEquals("Genesis 50", title(activity));
                pager.setCurrentItem(50, false);
                idle();
                assertEquals("Exodus 1", title(activity));
            });
        }
    }

    @Test
    public void bookMenuJumpsToTheChosenBookAndChapter() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                DrawerLayout drawer = activity.findViewById(R.id.drawer);
                RecyclerView books = activity.findViewById(R.id.books);
                // 66 books plus the two testament titles.
                assertEquals(68, books.getAdapter().getItemCount());

                // Show the whole list so any book can be tapped.
                books.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY));
                books.layout(0, 0, 600, 20000);
                idle();

                TextView john = findText(books, "John");
                assertNotNull(john);
                john.performClick();
                idle();

                Dialog dialog = ShadowDialog.getLatestDialog();
                assertNotNull(dialog);
                assertTrue(dialog.isShowing());
                RecyclerView grid = dialog.findViewById(R.id.chapter_grid);
                assertEquals(21, grid.getAdapter().getItemCount());
                grid.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
                grid.layout(0, 0, 600, 4000);
                idle();
                TextView three = findText(grid, "3");
                assertNotNull(three);
                three.performClick();
                idle();

                assertFalse(dialog.isShowing());
                assertEquals("John 3", title(activity));
                assertFalse(drawer.isDrawerOpen(GravityCompat.START));
            });
        }
    }

    @Test
    public void singleChapterBookOpensWithoutAChapterPicker() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                RecyclerView books = activity.findViewById(R.id.books);
                books.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY));
                books.layout(0, 0, 600, 20000);
                idle();
                findText(books, "Jude").performClick();
                idle();
                assertEquals("Jude", title(activity));
            });
        }
    }

    @Test
    public void reopensAtTheChapterLastRead() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.pager);
                pager.setCurrentItem(500, false);
                idle();
            });
            String expected = "Psalms 23";
            scenario.onActivity(activity -> assertEquals(expected, title(activity)));
            scenario.recreate();
            scenario.onActivity(activity -> {
                idle();
                assertEquals(expected, title(activity));
                ViewPager2 pager = activity.findViewById(R.id.pager);
                assertEquals(500, pager.getCurrentItem());
            });
        }
    }
}
