package com.biblereader.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;

/** The menu shown when a verse is held: bookmark, copy or share. */
final class VerseMenu {

    private VerseMenu() {
    }

    /**
     * Shows the menu beside the verse.
     *
     * @param bookmarked whether the verse is bookmarked now, which decides
     *     whether the menu offers to add or to remove the bookmark
     * @param toggleBookmark run when that item is chosen
     */
    static PopupMenu show(View anchor, Bible.Chapter chapter, Bible.Item verse,
            boolean bookmarked, Runnable toggleBookmark) {
        Context context = anchor.getContext();
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(Menu.NONE, R.id.action_bookmark, 0,
                bookmarked ? R.string.remove_bookmark : R.string.add_bookmark);
        menu.getMenu().add(Menu.NONE, R.id.action_copy, 1, R.string.copy_verse);
        menu.getMenu().add(Menu.NONE, R.id.action_share, 2, R.string.share_verse);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_bookmark) {
                toggleBookmark.run();
            } else if (id == R.id.action_copy) {
                copy(context, chapter, verse);
            } else {
                context.startActivity(Intent.createChooser(shareIntent(context, chapter, verse),
                        context.getString(R.string.share_verse)));
            }
            return true;
        });
        menu.show();
        return menu;
    }

    /** The verse followed by its reference, as copied and shared. */
    static String text(Context context, Bible.Chapter chapter, Bible.Item verse) {
        return context.getString(R.string.verse_share_format,
                verse.text, chapter.book.verseTitle(chapter.number, verse.verse));
    }

    static void copy(Context context, Bible.Chapter chapter, Bible.Item verse) {
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText(
                context.getString(R.string.app_name), text(context, chapter, verse)));
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.verse_copied, Toast.LENGTH_SHORT).show();
        }
    }

    static Intent shareIntent(Context context, Bible.Chapter chapter, Bible.Item verse) {
        return new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text(context, chapter, verse));
    }
}
