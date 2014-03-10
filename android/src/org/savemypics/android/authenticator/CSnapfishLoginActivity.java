package org.savemypics.android.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import java.io.IOException;
import org.savemypics.android.R;
import org.savemypics.android.activity.AActivity;
import org.savemypics.android.event.CExceptionEvent;
import org.savemypics.android.glue.CSnapfishGlue;
import org.savemypics.android.util.CUtils;

public class CSnapfishLoginActivity extends AActivity
    implements CSnapfishGlue.LoginEvent.Listener
{
    @Override
    protected void onCreate(Bundle saved)
    {
        super.onCreate(saved);
        setContentView(R.layout.snapfish_login);

        m_acctmgr = AccountManager.get(getApplicationContext());
        m_email = null;
        m_edit_email = (EditText) findViewById(R.id.login_email);

        m_edit_pass = (EditText) findViewById(R.id.snapfish_login_password);
        m_edit_pass.setOnEditorActionListener
            (new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction
                        (TextView tv, int id, KeyEvent ev) {
                        if ((id == R.id.snapfish_login) ||
                            (id == EditorInfo.IME_NULL)) {
                            attemptLogin();
                            return true;
                        }
                        return false;
                    }
                });

        m_form = findViewById(R.id.snapfish_login_form);
        m_status = findViewById(R.id.snapfish_login_status);
        m_status_msg = (TextView) findViewById
            (R.id.snapfish_login_status_message);

        findViewById(R.id.snapfish_login_button).setOnClickListener
            (new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        attemptLogin();
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle data)
    {
        super.onSaveInstanceState(data);
        data.putBoolean(LOGIN_IN_PROGRESS, m_login_in_progress);
    }

    @Override
    protected void onRestoreInstanceState(Bundle data)
    {
        super.onRestoreInstanceState(data);
        if (data != null) {
            m_login_in_progress = data.getBoolean(LOGIN_IN_PROGRESS, false);
        }
    }

    @Override
    protected void onPause()
    {
        CSnapfishGlue.LoginEvent.unsubscribe(this);
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        CSnapfishGlue.LoginEvent.subscribe(this);
    }

    public void onLogin(CSnapfishGlue.LoginEvent ev)
    {
        m_login_in_progress = false;
        showProgress(false);

        if (ev.wasSuccessful()) {
            onSuccessLogin(ev.getName());
        }
        else {
            onFailedLogin(ev);
        }
    }

    @Override
    public void onException(CExceptionEvent ev)
    {
        m_login_in_progress = false;
        showProgress(false);
        super.onException(ev);
    }

    private void onFailedLogin(CSnapfishGlue.LoginEvent ev)
    {
        CUtils.LOGD(TAG, "Failed to login");

        if (ev.getError() != null) {
            CUtils.LOGD(TAG, "auth-error: "+ev.getError());
            // First see if we have some kind of auth failure.
            // We can retry this right away.
            m_edit_pass.setError(ev.getError());
            m_edit_pass.requestFocus();
            return;
        }

        if (ev.getIssue() != null) {
            showErrorDialog(ev.getIssue());
        }
        else {
            showErrorDialog("Unknown error, woops.");
        }
    }

    private void onSuccessLogin(String name)
    {
        Intent intent = new Intent()
            .putExtra(CSelectProviderActivity.KEY_ACCOUNT_NAME, name)
            .putExtra
            (CSelectProviderActivity.KEY_ACCOUNT_TYPE,
             CUtils.SNAPFISH_ACCOUNT_TYPE);
        setResult(RESULT_OK, intent);
        finish();
    }

    private final void attemptLogin()
    {
        m_edit_email.setError(null);
        m_edit_pass.setError(null);

        // Ensure we have any network connectivity.
        // We don't enforce wifi settings, with the
        // theory being that login is important enough
        // and doesn't use enough resources to be a
        // huge issue.
        if (!CUtils.isAnyNetworkAvailable(this)) {
            showDialog(ERROR_WIFI_DIALOG);
            return;
        }

        m_email = m_edit_email.getText().toString();
        String pass = m_edit_pass.getText().toString();

        boolean cancel = false;
        View focus = null;

        // Check for a valid password.
        if (TextUtils.isEmpty(pass)) {
            m_edit_pass.setError(getString(R.string.error_field_required));
            focus = m_edit_pass;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(m_email)) {
            m_edit_email.setError(getString(R.string.error_field_required));
            focus = m_edit_email;
            cancel = true;
        }
        else if (!m_email.contains("@")) {
            m_edit_email.setError(getString(R.string.error_invalid_email));
            focus = m_edit_email;
            cancel = true;
        }

        if (cancel) {
            focus.requestFocus();
        }
        else {
            // Kick off an async task to login our user.
            m_status_msg.setText(R.string.login_in_progress);
            showProgress(true);
            CSnapfishGlue.asyncLogin(this, m_acctmgr, m_email, pass);
        }
    }

    private void showProgress(final boolean show)
    {
        m_status.setVisibility(show ? View.VISIBLE : View.GONE);
        m_form.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private EditText m_edit_email;
    private EditText m_edit_pass;
    private View m_form;
    private View m_status;
    private TextView m_status_msg;

    private String m_email;
    private AccountManager m_acctmgr = null;
    private boolean m_login_in_progress = false;

    private final static String LOGIN_IN_PROGRESS = "l_i_p";
    private final static String TAG =
        CUtils.makeLogTag(CSnapfishLoginActivity.class);
}
