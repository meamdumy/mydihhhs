package com.biblereader.app;

import static com.biblereader.app.TestSupport.findText;
import static com.biblereader.app.TestSupport.idle;
import static com.biblereader.app.TestSupport.showAllRows;
import static com.biblereader.app.TestSupport.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Drives the bookmarks screen: listing, opening, removing and undoing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class BookmarksActivityTest {

    private Bookmarks bookmarks;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        bookmarks = new Bookmarks(context);
    }

    private static RecyclerView list(BookmarksActivity activity) {
        return activity.findViewById(R.id.bookmarks);
    }

    private static void waitForRows(BookmarksActivity activity, int count) {
        waitFor(count + " bookmarks", () -> list(activity).getAdapter().getItemCount() == count
                && (count == 0 || list(activity).getVisibility() == View.VISIBLE));
        showAllRows(list(activity));
    }

    /** The row, a clickable group, that shows the given title. */
    private static ViewGroup row(BookmarksActivity activity, String title) {
        TextView found = findText(list(activity), title);
        assertNotNull(title, found);
        return (ViewGroup) found.getParent().getParent();
    }

    @Test
    public void saysSoWhenThereAreNoBookmarks() {
        try (ActivityScenario<BookmarksActivity> scenario =
                ActivityScenario.launch(BookmarksActivity.class)) {
            scenario.onActivity(activity -> {
                View empty = activity.findViewById(R.id.empty);
                waitFor("the empty message", () -> empty.getVisibility() == View.VISIBLE);
                assertEquals(View.GONE, list(activity).getVisibility());
            });
        }
    }

    @Test
    public void listsBookmarksNewestFirstWithTheirText() {
        bookmarks.toggle("psalms", 23, 1);
        bookmarks.toggle("john", 3, 16);
        try (ActivityScenario<BookmarksActivity> scenario =
                ActivityScenario.launch(BookmarksActivity.class)) {
            scenario.onActivity(activity -> {
                waitForRows(activity, 2);
                assertEquals(View.GONE, activity.findViewById(R.id.empty).getVisibility());
                ViewGroup first = (ViewGroup) list(activity).getLayoutManager().findViewByPosition(0);
                assertNotNull(findText(first, "John 3:16"));
                TextView text = first.findViewById(R.id.result_text);
                assertTrue(text.getText().toString().startsWith("For God so loved the world"));
                // Poetry is shown on one line.
                ViewGroup second = (ViewGroup) list(activity).getLayoutManager().findViewByPosition(1);
                assertNotNull(findText(second, "Psalms 23:1"));
                assertFalse(((TextView) second.findViewById(R.id.result_text))
                        .getText().toString().contains("\n"));
            });
        }
    }

    @Test
    public void tappingABookmarkReturnsItsPlace() {
        bookmarks.toggle("psalms", 23, 1);
        try (ActivityScenario<BookmarksActivity> scenario =
                ActivityScenario.launchActivityForResult(BookmarksActivity.class)) {
            scenario.onActivity(activity -> {
                waitForRows(activity, 1);
                row(activity, "Psalms 23:1").performClick();
            });
            ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_OK, result.getResultCode());
            // Psalms starts at chapter index 478.
            assertEquals(500, result.getResultData()
                    .getIntExtra(SearchActivity.EXTRA_CHAPTER_INDEX, -1));
            assertEquals(1, result.getResultData().getIntExtra(SearchActivity.EXTRA_VERSE, -1));
        }
    }

    @Test
    public void removingABookmarkCanBeUndone() {
        bookmarks.toggle("genesis", 1, 1);
        bookmarks.toggle("john", 3, 16);
        bookmarks.toggle("psalms", 23, 1);
        try (ActivityScenario<BookmarksActivity> scenario =
                ActivityScenario.launch(BookmarksActivity.class)) {
            scenario.onActivity(activity -> {
                waitForRows(activity, 3);
                View remove = row(activity, "John 3:16").findViewById(R.id.remove);
                assertEquals("Remove bookmark for John 3:16",
                        String.valueOf(remove.getContentDescription()));
                remove.performClick();
                idle();
                assertEquals(2, list(activity).getAdapter().getItemCount());
                assertFalse(bookmarks.contains("john", 3, 16));
                assertTrue(bookmarks.contains("genesis", 1, 1));

                View undo = activity.findViewById(com.google.android.material.R.id.snackbar_action);
                assertNotNull(undo);
                undo.performClick();
                waitForRows(activity, 3);
                assertTrue(bookmarks.contains("john", 3, 16));
                // Back in its original place, between the other two.
                ViewGroup middle = (ViewGroup) list(activity).getLayoutManager().findViewByPosition(1);
                assertNotNull(findText(middle, "John 3:16"));
            });
        }
    }

    @Test
    public void removingTheLastBookmarkShowsTheEmptyMessage() {
        bookmarks.toggle("john", 3, 16);
        try (ActivityScenario<BookmarksActivity> scenario =
                ActivityScenario.launch(BookmarksActivity.class)) {
            scenario.onActivity(activity -> {
                waitForRows(activity, 1);
                row(activity, "John 3:16").findViewById(R.id.remove).performClick();
                idle();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.empty).getVisibility());
                assertTrue(bookmarks.all().isEmpty());
            });
        }
    }

    @Test
    public void skipsBookmarksForPlacesTheTextDoesNotHave() {
        bookmarks.toggle("notabook", 1, 1);
        bookmarks.toggle("john", 99, 1);
        bookmarks.toggle("jude", 1, 5);
        try (ActivityScenario<BookmarksActivity> scenario =
                ActivityScenario.launch(BookmarksActivity.class)) {
            scenario.onActivity(activity -> {
                waitForRows(activity, 1);
                assertNotNull(findText(list(activity), "Jude 5"));
                assertNull(findText(list(activity), "John 99:1"));
            });
        }
    }
}
