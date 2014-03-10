package org.savemypics.android.authenticator;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import java.io.IOException;
import org.savemypics.android.glue.ABaseGlue;
import org.savemypics.android.util.CUtils;

public class CAuthenticator
    extends AbstractAccountAuthenticator
{
    public CAuthenticator(Context ctx)
    {
        super(ctx);
        m_appctx = ctx.getApplicationContext();
    }

    @Override
    public Bundle addAccount
        (AccountAuthenticatorResponse resp, String acct_type,
         String auth_type, String[] features, Bundle options)
        throws NetworkErrorException
    {
        CUtils.TLOGD(TAG, "add Account: "+acct_type);

        Intent intent = (new Intent(m_appctx, CSelectProviderActivity.class))
            .putExtra(AccountManager.KEY_ACCOUNT_TYPE, acct_type)
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, resp);
        Bundle ret = new Bundle();
        ret.putParcelable(AccountManager.KEY_INTENT, intent);
        return ret;
    }

    @Override
    public Bundle getAuthToken
        (AccountAuthenticatorResponse resp, Account acct,
         String auth_type, Bundle options)
        throws NetworkErrorException
    {
        CUtils.TLOGD(TAG, "get-auth-token: "+auth_type+" for "+ acct);

        if (!auth_type.equals(CUtils.AUTH_ACCESS)) {
            Bundle ret = new Bundle();
            ret.putString
                (AccountManager.KEY_ERROR_MESSAGE,
                 "Invalid auth type: "+auth_type);
            return ret;
        }

        // Start with any cached access strings.
        AccountManager amgr = AccountManager.get(m_appctx);
        String access_string = amgr.peekAuthToken(acct, auth_type);

        if (TextUtils.isEmpty(access_string)) {
            // Off to glue to see if it can regenerate it.
            String refresh_string = amgr.getPassword(acct);
            if (!TextUtils.isEmpty(refresh_string)) {
                access_string = ABaseGlue.maybeRefresh
                    (m_appctx, amgr, acct, refresh_string);
            }
        }
        else {
            CUtils.LOGD(TAG, "Using cached access-token");
        }

        if (!TextUtils.isEmpty(access_string)) {
            // yay.
            Bundle ret = new Bundle();
            ret.putString(AccountManager.KEY_ACCOUNT_TYPE, acct.type);
            ret.putString(AccountManager.KEY_ACCOUNT_NAME, acct.name);
            ret.putString(AccountManager.KEY_AUTHTOKEN, access_string);
            return ret;
        }

        // Oh well. We now need to interact with the user and get them
        // to enter their password again. Return a KEY_INTENT with the
        // appropriate target activity and data filled in.
        CUtils.LOGD
            (TAG, "Unable to auto-refresh token - user intervention needed");

        Intent intent = ABaseGlue.makeLoginIntent(m_appctx, acct.type)
            .putExtra(AccountManager.KEY_ACCOUNT_TYPE, acct.type)
            .putExtra(AccountManager.KEY_ACCOUNT_NAME, acct.name)
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, resp);
        Bundle ret = new Bundle();
        ret.putParcelable(AccountManager.KEY_INTENT, intent);
        return ret;
    }

    @Override
    public Bundle updateCredentials
        (AccountAuthenticatorResponse resp, Account acct,
         String auth_type, Bundle options)
    {
        CUtils.TLOGD(TAG, "update-credentials - nop");
        return null;
    }

    @Override
    public Bundle confirmCredentials
        (AccountAuthenticatorResponse resp, Account acct, Bundle options)
    {
        CUtils.TLOGD(TAG, "confirm-credentials - nop");
        return null;
    }

    @Override
    public Bundle editProperties
        (AccountAuthenticatorResponse resp, String acct_type)
    { throw new UnsupportedOperationException(); }

    @Override
    public Bundle hasFeatures
        (AccountAuthenticatorResponse resp, Account acct, String[] features)
        throws NetworkErrorException
    {
        Bundle ret = new Bundle();
        ret.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return ret;
    }

    @Override
    public String getAuthTokenLabel(String auth_type)
    { return auth_type + " (Label)"; }

    private final Context m_appctx;
    private final static String TAG = CUtils.makeLogTag(CAuthenticator.class);
}
