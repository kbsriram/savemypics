package org.savemypics.android.activity;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import org.savemypics.android.R;
import org.savemypics.android.util.CUtils;

public class CSnapfishSettingsActivity extends ABaseSettingsActivity
{
    @Override
    protected void onCreate(Bundle saved)
    {
        super.onCreate(saved);
        LayoutInflater inflater = getLayoutInflater();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        View divider = inflater.inflate(R.layout.view_divider, null);
        // height = 1sp
        int ht = (int) (0.5f + TypedValue.applyDimension
                        (TypedValue.COMPLEX_UNIT_SP, 1f, metrics));
        if (ht < 1) { ht = 1; }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams
            (LinearLayout.LayoutParams.MATCH_PARENT, ht);

        divider.setLayoutParams(lp);
        getMainViewGroup().addView(divider);

        m_include_shared = (ViewGroup)
            inflater.inflate(R.layout.checkbox_preference, null);
        lp = new LinearLayout.LayoutParams
            (LinearLayout.LayoutParams.MATCH_PARENT,
             LinearLayout.LayoutParams.WRAP_CONTENT);
        Resources res = getResources();
        lp.topMargin = res.getDimensionPixelSize(R.dimen.grid_b_2_3);
        lp.bottomMargin = res.getDimensionPixelSize(R.dimen.grid_b_6);
        m_include_shared.setLayoutParams(lp);

        getMainViewGroup().addView(m_include_shared);
        m_include_shared_cb = setCheckBoxPreference
            (m_include_shared,
             R.string.pref_download_shared_title,
             R.string.pref_download_shared_summary);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        setCheckBoxCallback
            (m_include_shared_cb,
             getBooleanPreference(CUtils.PREF_DOWNLOAD_SHARED_SNAPFISH, false),
             new View.OnClickListener() {
                 public void onClick(View v) {
                     setBooleanPreference
                         (CUtils.PREF_DOWNLOAD_SHARED_SNAPFISH,
                          ((CheckBox) v).isChecked());
                 }
             });
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        m_include_shared_cb.setOnClickListener(null);
    }

    @Override
    protected String getAccountType()
    { return CUtils.SNAPFISH_ACCOUNT_TYPE; }
    @Override
    protected int getAccountDrawableId()
    { return R.drawable.snapfish_icon; }

    @Override
    protected void onSetDownloadEnabled(boolean curval)
    { setEnabled(m_include_shared, curval); }

    private ViewGroup m_include_shared;
    private CheckBox m_include_shared_cb;
}
