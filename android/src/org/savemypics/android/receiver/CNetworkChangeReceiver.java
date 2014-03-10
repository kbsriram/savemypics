package org.savemypics.android.receiver;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import org.savemypics.android.util.CUtils;

public class CNetworkChangeReceiver extends BroadcastReceiver
{
    // This needs to be a quick call
    @Override
    public void onReceive(Context ctx, Intent intent)
    {
        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals
            (intent.getAction())) {
            NetworkInfo info = (NetworkInfo)
                intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (NetworkInfo.State.CONNECTED.equals(info.getState())) {
                final Handler handler = new Handler();
                final Context appctx = ctx.getApplicationContext();
                handler.postDelayed(new Runnable() {
                        public void run() {
                            requestSync(appctx);
                        }
                    }, NETWORK_DELAY_START_MSEC);
            }
        }
    }

    private final static void requestSync(Context appctx)
    {
        AccountManager mgr = AccountManager.get(appctx);
        requestSyncAccount(mgr, CUtils.BASE_ACCOUNT_TYPE);
    }

    private final static void requestSyncAccount
        (AccountManager amgr, String atype)
    {
        Account[] accounts = amgr.getAccountsByType(atype);
        if (accounts == null) { return; }

        for (int i = 0; i < accounts.length; i++) {
            Account acct = accounts[i];
            // Respect settings.
            if (!ContentResolver.getSyncAutomatically
                (acct, MediaStore.AUTHORITY)) {
                CUtils.LOGD(TAG, "Skip "+acct+" - sync not enabled");
                continue;
            }
            if (ContentResolver.getIsSyncable(acct, MediaStore.AUTHORITY)<= 0) {
                CUtils.LOGD(TAG, "Skip "+acct+" - not synced with mediastore");
                continue;
            }
            ContentResolver.requestSync
                (acct, MediaStore.AUTHORITY, new Bundle());
        }
    }

    // 90 seconds.
    private final static long NETWORK_DELAY_START_MSEC = 90*1000l;
    private final static String TAG =
        CUtils.makeLogTag(CNetworkChangeReceiver.class);
}
