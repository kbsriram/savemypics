package org.savemypics.android.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.savemypics.android.R;
import org.savemypics.android.glue.ABaseGlue;
import org.savemypics.android.util.CUtils;

public abstract class ABaseSettingsActivity extends AActivity
{
    public final static String KEY_ACCOUNT_NAME = "key_account_name";
    public final static String KEY_FIRST_TIME = "key_first_time";

    @Override
    protected void onCreate(Bundle saved)
    {
        super.onCreate(saved);
        m_aname = getIntent().getStringExtra(KEY_ACCOUNT_NAME);
        if (m_aname == null) {
            throw new IllegalArgumentException
                ("Unexpected - missing "+KEY_ACCOUNT_NAME);
        }
        setContentView(R.layout.base_settings);
        m_fade_in = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        TextView tv = (TextView) findViewById(R.id.base_settings_title);
        tv.setText(m_aname);
        if (false) {
            // Enable when we have more than one provider
            tv.setCompoundDrawablesWithIntrinsicBounds
                (getAccountDrawableId(), 0, 0, 0);
        }

        m_prefs = CUtils.getSharedPreferences
            (this, getAccountType(), m_aname);

        m_main = (LinearLayout) findViewById(R.id.base_settings_main);
        m_remove_button = (Button) findViewById
            (R.id.base_settings_account_remove_button);
        m_remove_button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showDialog(REMOVE_ACCOUNT_DIALOG);
                }
            });

        m_wificb = setCheckBoxPreference
            (R.id.base_settings_only_wifi,
             R.string.pref_only_wifi_title,
             R.string.pref_only_wifi_summary);

        m_uploadcb = setCheckBoxPreference
            (R.id.base_settings_upload_enabled,
             R.string.pref_upload_enabled_title,
             R.string.pref_upload_enabled_summary);

        m_upload_all_vg = (ViewGroup) findViewById
            (R.id.base_settings_upload_all);

        m_upload_allcb = setCheckBoxPreference
            (R.id.base_settings_upload_all,
             R.string.pref_upload_all_title,
             R.string.pref_upload_all_summary);

        m_debugcb = setCheckBoxPreference
            (R.id.base_settings_debug_enabled,
             R.string.pref_debug_enabled_title,
             R.string.pref_debug_enabled_summary);

        m_downloadcb = setCheckBoxPreference
            (R.id.base_settings_download_enabled,
             R.string.pref_download_enabled_title,
             R.string.pref_download_enabled_summary);

        m_acctcb = setCheckBoxPreference
            (R.id.base_settings_account_enabled,
             R.string.pref_account_enabled_title,
             R.string.pref_account_enabled_summary);

        m_acct =
            ABaseGlue.asAndroidAccount(getAccountType(), m_aname);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        setCheckBoxCallback
            (m_wificb,
             getBooleanPreference(CUtils.PREF_ONLY_WIFI, true),
             new View.OnClickListener() {
                 public void onClick(View v) {
                     setBooleanPreference
                         (CUtils.PREF_ONLY_WIFI,
                          ((CheckBox) v).isChecked());
                 }
             });
        setCheckBoxCallback
            (m_uploadcb,
             getBooleanPreference(CUtils.PREF_UPLOAD_ENABLED, true),
             new View.OnClickListener() {
                 public void onClick(View v) {
                     boolean ischecked = ((CheckBox) v).isChecked();
                     setBooleanPreference
                         (CUtils.PREF_UPLOAD_ENABLED, ischecked);
                     setEnabled(m_upload_all_vg, ischecked);
                 }
             });
        setCheckBoxCallback
            (m_upload_allcb,
             getBooleanPreference(CUtils.PREF_UPLOAD_ALL, true),
             new View.OnClickListener() {
                 public void onClick(View v) {
                     setBooleanPreference
                         (CUtils.PREF_UPLOAD_ALL,
                          ((CheckBox) v).isChecked());
                 }
             });
        setEnabled
            (m_upload_all_vg,
             getBooleanPreference(CUtils.PREF_UPLOAD_ENABLED, true));

        setCheckBoxCallback
            (m_debugcb,
             getBooleanPreference(CUtils.PREF_DEBUG_ENABLED, false),
             new View.OnClickListener() {
                 public void onClick(View v) {
                     setBooleanPreference
                         (CUtils.PREF_DEBUG_ENABLED,
                          ((CheckBox) v).isChecked());
                 }
             });

        boolean downloadable =
            getBooleanPreference(CUtils.PREF_DOWNLOAD_ENABLED, false);
        setCheckBoxCallback
            (m_downloadcb, downloadable,
             new View.OnClickListener() {
                 public void onClick(View v) {
                     boolean ischecked = ((CheckBox) v).isChecked();
                     setBooleanPreference
                         (CUtils.PREF_DOWNLOAD_ENABLED, ischecked);
                     onSetDownloadEnabled(ischecked);
                 }
             });
        onSetDownloadEnabled(downloadable);

        boolean syncable = ContentResolver.getSyncAutomatically
            (m_acct, MediaStore.AUTHORITY);
        setMyVisibility(syncable);

        setCheckBoxCallback
            (m_acctcb, syncable,
             new View.OnClickListener() {
                 public void onClick(View v) {
                     boolean ischecked = ((CheckBox) v).isChecked();
                     setMyVisibility(ischecked);
                     ContentResolver.setSyncAutomatically
                         (m_acct, MediaStore.AUTHORITY, ischecked);
                 }
             });
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        m_wificb.setOnClickListener(null);
        m_uploadcb.setOnClickListener(null);
        m_upload_allcb.setOnClickListener(null);
        m_debugcb.setOnClickListener(null);
        m_downloadcb.setOnClickListener(null);
        m_acctcb.setOnClickListener(null);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle)
    {
        switch (id) {

        case REMOVE_ACCOUNT_DIALOG:
            return CUtils.makeYesCancelDialog
                (this, R.string.action_remove_account,
                 R.string.remove_account_dialog_summary,
                 R.string.action_remove_account,
                 new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface d, int id) {
                         d.cancel();
                         removeDialog(id);
                         deleteAccountAndFinish();
                     }
                 },
                 new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface d, int id) {
                         d.cancel();
                         removeDialog(id);
                     }
                 });

        default:
            return super.onCreateDialog(id, bundle);
        }
    }

    protected LinearLayout getMainViewGroup()
    { return m_main; }

    protected void onSetDownloadEnabled(boolean curval)
    { }

    protected void setEnabled(ViewGroup vg, boolean enabled)
    {
        final int count = vg.getChildCount();
        for (int i=0; i<count; i++) {
            View v = vg.getChildAt(i);
            if (v instanceof ViewGroup) {
                setEnabled((ViewGroup) v, enabled);
            }
            else {
                v.setEnabled(enabled);
            }
        }
        vg.setEnabled(enabled);
    }

    private void deleteAccountAndFinish()
    {
        m_remove_button.setEnabled(false);
        m_acctcb.setEnabled(false);
        AccountManager amgr = AccountManager.get(getApplicationContext());
        amgr.removeAccount
            (m_acct,
             new AccountManagerCallback<Boolean>() {
                @Override public void run
                    (AccountManagerFuture<Boolean> future) {

                    boolean ok = false;
                    Throwable t = null;

                    try { ok = future.getResult(); }
                    catch (Throwable th) { t = th; }

                    if (!ok && !isFinishing()) {
                        m_remove_button.setEnabled(true);
                        m_acctcb.setEnabled(true);
                        if (t != null) {
                            showErrorDialog(t);
                        }
                        else {
                            showErrorDialog
                                ("Sorry, wasn't able to remove account");
                        }
                    }
                    else {
                        finish();
                    }
                }
            }, null);
    }

    private void setMyVisibility(boolean syncable)
    {
        if (syncable) {
            m_remove_button.setVisibility(View.GONE);
            if (m_main.getVisibility() != View.VISIBLE) {
                m_main.setVisibility(View.VISIBLE);
                m_fade_in.cancel();
                m_main.startAnimation(m_fade_in);
            }
        }
        else {
            m_main.setVisibility(View.GONE);
            if (m_remove_button.getVisibility() != View.VISIBLE) {
                m_remove_button.setVisibility(View.VISIBLE);
                m_fade_in.cancel();
                m_remove_button.startAnimation(m_fade_in);
            }
        }
    }

    protected void setBooleanPreference(String k, boolean v)
    {
        SharedPreferences.Editor editor = m_prefs.edit();
        editor.putBoolean(k, v);
        editor.commit();
    }

    protected boolean getBooleanPreference(String k, boolean dflt)
    { return m_prefs.getBoolean(k, dflt); }

    protected void setCheckBoxCallback
        (CheckBox cb, boolean initial, View.OnClickListener cl)
    {
        cb.setOnClickListener(null);
        cb.setChecked(initial);
        cb.setOnClickListener(cl);
    }

    protected CheckBox setCheckBoxPreference
        (int prefid, int titleid, int summaryid)
    {
        ViewGroup vg = (ViewGroup) findViewById(prefid);
        return setCheckBoxPreference(vg, titleid, summaryid);
    }

    protected CheckBox setCheckBoxPreference
        (ViewGroup vg, int titleid, int summaryid)
    {
        ((TextView) vg.findViewById(R.id.preference_title))
            .setText(titleid);
        if (summaryid == 0) {
            ((TextView) vg.findViewById(R.id.preference_summary))
                .setVisibility(View.GONE);
        }
        else {
            ((TextView) vg.findViewById(R.id.preference_summary))
                .setText(summaryid);
        }
        final CheckBox cb =
            (CheckBox) vg.findViewById(R.id.preference_checkbox);

        vg.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cb.performClick();
                }
            });
        return cb;
    }

    protected abstract String getAccountType();
    protected abstract int getAccountDrawableId();

    private String m_aname;
    private LinearLayout m_main;
    private Button m_remove_button;
    private CheckBox m_wificb;
    private CheckBox m_uploadcb;
    private CheckBox m_upload_allcb;
    private ViewGroup m_upload_all_vg;
    private CheckBox m_debugcb;
    private CheckBox m_downloadcb;
    private CheckBox m_acctcb;
    private SharedPreferences m_prefs;
    private Account m_acct;
    private Animation m_fade_in;

    private final static int REMOVE_ACCOUNT_DIALOG = 200;
    private final static String TAG =
        CUtils.makeLogTag(ABaseSettingsActivity.class);
}
