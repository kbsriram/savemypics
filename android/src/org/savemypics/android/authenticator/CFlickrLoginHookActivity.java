package org.savemypics.android.authenticator;

// Prepare tokens, extract auth-tokens and delegate to CFlickrWebView
// to do the actual login.

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import java.util.Map;
import org.savemypics.android.R;
import org.savemypics.android.activity.AActivity;
import org.savemypics.android.event.CExceptionEvent;
import org.savemypics.android.glue.CFlickrGlue;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;

public class CFlickrLoginHookActivity extends AActivity
    implements CFlickrGlue.RequestTokenEvent.Listener,
               CFlickrGlue.AccessTokenEvent.Listener
{
    public void onRequestToken(CFlickrGlue.RequestTokenEvent rte)
    {
        // Launch webview after saving request tokens.
        showProgress(false, 0);
        m_waiting_req = false;
        m_token = rte.getRequestToken().getToken();
        m_secret = rte.getRequestToken().getSecret();
        m_callback = rte.getRequestToken().getCallback();

        Intent intent = new Intent(this, CFlickrWebActivity.class);
        intent.putExtra
            (CFlickrWebActivity.KEY_TARGET_URL,
             "http://m.flickr.com/services/oauth/authorize?"+
             "oauth_token="+m_token);
        intent.putExtra
            (CFlickrWebActivity.KEY_CALLBACK_URL, m_callback);
        startActivityForResult(intent, REQ_WEB);
    }

    public void onAccessToken(CFlickrGlue.AccessTokenEvent rte)
    {
        showProgress(false, 0);
        m_waiting_access = false;
        // Great - we have everything we need.
        String name = CFlickrGlue.addAccount
            (getApplicationContext(), rte.getAccessToken());

        Intent intent = new Intent()
            .putExtra(CSelectProviderActivity.KEY_ACCOUNT_NAME, name)
            .putExtra
                 (CSelectProviderActivity.KEY_ACCOUNT_TYPE,
                  CUtils.FLICKR_ACCOUNT_TYPE);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onCreate(Bundle saved)
    {
        super.onCreate(saved);
        setContentView(R.layout.flickr_login_hook);

        m_form = findViewById(R.id.flickr_login_form);
        m_status = findViewById(R.id.flickr_hook_status);
        m_status_msg = (TextView)
            m_status.findViewById(R.id.flickr_hook_status_message);

        findViewById(R.id.flickr_login_button).setOnClickListener
            (new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        attemptLogin();
                    }
                });

        // restore any saved state.
        if (saved != null) {
            m_waiting_req = saved.getBoolean(WAITING_REQ, false);
            m_waiting_access = saved.getBoolean(WAITING_ACCESS, false);
            m_token = saved.getString(TOKEN);
            m_secret = saved.getString(SECRET);
            m_callback = saved.getString(CALLBACK);
        }
        else {
            m_waiting_req = false;
            m_waiting_access = false;
            m_token = null;
            m_secret = null;
            m_callback = null;
        }

        runTasksForState();
    }

    @Override
    public void onException(CExceptionEvent ev)
    {
        m_waiting_req = false;
        m_waiting_access = false;
        showProgress(false, 0);
        super.onException(ev);
    }

    @Override
    protected void onPause()
    {
        CFlickrGlue.RequestTokenEvent.unsubscribe(this);
        CFlickrGlue.AccessTokenEvent.unsubscribe(this);
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        CFlickrGlue.RequestTokenEvent.subscribe(this);
        CFlickrGlue.AccessTokenEvent.subscribe(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle data)
    {
        super.onSaveInstanceState(data);
        if (m_token != null) {
            // save current tokens.
            data.putString(TOKEN, m_token);
            data.putString(SECRET, m_secret);
            data.putString(CALLBACK, m_callback);
        }
        data.putBoolean(WAITING_REQ, m_waiting_req);
        data.putBoolean(WAITING_ACCESS, m_waiting_access);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data)
    {
        if (req != REQ_WEB) {
            super.onActivityResult(req, res, data);
            return;
        }

        m_waiting_req = false;
        if (res != RESULT_OK) {
            setResult(res);
            finish();
            return;
        }

        if (data == null) {
            showErrorDialog("Empty data returned");
            return;
        }

        String qry = data.getStringExtra(CFlickrWebActivity.KEY_TOKEN_PARAMS);
        if (TextUtils.isEmpty(qry)) {
            showErrorDialog("Empty data returned");
            return;
        }

        Map<String,String> params = CIOUtils.queryToMap(qry);
        String otok = params.get("oauth_token");
        String over = params.get("oauth_verifier");
        if (TextUtils.isEmpty(otok) ||
            TextUtils.isEmpty(over)) {
            showErrorDialog("Missing oauth parameters: "+qry);
            return;
        }

        if (!otok.equals(m_token)) {
            showErrorDialog("Mismatched oauth tokens!");
            return;
        }

        // Ok - kick off exchange sequence.
        m_waiting_access = true;
        showProgress(true, R.string.flickr_waiting_for_access);
        CFlickrGlue.asyncAccessToken
            (getApplicationContext(), m_token, m_secret, over);
    }

    private final void attemptLogin()
    {
        if (!CUtils.isAnyNetworkAvailable(this)) {
            showDialog(ERROR_WIFI_DIALOG);
            return;
        }

        m_waiting_req = true;
        showProgress(true, R.string.flickr_waiting_for_request);
        CFlickrGlue.asyncRequestToken(getApplicationContext());
    }

    private void runTasksForState()
    {
        if (m_waiting_req) {
            showProgress(true, R.string.flickr_waiting_for_request);
        }
        else if (m_waiting_access) {
            showProgress(true, R.string.flickr_waiting_for_access);
        }
    }

    private void showProgress(final boolean show, int id)
    {
        if (show) {
            m_status.setVisibility(View.VISIBLE);
            m_form.setVisibility(View.GONE);
            m_status_msg.setText(id);
        }
        else {
            m_status.setVisibility(View.GONE);
            m_form.setVisibility(View.VISIBLE);
            m_status_msg.setText("");
        }
    }

    private View m_status;
    private View m_form;
    private TextView m_status_msg;

    private final static int REQ_WEB = 100;

    private boolean m_waiting_req = false;
    private boolean m_waiting_access = false;
    private String m_token = null;
    private String m_secret = null;
    private String m_callback = null;

    private final static String WAITING_REQ = "waiting_req";
    private final static String WAITING_ACCESS = "waiting_access";
    private final static String TOKEN = "token";
    private final static String SECRET = "SECRET";
    private final static String CALLBACK = "callback";

    private final static String TAG =
        CUtils.makeLogTag(CFlickrLoginHookActivity.class);
}
