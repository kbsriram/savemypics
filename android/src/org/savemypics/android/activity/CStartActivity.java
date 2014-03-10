package org.savemypics.android.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;
import org.savemypics.android.R;
import org.savemypics.android.util.CUtils;

public class CStartActivity extends AActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        m_account_adapter =
            new CAccountInfoListAdapter(this, getAccounts());
        ((ListView) findViewById(R.id.main_list))
            .setAdapter(m_account_adapter);
        // Save app version-string somewhere easy to obtain.
        try {
            PackageInfo pi =
                getPackageManager().getPackageInfo(getPackageName(), 0);
            m_version = String.valueOf(pi.versionCode)+"/"+pi.versionName;
        }
        catch (Throwable ign) {
            CUtils.LOGD(TAG, "Skip version data", ign);
            m_version = "unknown";
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        m_account_adapter.onResume(getAccounts());
    }

    @Override
    protected void onPause()
    {
        m_account_adapter.onPause();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data)
    {
        CUtils.LOGD(TAG, "on-activity-result: "+res+":"+data);
    }

    void startAddAccount(Intent intent)
    { startActivityForResult(intent, REQ_ADD_ACCOUNT); }

    String getVersion()
    { return m_version; }

    private final List<Account> getAccounts()
    {
        AccountManager mgr = AccountManager.get(this);
        List<Account> ret = new ArrayList<Account>();

        Account[] accounts = mgr.getAccountsByType(CUtils.BASE_ACCOUNT_TYPE);
        for (Account acct: accounts) {
            ret.add(acct);
        }
        return ret;
    }

    private CAccountInfoListAdapter m_account_adapter;
    private String m_version = null;
    private final static int REQ_ADD_ACCOUNT = 1;
    private final static String TAG = CUtils.makeLogTag(CStartActivity.class);
}
