package com.mahroch.tapeapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Generic WebView screen. Receives the target URL + a display title via
 * Intent extras from MainActivity, so the same screen serves both links.
 */
public class WebViewActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;

    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 5173;
    private static final int PERMISSIONS_REQUEST_CODE = 4242;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (title != null) {
                actionBar.setTitle(title);
            }
        }

        requestNeededPermissions();

        webView = findViewById(R.id.webview);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        progressBar = findViewById(R.id.progress_bar);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        // Allow a wider zoom-out range. The on-page viewport meta tag is also
        // relaxed in onPageFinished below because Apps Script pages often
        // provide their own restrictive viewport settings.
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
        }
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " TapeApp/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String finishedUrl) {
                super.onPageFinished(view, finishedUrl);
                swipeRefreshLayout.setRefreshing(false);
                progressBar.setVisibility(android.view.View.GONE);

                // Apps Script / responsive pages can cap the minimum zoom
                // through <meta name="viewport">. Relax it so pinch-zoom
                // can zoom out farther on phones and tablets.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    view.evaluateJavascript(
                            "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
                            "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                            "m.setAttribute('content','width=device-width, initial-scale=1, minimum-scale=0.25, maximum-scale=5, user-scalable=yes');" +
                            "})()", null);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Do NOT bypass SSL errors in production; kept strict.
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(android.view.View.GONE);
                } else {
                    progressBar.setVisibility(android.view.View.VISIBLE);
                }
            }

            // Needed so the HTML <input type="file"> (image upload) opens the
            // camera/gallery picker instead of doing nothing.
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                              FileChooserParams fileChooserParams) {
                if (WebViewActivity.this.filePathCallback != null) {
                    WebViewActivity.this.filePathCallback.onReceiveValue(null);
                }
                WebViewActivity.this.filePathCallback = filePathCallback;

                android.content.Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (android.content.ActivityNotFoundException e) {
                    WebViewActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Pull-to-refresh is intentionally restricted to the real top of the
        // WebView. This prevents a downward drag anywhere in the page from
        // being mistaken for a refresh gesture.
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (webView.canScrollVertically(-1)) {
                swipeRefreshLayout.setRefreshing(false);
                return;
            }
            webView.reload();
        });

        // Keep the refresh gesture disabled unless the WebView is actually
        // at the top. The custom SwipeRefreshLayout also checks this during
        // touch interception, so it remains correct while the user is
        // scrolling or zooming.
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                swipeRefreshLayout.setEnabled(scrollY == 0 && !webView.canScrollVertically(-1)));

        if (savedInstanceState == null && url != null) {
            webView.loadUrl(url);
        }
    }

    private void requestNeededPermissions() {
        String[] perms = new String[]{ Manifest.permission.CAMERA };
        boolean needed = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed = true;
                break;
            }
        }
        if (needed) {
            ActivityCompat.requestPermissions(this, perms, PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
