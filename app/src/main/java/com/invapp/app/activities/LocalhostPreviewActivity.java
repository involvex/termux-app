package com.invapp.app.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.invapp.R;
import com.invapp.app.utils.LanShareHelper;
import com.invapp.app.utils.LocalhostPortScanner;
import com.invapp.app.utils.WorkflowHelper;
import com.invapp.shared.interact.ShareUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app preview of loopback HTTP servers (Vite, Expo web, {@code opencode web}).
 * Only {@code http://127.0.0.1} / {@code localhost} are allowed in the WebView.
 * Opt-in <strong>Copy LAN</strong> copies {@code http://&lt;wifi-ip&gt;:&lt;port&gt;}
 * when the server listens on {@code 0.0.0.0} / {@code ::} — no public tunnels.
 */
public final class LocalhostPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_PORT = "com.invapp.preview.port";
    /** OpenCode default web port ({@code http://127.0.0.1:4096/}). */
    private static final int DEFAULT_PORT = WorkflowHelper.AI_PREVIEW_PORT;

    private WebView mWebView;
    private EditText mPortInput;
    private LinearLayout mPortChips;
    private TextView mPortsEmpty;
    private TextView mLanHint;
    private final ExecutorService mScanExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @NonNull
    public static Intent createIntent(@NonNull Context context, int port) {
        Intent intent = new Intent(context, LocalhostPreviewActivity.class);
        intent.putExtra(EXTRA_PORT, port);
        return intent;
    }

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
        Button scanButton = findViewById(R.id.preview_scan_button);
        Button copyLanButton = findViewById(R.id.preview_copy_lan_button);
        mPortChips = findViewById(R.id.preview_port_chips);
        mPortsEmpty = findViewById(R.id.preview_ports_empty);
        mLanHint = findViewById(R.id.preview_lan_hint);
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
        scanButton.setOnClickListener(v -> scanPortsAsync());
        copyLanButton.setOnClickListener(v -> copyLanUrlForCurrentPort());
        mPortInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromPortInput();
                return true;
            }
            return false;
        });

        loadPort(port);
        scanPortsAsync();
        refreshLanHint();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanPortsAsync();
        refreshLanHint();
    }

    private void scanPortsAsync() {
        mScanExecutor.execute(() -> {
            final List<Integer> ports = LocalhostPortScanner.scanListeningPorts();
            mMainHandler.post(() -> {
                renderPortChips(ports);
                refreshLanHint();
            });
        });
    }

    private void renderPortChips(@NonNull List<Integer> ports) {
        if (mPortChips == null) {
            return;
        }
        mPortChips.removeAllViews();
        if (ports.isEmpty()) {
            if (mPortsEmpty != null) {
                mPortsEmpty.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (mPortsEmpty != null) {
            mPortsEmpty.setVisibility(View.GONE);
        }
        int pad = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        for (final Integer port : ports) {
            Button chip = new Button(this, null, android.R.attr.buttonStyleSmall);
            chip.setText(String.valueOf(port));
            chip.setAllCaps(false);
            chip.setPadding(pad * 2, pad, pad * 2, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, pad, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                mPortInput.setText(String.valueOf(port));
                loadPort(port);
                refreshLanHint();
            });
            chip.setOnLongClickListener(v -> {
                copyLanUrlForPort(port);
                return true;
            });
            mPortChips.addView(chip);
        }
    }

    private void copyLanUrlForCurrentPort() {
        int port = readPortInputOrToast();
        if (port < 0) {
            return;
        }
        copyLanUrlForPort(port);
    }

    private void copyLanUrlForPort(int port) {
        mScanExecutor.execute(() -> {
            final boolean wildcard = LocalhostPortScanner.isWildcardListen(port);
            final String ip = LanShareHelper.getSiteLocalIpv4();
            mMainHandler.post(() -> {
                if (!wildcard) {
                    Toast.makeText(this,
                        getString(R.string.msg_preview_lan_loopback_only, port),
                        Toast.LENGTH_LONG).show();
                    return;
                }
                if (ip == null) {
                    Toast.makeText(this, R.string.msg_preview_lan_no_wifi,
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                String url = LanShareHelper.buildLanHttpUrl(ip, port);
                if (url == null) {
                    Toast.makeText(this, R.string.msg_preview_lan_unavailable,
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                ShareUtils.copyTextToClipboard(this, url,
                    getString(R.string.msg_preview_lan_copied, url));
            });
        });
    }

    private void refreshLanHint() {
        if (mLanHint == null) {
            return;
        }
        mScanExecutor.execute(() -> {
            final String ip = LanShareHelper.getSiteLocalIpv4();
            mMainHandler.post(() -> {
                if (ip == null) {
                    mLanHint.setVisibility(View.GONE);
                    return;
                }
                mLanHint.setText(getString(R.string.msg_preview_lan_hint, ip));
                mLanHint.setVisibility(View.VISIBLE);
            });
        });
    }

    /** @return port or {@code -1} after toasting on error */
    private int readPortInputOrToast() {
        String raw = mPortInput.getText() != null
            ? mPortInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
            return -1;
        }
        try {
            int port = Integer.parseInt(raw);
            if (port < 1 || port > 65535) {
                Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    private void loadFromPortInput() {
        int port = readPortInputOrToast();
        if (port < 0) {
            return;
        }
        loadPort(port);
        refreshLanHint();
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
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        mScanExecutor.shutdownNow();
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
