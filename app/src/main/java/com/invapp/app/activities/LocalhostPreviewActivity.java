package com.invapp.app.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.invapp.R;
import com.invapp.app.utils.LanShareHelper;
import com.invapp.app.utils.LocalhostPortScanner;
import com.invapp.app.utils.PreviewPortPrefs;
import com.invapp.app.utils.WorkflowHelper;
import com.invapp.shared.interact.ShareUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-app preview of loopback HTTP servers (Vite, Expo web, {@code opencode web}).
 * Only {@code http://127.0.0.1} / {@code localhost} are allowed in the WebView.
 * LAN URLs (when servers bind {@code 0.0.0.0}, including OpenCode via {@code td-ai})
 * are for other devices — use Copy LAN from Preview chips.
 * Opt-in <strong>Copy LAN</strong> copies {@code http://&lt;wifi-ip&gt;:&lt;port&gt;}
 * when the server listens on {@code 0.0.0.0} / {@code ::} — no public tunnels.
 */
public final class LocalhostPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_PORT = "com.invapp.preview.port";
    /** OpenCode default web port ({@code http://127.0.0.1:4096/}). */
    private static final int DEFAULT_PORT = WorkflowHelper.AI_PREVIEW_PORT;
    private static final long AUTO_SCAN_INTERVAL_MS = 3000L;

    private WebView mWebView;
    private EditText mPortInput;
    private LinearLayout mPortChips;
    private TextView mPortsEmpty;
    private TextView mLanHint;
    private final ExecutorService mScanExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mAutoScanActive = new AtomicBoolean(false);
    private final Set<Integer> mKnownListening = new HashSet<>();
    private boolean mListeningSeeded;
    private int mCurrentPort = DEFAULT_PORT;
    /** Path+query preserved when switching ports (default {@code /}). */
    @NonNull
    private String mCurrentPathAndQuery = "/";
    private boolean mShowingErrorPage;

    private final Runnable mAutoScanTick = new Runnable() {
        @Override
        public void run() {
            if (!mAutoScanActive.get()) {
                return;
            }
            scanPortsAsync(true);
            mMainHandler.postDelayed(this, AUTO_SCAN_INTERVAL_MS);
        }
    };

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

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mShowingErrorPage = false;
                updatePathFromUrl(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    showPreviewErrorPage(mCurrentPort);
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                // API < 23 fallback
                showPreviewErrorPage(mCurrentPort);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse errorResponse) {
                if (request != null && request.isForMainFrame()
                    && errorResponse != null && errorResponse.getStatusCode() >= 400) {
                    showPreviewErrorPage(mCurrentPort);
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mWebView != null && mWebView.canGoBack()) {
                    mWebView.goBack();
                } else {
                    finish();
                }
            }
        });

        int port = getIntent().getIntExtra(EXTRA_PORT, DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            port = DEFAULT_PORT;
        }
        mPortInput.setText(String.valueOf(port));

        goButton.setOnClickListener(v -> loadFromPortInput());
        reloadButton.setOnClickListener(v -> reloadCurrent());
        scanButton.setOnClickListener(v -> scanPortsAsync(false));
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
        scanPortsAsync(false);
        refreshLanHint();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanPortsAsync(false);
        refreshLanHint();
        if (mAutoScanActive.compareAndSet(false, true)) {
            mMainHandler.postDelayed(mAutoScanTick, AUTO_SCAN_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        mAutoScanActive.set(false);
        mMainHandler.removeCallbacks(mAutoScanTick);
        super.onPause();
    }

    private void scanPortsAsync(boolean fromAutoPoll) {
        mScanExecutor.execute(() -> {
            final List<Integer> listening = LocalhostPortScanner.scanListeningPorts(this);
            final List<Integer> chips = PreviewPortPrefs.orderChips(this, listening);
            mMainHandler.post(() -> {
                if (fromAutoPoll && mListeningSeeded) {
                    Integer newly = null;
                    for (Integer p : listening) {
                        if (!mKnownListening.contains(p)
                            && LocalhostPortScanner.isPreferredPort(this, p)) {
                            newly = p;
                            break;
                        }
                    }
                    if (newly != null && newly != mCurrentPort) {
                        final int offer = newly;
                        View root = findViewById(android.R.id.content);
                        if (root != null) {
                            Snackbar.make(root, getString(R.string.msg_preview_new_port, offer),
                                    Snackbar.LENGTH_LONG)
                                .setAction(R.string.action_open_preview, v -> {
                                    mPortInput.setText(String.valueOf(offer));
                                    loadPort(offer);
                                    refreshLanHint();
                                })
                                .show();
                        }
                    }
                }
                mKnownListening.clear();
                mKnownListening.addAll(listening);
                mListeningSeeded = true;
                renderPortChips(chips, listening);
                refreshLanHint();
            });
        });
    }

    private void renderPortChips(@NonNull List<Integer> ports,
                                 @NonNull List<Integer> listening) {
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
        Set<Integer> listenSet = new HashSet<>(listening);
        int pad = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        for (final Integer port : ports) {
            Button chip = new Button(this, null, android.R.attr.buttonStyleSmall);
            boolean starred = PreviewPortPrefs.isStarred(this, port);
            String label = starred ? ("★ " + port) : String.valueOf(port);
            if (!listenSet.contains(port)) {
                label = label + "·";
            }
            chip.setText(label);
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
                showPortActions(port);
                return true;
            });
            mPortChips.addView(chip);
        }
    }

    private void showPortActions(final int port) {
        boolean starred = PreviewPortPrefs.isStarred(this, port);
        final String[] items = new String[] {
            getString(R.string.action_preview_copy_lan),
            getString(starred ? R.string.action_preview_unstar : R.string.action_preview_star),
            getString(R.string.action_preview_forget)
        };
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_preview_port_actions, port))
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0:
                        copyLanUrlForPort(port);
                        break;
                    case 1:
                        PreviewPortPrefs.toggleStar(this, port);
                        Toast.makeText(this,
                            getString(starred
                                ? R.string.msg_preview_port_unstarred
                                : R.string.msg_preview_port_starred, port),
                            Toast.LENGTH_SHORT).show();
                        scanPortsAsync(false);
                        break;
                    case 2:
                        PreviewPortPrefs.forgetPort(this, port);
                        Toast.makeText(this,
                            getString(R.string.msg_preview_port_forgotten, port),
                            Toast.LENGTH_SHORT).show();
                        scanPortsAsync(false);
                        break;
                    default:
                        break;
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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

    private void reloadCurrent() {
        if (mShowingErrorPage) {
            loadPort(mCurrentPort);
        } else if (mWebView != null) {
            mWebView.reload();
        }
    }

    private void loadPort(int port) {
        mCurrentPort = port;
        PreviewPortPrefs.addRecent(this, port);
        String path = mCurrentPathAndQuery;
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String url = "http://127.0.0.1:" + port + path;
        mShowingErrorPage = false;
        mWebView.loadUrl(url);
    }

    private void updatePathFromUrl(@Nullable String url) {
        if (url == null || url.startsWith("data:") || url.startsWith("about:")) {
            return;
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) {
                return;
            }
            String lower = host.toLowerCase(Locale.US);
            if (!"127.0.0.1".equals(lower) && !"localhost".equals(lower)) {
                return;
            }
            String path = uri.getEncodedPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = uri.getEncodedQuery();
            mCurrentPathAndQuery = query != null && !query.isEmpty()
                ? path + "?" + query : path;
            int port = uri.getPort();
            if (port > 0) {
                mCurrentPort = port;
            }
        } catch (Exception ignored) {
            // keep previous path
        }
    }

    private void showPreviewErrorPage(int port) {
        if (mWebView == null) {
            return;
        }
        mShowingErrorPage = true;
        String html = ""
            + "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>Preview</title>"
            + "<style>body{font-family:sans-serif;background:#111;color:#ddd;"
            + "padding:1.2rem;line-height:1.45}h1{font-size:1.1rem;color:#9f9}"
            + "code{background:#222;padding:.1rem .3rem;border-radius:3px}"
            + "li{margin:.4rem 0}</style></head><body>"
            + "<h1>Nothing on :" + port + "</h1>"
            + "<p>Preview could not load "
            + "<code>http://127.0.0.1:" + port + escapeHtml(mCurrentPathAndQuery)
            + "</code>.</p>"
            + "<ul>"
            + "<li>Is <code>bun run dev</code> / <code>td-dev</code> running?</li>"
            + "<li>Bind on <code>0.0.0.0</code> (not only loopback) for Copy LAN.</li>"
            + "<li>Use <b>Scan</b> to find listening ports, then <b>Reload</b>.</li>"
            + "<li>OpenCode: <code>td-ai</code> → health on <code>:4096</code>.</li>"
            + "</ul>"
            + "<p>Toolbar: Scan · Reload · Copy LAN</p>"
            + "</body></html>";
        mWebView.loadDataWithBaseURL("http://127.0.0.1:" + port + "/",
            html, "text/html", "utf-8", null);
    }

    @NonNull
    private static String escapeHtml(@NonNull String s) {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static boolean isAllowedLoopbackUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
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
    protected void onDestroy() {
        mAutoScanActive.set(false);
        mMainHandler.removeCallbacks(mAutoScanTick);
        mScanExecutor.shutdownNow();
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
