package com.biblereader.app;

import static com.biblereader.app.TestSupport.findText;
import static com.biblereader.app.TestSupport.idle;
import static com.biblereader.app.TestSupport.showAllRows;
import static com.biblereader.app.TestSupport.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Drives the search screen: word search, reference jumps and the result returned. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class SearchActivityTest {

    private static void type(SearchActivity activity, String query) {
        EditText input = activity.findViewById(R.id.query);
        input.setText(query);
    }

    private static String status(SearchActivity activity) {
        return ((TextView) activity.findViewById(R.id.status)).getText().toString();
    }

    private static RecyclerView results(SearchActivity activity) {
        return activity.findViewById(R.id.results);
    }

    @Test
    public void findsVersesByWordAndReturnsTheChosenOne() {
        try (ActivityScenario<SearchActivity> scenario =
                ActivityScenario.launchActivityForResult(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(status(activity).startsWith("Type words"));
                type(activity, "green pastures");
                waitFor("search results", () -> results(activity).getAdapter().getItemCount() == 1);
                assertEquals("1 verse", status(activity));
                showAllRows(results(activity));
                TextView title = findText(results(activity), "Psalms 23:2");
                assertNotNull(title);
                ((android.view.View) title.getParent()).performClick();
            });
            ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_OK, result.getResultCode());
            // Psalms starts at chapter index 478.
            assertEquals(500, result.getResultData()
                    .getIntExtra(SearchActivity.EXTRA_CHAPTER_INDEX, -1));
            assertEquals(2, result.getResultData().getIntExtra(SearchActivity.EXTRA_VERSE, -1));
        }
    }

    @Test
    public void offersToGoStraightToAReference() {
        try (ActivityScenario<SearchActivity> scenario =
                ActivityScenario.launchActivityForResult(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                type(activity, "jn 3:16");
                waitFor("the reference row", () -> results(activity).getAdapter().getItemCount() == 1);
                showAllRows(results(activity));
                TextView go = findText(results(activity), "Go to John 3:16");
                assertNotNull(go);
                ((android.view.View) go.getParent()).performClick();
            });
            ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_OK, result.getResultCode());
            assertEquals(16, result.getResultData().getIntExtra(SearchActivity.EXTRA_VERSE, -1));
        }
    }

    @Test
    public void pointsAReferenceAtAVerseThatExists() {
        try (ActivityScenario<SearchActivity> scenario =
                ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                type(activity, "john 3:99");
                waitFor("the reference row", () -> results(activity).getAdapter().getItemCount() == 1);
                showAllRows(results(activity));
                assertNotNull(findText(results(activity), "Go to John 3:36"));
            });
        }
    }

    @Test
    public void doesNotMistakeAnOrdinaryWordForABook() {
        try (ActivityScenario<SearchActivity> scenario =
                ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                type(activity, "judge");
                waitFor("search results", () -> results(activity).getAdapter().getItemCount() > 1);
                showAllRows(results(activity));
                assertNull(findText(results(activity), "Go to Judges 1"));
                // The book's full name is still a reference.
                type(activity, "judges");
                waitFor("the reference row", () -> {
                    showAllRows(results(activity));
                    return findText(results(activity), "Go to Judges 1") != null;
                });
            });
        }
    }

    @Test
    public void reportsNoResultsAndTooManyResults() {
        try (ActivityScenario<SearchActivity> scenario =
                ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                type(activity, "zzzzqqq");
                waitFor("the empty message", () -> status(activity).startsWith("No verses"));
                assertEquals(0, results(activity).getAdapter().getItemCount());

                type(activity, "the");
                waitFor("the truncation notice", () -> status(activity).startsWith("Showing"));
                assertEquals(200, results(activity).getAdapter().getItemCount());

                type(activity, "a");
                waitFor("the hint", () -> status(activity).startsWith("Type words"));
                idle();
                assertEquals(0, results(activity).getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void emphasisesTheWordsSearchedFor() {
        CharSequence out = SearchActivity.emphasise("The LORD is my shepherd; the end",
                Bible.Query.parse("the shepherd"));
        Spanned spanned = (Spanned) out;
        StyleSpan[] spans = spanned.getSpans(0, out.length(), StyleSpan.class);
        assertEquals(3, spans.length);
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(3, spanned.getSpanEnd(spans[0]));
    }
}
