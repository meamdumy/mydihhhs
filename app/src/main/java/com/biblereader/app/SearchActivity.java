package com.biblereader.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds verses by the words they contain, and offers a direct jump when the
 * query is a reference such as "John 3:16". Finishes with the chosen place.
 */
public class SearchActivity extends AppCompatActivity {

    /** Result extra: global index of the chosen chapter. */
    static final String EXTRA_CHAPTER_INDEX = "chapterIndex";
    /** Result extra: chosen verse number, or 0 for the top of the chapter. */
    static final String EXTRA_VERSE = "verse";

    private static final int MAX_RESULTS = 200;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final long TYPING_PAUSE_MS = 250;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Identifies the latest query, so that older searches stop and are ignored. */
    private final AtomicInteger latestSearch = new AtomicInteger();
    private final ResultAdapter results = new ResultAdapter();

    private Bible bible;
    private EditText input;
    private TextView status;
    private final Runnable pendingSearch = () -> search(input.getText().toString());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        SystemBars.pad(findViewById(R.id.content), true);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        input = findViewById(R.id.query);
        status = findViewById(R.id.status);
        RecyclerView list = findViewById(R.id.results);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(results);

        try {
            bible = BibleProvider.get(this);
        } catch (IOException e) {
            finish();
            return;
        }

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Wait for a pause in typing before searching.
                main.removeCallbacks(pendingSearch);
                main.postDelayed(pendingSearch, TYPING_PAUSE_MS);
            }
        });
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                main.removeCallbacks(pendingSearch);
                pendingSearch.run();
                getSystemService(InputMethodManager.class)
                        .hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        input.requestFocus();
        search(input.getText().toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(pendingSearch);
        latestSearch.incrementAndGet();
        worker.shutdown();
    }

    private void search(String rawQuery) {
        String query = rawQuery.trim();
        int id = latestSearch.incrementAndGet();
        if (query.length() < MIN_QUERY_LENGTH) {
            results.show(null, Collections.emptyList(), Collections.emptyList());
            status.setText(R.string.search_hint_long);
            return;
        }
        Bible.Reference reference = bible.parseReference(query);
        List<String> words = wordsOf(query);
        worker.execute(() -> {
            Bible.SearchResult found;
            try {
                found = bible.search(query, MAX_RESULTS, () -> latestSearch.get() != id);
            } catch (IOException e) {
                found = null;
            }
            Bible.SearchResult result = found;
            main.post(() -> {
                if (latestSearch.get() != id) {
                    return;
                }
                if (result == null) {
                    results.show(reference, Collections.emptyList(), words);
                    status.setText(R.string.search_failed);
                    return;
                }
                results.show(reference, result.hits, words);
                int count = result.hits.size();
                if (result.truncated) {
                    status.setText(getResources()
                            .getQuantityString(R.plurals.search_truncated, count, count));
                } else if (count == 0) {
                    status.setText(reference == null
                            ? getString(R.string.search_no_results, query) : "");
                } else {
                    status.setText(getResources()
                            .getQuantityString(R.plurals.search_results, count, count));
                }
            });
        });
    }

    private static List<String> wordsOf(String query) {
        List<String> words = new ArrayList<>();
        for (String word : query.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    private void choose(Bible.Reference reference) {
        setResult(RESULT_OK, new Intent()
                .putExtra(EXTRA_CHAPTER_INDEX, bible.indexOf(reference.book, reference.chapter))
                .putExtra(EXTRA_VERSE, reference.verse));
        finish();
    }

    /** An optional "go to reference" row followed by the matching verses. */
    private final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {

        private Bible.Reference reference;
        private List<Bible.Hit> hits = Collections.emptyList();
        private List<String> words = Collections.emptyList();

        @SuppressLint("NotifyDataSetChanged") // Every search replaces the whole list.
        void show(Bible.Reference reference, List<Bible.Hit> hits, List<String> words) {
            this.reference = reference;
            this.hits = hits;
            this.words = words;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return hits.size() + (reference == null ? 0 : 1);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_result, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (reference != null && position == 0) {
                Bible.Reference target = reference;
                holder.title.setText(getString(R.string.go_to, target.title()));
                holder.text.setVisibility(View.GONE);
                holder.itemView.setOnClickListener(v -> choose(target));
                return;
            }
            Bible.Hit hit = hits.get(position - (reference == null ? 0 : 1));
            holder.title.setText(hit.reference.title());
            holder.text.setVisibility(View.VISIBLE);
            holder.text.setText(emphasise(hit.text, words));
            holder.itemView.setOnClickListener(v -> choose(hit.reference));
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView text;

            Holder(View view) {
                super(view);
                title = view.findViewById(R.id.result_title);
                text = view.findViewById(R.id.result_text);
            }
        }
    }

    /** Returns the text with every occurrence of the given words in bold. */
    static CharSequence emphasise(String text, List<String> words) {
        SpannableString out = new SpannableString(text);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.length() != text.length()) {
            // Lower-casing changed the length, so positions would not line up.
            return out;
        }
        for (String word : words) {
            int at = lower.indexOf(word);
            while (at >= 0) {
                out.setSpan(new StyleSpan(Typeface.BOLD), at, at + word.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                at = lower.indexOf(word, at + word.length());
            }
        }
        return out;
    }
}
