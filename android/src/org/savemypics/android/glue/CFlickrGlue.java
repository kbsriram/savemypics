package org.savemypics.android.glue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import java.io.File;
import java.io.IOException;
import org.savemypics.android.activity.ABaseSettingsActivity;
import org.savemypics.android.activity.CFlickrSettingsActivity;
import org.savemypics.android.authenticator.CFlickrLoginHookActivity;
import org.savemypics.android.db.CAccount;
import org.savemypics.android.db.CMap;
import org.savemypics.android.event.AEvent;
import org.savemypics.android.event.CEventBus;
import org.savemypics.android.service.CTaskQueue;
import org.savemypics.android.sync.CMediaUtils;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CPlugin;
import org.savemypics.plugin.flickr.CFlickrAlbum;
import org.savemypics.plugin.flickr.CFlickrUtils;

public class CFlickrGlue extends ABaseGlue
{
    public final static class RequestTokenEvent extends AEvent
    {
        public interface Listener
            extends AEvent.Listener
        {
            public void onRequestToken(RequestTokenEvent ev);
        }
        public final static void subscribe(final Listener l)
        { doSubscribe(l, CEventBus.EVENT_REQUEST_TOKEN); }
        public final static void unsubscribe(final Listener l)
        { doUnsubscribe(l, CEventBus.EVENT_REQUEST_TOKEN); }

        public final void publish()
        { doPublish(CEventBus.EVENT_REQUEST_TOKEN); }

        protected final void onUpdate(AEvent.Listener l)
        { ((Listener) l).onRequestToken(this); }

        private RequestTokenEvent(CFlickrUtils.RequestToken tok)
        { m_tok = tok; }
        public CFlickrUtils.RequestToken getRequestToken()
        { return m_tok; }
        private final CFlickrUtils.RequestToken m_tok;
    }

    public final static class AccessTokenEvent extends AEvent
    {
        public interface Listener
            extends AEvent.Listener
        {
            public void onAccessToken(AccessTokenEvent ev);
        }
        public final static void subscribe(final Listener l)
        { doSubscribe(l, CEventBus.EVENT_ACCESS_TOKEN); }
        public final static void unsubscribe(final Listener l)
        { doUnsubscribe(l, CEventBus.EVENT_ACCESS_TOKEN); }

        public final void publish()
        { doPublish(CEventBus.EVENT_ACCESS_TOKEN); }

        protected final void onUpdate(AEvent.Listener l)
        { ((Listener) l).onAccessToken(this); }

        private AccessTokenEvent(CFlickrUtils.AccessToken tok)
        { m_tok = tok; }
        public CFlickrUtils.AccessToken getAccessToken()
        { return m_tok; }
        private final CFlickrUtils.AccessToken m_tok;
    }

    public final static void asyncRequestToken(Context appctx)
    {
        CTaskQueue.enqueueNetworkTask(appctx, new CTaskQueue.Task() {
                @Override protected void runTask()
                    throws IOException, CPlugin.PermanentException,
                           CPlugin.AuthorizationException {
                    AppCredentials appcred = appCredentials(getContext());
                    (new RequestTokenEvent
                     (CFlickrUtils.requestToken
                      (appcred.m_appid, appcred.m_appsecret))).publish();
                }
            });
    }

    public final static void asyncAccessToken
        (Context appctx, final String utok, final String usec, final String ver)
    {
        CTaskQueue.enqueueNetworkTask(appctx, new CTaskQueue.Task() {
                @Override protected void runTask()
                    throws IOException, CPlugin.PermanentException,
                           CPlugin.AuthorizationException {
                    AppCredentials appcred = appCredentials(getContext());
                    (new AccessTokenEvent
                     (CFlickrUtils.accessToken
                      (appcred.m_appid, appcred.m_appsecret,
                       utok, usec, ver))).publish();
                }
            });
    }

    protected Intent doMakeLoginIntent(Context ctx)
    { return new Intent(ctx, CFlickrLoginHookActivity.class); }

    protected Intent doMakeSettingsIntent
        (Context ctx, String aname, boolean first)
    {
        return (new Intent(ctx, CFlickrSettingsActivity.class))
            .putExtra(ABaseSettingsActivity.KEY_ACCOUNT_NAME, aname)
            .putExtra(ABaseSettingsActivity.KEY_FIRST_TIME, first);
    }

    // No refresh possible - we get permanent tokens, or we
    // need to re-issue the login.
    protected String doMaybeRefresh
        (Context ctx, AccountManager amgr, Account acct, String refresh)
    { return null; }

    protected CPlugin.Feed doGetFeed
        (SQLiteDatabase db, CAccount acct, String auth,
         Context ctx, boolean force)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        // Enforce a minimum delay between calls to this method.
        CUtils.LOGD(TAG, "Check for downloads: "+acct+", force="+force);

        // pull out last watermark.
        String prevmark = CMap.optString
            (db, acct.getId(), FLICKR_REMOTE_MARK, "*unmarked*");
        // Also pull out last run time
        long prev = CMap.optLong(db, acct.getId(), FLICKR_LAST_FEED_CHECK, 0l);

        // Enforce minimum delay
        long cur = System.currentTimeMillis();
        if (!force && ((cur - prev) < ONE_DAY_MSEC)) {
            CUtils.LOGD(TAG, "Won't check feed - last call was too recent.");
            return new CPlugin.Feed(prevmark).setNothingChanged(true);
        }

        AppCredentials appcred = appCredentials(ctx);
        CPlugin.Feed feed = CFlickrAlbum.getFeed
            (appcred.m_appid, appcred.m_appsecret,
             CFlickrUtils.AccessToken.fromString(auth),
             CUtils.DOWNLOAD_COUNT, prevmark);

        // Update mark and last call.
        CMap.put(db, acct.getId(), FLICKR_REMOTE_MARK, feed.getMark());
        CMap.put(db, acct.getId(), FLICKR_LAST_FEED_CHECK, String.valueOf(cur));
        return feed;
    }

    protected boolean okForUpload(CMediaUtils.Info file)
    { return CMediaUtils.looksLikeImage(file); }

    protected CPlugin.ImageResult doUpload
        (Context ctx, SQLiteDatabase db, CAccount acct,
         String auth, CMediaUtils.Info file)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        CFlickrUtils.AccessToken access =
            CFlickrUtils.AccessToken.fromString(auth);

        AppCredentials appcred = appCredentials(ctx);

        File src = new File(file.getPath());
        return CFlickrAlbum.upload
            (appcred.m_appid, appcred.m_appsecret, access, src, src.getName());
    }

    public final static String addAccount
        (Context appctx, CFlickrUtils.AccessToken atok)
    {
        Account acct = asAndroidAccount
            (CUtils.FLICKR_ACCOUNT_TYPE, atok.getUserName());

        // 1. Add new account.
        AccountManager acctmgr = AccountManager.get(appctx);

        String access = atok.toString();
        acctmgr.addAccountExplicitly(acct, access, null);

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
        acctmgr.setAuthToken(acct, CUtils.AUTH_ACCESS, access);

        // 6. Mark account-added timestamp
        ABaseGlue.setLongPreference
            (appctx, CUtils.FLICKR_ACCOUNT_TYPE, atok.getUserName(),
             CUtils.PREF_ACCOUNT_INSTALLED_ON, System.currentTimeMillis());

        return atok.getUserName();
    }

    private static synchronized AppCredentials appCredentials(Context ctx)
        throws IOException
    {
        if (s_appcred != null) { return s_appcred; }

        KeyFlickr tmp = KeyFlickr.make(ctx);
        s_appcred = new AppCredentials
            (tmp.getAppId(), tmp.getAppSecret());
        return s_appcred;
    }

    final static class AppCredentials
    {
        private AppCredentials(String aid, String asecret)
        {
            m_appid = aid;
            m_appsecret = asecret;
        }
        final String m_appid;
        final String m_appsecret;
    }

    private static AppCredentials s_appcred = null;

    private final static long ONE_DAY_MSEC = 86400l*1000l;
    private final static String FLICKR_REMOTE_MARK = "flickr.remote.mark";
    private final static String FLICKR_LAST_FEED_CHECK="flickr.last.feed.check";
    private final static String TAG = CUtils.makeLogTag(CFlickrGlue.class);
}
