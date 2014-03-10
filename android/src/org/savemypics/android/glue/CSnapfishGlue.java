package org.savemypics.android.glue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.savemypics.android.activity.ABaseSettingsActivity;
import org.savemypics.android.activity.CSnapfishSettingsActivity;
import org.savemypics.android.authenticator.CSnapfishLoginActivity;
import org.savemypics.android.db.CAccount;
import org.savemypics.android.db.CMap;
import org.savemypics.android.event.AEvent;
import org.savemypics.android.event.CEventBus;
import org.savemypics.android.service.CTaskQueue;
import org.savemypics.android.sync.CMediaUtils;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CPlugin;
import org.savemypics.plugin.snapfish.CSnapfishAlbum;
import org.savemypics.plugin.snapfish.CSnapfishFeed;
import org.savemypics.plugin.snapfish.CSnapfishUser;

public class CSnapfishGlue extends ABaseGlue
{
    public final static class LoginEvent
        extends AEvent
    {
        public interface Listener
            extends AEvent.Listener
        {
            public void onLogin(LoginEvent ev);
        }

        public final static void subscribe(final Listener l)
        { doSubscribe(l, CEventBus.EVENT_SNAPFISH_LOGIN); }

        public final static void unsubscribe(final Listener l)
        { doUnsubscribe(l, CEventBus.EVENT_SNAPFISH_LOGIN); }

        private final static void publish(String err, Throwable issue)
        {
            LoginEvent le = new LoginEvent(err, issue);
            le.doPublish(CEventBus.EVENT_SNAPFISH_LOGIN);
        }

        private final static void publish(String name)
        {
            LoginEvent le = new LoginEvent(name);
            le.doPublish(CEventBus.EVENT_SNAPFISH_LOGIN);
        }

        protected final void onUpdate(AEvent.Listener l)
        { ((Listener) l).onLogin(this); }

        private LoginEvent(String name)
        {
            m_err = null;
            m_issue = null;
            m_ok = true;
            m_name = name;
        }
        private LoginEvent(String err, Throwable issue)
        {
            m_err = err;
            m_issue = issue;
            m_ok = false;
            m_name = null;
        }

        public Throwable getIssue()
        { return m_issue; }

        public String getError()
        { return m_err; }

        public boolean wasSuccessful()
        { return m_ok; }

        public String getName()
        { return m_name; }

        private final String m_name;
        private final String m_err;
        private final Throwable m_issue;
        private final boolean m_ok;
    }

    protected Intent doMakeLoginIntent(Context ctx)
    { return new Intent(ctx, CSnapfishLoginActivity.class); }

    protected Intent doMakeSettingsIntent
        (Context ctx, String aname, boolean first)
    {
        return (new Intent(ctx, CSnapfishSettingsActivity.class))
            .putExtra(ABaseSettingsActivity.KEY_ACCOUNT_NAME, aname)
            .putExtra(ABaseSettingsActivity.KEY_FIRST_TIME, first);
    }

    // Only jpeg files allowed.
    protected boolean okForUpload(CMediaUtils.Info file)
    { return CMediaUtils.looksLikeJPEG(file); }

    protected String doMaybeRefresh
        (Context ctx, AccountManager amgr, Account acct, String refresh)
        throws NetworkErrorException
    {
        try {
            CPlugin.Tokens tokens = CSnapfishUser.refresh
                (appCredentials(ctx), refresh);
            if (tokens == null) { return null; }

            // reset "password" and "auth" within our account.
            amgr.setPassword(acct, tokens.getPermanent());
            amgr.setAuthToken(acct, CUtils.AUTH_ACCESS, tokens.getTemporary());
            return tokens.getTemporary();
        }
        catch (IOException ioe) {
            throw new NetworkErrorException(ioe);
        }
        catch (CPlugin.AuthorizationException ae) {
            CUtils.LOGW(TAG, "auto-refresh failed", ae);
            // Clear out password and token; so we'll force the
            // user mediated flow to occur at some point.
            amgr.setPassword(acct, null);
            return null;
        }
    }

    public final static void asyncLogin
        (Context ctx, final AccountManager amgr,
         final String email, final String pass)
    {
        CTaskQueue.enqueueNetworkTask(ctx, new CTaskQueue.Task() {
                protected void runTask() {
                    login(getContext(), amgr, email, pass);
                }
            });
    }

    private final static void login
        (Context ctx, AccountManager acctmgr, String email, String pass)
    {
        CPlugin.Tokens tokens;
        try {
            tokens = CSnapfishUser.login(appCredentials(ctx), email, pass);
        }
        catch (CPlugin.AuthorizationException ae) {
            LoginEvent.publish(ae.getMessage(), null);
            return;
        }
        catch (IOException ioe) {
            CUtils.LOGD(TAG, "Unable to login", ioe);
            LoginEvent.publish(null, ioe);
            return;
        }

        if (tokens == null) {
            // hmm -- this should not happen.
            LoginEvent.publish("empty login", null);
            return;
        }

        Account acct = asAndroidAccount(CUtils.SNAPFISH_ACCOUNT_TYPE, email);

        CUtils.LOGD(TAG, "Adding new account: "+acct);
        // Configure new account.
        // 1. store entire json object as our 'token'
        acctmgr.addAccountExplicitly(acct, tokens.getPermanent(), null);

        // 2. Mark it as being syncable with the mediastore.
        ContentResolver.setIsSyncable(acct, MediaStore.AUTHORITY, 1);

        // 3. Enable periodic sync. once-a-day.
        ContentResolver.addPeriodicSync
            (acct, MediaStore.AUTHORITY, new Bundle(), ONE_DAY_MSEC);

        // 4. Auto-sync when it makes sense [but unfortunately,
        // doesn't support wifi-only triggers. Oh well.]
        ContentResolver.setSyncAutomatically
            (acct, MediaStore.AUTHORITY, true);

        // 5. Cache access token.
        acctmgr.setAuthToken
            (acct, CUtils.AUTH_ACCESS, tokens.getTemporary());

        // 6. Mark account-added timestamp
        ABaseGlue.setLongPreference
            (ctx, CUtils.SNAPFISH_ACCOUNT_TYPE, email,
             CUtils.PREF_ACCOUNT_INSTALLED_ON, System.currentTimeMillis());

        LoginEvent.publish(email);
    }

    protected CPlugin.Feed doGetFeed
        (SQLiteDatabase db, CAccount acct, String auth,
         Context ctx, boolean force)
        throws IOException, CPlugin.AuthorizationException
    {
        // Enforce a minimum delay between calls to this method.
        CUtils.LOGD(TAG, "Check for downloads: "+acct+", force="+force);

        // pull out last watermark.
        String prevmark = CMap.optString
            (db, acct.getId(), SF_REMOTE_MARK, "*unmarked*");
        // Also pull out last run time
        long prev = CMap.optLong(db, acct.getId(), SF_LAST_FEED_CHECK, 0l);

        // Enforce minimum delay
        long cur = System.currentTimeMillis();
        if (!force && ((cur - prev) < ONE_DAY_MSEC)) {
            CUtils.LOGD(TAG, "Won't check feed - last call was too recent.");
            return new CPlugin.Feed(prevmark).setNothingChanged(true);
        }

        CPlugin.Feed feed = CSnapfishFeed.getFeed
            (auth, CUtils.DOWNLOAD_COUNT,
             getBooleanPreference
             (ctx, acct, CUtils.PREF_DOWNLOAD_SHARED_SNAPFISH, false),
             prevmark);

        // Update mark and last call.
        CMap.put(db, acct.getId(), SF_REMOTE_MARK, feed.getMark());
        CMap.put(db, acct.getId(), SF_LAST_FEED_CHECK, String.valueOf(cur));
        return feed;
    }

    protected CPlugin.ImageResult doUpload
        (Context ctx, SQLiteDatabase db, CAccount acct,
         String auth, CMediaUtils.Info file)
        throws IOException, CPlugin.AuthorizationException
    {
        String atitle = makeAlbumTitle(file.getCreated());

        // Check for a cached album-id.
        String key = "snapfish.album.id-"+atitle;
        String albumid = CMap.get(db, acct.getId(), key);

        if (albumid == null) {
            // Oh well. Make the expensive call.
            CPlugin.AlbumResult ar =
                CSnapfishAlbum.getAlbumByTitle(auth, atitle, true);
            if (ar == null) {
                // unexpected - mark as transient?
                CUtils.LOGW(TAG, "Unable to create album - "+atitle);
                throw new IOException("Unable to create album - ?");
            }
            albumid = ar.getId();
            CMap.put(db, acct.getId(), key, albumid);
        }
        else {
            // Verify that it's still around.?
            // TBD
        }
        return CSnapfishAlbum.uploadToAlbum
            (auth, albumid, new File(file.getPath()));
    }

    private final static boolean getBooleanPreference
        (Context ctx, CAccount acct, String key, boolean dflt)
    {
        return CUtils.getSharedPreferences(ctx, acct.getType(), acct.getName())
            .getBoolean(key, dflt);
    }

    private final static String makeAlbumTitle(long epoch_msec)
        throws IOException
    {
        SimpleDateFormat sdf = new SimpleDateFormat
            ("'Mobile-'yyyy-MM", Locale.US);
        return sdf.format(new Date(epoch_msec));
    }

    private static synchronized CSnapfishUser.AppCredentials
        appCredentials(Context ctx)
        throws IOException
    {
        if (s_appcred != null) { return s_appcred; }

        KeyFish tmp = KeyFish.make(ctx);
        s_appcred = new CSnapfishUser.AppCredentials
            (tmp.getAppId(), tmp.getAppSecret());
        return s_appcred;
    }

    private static CSnapfishUser.AppCredentials s_appcred = null;
    private final static long ONE_DAY_MSEC = 86400l*1000l;
    private final static String SF_REMOTE_MARK = "snapfish.remote.mark";
    private final static String SF_LAST_FEED_CHECK = "snapfish.last.feed.check";
    private final static String TAG = CUtils.makeLogTag(CSnapfishGlue.class);
}
