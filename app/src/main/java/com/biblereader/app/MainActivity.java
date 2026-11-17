package com.biblereader.app;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The reader. Verses scroll vertically, chapters swipe horizontally, and the
 * side menu lists every book for jumping around quickly.
 */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_HISTORY = "history";
    /** How many earlier places the Back button can return to. */
    private static final int MAX_HISTORY = 30;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    /** Places left by jumping elsewhere, most recent first, as {chapter, row}. */
    private final ArrayDeque<int[]> history = new ArrayDeque<>();
    private final OnBackPressedCallback returnToPlace = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            returnToPreviousPlace();
        }
    };

    private Bible bible;
    private Bookmarks bookmarks;
    private DrawerLayout drawer;
    private ViewPager2 pager;
    private ChapterPagerAdapter pagerAdapter;
    private RecyclerView bookList;
    private BookListAdapter bookAdapter;
    private float textSizeSp;
    /** The dialog on show, if any, so it can be closed with the screen. */
    private Dialog openDialog;
    private PopupMenu openPopup;

    /** Opens a screen that finishes with a place to go to: search or bookmarks. */
    private final ActivityResultLauncher<Intent> placePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && bible != null) {
                    showVerse(PlaceResult.chapterIndex(data), PlaceResult.verse(data));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        drawer = findViewById(R.id.drawer);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        pager = findViewById(R.id.pager);
        bookList = findViewById(R.id.books);
        setSupportActionBar(toolbar);
        SystemBars.pad(findViewById(R.id.content), false);
        SystemBars.pad(findViewById(R.id.book_menu), false);

        try {
            bible = BibleProvider.get(this);
        } catch (IOException e) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.bible_load_failed)
                    .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }

        bookmarks = new Bookmarks(this);
        textSizeSp = Settings.textSize(this);
        int chapter = Math.max(0, Math.min(Settings.chapter(this), bible.chapterCount() - 1));

        pagerAdapter = new ChapterPagerAdapter(bible, loader, bookmarks, textSizeSp,
                this::showVerseMenu);
        pagerAdapter.scrollToRow(chapter, Settings.row(this));
        pager.setAdapter(pagerAdapter);
        // Keep the neighbouring chapters laid out so swipes start instantly.
        pager.setOffscreenPageLimit(1);
        pager.setCurrentItem(chapter, false);
        pager.setKeepScreenOn(Settings.keepScreenOn(this));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPosition(position);
                pagerAdapter.onPageSelected(position);
            }
        });

        bookAdapter = new BookListAdapter(bible.books(),
                getString(R.string.old_testament), getString(R.string.new_testament),
                this::onBookChosen);
        bookList.setLayoutManager(new LinearLayoutManager(this));
        bookList.setAdapter(bookAdapter);

        toolbar.setNavigationOnClickListener(v -> openBookMenu());
        toolbar.setOnClickListener(v -> pickChapter(bible.bookAt(pager.getCurrentItem())));
        showPosition(chapter);

        if (savedInstanceState != null) {
            int[] saved = savedInstanceState.getIntArray(STATE_HISTORY);
            for (int i = 0; saved != null && i + 1 < saved.length; i += 2) {
                history.addLast(new int[] {saved[i], saved[i + 1]});
            }
        }
        // Registered first so that an open book menu, added below, closes first.
        returnToPlace.setEnabled(!history.isEmpty());
        getOnBackPressedDispatcher().addCallback(this, returnToPlace);
        OnBackPressedCallback closeDrawer = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawer.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeDrawer);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View view) {
                closeDrawer.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View view) {
                closeDrawer.setEnabled(false);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pagerAdapter != null) {
            // Bookmarks may have been removed on the bookmarks screen.
            pagerAdapter.refreshBookmarks();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bible == null) {
            return;
        }
        int chapter = pager.getCurrentItem();
        int row = currentRow();
        if (row < 0) {
            // Still loading: keep the row saved for this chapter, if it was this one.
            row = chapter == Settings.chapter(this) ? Settings.row(this) : 0;
        }
        Settings.savePosition(this, chapter, row);
        Settings.setTextSize(this, textSizeSp);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        int[] saved = new int[history.size() * 2];
        int i = 0;
        for (int[] place : history) {
            saved[i++] = place[0];
            saved[i++] = place[1];
        }
        outState.putIntArray(STATE_HISTORY, saved);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // A window left open would outlive the screen it belongs to.
        if (openDialog != null) {
            openDialog.dismiss();
        }
        if (openPopup != null) {
            openPopup.dismiss();
        }
        pager.setAdapter(null);
        loader.shutdown();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (bible == null) {
            return false;
        }
        getMenuInflater().inflate(R.menu.reader, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem keepOn = menu.findItem(R.id.action_keep_screen_on);
        if (keepOn != null) {
            keepOn.setChecked(Settings.keepScreenOn(this));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            placePicker.launch(new Intent(this, SearchActivity.class));
        } else if (id == R.id.action_chapters) {
            pickChapter(bible.bookAt(pager.getCurrentItem()));
        } else if (id == R.id.action_bookmarks) {
            placePicker.launch(new Intent(this, BookmarksActivity.class));
        } else if (id == R.id.action_text_size) {
            pickTextSize();
        } else if (id == R.id.action_theme) {
            pickTheme();
        } else if (id == R.id.action_keep_screen_on) {
            setKeepScreenOn(!Settings.keepScreenOn(this));
        } else if (id == R.id.action_about) {
            showAbout();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /** Jumps to a chapter by book and 1-based chapter number. */
    void goTo(Bible.Book book, int chapterNumber) {
        goTo(bible.indexOf(book, chapterNumber));
    }

    private void goTo(int chapterIndex) {
        rememberPlaceBeforeJumpingTo(chapterIndex);
        // Animate only between neighbours; a long jump would scroll through
        // every chapter in between.
        pager.setCurrentItem(chapterIndex, Math.abs(chapterIndex - pager.getCurrentItem()) == 1);
    }

    /** Row at the top of the chapter on screen, or -1 while it is still loading. */
    private int currentRow() {
        RecyclerView pages = (RecyclerView) pager.getChildAt(0);
        RecyclerView.ViewHolder page = pages.findViewHolderForAdapterPosition(pager.getCurrentItem());
        return page instanceof ChapterPagerAdapter.Page
                ? ((ChapterPagerAdapter.Page) page).firstVisibleRow() : -1;
    }

    /** Lets the Back button return here after a jump to another chapter. */
    private void rememberPlaceBeforeJumpingTo(int chapterIndex) {
        if (chapterIndex == pager.getCurrentItem()) {
            return;
        }
        history.addFirst(new int[] {pager.getCurrentItem(), Math.max(0, currentRow())});
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        returnToPlace.setEnabled(true);
    }

    private void returnToPreviousPlace() {
        int[] place = history.pollFirst();
        returnToPlace.setEnabled(!history.isEmpty());
        if (place != null) {
            pagerAdapter.clearHighlight();
            pager.setCurrentItem(place[0], false);
            pagerAdapter.scrollToRow(place[0], place[1]);
        }
    }

    /** Jumps to a chapter and, if a verse is given, scrolls to and highlights it. */
    private void showVerse(int chapterIndex, int verse) {
        if (chapterIndex < 0 || chapterIndex >= bible.chapterCount()) {
            return;
        }
        rememberPlaceBeforeJumpingTo(chapterIndex);
        pager.setCurrentItem(chapterIndex, false);
        if (verse > 0) {
            pagerAdapter.showVerse(chapterIndex, verse);
        } else {
            pagerAdapter.scrollToRow(chapterIndex, 0);
        }
    }

    private void showPosition(int chapterIndex) {
        // Set through the action bar, not the toolbar view: AppCompat replaces a
        // title set directly on the view with the app name after onCreate.
        getSupportActionBar().setTitle(bible.titleAt(chapterIndex));
        bookAdapter.setCurrent(bible.bookAt(chapterIndex));
    }

    private void openBookMenu() {
        int row = bookAdapter.setCurrent(bible.bookAt(pager.getCurrentItem()));
        if (row >= 0) {
            // Bring the current book into view, a few rows below the top.
            ((LinearLayoutManager) bookList.getLayoutManager())
                    .scrollToPositionWithOffset(Math.max(0, row - 3), 0);
        }
        drawer.openDrawer(GravityCompat.START);
    }

    private void onBookChosen(Bible.Book book) {
        drawer.closeDrawer(GravityCompat.START);
        if (book.chapterCount == 1) {
            goTo(book, 1);
        } else {
            pickChapter(book);
        }
    }

    /** Shows a grid of the book's chapter numbers and jumps to the one tapped. */
    private void pickChapter(Bible.Book book) {
        int currentIndex = pager.getCurrentItem();
        int currentNumber = bible.bookAt(currentIndex) == book
                ? currentIndex - book.firstChapter + 1 : 0;
        show(ChapterPicker.create(this, book, currentNumber, number -> goTo(book, number)));
    }

    /** Shows a dialog, replacing any already open. */
    private void show(Dialog dialog) {
        if (openDialog != null) {
            openDialog.dismiss();
        }
        openDialog = dialog;
        dialog.show();
    }

    /** Offers to bookmark, copy or share the verse that was long-pressed. */
    private void showVerseMenu(View anchor, Bible.Chapter chapter, Bible.Item verse) {
        if (openPopup != null) {
            openPopup.dismiss();
        }
        openPopup = VerseMenu.show(anchor, chapter, verse,
                bookmarks.contains(chapter.book.id, chapter.number, verse.verse),
                () -> toggleBookmark(chapter, verse));
    }

    /** Adds a bookmark on the verse, or removes the one already there. */
    void toggleBookmark(Bible.Chapter chapter, Bible.Item verse) {
        bookmarks.toggle(chapter.book.id, chapter.number, verse.verse);
        pagerAdapter.refreshBookmarks();
    }

    /** Offers the choice of following the system theme, or always light or dark. */
    private void pickTheme() {
        show(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme)
                .setSingleChoiceItems(R.array.theme_names, Settings.theme(this), (dialog, which) -> {
                    dialog.dismiss();
                    // Applying a different theme rebuilds this screen.
                    Settings.setTheme(this, which);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    void setKeepScreenOn(boolean keepOn) {
        Settings.setKeepScreenOn(this, keepOn);
        pager.setKeepScreenOn(keepOn);
        // Redraw the menu so its tick matches straight away.
        invalidateOptionsMenu();
    }

    /** Shows a slider that resizes the text as it moves. */
    private void pickTextSize() {
        View view = getLayoutInflater().inflate(R.layout.dialog_text_size, null);
        Slider slider = view.findViewById(R.id.text_size_slider);
        slider.setValueFrom(Settings.MIN_TEXT_SP);
        slider.setValueTo(Settings.MAX_TEXT_SP);
        slider.setStepSize(1f);
        slider.setValue(Math.round(textSizeSp));
        slider.addOnChangeListener((s, value, fromUser) -> setTextSize(value));
        show(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.text_size)
                .setView(view)
                .setPositiveButton(R.string.done, null)
                .create());
    }

    private void showAbout() {
        show(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_title, BuildConfig.VERSION_NAME))
                .setMessage(R.string.about_message)
                .setPositiveButton(android.R.string.ok, null)
                .create());
    }

    private void setTextSize(float sizeSp) {
        textSizeSp = Settings.clampTextSize(sizeSp);
        pagerAdapter.setTextSize(textSizeSp);
    }
}
