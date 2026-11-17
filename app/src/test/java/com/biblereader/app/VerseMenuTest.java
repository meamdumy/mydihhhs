package com.biblereader.app;

import static org.junit.Assert.assertEquals;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

/** Checks what copying and sharing a verse hand to the rest of the device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class VerseMenuTest {

    private Context context;
    private Bible.Chapter john3;
    private Bible.Chapter psalm23;

    @Before
    public void setUp() throws IOException {
        context = ApplicationProvider.getApplicationContext();
        Bible bible = BibleProvider.get(context);
        john3 = bible.chapterAt(bible.indexOf(bible.bookById("john"), 3));
        psalm23 = bible.chapterAt(bible.indexOf(bible.bookById("psalms"), 23));
    }

    @Test
    public void textIsTheVerseFollowedByItsReference() {
        Bible.Item verse = john3.items.get(john3.indexOfVerse(17));
        assertEquals("For God didn’t send his Son into the world to judge the world, "
                + "but that the world should be saved through him.\n— John 3:17 (WEB)",
                VerseMenu.text(context, john3, verse));
    }

    @Test
    public void poetryKeepsItsLineBreaks() {
        // Item 0 is the psalm's heading; item 1 is verse 1.
        assertEquals("Yahweh is my shepherd:\nI shall lack nothing.\n— Psalms 23:1 (WEB)",
                VerseMenu.text(context, psalm23, psalm23.items.get(1)));
    }

    @Test
    public void copyingPutsTheTextOnTheClipboard() {
        Bible.Item verse = psalm23.items.get(1);
        VerseMenu.copy(context, psalm23, verse);
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        assertEquals(VerseMenu.text(context, psalm23, verse),
                clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void sharingSendsTheTextAsPlainText() {
        Bible.Item verse = psalm23.items.get(1);
        Intent send = VerseMenu.shareIntent(context, psalm23, verse);
        assertEquals(Intent.ACTION_SEND, send.getAction());
        assertEquals("text/plain", send.getType());
        assertEquals(VerseMenu.text(context, psalm23, verse),
                send.getStringExtra(Intent.EXTRA_TEXT));
    }
}
