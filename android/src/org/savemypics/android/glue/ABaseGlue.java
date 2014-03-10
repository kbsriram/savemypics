package org.savemypics.android.glue;

// Abstract template class that manages shared work, and delegates to
// specific sub-classes based on the account type.

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.util.List;
import org.savemypics.android.db.CAccount;
import org.savemypics.android.db.CLocalImage;
import org.savemypics.android.service.CTaskQueue;
import org.savemypics.android.sync.CMediaUtils;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CPlugin;

public abstract class ABaseGlue
{
    public final static class ParsedName
    {
        private ParsedName(String pt, String n)
        {
            m_type = pt;
            m_name = n;
        }
        public final String getType()
        { return m_type; }
        public final String getName()
        { return m_name;
        }
        private final String m_type;
        private final String m_name;
    }

    public final static ParsedName asParsedName(String orig)
    {
        if (orig.startsWith(SNAPFISH_PREFIX)) {
            return new ParsedName
                (CUtils.SNAPFISH_ACCOUNT_TYPE,
                 orig.substring(SNAPFISH_PREFIX.length()));
        }
        else if (orig.startsWith(FLICKR_PREFIX)) {
            return new ParsedName
                (CUtils.FLICKR_ACCOUNT_TYPE,
                 orig.substring(FLICKR_PREFIX.length()));
        }
        else {
            throw new IllegalArgumentException
                ("Unexpected missing name "+orig);
        }
    }

    public final static Account asAndroidAccount(CAccount acct)
    { return asAndroidAccount(acct.getType(), acct.getName()); }

    public final static Account asAndroidAccount(String atype, String aname)
    {
        return new Account
            (asAndroidAccountName(atype, aname), CUtils.BASE_ACCOUNT_TYPE);
    }

    public final static String asAndroidAccountName(String atype, String aname)
    {
        StringBuilder ext = new StringBuilder();
        if (atype.equals(CUtils.SNAPFISH_ACCOUNT_TYPE)) {
            ext.append(SNAPFISH_PREFIX);
        }
        else if (atype.equals(CUtils.FLICKR_ACCOUNT_TYPE)) {
            ext.append(FLICKR_PREFIX);
        }
        else {
            throw new IllegalArgumentException
                ("Unexpected type: "+atype+":"+aname);
        }
        ext.append(aname);
        return ext.toString();
    }

    public final static String maybeRefresh
        (Context ctx, AccountManager amgr, Account acct, String refresh)
        throws NetworkErrorException
    { return glueFor(acct).doMaybeRefresh(ctx, amgr, acct, refresh); }

    public final static Intent makeLoginIntent(Context ctx, String atype)
    { return glueFor(atype).doMakeLoginIntent(ctx); }

    public final static Intent makeSettingsIntent
        (Context ctx, String atype, String aname, boolean first)
    { return glueFor(atype).doMakeSettingsIntent(ctx, aname, first); }

    public final static CPlugin.Feed getFeed
        (SQLiteDatabase db, CAccount acct, String auth,
         Context ctx, boolean force)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        return glueFor(acct.getType()).doGetFeed
            (db, acct, auth, ctx, force);
    }

    public final static boolean downloadEnabled(Context ctx, CAccount acct)
    {
        return
            CUtils.getSharedPreferences(ctx, acct.getType(), acct.getName())
            .getBoolean(CUtils.PREF_DOWNLOAD_ENABLED, false);
    }

    public final static boolean uploadEnabled(Context ctx, CAccount acct)
    {
        return
            CUtils.getSharedPreferences(ctx, acct.getType(), acct.getName())
            .getBoolean(CUtils.PREF_UPLOAD_ENABLED, true);
    }

    // The list must be sorted in ascending order of time, or the sync
    // code won't work correcly.
    public final static void upload
        (Context ctx, SQLiteDatabase db, CAccount acct, String auth,
         CMediaUtils.Info cur_file, SyncResult result)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        // Mabye we've uploaded it in a previous attempt.
        if (CLocalImage.exists(db, acct.getId(), cur_file.getUri())) {
            return;
        }

        // Skip files that we've created ourselves.
        if (cur_file.getPath().contains(CUtils.SAVE_MY_PICS_BASEDIR_COMPONENT)){
            markLocalImage
                (db, acct, cur_file, CLocalImage.SKIP,
                 System.currentTimeMillis());
            return;
        }

        CUtils.LOGD(TAG, "Working on "+cur_file.getPath());
        ABaseGlue glue = glueFor(acct.getType());

        if (glue.okForUpload(cur_file)) {
            CPlugin.ImageResult remote_image =
                glue.doUpload(ctx, db, acct, auth, cur_file);
            markLocalImage
                (db, acct, cur_file, CLocalImage.OK, remote_image.getCreated());
            result.stats.numInserts++;
        }
        else {
            markLocalImage
                (db, acct, cur_file, CLocalImage.SKIP,
                 System.currentTimeMillis());
        }
    }

    protected final static void setLongPreference
        (Context ctx, String atype, String aname, String k, long v)
    {
        SharedPreferences prefs = CUtils.getSharedPreferences
            (ctx, atype, aname);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(k, v);
        editor.commit();
    }

    private final static void markLocalImage
        (SQLiteDatabase db, CAccount acct, CMediaUtils.Info file,
         String status, long remote_createts)
    {
        // NB: use mediastore.added as the "local creation" time for
        // this image. In conjunction with the fact that images are
        // uploaded in ascending sequence of mediastore.added, makes
        // determining the "pending" set of images possible by using
        // the last "local creation" time as a cutoff.
        CLocalImage.addOrReplace
            (db, acct.getId(), file.getUri(), status,
             file.getAdded(), remote_createts);
    }

    private final static ABaseGlue glueFor(String atype)
    {
        if (CUtils.SNAPFISH_ACCOUNT_TYPE.equals(atype)) {
            return s_fishglue;
        }
        else if (CUtils.FLICKR_ACCOUNT_TYPE.equals(atype)) {
            return s_flickrglue;
        }
        else {
            throw new IllegalArgumentException
                ("Unknown account type: "+atype);
        }
    }

    private final static ABaseGlue glueFor(Account acct)
    {
        ParsedName pname = asParsedName(acct.name);

        if (CUtils.SNAPFISH_ACCOUNT_TYPE.equals(pname.getType())) {
            return s_fishglue;
        }
        else if (CUtils.FLICKR_ACCOUNT_TYPE.equals(pname.getType())) {
            return s_flickrglue;
        }
        else {
            throw new IllegalArgumentException
                ("Unknown account type: "+acct);
        }
    }

    protected abstract String doMaybeRefresh
        (Context ctx, AccountManager amgr, Account acct, String refresh)
        throws NetworkErrorException;
    protected abstract Intent doMakeLoginIntent(Context ctx);
    protected abstract Intent doMakeSettingsIntent
        (Context ctx, String aname, boolean first_time);
    protected abstract CPlugin.Feed doGetFeed
        (SQLiteDatabase db, CAccount acct, String auth,
         Context ctx, boolean force)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException;
    protected abstract boolean okForUpload(CMediaUtils.Info file);
    protected abstract CPlugin.ImageResult doUpload
        (Context ctx, SQLiteDatabase db, CAccount acct,
         String auth, CMediaUtils.Info file)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException;

    private final static ABaseGlue s_fishglue = new CSnapfishGlue();
    private final static ABaseGlue s_flickrglue = new CFlickrGlue();
    protected final static String FLICKR_PREFIX = "flickr:";
    protected final static String SNAPFISH_PREFIX = "snapfish:";

    private final static String TAG = CUtils.makeLogTag(ABaseGlue.class);
}
