package com.biblereader.app;

import static com.biblereader.app.TestSupport.findText;
import static com.biblereader.app.TestSupport.idle;
import static com.biblereader.app.TestSupport.showAllRows;
import static com.biblereader.app.TestSupport.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/** Drives the reader screen: paging, the book menu, search results and settings. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class MainActivityTest {

    private static String title(MainActivity activity) {
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        return String.valueOf(toolbar.getTitle());
    }

    /** The page showing the current chapter, once its text has loaded. */
    private static ChapterPagerAdapter.Page currentPage(MainActivity activity) {
        ViewPager2 pager = activity.findViewById(R.id.pager);
        RecyclerView pages = (RecyclerView) pager.getChildAt(0);
        ChapterPagerAdapter.Page[] found = new ChapterPagerAdapter.Page[1];
        waitFor("the chapter to load", () -> {
            RecyclerView.ViewHolder holder =
                    pages.findViewHolderForAdapterPosition(pager.getCurrentItem());
            if (holder instanceof ChapterPagerAdapter.Page
                    && ((ChapterPagerAdapter.Page) holder).chapter != null) {
                found[0] = (ChapterPagerAdapter.Page) holder;
                return true;
            }
            return false;
        });
        idle();
        return found[0];
    }

    @Test
    public void opensAtGenesisAndShowsItsVerses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("Genesis 1", title(activity));
                ViewPager2 pager = activity.findViewById(R.id.pager);
                assertEquals(1189, pager.getAdapter().getItemCount());
                ChapterPagerAdapter.Page page = currentPage(activity);
                // Title row plus the 31 verses of Genesis 1.
                assertEquals(32, page.verses.getItemCount());
                assertEquals(View.VISIBLE, page.list.getVisibility());
                TextView first = (TextView) page.layout.findViewByPosition(1);
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
                // Exodus was not in memory; it loads in the background.
                assertEquals("Exodus 1", currentPage(activity).chapter.title());
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
                showAllRows(books);

                TextView john = findText(books, "John");
                assertNotNull(john);
                john.performClick();
                idle();

                Dialog dialog = ShadowDialog.getLatestDialog();
                assertNotNull(dialog);
                assertTrue(dialog.isShowing());
                RecyclerView grid = dialog.findViewById(R.id.chapter_grid);
                assertEquals(21, grid.getAdapter().getItemCount());
                showAllRows(grid);
                TextView three = findText(grid, "3");
                assertNotNull(three);
                assertEquals("Chapter 3", String.valueOf(three.getContentDescription()));
                three.performClick();
                idle();

                assertFalse(dialog.isShowing());
                assertEquals("John 3", title(activity));
                assertFalse(drawer.isDrawerOpen(GravityCompat.START));
                assertEquals(36, currentPage(activity).chapter.items.size());
            });
        }
    }

    @Test
    public void singleChapterBookOpensWithoutAChapterPicker() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                RecyclerView books = activity.findViewById(R.id.books);
                showAllRows(books);
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
                assertEquals("Psalm 23", title(activity));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                idle();
                assertEquals("Psalm 23", title(activity));
                ViewPager2 pager = activity.findViewById(R.id.pager);
                assertEquals(500, pager.getCurrentItem());
                assertEquals("Psalm 23", currentPage(activity).chapter.title());
            });
        }
    }

    @Test
    public void searchResultOpensAndHighlightsTheVerse() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                toolbar.getMenu().performIdentifierAction(R.id.action_search, 0);
                idle();
                Intent started = shadowOf(activity).getNextStartedActivityForResult().intent;
                assertEquals(SearchActivity.class.getName(),
                        started.getComponent().getClassName());

                // John 3 is the 1000th chapter; choose verse 16 of it.
                int john3 = 0;
                while (!"John 3".equals(titleAt(activity, john3))) {
                    john3++;
                }
                shadowOf(activity).receiveResult(started, Activity.RESULT_OK, new Intent()
                        .putExtra(SearchActivity.EXTRA_CHAPTER_INDEX, john3)
                        .putExtra(SearchActivity.EXTRA_VERSE, 16));
                idle();

                assertEquals("John 3", title(activity));
                ChapterPagerAdapter.Page page = currentPage(activity);
                int row = page.verses.rowOfVerse(16);
                assertEquals(16, row);
                showAllRows(page.list);
                TextView verse16 = (TextView) page.layout.findViewByPosition(row);
                assertTrue(verse16.getText().toString().contains("For God so loved the world"));
                assertTrue(((VerseBackground) verse16.getBackground()).isHighlighted());
                TextView verse15 = (TextView) page.layout.findViewByPosition(row - 1);
                assertFalse(((VerseBackground) verse15.getBackground()).isHighlighted());
            });
        }
    }

    private static String titleAt(MainActivity activity, int chapterIndex) {
        try {
            return BibleProvider.get(activity).titleAt(chapterIndex);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void textSizeSliderResizesTheVerses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ChapterPagerAdapter.Page page = currentPage(activity);
                float scale = activity.getResources().getDisplayMetrics().scaledDensity;
                TextView verse = (TextView) page.layout.findViewByPosition(1);
                assertEquals(18f, verse.getTextSize() / scale, 0.01f);

                MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                toolbar.getMenu().performIdentifierAction(R.id.action_text_size, 0);
                idle();
                Slider slider = ShadowDialog.getLatestDialog().findViewById(R.id.text_size_slider);
                slider.setValue(26f);
                idle();

                verse = (TextView) page.layout.findViewByPosition(1);
                assertEquals(26f, verse.getTextSize() / scale, 0.01f);
            });
            // The size is remembered.
            scenario.recreate();
            scenario.onActivity(activity -> {
                ChapterPagerAdapter.Page page = currentPage(activity);
                float scale = activity.getResources().getDisplayMetrics().scaledDensity;
                TextView verse = (TextView) page.layout.findViewByPosition(1);
                assertEquals(26f, verse.getTextSize() / scale, 0.01f);
            });
        }
    }

    @Test
    public void contentStaysClearOfTheSystemBars() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.drawer);
                WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 90, 0, 60))
                        .build();
                ViewCompat.dispatchApplyWindowInsets(root, insets);
                idle();
                View content = activity.findViewById(R.id.content);
                assertEquals(90, content.getPaddingTop());
                assertEquals(60, content.getPaddingBottom());
                View menu = activity.findViewById(R.id.book_menu);
                assertEquals(90, menu.getPaddingTop());
                assertEquals(60, menu.getPaddingBottom());
            });
        }
    }

    @Test
    public void longPressingAVerseIsHandled() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ChapterPagerAdapter.Page page = currentPage(activity);
                assertTrue(page.layout.findViewByPosition(1).performLongClick());
                // A screen reader announces what holding the verse does.
                AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
                ViewCompat.onInitializeAccessibilityNodeInfo(page.layout.findViewByPosition(1), info);
                String label = null;
                for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action
                        : info.getActionList()) {
                    if (action.getId() == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK) {
                        label = String.valueOf(action.getLabel());
                    }
                }
                assertEquals("Bookmark, copy or share", label);
                // The chapter title offers nothing to copy.
                assertFalse(page.layout.findViewByPosition(0).performLongClick());
            });
        }
    }

    private static Bible.Book book(MainActivity activity, String name) {
        try {
            for (Bible.Book book : BibleProvider.get(activity).books()) {
                if (book.name.equals(name)) {
                    return book;
                }
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("No book named " + name);
    }

    @Test
    public void backReturnsToThePlaceLeftByAJump() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                currentPage(activity);
                activity.goTo(book(activity, "John"), 3);
                idle();
                assertEquals("John 3", title(activity));
                activity.goTo(book(activity, "Romans"), 8);
                idle();
                assertEquals("Romans 8", title(activity));

                activity.getOnBackPressedDispatcher().onBackPressed();
                idle();
                assertEquals("John 3", title(activity));
                assertFalse(activity.isFinishing());
            });
            // The trail survives the screen being rebuilt, as on rotation.
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("John 3", title(activity));
                activity.getOnBackPressedDispatcher().onBackPressed();
                idle();
                assertEquals("Genesis 1", title(activity));
                assertFalse(activity.isFinishing());
                // Nowhere left to return to, so Back leaves the app.
                activity.getOnBackPressedDispatcher().onBackPressed();
                idle();
                assertTrue(activity.isFinishing());
            });
        }
    }

    @Test
    public void swipingLeavesNothingForBackToReturnTo() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.pager);
                pager.setCurrentItem(1, true);
                idle();
                activity.getOnBackPressedDispatcher().onBackPressed();
                idle();
                assertTrue(activity.isFinishing());
            });
        }
    }

    @Test
    public void highlightClearsOnceTheReaderMovesOn() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                toolbar.getMenu().performIdentifierAction(R.id.action_search, 0);
                idle();
                Intent started = shadowOf(activity).getNextStartedActivityForResult().intent;
                int john3 = book(activity, "John").firstChapter + 2;
                shadowOf(activity).receiveResult(started, Activity.RESULT_OK, new Intent()
                        .putExtra(SearchActivity.EXTRA_CHAPTER_INDEX, john3)
                        .putExtra(SearchActivity.EXTRA_VERSE, 16));
                idle();
                ChapterPagerAdapter.Page page = currentPage(activity);
                showAllRows(page.list);
                TextView verse = (TextView) page.layout.findViewByPosition(16);
                assertTrue(((VerseBackground) verse.getBackground()).isHighlighted());

                ViewPager2 pager = activity.findViewById(R.id.pager);
                pager.setCurrentItem(john3 + 1, false);
                idle();
                pager.setCurrentItem(john3, false);
                idle();
                page = currentPage(activity);
                showAllRows(page.list);
                verse = (TextView) page.layout.findViewByPosition(16);
                assertFalse(((VerseBackground) verse.getBackground()).isHighlighted());
            });
        }
    }

    private static VerseBackground backgroundOfRow(ChapterPagerAdapter.Page page, int row) {
        showAllRows(page.list);
        return (VerseBackground) page.layout.findViewByPosition(row).getBackground();
    }

    @Test
    public void bookmarkingAVerseMarksItAndIsRemembered() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ChapterPagerAdapter.Page page = currentPage(activity);
                assertFalse(backgroundOfRow(page, 3).isBookmarked());
                // Row 3 is Genesis 1:3.
                activity.toggleBookmark(page.chapter, page.chapter.items.get(2));
                idle();
                assertTrue(backgroundOfRow(page, 3).isBookmarked());
                assertFalse(backgroundOfRow(page, 2).isBookmarked());
                assertTrue(new Bookmarks(activity).contains("genesis", 1, 3));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                ChapterPagerAdapter.Page page = currentPage(activity);
                assertTrue(backgroundOfRow(page, 3).isBookmarked());
                activity.toggleBookmark(page.chapter, page.chapter.items.get(2));
                idle();
                assertFalse(backgroundOfRow(page, 3).isBookmarked());
                assertFalse(new Bookmarks(activity).contains("genesis", 1, 3));
            });
        }
    }

    @Test
    public void bookmarkRemovedElsewhereDisappearsOnReturn() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ChapterPagerAdapter.Page page = currentPage(activity);
                activity.toggleBookmark(page.chapter, page.chapter.items.get(0));
                idle();
                assertTrue(backgroundOfRow(page, 1).isBookmarked());
                // As the bookmarks screen does when a bookmark is removed there.
                new Bookmarks(activity).toggle("genesis", 1, 1);
            });
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                idle();
                assertFalse(backgroundOfRow(currentPage(activity), 1).isBookmarked());
            });
        }
    }

    @Test
    public void bookmarksOpenFromTheMenu() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                toolbar.getMenu().performIdentifierAction(R.id.action_bookmarks, 0);
                idle();
                Intent started = shadowOf(activity).getNextStartedActivityForResult().intent;
                assertEquals(BookmarksActivity.class.getName(),
                        started.getComponent().getClassName());
                // Choosing a bookmark there opens that verse here.
                int john3 = book(activity, "John").firstChapter + 2;
                shadowOf(activity).receiveResult(started, Activity.RESULT_OK, new Intent()
                        .putExtra(SearchActivity.EXTRA_CHAPTER_INDEX, john3)
                        .putExtra(SearchActivity.EXTRA_VERSE, 16));
                idle();
                assertEquals("John 3", title(activity));
            });
        }
    }

    @Test
    public void keepScreenOnIsAppliedAndRemembered() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                ViewPager2 pager = activity.findViewById(R.id.pager);
                assertFalse(pager.getKeepScreenOn());
                MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                toolbar.getMenu().performIdentifierAction(R.id.action_keep_screen_on, 0);
                idle();
                assertTrue(pager.getKeepScreenOn());
                assertTrue(Settings.keepScreenOn(activity));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                idle();
                ViewPager2 pager = activity.findViewById(R.id.pager);
                assertTrue(pager.getKeepScreenOn());
                MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                activity.onPrepareOptionsMenu(toolbar.getMenu());
                assertTrue(toolbar.getMenu().findItem(R.id.action_keep_screen_on).isChecked());
                toolbar.getMenu().performIdentifierAction(R.id.action_keep_screen_on, 0);
                idle();
                assertFalse(pager.getKeepScreenOn());
                assertFalse(Settings.keepScreenOn(activity));
            });
        }
    }

    @Test
    public void themeChoiceIsAppliedAndRemembered() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                idle();
                assertEquals(Settings.THEME_SYSTEM, Settings.theme(activity));
                MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
                toolbar.getMenu().performIdentifierAction(R.id.action_theme, 0);
                idle();
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertEquals(3, dialog.getListView().getCount());
                assertEquals(Settings.THEME_SYSTEM, dialog.getListView().getCheckedItemPosition());
                shadowOf(dialog.getListView()).performItemClick(Settings.THEME_DARK);
                assertEquals(Settings.THEME_DARK, Settings.theme(activity));
                assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                        AppCompatDelegate.getDefaultNightMode());
            });
            // The screen rebuilds itself in the new theme and keeps the choice.
            scenario.onActivity(activity -> {
                idle();
                assertEquals(Settings.THEME_DARK, Settings.theme(activity));
                int night = activity.getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES, night);
                Settings.setTheme(activity, Settings.THEME_LIGHT);
                assertEquals(AppCompatDelegate.MODE_NIGHT_NO,
                        AppCompatDelegate.getDefaultNightMode());
            });
        } finally {
            // The night mode is process-wide; leave it as other tests expect.
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
