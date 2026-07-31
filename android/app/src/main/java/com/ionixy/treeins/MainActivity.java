package com.ionixy.treeins;

import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

/**
 * Thin WebView wrapper around the bundled GoalTree PWA.
 *
 * The page is served through WebViewAssetLoader rather than file:///android_asset/.
 * That is not cosmetic: a file:// page gets an opaque origin, where localStorage is
 * either unavailable or needs the unsafe setAllowFileAccessFromFileURLs flag.
 * WebViewAssetLoader answers on a real https origin
 * (https://appassets.androidplatform.net), so storage behaves normally — and, critically,
 * that origin stays identical across app versions, which is the only reason an update can
 * keep the user's goals. Changing the host or the applicationId would silently orphan
 * every goal on the device.
 */
public class MainActivity extends AppCompatActivity {

    /** Must match ANDROID_ASSET_HOST in TreeIns.html, which uses it to skip the SW. */
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + ASSET_HOST + "/assets/TreeIns.html";

    /**
     * The app is a single page with no URL routing, so the system Back button has nothing
     * to navigate. Instead it closes whatever overlay is on top — matching what a user
     * expects Back to do — and only leaves the app when nothing is open. Returns "closed"
     * when it consumed the press.
     */
    private static final String CLOSE_TOP_LAYER_JS =
            "(function(){"
          + "  var sel='.overlay[data-close-modal],.overlay[data-close-nodepop],"
          + ".overlay[data-close-reminder],.congrats[data-close-congrats]';"
          + "  var xs=document.querySelectorAll(sel);"
          // render() appends overlays in order, so the last match is the topmost one.
          + "  if(xs.length){ xs[xs.length-1].click(); return 'closed'; }"
          // Non-overlay transient state (add-branch panel, armed delete) — the page
          // already cancels both on Escape.
          + "  if(document.querySelector('.add-panel, .icon-act.danger-armed')){"
          + "    document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));"
          + "    return 'closed';"
          + "  }"
          + "  return 'none';"
          + "})()";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);   // localStorage — where every goal lives

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript(CLOSE_TOP_LAYER_JS, value -> {
                    // evaluateJavascript hands back a JSON-encoded string, e.g. "\"closed\"".
                    if (value != null && value.contains("closed")) return;
                    // Nothing was open: behave like Home rather than finish(), so returning
                    // to the app resumes the same WebView instead of reloading the page.
                    moveTaskToBack(true);
                });
            }
        });

        if (savedInstanceState == null) {
            web.loadUrl(START_URL);
        }
    }

    /** Keeps the page (and its scroll/zoom state) alive across configuration changes. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (web.restoreState(savedInstanceState) == null) {
            web.loadUrl(START_URL);
        }
    }
}
