package com.invapp.app.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.invapp.R;
import com.invapp.app.utils.AiSessionHelper;
import com.invapp.app.utils.LanShareHelper;
import com.invapp.app.utils.LocalhostPortScanner;
import com.invapp.app.utils.PreviewPortPrefs;
import com.invapp.app.utils.WorkflowHelper;
import com.invapp.shared.interact.ShareUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private SwipeRefreshLayout mSwipeRefresh;
    private ProgressBar mProgress;
    private TextView mStatus;
    private EditText mPortInput;
    private LinearLayout mPortChips;
    private TextView mPortsEmpty;
    private TextView mLanHint;
    private Button mBackButton;
    private Button mForwardButton;
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
        Button clearButton = findViewById(R.id.preview_clear_button);
        Button browserButton = findViewById(R.id.preview_browser_button);
        mBackButton = findViewById(R.id.preview_back_button);
        mForwardButton = findViewById(R.id.preview_forward_button);
        mPortChips = findViewById(R.id.preview_port_chips);
        mPortsEmpty = findViewById(R.id.preview_ports_empty);
        mLanHint = findViewById(R.id.preview_lan_hint);
        mStatus = findViewById(R.id.preview_status);
        mProgress = findViewById(R.id.preview_progress);
        mWebView = findViewById(R.id.preview_webview);
        mSwipeRefresh = findViewById(R.id.preview_swipe_refresh);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return true;
                }
                return shouldOverrideUrlLoading(view, request.getUrl().toString());
            }

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
                syncUrlBar(url);
                if (mProgress != null) {
                    mProgress.setVisibility(View.VISIBLE);
                    mProgress.setProgress(10);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                stopRefreshIndicator();
                if (mProgress != null) {
                    mProgress.setVisibility(View.GONE);
                }
                updatePathFromUrl(url);
                syncUrlBar(url);
                updateNavButtons();
                refreshStatusIndicator();
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
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (mProgress != null) {
                    if (newProgress >= 100) {
                        mProgress.setVisibility(View.GONE);
                    } else if (mWebView != null && mWebView.getUrl() != null
                        && !mShowingErrorPage) {
                        mProgress.setVisibility(View.VISIBLE);
                        mProgress.setProgress(newProgress);
                    }
                }
            }
        });

        if (mSwipeRefresh != null) {
            mSwipeRefresh.setOnRefreshListener(this::reloadCurrent);
        }

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
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> clearPreviewData());
        }
        if (browserButton != null) {
            browserButton.setOnClickListener(v -> openCurrentInBrowser());
        }
        if (mBackButton != null) {
            mBackButton.setOnClickListener(v -> {
                if (mWebView != null && mWebView.canGoBack()) {
                    mWebView.goBack();
                }
            });
        }
        if (mForwardButton != null) {
            mForwardButton.setOnClickListener(v -> {
                if (mWebView != null && mWebView.canGoForward()) {
                    mWebView.goForward();
                }
            });
        }
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
        updateNavButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanPortsAsync(false);
        refreshLanHint();
        refreshStatusIndicator();
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
                refreshStatusIndicator();
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

    /** @return parsed target or {@code null} after toasting on error */
    @Nullable
    private WorkflowHelper.ParsedPreviewUrl readPreviewInputOrToast() {
        String raw = mPortInput.getText() != null
            ? mPortInput.getText().toString().trim() : "";
        WorkflowHelper.ParsedPreviewUrl parsed = WorkflowHelper.parsePreviewInput(raw);
        if (parsed == null) {
            Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (!parsed.isLoopback()) {
            // WebView is loopback-only; LAN URLs go to other devices via Copy LAN.
            Toast.makeText(this,
                getString(R.string.msg_preview_only_localhost)
                    + " — :" + parsed.port + " loaded locally",
                Toast.LENGTH_LONG).show();
        }
        return parsed;
    }

    /** @return port or {@code -1} after toasting on error */
    private int readPortInputOrToast() {
        WorkflowHelper.ParsedPreviewUrl parsed = readPreviewInputOrToast();
        return parsed == null ? -1 : parsed.port;
    }

    private void loadFromPortInput() {
        WorkflowHelper.ParsedPreviewUrl parsed = readPreviewInputOrToast();
        if (parsed == null) {
            return;
        }
        if (parsed.pathAndQuery != null && !parsed.pathAndQuery.isEmpty()) {
            String p = parsed.pathAndQuery;
            if (!p.startsWith("/")) {
                p = "/" + p;
            }
            mCurrentPathAndQuery = p;
        }
        loadPort(parsed.port);
        refreshLanHint();
    }

    private void reloadCurrent() {
        if (mSwipeRefresh != null && !mSwipeRefresh.isRefreshing()) {
            // Show spinner only for user-initiated reload; page callbacks hide it.
        }
        if (mShowingErrorPage) {
            loadPort(mCurrentPort);
        } else if (mWebView != null) {
            Map<String, String> headers = authHeaders();
            if (headers.isEmpty()) {
                mWebView.reload();
            } else {
                // reload() drops custom headers, so re-load current URL with auth.
                mWebView.loadUrl(currentUrl(), headers);
            }
        } else {
            stopRefreshIndicator();
        }
    }

    private void loadPort(int port) {
        boolean portChanged = port != mCurrentPort;
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
        if (mWebView == null) {
            return;
        }
        if (portChanged) {
            // Drop stale history so Back never resurrects another port's SPA shell
            // (the "ghost OpenCode UI" when :4096 is actually down).
            mWebView.clearHistory();
        }
        syncUrlBar(url);
        updateNavButtons();
        refreshStatusIndicator();
        Map<String, String> headers = authHeaders();
        if (headers.isEmpty()) {
            mWebView.loadUrl(url);
        } else {
            mWebView.loadUrl(url, headers);
        }
    }

    @NonNull
    private String currentUrl() {
        String path = mCurrentPathAndQuery;
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "http://127.0.0.1:" + mCurrentPort + path;
    }

    @NonNull
    private Map<String, String> authHeaders() {
        Map<String, String> out = new HashMap<>();
        try {
            String password = AiSessionHelper.readServicePassword();
            if (password != null && !password.isEmpty()) {
                String creds = "opencode:" + password;
                String basic = android.util.Base64.encodeToString(
                    creds.getBytes(StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP);
                out.put("Authorization", "Basic " + basic);
            }
        } catch (Exception ignored) {
            // No auth — plain loopback load.
        }
        return out;
    }

    private void syncUrlBar(@Nullable String url) {
        if (mPortInput == null || url == null) {
            return;
        }
        if (mPortInput.hasFocus()) {
            return;
        }
        if (url.startsWith("data:") || url.startsWith("about:")) {
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
            if (!url.equals(mPortInput.getText().toString())) {
                mPortInput.setText(url);
            }
        } catch (Exception ignored) {
            // keep user input
        }
    }

    private void updateNavButtons() {
        if (mWebView == null) {
            return;
        }
        if (mBackButton != null) {
            mBackButton.setEnabled(mWebView.canGoBack());
        }
        if (mForwardButton != null) {
            mForwardButton.setEnabled(mWebView.canGoForward());
        }
    }

    private void stopRefreshIndicator() {
        if (mSwipeRefresh != null && mSwipeRefresh.isRefreshing()) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    private void refreshStatusIndicator() {
        if (mStatus == null) {
            return;
        }
        final int port = mCurrentPort;
        mStatus.setText(getString(R.string.msg_preview_status_checking, port));
        mStatus.setVisibility(View.VISIBLE);
        mScanExecutor.execute(() -> {
            final boolean listening = AiSessionHelper.isPortListening(port);
            mMainHandler.post(() -> {
                if (port != mCurrentPort || mStatus == null) {
                    return;
                }
                if (listening) {
                    mStatus.setText(getString(R.string.msg_preview_status_up, port));
                } else {
                    mStatus.setText(getString(R.string.msg_preview_status_down, port));
                }
                mStatus.setVisibility(View.VISIBLE);
            });
        });
    }

    private void clearPreviewData() {
        if (mWebView == null) {
            return;
        }
        mWebView.clearCache(true);
        try {
            WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {
            // ignore
        }
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Exception ignored) {
            // ignore
        }
        Toast.makeText(this, R.string.msg_preview_cleared, Toast.LENGTH_SHORT).show();
        loadPort(mCurrentPort);
    }

    private void openCurrentInBrowser() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.msg_preview_invalid_port, Toast.LENGTH_SHORT).show();
        }
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
        stopRefreshIndicator();
        if (mProgress != null) {
            mProgress.setVisibility(View.GONE);
        }
        refreshStatusIndicator();
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
            + "<li>Pull down to retry. Use <b>Clear</b> if you see a stale app shell.</li>"
            + "</ul>"
            + "<p>Toolbar: Back · Fwd · Reload · Scan · Copy LAN · Clear · Browser</p>"
            + "</body></html>";
        // Null base URL on purpose: using http://127.0.0.1:port as base lets a
        // cached SPA shell resolve relative assets and masquerade as a live app
        // ("ghost OpenCode UI" with no projects). about:blank keeps it inert.
        mWebView.loadDataWithBaseURL(null,
            html, "text/html", "utf-8", null);
        updateNavButtons();
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
