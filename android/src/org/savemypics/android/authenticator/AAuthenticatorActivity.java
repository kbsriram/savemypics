package org.savemypics.android.authenticator;

import android.os.Bundle;
import android.app.Dialog;
import android.content.DialogInterface;
import android.accounts.AccountAuthenticatorActivity;
import org.savemypics.android.R;
import org.savemypics.android.event.CExceptionEvent;
import org.savemypics.android.util.CUtils;

public abstract class AAuthenticatorActivity
    extends AccountAuthenticatorActivity
    implements CExceptionEvent.Listener
{
    @Override
    protected void onPause()
    {
        CExceptionEvent.unsubscribe(this);
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        CExceptionEvent.subscribe(this);
    }

    public void onException(CExceptionEvent ev)
    {
        StringBuilder sb = new StringBuilder();
        if (ev.getMessage() != null) {
            sb.append(ev.getMessage());
            sb.append("\n");
        }
        sb.append(ev.getCause().toString());
        showGeneralErrorDialog(sb.toString());
    }

    private void showGeneralErrorDialog(String msg)
    {
        final Bundle bundle = new Bundle();
        bundle.putString(ERROR_MESSAGE, msg);

        runOnUiThread(new Runnable() {
                public void run() {
                    showDialog(ERROR_DIALOG, bundle);
                }
            });
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle)
    {
        switch (id) {

        case ERROR_WIFI_DIALOG:
            return CUtils.makeEnableWifiDialog
                (this, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int id) {
                            d.cancel();
                            setResult(RESULT_CANCELED);
                            AAuthenticatorActivity.this.finish();
                        }
                    });

        case ERROR_DIALOG:
            String msg = bundle.getString(ERROR_MESSAGE);
            if (msg != null) {
                return CUtils.makeAlertDialog
                    (this, "Error", msg,
                     new DialogInterface.OnClickListener() {
                         public void onClick(DialogInterface d, int id) {
                             d.cancel();
                             removeDialog(id);
                         }
                     });
            }
            else {
                return super.onCreateDialog(id, bundle);
            }


        default:
            return super.onCreateDialog(id, bundle);
        }
    }

    protected final void showErrorDialog(Throwable issue)
    {
        CUtils.LOGW(TAG, "Issue", issue);
        showErrorDialog(issue.getMessage());
    }
    protected final void showErrorDialog(String msg)
    {
        final Bundle bundle = new Bundle();
        bundle.putString(ERROR_MESSAGE, msg);
        showDialog(ERROR_DIALOG, bundle);
    }

    protected final static int ERROR_DIALOG = 100;
    protected final static String ERROR_MESSAGE = "error_msg";
    protected final static int ERROR_WIFI_DIALOG = 101;
    private final static String TAG =
        CUtils.makeLogTag(AAuthenticatorActivity.class);
}
