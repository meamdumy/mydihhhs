package com.biblereader.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The reader. Verses scroll vertically, chapters swipe horizontally, and the
 * side menu lists every book for jumping around quickly.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "reader";
    private static final String KEY_CHAPTER = "chapter";
    private static final String KEY_ROW = "item";
    private static final String KEY_TEXT_SIZE = "textSize";

    private static final float DEFAULT_TEXT_SP = 18f;
    private static final float MIN_TEXT_SP = 12f;
    private static final float MAX_TEXT_SP = 34f;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    private Bible bible;
    private SharedPreferences prefs;
    private DrawerLayout drawer;
    private ViewPager2 pager;
    private ChapterPagerAdapter pagerAdapter;
    private RecyclerView bookList;
    private BookListAdapter bookAdapter;
    private float textSizeSp;

    private final ActivityResultLauncher<Intent> search = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && bible != null) {
                    showVerse(data.getIntExtra(SearchActivity.EXTRA_CHAPTER_INDEX, 0),
                            data.getIntExtra(SearchActivity.EXTRA_VERSE, 0));
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

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        textSizeSp = clampTextSize(prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SP));
        int chapter = Math.max(0,
                Math.min(prefs.getInt(KEY_CHAPTER, 0), bible.chapterCount() - 1));

        pagerAdapter = new ChapterPagerAdapter(bible, loader, textSizeSp, this::showVerseMenu);
        pagerAdapter.scrollToRow(chapter, prefs.getInt(KEY_ROW, 0));
        pager.setAdapter(pagerAdapter);
        // Keep the neighbouring chapters laid out so swipes start instantly.
        pager.setOffscreenPageLimit(1);
        pager.setCurrentItem(chapter, false);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPosition(position);
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
    protected void onPause() {
        super.onPause();
        if (bible == null) {
            return;
        }
        int chapter = pager.getCurrentItem();
        RecyclerView pages = (RecyclerView) pager.getChildAt(0);
        RecyclerView.ViewHolder page = pages.findViewHolderForAdapterPosition(chapter);
        int row = page instanceof ChapterPagerAdapter.Page
                ? ((ChapterPagerAdapter.Page) page).firstVisibleRow() : 0;
        prefs.edit()
                .putInt(KEY_CHAPTER, chapter)
                .putInt(KEY_ROW, row)
                .putFloat(KEY_TEXT_SIZE, textSizeSp)
                .apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            search.launch(new Intent(this, SearchActivity.class));
        } else if (id == R.id.action_chapters) {
            pickChapter(bible.bookAt(pager.getCurrentItem()));
        } else if (id == R.id.action_text_size) {
            pickTextSize();
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
        // Animate only between neighbours; a long jump would scroll through
        // every chapter in between.
        pager.setCurrentItem(chapterIndex, Math.abs(chapterIndex - pager.getCurrentItem()) == 1);
    }

    /** Jumps to a chapter and, if a verse is given, scrolls to and highlights it. */
    private void showVerse(int chapterIndex, int verse) {
        if (chapterIndex < 0 || chapterIndex >= bible.chapterCount()) {
            return;
        }
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

        RecyclerView grid = (RecyclerView) getLayoutInflater()
                .inflate(R.layout.dialog_chapters, null);
        grid.setLayoutManager(new GridLayoutManager(this, 5));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(book.name)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                View cell = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_chapter_number, parent, false);
                return new RecyclerView.ViewHolder(cell) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                int number = position + 1;
                TextView cell = (TextView) holder.itemView;
                cell.setText(String.valueOf(number));
                cell.setSelected(number == currentNumber);
                cell.setOnClickListener(v -> {
                    dialog.dismiss();
                    goTo(book, number);
                });
            }

            @Override
            public int getItemCount() {
                return book.chapterCount;
            }
        });
        if (currentNumber > 0) {
            grid.scrollToPosition(currentNumber - 1);
        }
        dialog.show();
    }

    /** Offers to copy or share the verse that was long-pressed. */
    private void showVerseMenu(View anchor, Bible.Chapter chapter, Bible.Item verse) {
        String text = getString(R.string.verse_share_format,
                verse.text, chapter.book.verseTitle(chapter.number, verse.verse));
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(Menu.NONE, R.id.action_copy, 0, R.string.copy_verse);
        menu.getMenu().add(Menu.NONE, R.id.action_share, 1, R.string.share_verse);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_copy) {
                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
                // Android 13 and later confirm a copy themselves.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(this, R.string.verse_copied, Toast.LENGTH_SHORT).show();
                }
            } else {
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, getString(R.string.share_verse)));
            }
            return true;
        });
        menu.show();
    }

    /** Shows a slider that resizes the text as it moves. */
    private void pickTextSize() {
        View view = getLayoutInflater().inflate(R.layout.dialog_text_size, null);
        Slider slider = view.findViewById(R.id.text_size_slider);
        slider.setValueFrom(MIN_TEXT_SP);
        slider.setValueTo(MAX_TEXT_SP);
        slider.setStepSize(1f);
        slider.setValue(Math.round(textSizeSp));
        slider.addOnChangeListener((s, value, fromUser) -> setTextSize(value));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.text_size)
                .setView(view)
                .setPositiveButton(R.string.done, null)
                .show();
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_title, BuildConfig.VERSION_NAME))
                .setMessage(R.string.about_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void setTextSize(float sizeSp) {
        textSizeSp = clampTextSize(sizeSp);
        pagerAdapter.setTextSize(textSizeSp);
    }

    private static float clampTextSize(float sizeSp) {
        return Math.max(MIN_TEXT_SP, Math.min(MAX_TEXT_SP, sizeSp));
    }
}
