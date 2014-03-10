package org.savemypics.android.authenticator;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import org.savemypics.android.R;
import org.savemypics.android.glue.ABaseGlue;
import org.savemypics.android.util.CUtils;

public class CSelectProviderActivity extends AAuthenticatorActivity
{
    public final static String KEY_ACCOUNT_NAME = "account.name";
    public final static String KEY_ACCOUNT_TYPE = "account.type";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_provider);

        setClick(R.id.select_provider_snapfish_button,
                 CUtils.SNAPFISH_ACCOUNT_TYPE);
        setClick(R.id.select_provider_flickr_button,
                 CUtils.FLICKR_ACCOUNT_TYPE);
        ((Button)findViewById(R.id.select_provider_done_button))
            .setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        finish();
                    }
                });

        m_wificb = setCheckBoxPreference
            (R.id.select_provider_wifi_pref,
             R.string.pref_only_wifi_title, R.string.pref_only_wifi_summary,
             new CompoundButton.OnCheckedChangeListener() {
                     public void onCheckedChanged
                         (CompoundButton bv, boolean ischecked) {
                         setBooleanSetting(CUtils.PREF_ONLY_WIFI, ischecked);
                     }
                 });

        m_existingcb = setCheckBoxPreference
            (R.id.select_provider_upload_existing_pref,
             R.string.pref_upload_existing_title,
             R.string.pref_upload_existing_summary,
             new CompoundButton.OnCheckedChangeListener() {
                     public void onCheckedChanged
                         (CompoundButton bv, boolean ischecked) {
                         setBooleanSetting(CUtils.PREF_UPLOAD_ALL, ischecked);
                     }
                 });

        // this is over-ridden only on a successful onactivityresult
        // from login request.
        setResult(Activity.RESULT_CANCELED);

        // Since we've got only one provider at this point -- just
        // start it up directly. Remove this line if we decide to
        // enable additional providers.
        startLoginFor(CUtils.SNAPFISH_ACCOUNT_TYPE);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        updateUI();
    }

    private void setBooleanSetting(String k, boolean v)
    {
        if ((m_atype != null) && (m_aname != null)) {
            SharedPreferences settings =
                CUtils.getSharedPreferences(this, m_atype, m_aname);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(k, v);
            editor.commit();
        }
        else {
            CUtils.LOGW(TAG, "ignoring setting - no acct set.");
        }
    }

    private void setLongSetting(String k, long v)
    {
        if ((m_atype != null) && (m_aname != null)) {
            SharedPreferences settings =
                CUtils.getSharedPreferences(this, m_atype, m_aname);
            SharedPreferences.Editor editor = settings.edit();
            editor.putLong(k, v);
            editor.commit();
        }
        else {
            CUtils.LOGW(TAG, "ignoring setting - no acct set.");
        }
    }


    private CheckBox setCheckBoxPreference
        (int prefid, int titleid, int summaryid,
         CompoundButton.OnCheckedChangeListener cl)
    {
        ViewGroup vg = (ViewGroup) findViewById(prefid);

        ((TextView) vg.findViewById(R.id.preference_title))
            .setText(titleid);
        ((TextView) vg.findViewById(R.id.preference_summary))
            .setText(summaryid);
        final CheckBox cb =
            (CheckBox) vg.findViewById(R.id.preference_checkbox);

        vg.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cb.performClick();
                }
            });
        cb.setOnCheckedChangeListener(cl);
        return cb;
    }

    private void updateUI()
    {
        if (m_retintent == null) {
            findViewById(R.id.select_provider_choose)
                .setVisibility(View.VISIBLE);
            findViewById(R.id.select_provider_done)
                .setVisibility(View.GONE);
        }
        else {
            findViewById(R.id.select_provider_choose)
                .setVisibility(View.GONE);
            findViewById(R.id.select_provider_done)
                .setVisibility(View.VISIBLE);
            SharedPreferences settings =
                CUtils.getSharedPreferences(this, m_atype, m_aname);
            m_wificb.setChecked
                (settings.getBoolean(CUtils.PREF_ONLY_WIFI, true));
            m_existingcb.setChecked
                (settings.getBoolean(CUtils.PREF_UPLOAD_ALL, true));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data)
    {
        switch (req) {
        case REQ_LOGIN:
            if (res == Activity.RESULT_OK) {

                m_aname = null;
                m_atype = null;
                Bundle extras = data.getExtras();
                if (extras != null) {
                    m_aname = extras.getString(KEY_ACCOUNT_NAME);
                    m_atype = extras.getString(KEY_ACCOUNT_TYPE);
                }
                if ((m_aname == null) || (m_atype == null)) {
                    throw new IllegalStateException
                        ("Unexpected - missing bundle data");
                }

                m_retintent = new Intent()
                    .putExtra
                    (AccountManager.KEY_ACCOUNT_NAME,
                     ABaseGlue.asAndroidAccountName(m_atype, m_aname))
                    .putExtra
                    (AccountManager.KEY_ACCOUNT_TYPE,
                     CUtils.BASE_ACCOUNT_TYPE);
                setAccountAuthenticatorResult(m_retintent.getExtras());
                setResult(RESULT_OK, m_retintent);
                updateUI();
            }
            else {
                // Remove this when we have multiple providers
                setResult(res);
                finish();
            }
            break;

        default:
            super.onActivityResult(req, res, data);
            break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out)
    {
        super.onSaveInstanceState(out);
        if (m_retintent != null) {
            out.putParcelable(RET_INTENT_TAG, m_retintent);
            out.putString(ANAME_TAG, m_aname);
            out.putString(ATYPE_TAG, m_atype);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle saved)
    {
        super.onRestoreInstanceState(saved);
        m_retintent = (Intent) saved.getParcelable(RET_INTENT_TAG);
        if (m_retintent != null) {
            m_aname = saved.getString(ANAME_TAG);
            m_atype = saved.getString(ATYPE_TAG);
            setAccountAuthenticatorResult(m_retintent.getExtras());
            setResult(RESULT_OK, m_retintent);
        }
    }

    private final void setClick(final int id, final String atype)
    {
        ((Button) findViewById(id))
            .setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        startLoginFor(atype);
                    }
                });
    }

    private final void startLoginFor(String atype)
    {
        startActivityForResult
            (ABaseGlue.makeLoginIntent(this, atype),
             REQ_LOGIN);
    }

    private Intent m_retintent = null;
    private String m_aname = null;
    private String m_atype = null;
    private CheckBox m_wificb = null;
    private CheckBox m_existingcb = null;
    private final static int REQ_LOGIN = 1;
    private final static String RET_INTENT_TAG = "smp:ret_intent_tag";
    private final static String ANAME_TAG = "smp:aname_tag";
    private final static String ATYPE_TAG = "smp:atype_tag";
    private final static String TAG =
        CUtils.makeLogTag(CSelectProviderActivity.class);
}
