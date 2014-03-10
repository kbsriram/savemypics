package org.savemypics.android.authenticator;

// Wrap a webview that finish() by returning
// oauth tokens [if it sees it]

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.savemypics.android.R;
import org.savemypics.android.util.CUtils;

public class CFlickrWebActivity extends Activity
{
    final static String KEY_TARGET_URL =
        "org.savemypics.android.cfwa_target_url";
    final static String KEY_CALLBACK_URL =
        "org.savemypics.android.cfwa_callback_url";
    final static String KEY_TOKEN_PARAMS =
        "org.savemypics.android.cfwa_token_params";

    @Override
    protected void onCreate(Bundle saved)
    {
        super.onCreate(saved);
        final String target = getIntent().getStringExtra(KEY_TARGET_URL);
        final String callback = getIntent().getStringExtra(KEY_CALLBACK_URL);
        if (TextUtils.isEmpty(target) ||
            TextUtils.isEmpty(callback)) {
            CUtils.LOGW(TAG, "Unexpected empty intent");
            setResult(RESULT_CANCELED);
            finish();
        }

        setContentView(R.layout.flickr_web);
        m_wv = (WebView) findViewById(R.id.flickr_webview);

        //CUtils.LOGD(TAG, "target="+target);
        //CUtils.LOGD(TAG, "callback="+callback);

        WebSettings ws = m_wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setUserAgentString("savemypics/1.0");

        m_wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading
                        (WebView view, String url) {
                        if (url.startsWith(callback)) {
                            onFinishWith(url);
                            return true;
                        }
                        else {
                            //CUtils.LOGD(TAG, "skip: "+url);
                            return false;
                        }
                    }
            });

        // Remove all cookies before starting.
        CookieSyncManager.createInstance(this);
        CookieManager cmgr = CookieManager.getInstance();
        cmgr.removeAllCookie();

        m_wv.loadUrl(target);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && m_wv.canGoBack()) {
            m_wv.goBack();
            return true;
        }
        else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void onFinishWith(String url)
    {
        Uri uri = Uri.parse(url);

        Intent intent = new Intent().putExtra(KEY_TOKEN_PARAMS, uri.getQuery());
        setResult(RESULT_OK, intent);
        finish();            
    }

    private WebView m_wv;
    private final static String TAG =
        CUtils.makeLogTag(CFlickrWebActivity.class);
}
