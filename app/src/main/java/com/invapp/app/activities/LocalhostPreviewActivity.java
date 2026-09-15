package com.invapp.app.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.invapp.R;

/**
 * In-app preview of loopback HTTP servers (Vite, Expo web, {@code opencode serve}).
 * Only {@code http://127.0.0.1} / {@code localhost} are allowed.
 */
public final class LocalhostPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_PORT = "com.invapp.preview.port";
    private static final int DEFAULT_PORT = 5000;

    private WebView mWebView;
    private EditText mPortInput;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localhost_preview);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_localhost_preview);
        }

        mPortInput = findViewById(R.id.preview_port_input);
        Button goButton = findViewById(R.id.preview_go_button);
        Button reloadButton = findViewById(R.id.preview_reload_button);
        mWebView = findViewById(R.id.preview_webview);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) {
                    return true;
                }
                if (isAllowedLoopbackUrl(url)) {
                    return false;
                }
                Toast.makeText(LocalhostPreviewActivity.this,
                    R.string.msg_preview_only_localhost, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        int port = getIntent().getIntExtra(EXTRA_PORT, DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            port = DEFAULT_PORT;
        }
        mPortInput.setText(String.valueOf(port));

        goButton.setOnClickListener(v -> loadFromPortInput());
        reloadButton.setOnClickListener(v -> mWebView.reload());
        mPortInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromPortInput();
                return true;
            }
            return false;
        });

        loadPort(port);
    }

    private void loadFromPortInput() {
        String raw = mPortInput.getText() != null
            ? mPortInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int port = Integer.parseInt(raw);
            if (port < 1 || port > 65535) {
                Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
                return;
            }
            loadPort(port);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPort(int port) {
        String url = "http://127.0.0.1:" + port + "/";
        mWebView.loadUrl(url);
    }

    private static boolean isAllowedLoopbackUrl(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("http://127.0.0.1:")
            || lower.startsWith("http://127.0.0.1/")
            || lower.startsWith("http://localhost:")
            || lower.startsWith("http://localhost/")
            || lower.equals("http://127.0.0.1")
            || lower.equals("http://localhost")
            || lower.startsWith("about:")
            || lower.startsWith("data:");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
