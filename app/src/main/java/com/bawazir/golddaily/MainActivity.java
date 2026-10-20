package com.bawazir.golddaily;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * النافذة الرئيسية: تعرض نموذج الويب داخل WebView وتوفّر جسور الطباعة والإغلاق.
 *
 * الجسور المتاحة لصفحة النموذج (window.AndroidApp):
 *   • printHtml(html)  — يطبع ورقة HTML عبر نظام الطباعة (طابعة أو حفظ PDF).
 *   • printPdf(base64)— يستقبل PDF جاهزاً (Base64) ويطبعه/يشاركه.
 *   • closeApp()      — ينهي التطبيق بالكامل ويزيله من التطبيقات الأخيرة.
 *
 * كما يرسل التطبيق رسالة "gold-close" عبر window.postMessage عند طلب الإغلاق،
 * ليعمل النموذج بنفس المنطق في المتصفح وفي التطبيق.
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    private static final String PRINT_JOB = "GoldDailyPrint";

    private WebView web;
    private ProgressBar progress;
    private FrameLayout errorBox;
    private String url;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) {
            url = Prefs.getUrl(this);
        }
        if (url == null || url.isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        web = findViewById(R.id.web);
        progress = findViewById(R.id.progress);
        errorBox = findViewById(R.id.errorBox);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " GoldDailyApp/1.0");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u == null) return false;
                if ("script.google.com".equals(u.getHost()) || "script.googleusercontent.com".equals(u.getHost())
                        || "accounts.google.com".equals(u.getHost())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                progress.setVisibility(View.GONE);
                errorBox.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // API 23+ — يغطي كل الأخطاء بما فيها أخطاء الإطارات الفرعية
                if (req != null && req.isForMainFrame()) {
                    showError();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView v, int errorCode, String description, String failingUrl) {
                // API 21-22 (أندرويد 5) — لا يوجد WebResourceRequest/WebResourceError
                // نتحقق أنها ليست NO_ERROR (‎-1) قبل إظهار شاشة الخطأ
                if (errorCode != -1) {
                    showError();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
                progress.setProgress(p);
            }
        });

        web.addJavascriptInterface(new Bridges(), "AndroidApp");

        errorBox.findViewById(R.id.retry).setOnClickListener(b -> load());
        errorBox.findViewById(R.id.changeUrl).setOnClickListener(b -> {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            load();
        }
    }

    private void load() {
        errorBox.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        web.loadUrl(url);
    }

    private void showError() {
        progress.setVisibility(View.GONE);
        errorBox.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    /** عند الرجوع: تنقّل داخل الصفحة إن أمكن، وإلا لا تخرج من التطبيق فوراً */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else if (web != null) {
            web.evaluateJavascript(
                    "if (typeof closeBtn !== 'undefined' && closeBtn) closeBtn.click();", null);
        } else {
            super.onBackPressed();
        }
    }

    /** جسور الصفحة ← التطبيق */
    private class Bridges {

        @JavascriptInterface
        public void printHtml(final String html) {
            runOnUiThread(() -> printHtmlInternal(html));
        }

        @JavascriptInterface
        public void printPdf(final String base64) {
            runOnUiThread(() -> {
                try {
                    printPdfInternal(base64);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, R.string.print_failed, Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void closeApp() {
            runOnUiThread(MainActivity.this::finishAndRemoveTask);
        }

        @JavascriptInterface
        public void toast(final String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }

    private WebView printWebView;

    private void printHtmlInternal(String html) {
        try {
            // نحتفظ بمرجع حتى لا يُحرَّر الـ WebView قبل انتهاء الطباعة
            printWebView = new WebView(this);
            printWebView.getSettings().setJavaScriptEnabled(false);
            printWebView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null);
            printWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String u) {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        String job = PRINT_JOB + " — " + getString(R.string.app_name);
                        PrintDocumentAdapter adapter = v.createPrintDocumentAdapter(job);
                        pm.print(job, adapter, new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build());
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, R.string.print_failed, Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, R.string.print_failed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * يستقبل PDF جاهزاً من الصفحة (Base64) ويحفظه في ذاكرة التخزين المؤقت ثم يشاركه،
     * حتى يمكن حفظه أو طباعته من أي تطبيق PDF مثبّت على الجهاز.
     */
    private void printPdfInternal(String base64) throws Exception {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        File dir = new File(getCacheDir(), "prints");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("mkdir failed");
        File f = new File(dir, "gold-summary-" + System.currentTimeMillis() + ".pdf");
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(bytes);
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
        share(uri);
    }

    private void share(Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_print_app, Toast.LENGTH_LONG).show();
        }
    }
}
