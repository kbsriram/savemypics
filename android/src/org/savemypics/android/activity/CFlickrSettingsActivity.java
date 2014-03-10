package org.savemypics.android.activity;

import org.savemypics.android.R;
import org.savemypics.android.util.CUtils;

public class CFlickrSettingsActivity extends ABaseSettingsActivity
{
    @Override
    protected String getAccountType()
    { return CUtils.FLICKR_ACCOUNT_TYPE; }
    @Override
    protected int getAccountDrawableId()
    { return R.drawable.flickr_icon; }
}
