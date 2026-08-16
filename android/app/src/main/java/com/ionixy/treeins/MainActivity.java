package com.ionixy.treeins;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import java.util.Locale;

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

    /** Latest window insets in CSS pixels, mirrored into the page's --safe-* properties. */
    private float safeT, safeB, safeL, safeR;
    /** evaluateJavascript before the document exists is a no-op, so pushes are deferred. */
    private boolean pageReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw edge to edge on purpose. Android 15+ enforces this for anyone targeting
        // SDK 35, so opting out only postpones the problem; owning it means the page's own
        // translucent bars sit behind the status and gesture bars the way they do in an
        // iOS standalone PWA — and the insets below are what stop content hiding under them.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Without this Android paints its own scrim over a transparent gesture bar,
            // which would show as a grey band under the tab bar.
            getWindow().setNavigationBarContrastEnforced(false);
        }

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

        // Safe to expose: this WebView only ever loads the bundled assets, and the app has
        // no INTERNET permission, so no remote page can exist to reach the bridge.
        web.addJavascriptInterface(new Host(), "GoalTreeHost");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageReady = false;   // the new document has none of our properties yet
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                pushSafeArea();
            }
        });

        // Android WebView reports env(safe-area-inset-*) as 0 for the status and gesture
        // bars — there it only ever describes a display cutout. So the real insets are
        // handed to the page as inline custom properties on <html>, which override the
        // env()-based defaults in its :root. Fires again whenever the insets change
        // (gesture/3-button navigation switch, cutout, split screen).
        // Listener on the decor view, not on the WebView: AppCompat's own root layout
        // consumes insets on some API levels before they reach the content child, and a
        // listener that never fires would leave every inset at 0 — the original bug.
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            // ime() is in the mask because an edge-to-edge window is not guaranteed to be
            // resized for the keyboard. getInsets() takes the larger value per side, so
            // this is right either way: if the window did resize, the keyboard is outside
            // it and reports 0; if it didn't, the bottom inset becomes the keyboard's
            // height and the tab bar, sheets and toasts ride above it instead of vanishing.
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            float density = getResources().getDisplayMetrics().density;
            safeT = bars.top / density;
            safeB = bars.bottom / density;
            safeL = bars.left / density;
            safeR = bars.right / density;
            pushSafeArea();
            return insets;   // not consumed: nothing else in this window needs them
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

    /**
     * Writes the current insets into the page. The page's viewport is width=device-width
     * at initial-scale 1, so one CSS pixel is one dp — hence the density division above.
     * Locale.US is not optional: formatting under a locale with a comma decimal separator
     * would emit "24,5px", which CSS drops on the floor, silently restoring the bug.
     */
    private void pushSafeArea() {
        if (web == null || !pageReady) return;
        String js = "(function(){var s=document.documentElement.style;"
                + prop("--safe-t", safeT)
                + prop("--safe-b", safeB)
                + prop("--safe-l", safeL)
                + prop("--safe-r", safeR)
                + "})()";
        web.evaluateJavascript(js, null);
    }

    private static String prop(String name, float cssPx) {
        return String.format(Locale.US, "s.setProperty('%s','%.2fpx');", name, cssPx);
    }

    /**
     * The page's side of the bridge — see applyTheme() in TreeIns.html. Public because
     * the WebView reaches its methods by reflection, which a private class can hide.
     */
    public class Host {
        /**
         * The status and gesture bar icons are drawn by the system over our content, so
         * only the system can tint them — and it has no idea which theme the user picked
         * inside the app. Called on every theme change, including the automatic one.
         */
        @JavascriptInterface
        public void setTheme(final String theme) {
            final boolean dark = "dark".equals(theme);
            // Arrives on the WebView's JS thread; window flags are main-thread only.
            runOnUiThread(() -> {
                WindowInsetsControllerCompat controller =
                        WindowCompat.getInsetsController(getWindow(), web);
                controller.setAppearanceLightStatusBars(!dark);
                controller.setAppearanceLightNavigationBars(!dark);
            });
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
