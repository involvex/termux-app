package com.invapp.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.util.Linkify;

import com.invapp.R;
import com.invapp.app.utils.HelpIndex;
import com.invapp.shared.theme.NightMode;
import com.invapp.shared.activity.media.AppCompatActivityUtils;

import org.commonmark.node.FencedCodeBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.recycler.MarkwonAdapter;
import io.noties.markwon.recycler.SimpleEntry;

/**
 * Offline InVxTermux Help — Markwon rendering of {@code assets/help/*.md}.
 * Deep-link: {@link #EXTRA_PAGE} or {@code invapp://help/<page>}.
 */
public final class HelpActivity extends AppCompatActivity {

    public static final String EXTRA_PAGE = "com.invapp.help.page";

    @Nullable
    private String mCurrentPageId;
    private EditText mSearch;
    private RecyclerView mRecycler;
    private Markwon mMarkwon;
    private MarkwonAdapter mAdapter;
    @Nullable
    private Map<String, String> mCachedBodies;
    private String mFilterQuery = "";

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, HelpActivity.class);
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context, @Nullable String pageId) {
        Intent intent = createIntent(context);
        if (pageId != null) {
            intent.putExtra(EXTRA_PAGE, pageId);
        }
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.activity_help);

        Toolbar toolbar = findViewById(R.id.help_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_help);
        }

        mSearch = findViewById(R.id.help_search);
        mRecycler = findViewById(R.id.help_recycler);
        mRecycler.setLayoutManager(new LinearLayoutManager(this));

        mMarkwon = Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create(Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS))
            .usePlugin(new AbstractMarkwonPlugin() {
                @Override
                public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
                    builder.on(FencedCodeBlock.class, (visitor, fencedCodeBlock) -> {
                        final CharSequence code = visitor.configuration()
                            .syntaxHighlight()
                            .highlight(fencedCodeBlock.getInfo(),
                                fencedCodeBlock.getLiteral().trim());
                        visitor.builder().append(code);
                    });
                }

                @Override
                public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                    builder.linkResolver((view, link) -> handleLink(link));
                }
            })
            .build();

        mAdapter = MarkwonAdapter.builderTextViewIsRoot(
                com.invapp.shared.R.layout.markdown_adapter_node_default)
            .include(FencedCodeBlock.class, SimpleEntry.create(
                com.invapp.shared.R.layout.markdown_adapter_node_code_block,
                com.invapp.shared.R.id.code_text_view))
            .build();
        mRecycler.setAdapter(mAdapter);

        mSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mFilterQuery = s != null ? s.toString() : "";
                if (mCurrentPageId == null) {
                    showIndex();
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mCurrentPageId != null) {
                    showIndex();
                } else {
                    finish();
                }
            }
        });

        String page = null;
        if (savedInstanceState != null) {
            page = savedInstanceState.getString(EXTRA_PAGE);
        } else if (getIntent() != null) {
            page = getIntent().getStringExtra(EXTRA_PAGE);
            Uri data = getIntent().getData();
            if (page == null && data != null) {
                page = HelpIndex.resolvePageId(data.toString());
            }
        }
        if (page != null) {
            showPage(page);
        } else {
            showIndex();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCurrentPageId != null) {
            outState.putString(EXTRA_PAGE, mCurrentPageId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (mCurrentPageId != null) {
                showIndex();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showIndex() {
        mCurrentPageId = null;
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_help);
        }
        if (mSearch != null) {
            mSearch.setVisibility(android.view.View.VISIBLE);
        }
        ensureBodiesCached();
        String md = HelpIndex.buildFilteredIndexMarkdown(mFilterQuery, mCachedBodies);
        renderMarkdown(md);
    }

    private void showPage(@Nullable String rawPageId) {
        String id = HelpIndex.resolvePageId(rawPageId);
        if (id == null) {
            Toast.makeText(this, R.string.msg_help_page_missing, Toast.LENGTH_SHORT).show();
            showIndex();
            return;
        }
        try {
            String md = HelpIndex.readAsset(getAssets(), id);
            mCurrentPageId = id;
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(HelpIndex.titleFor(id));
            }
            if (mSearch != null) {
                mSearch.setVisibility(android.view.View.GONE);
            }
            renderMarkdown(md);
        } catch (IOException e) {
            Toast.makeText(this, R.string.msg_help_page_missing, Toast.LENGTH_SHORT).show();
            showIndex();
        }
    }

    private void renderMarkdown(@NonNull String markdown) {
        mAdapter.setMarkdown(mMarkwon, markdown);
        mAdapter.notifyDataSetChanged();
        mRecycler.scrollToPosition(0);
    }

    private void ensureBodiesCached() {
        if (mCachedBodies != null) {
            return;
        }
        Map<String, String> bodies = new HashMap<>();
        for (String id : HelpIndex.PAGES) {
            try {
                bodies.put(id, HelpIndex.readAsset(getAssets(), id));
            } catch (IOException ignored) {
                // page missing from APK — skip body match
            }
        }
        mCachedBodies = bodies;
    }

    private void handleLink(@Nullable String link) {
        if (link == null || link.isEmpty()) {
            return;
        }
        String lower = link.toLowerCase(Locale.US);
        if (lower.startsWith(HelpIndex.LINK_SCHEME) || HelpIndex.resolvePageId(link) != null) {
            String id = HelpIndex.resolvePageId(link);
            if (id != null) {
                showPage(id);
            }
            return;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")
            || lower.startsWith("mailto:")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.msg_help_open_link_failed, Toast.LENGTH_SHORT)
                    .show();
            }
        }
    }
}
