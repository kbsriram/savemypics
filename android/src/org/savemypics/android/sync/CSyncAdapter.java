package org.savemypics.android.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.savemypics.android.db.CAccount;
import org.savemypics.android.db.CDb;
import org.savemypics.android.db.CLocalImage;
import org.savemypics.android.db.CMap;
import org.savemypics.android.glue.ABaseGlue;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;

public class CSyncAdapter extends AbstractThreadedSyncAdapter
{
    CSyncAdapter(Context ctx, boolean auto)
    {
        super(ctx, auto);
        m_appctx = ctx.getApplicationContext();
        m_resolver = m_appctx.getContentResolver();
        m_acctmgr = AccountManager.get(m_appctx);
        m_cdb = new CDb(m_appctx);
    }

    CSyncAdapter(Context ctx, boolean auto, boolean parallel)
    {
        super(ctx, auto /*, parallel - TODO: flip target-sdk */);
        m_appctx = ctx.getApplicationContext();
        m_resolver = m_appctx.getContentResolver();
        m_acctmgr = AccountManager.get(m_appctx);
        m_cdb = new CDb(m_appctx);
    }

    // NB: runs on background thread.
    @Override
    public void onPerformSync
        (Account androacct, Bundle extras, String authority,
         ContentProviderClient provider, SyncResult result)
    {
        CUtils.LOGD(TAG, "Acct = "+androacct+", authority="+authority);
        ABaseGlue.ParsedName pname = ABaseGlue.asParsedName(androacct.name);

        CSyncUtils.incrementInProgress(pname.getType(), pname.getName());
        try {
            _onPerformSync(pname, extras, authority, provider, result);
        }
        finally {
            CSyncUtils.decrementInProgress(pname.getType(), pname.getName());
        }
    }

    private void _onPerformSync
        (ABaseGlue.ParsedName pname, Bundle extras, String authority,
         ContentProviderClient provider, SyncResult result)
    {
        CAccount acct = CAccount.findOrAdd
            (m_cdb.getDb(), pname.getType(), pname.getName());
        boolean force =
            (extras != null) &&
            extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
        updateLastRun(acct);

        if (!canUseNetwork(acct, result)) {
            return;
        }

        // First get any new downloads - this slightly optimizes the
        // work for plugins that have a hard time filtering out images
        // that are uploaded.
        if (ABaseGlue.downloadEnabled(m_appctx, acct)) {
            int times = 0;
            String authS;
            boolean retry;

            do {
                authS = grabAuth(acct, result);
                if (authS == null) { return; }
                retry = attemptDownload
                    (provider, acct, authS, result, force);
                times++;
            } while (retry && (times <= 3) && (!Thread.interrupted()));
        }
        else {
            CUtils.LOGD(TAG, "Downloads turned off, skip.");
        }

        if (ABaseGlue.uploadEnabled(m_appctx, acct)) {
            processUpload(acct, provider, result);
        }
        else {
            CUtils.LOGD(TAG, "Uploads turned off, skip.");
        }
    }

    private void processUpload
        (CAccount acct, ContentProviderClient provider, SyncResult result)
    {
        CSyncUtils.setResult
            (m_cdb, acct, CSyncUtils.Result.UPLOADS_IN_PROGRESS, null);

        List<CMediaUtils.Info> newimages =
            CSyncUtils.getPendingUploads(m_appctx, m_cdb, acct);

        if (newimages.size() != 0) {
            int times = 0;
            String authS;
            boolean retry;

            SharedPreferences prefs = CUtils.getSharedPreferences
                (m_appctx, acct.getType(), acct.getName());
            long account_added = prefs.getLong
                (CUtils.PREF_ACCOUNT_INSTALLED_ON, 0l);
            do {
                authS = grabAuth(acct, result);
                if (authS == null) { return; }
                retry = attemptUpload
                    (acct, prefs, account_added, authS, newimages, result);
                times++;
            } while (retry && (times <= 3)  && (!Thread.interrupted()));
            if (retry) {
                CSyncUtils.setResult
                    (m_cdb, acct, CSyncUtils.Result.IO_RETRY, null);
            }
        }
        else {
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.DONE, null);
            CUtils.LOGD(TAG, "No new images");
        }
    }

    private boolean canUseNetwork(CAccount acct, SyncResult result)
    {
        if (!CUtils.isNetworkAvailable
            (m_appctx, acct.getType(), acct.getName())) {
            CUtils.LOGD(TAG, "skip - no usable network.");
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.NO_USABLE_NETWORK, null);
            result.stats.numIoExceptions++;
            updateBackoff(acct, result);
            return false;
        }
        else {
            return true;
        }
    }

    // note: return true if you can attempt a retry right away.
    private boolean attemptDownload
        (ContentProviderClient provider, CAccount acct, String auth,
         SyncResult result, boolean force)
    {
        try {
            if (!canUseNetwork(acct, result)) {
                return false;
            }

            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.DOWNLOADS_IN_PROGRESS, null);
            CSyncUtils.updateInProgress(acct.getType(), acct.getName());

            CPlugin.Feed feed = ABaseGlue.getFeed
                (m_cdb.getDb(), acct, auth, m_appctx, force);

            if (feed.nothingChanged() || (feed.getImages().size() == 0)) {
                CUtils.LOGD(TAG, "no new downloads");
                CSyncUtils.setResult
                    (m_cdb, acct, CSyncUtils.Result.DONE, null);
                return false;
            }

            File base = CUtils.makeDownloadRoot
                (m_appctx, acct.getType(), acct.getName());

            Set<File> only = new HashSet<File>();

            // Fetch in reverse order; since setting create/last-modified
            // appears basically impossible across all android versions.
            //
            // This approximates some sort of time ordering locally.
            List<CPlugin.RemoteImage> images = feed.getImages();
            final int max = images.size();
            for (int i = max-1; i >= 0; i--) {
                CPlugin.RemoteImage image = images.get(i);

                // Recheck that the network is still around,
                // in case we switched it while we're downloading
                // a bunch of images.
                if (!canUseNetwork(acct, result)) {
                    return false;
                }
                if (!ABaseGlue.downloadEnabled(m_appctx, acct)) {
                    CSyncUtils.setResult
                        (m_cdb, acct, CSyncUtils.Result.DONE, null);
                    return false;
                }

                File out = maybeDownload(acct, base, image, result);
                if (out != null) { only.add(out); }
            }

            // Remove all other downloaded files.
            keepOnly(acct, provider, base, only, result);

            // all done.
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.DONE, null);
            return false;
        }
        catch (RemoteException re) {
            // Assumed to be a temporary failure.
            CUtils.LOGW(TAG, "Transient(?) remote exception", re);
            CSyncUtils.logException(m_cdb, acct, re);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.UNEXPECTED_RETRY,
                 "transient problem: "+re.getMessage());
            result.stats.numIoExceptions++;
            updateBackoff(acct, result);
            return false;
        }
        catch (IOException ioe) {
            // Assumed to be a temporary failure.
            CUtils.LOGD(TAG, "temporary io exception", ioe);
            CSyncUtils.logException(m_cdb, acct, ioe);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.IO_RETRY, null);
            result.stats.numIoExceptions++;
            updateBackoff(acct, result);
            return false;
        }
        catch (CPlugin.AuthorizationException ae) {
            // Assumed to be an invalid token.
            // Remove cached token, and retry.
            CUtils.LOGD(TAG, "Removing cached auth token.", ae);
            m_acctmgr.invalidateAuthToken(CUtils.BASE_ACCOUNT_TYPE, auth);
            return true;
        }
        catch (CPlugin.PermanentException pe) {
            // Ah well. Go back to removing everything
            CUtils.LOGD(TAG, "Permanent exception.", pe);
            CSyncUtils.logException(m_cdb, acct, pe);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.AUTH_FAILURE, null);
            m_acctmgr.invalidateAuthToken(CUtils.BASE_ACCOUNT_TYPE, auth);
            m_acctmgr.setPassword(ABaseGlue.asAndroidAccount(acct), null);
            result.stats.numAuthExceptions++;
            return false;
        }
    }

    private File maybeDownload
        (CAccount acct, File root, CPlugin.RemoteImage image, SyncResult result)
        throws IOException
    {
        URL src = new URL(image.getURL());
        String id = image.getId();
        long created = image.getCreated();
        String title = image.getTitle();

        File dest = new File(root, created+"_"+id+".jpg");
        if (dest.canRead()) {
            // Make sure we still have it in the mediastore and also
            // our remotefile db.
            if (!CMediaUtils.checkOrAddMedia
                (m_appctx, m_cdb.getDb(), acct.getId(),
                 image.getId(), dest, title, created)) {
                CSyncUtils.updateInProgress(acct.getType(), acct.getName());
            }
            return dest;
        }

        // Download url to destination; then notify mediastore.
        boolean ok = false;
        InputStream in = null;
        OutputStream out = null;
        try {
            CUtils.LOGD(TAG, "Downloading "+src+" to "+dest);
            in = src.openConnection().getInputStream();
            out = new FileOutputStream(dest);
            CIOUtils.copy(in, out);
            out.close();
            out = null;
            CMediaUtils.addMedia
                (m_appctx, m_cdb.getDb(), acct.getId(),
                 image.getId(), dest, title, created);
            ok = true;
            result.stats.numInserts++;
            // Allow UI to update the thumbnails
            CSyncUtils.updateInProgress(acct.getType(), acct.getName());
            return dest;
        }
        finally {
            CUtils.quietlyClose(in);
            CUtils.quietlyClose(out);
            // Clean up on any errors.
            if (!ok) { dest.delete(); }
        }
    }

    private void keepOnly
        (CAccount acct, ContentProviderClient provider,
         File base, Set<File> keep, SyncResult result)
        throws IOException, RemoteException
    {
        File actual[] = base.listFiles();
        if (actual == null) { return; }
        for (int i=0; i<actual.length; i++) {
            File cur = actual[i];
            if (!keep.contains(cur)) {
                CMediaUtils.removeMedia
                    (provider, m_cdb.getDb(), acct.getId(), cur);
                result.stats.numDeletes++;
            }
        }
    }

    // note: return true if you can attempt a retry right away.
    private boolean attemptUpload
        (CAccount acct, SharedPreferences prefs, long account_added,
         String auth, List<CMediaUtils.Info> images, SyncResult result)
    {
        try {
            int count = 0;
            int max = images.size();
            for (CMediaUtils.Info image: images) {

                // Various things can dynamically affect whether we
                // should in fact perform an upload. Take care of
                // these.
                if (!canUseNetwork(acct, result)) {
                    return false;
                }
                if (!ABaseGlue.uploadEnabled(m_appctx, acct)) {
                    CSyncUtils.setResult
                        (m_cdb, acct, CSyncUtils.Result.DONE, null);
                    return false;
                }

                // If the user flips the "upload-all" setting in the
                // middle of an upload batch, check if we need to
                // abort it.
                if (!prefs.getBoolean(CUtils.PREF_UPLOAD_ALL, true)) {
                    if (image.getCreated() < account_added) {
                        max--;
                        continue;
                    }
                }

                count++;
                CSyncUtils.setResult
                    (m_cdb, acct, CSyncUtils.Result.UPLOADS_IN_PROGRESS,
                     count+"/"+max);
                CSyncUtils.updateInProgress(acct.getType(), acct.getName());

                ABaseGlue.upload
                    (m_appctx, m_cdb.getDb(), acct, auth, image, result);
            }
            // all finished; so we don't need to retry.
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.DONE, null);
            return false;
        }
        catch (IOException ioe) {
            // Assumed to be a temporary failure.
            CUtils.LOGD(TAG, "Temporary failure", ioe);
            CSyncUtils.logException(m_cdb, acct, ioe);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.IO_RETRY, null);
            result.stats.numIoExceptions++;
            updateBackoff(acct, result);
            return false;
        }
        catch (CPlugin.AuthorizationException ae) {
            // Assumed to be an invalid token.
            // Remove cached token, and retry.
            CUtils.LOGD(TAG, "Removing cached auth token.", ae);
            m_acctmgr.invalidateAuthToken(CUtils.BASE_ACCOUNT_TYPE, auth);
            return true;
        }
        catch (CPlugin.PermanentException pe) {
            // Ah well. Remove everything and retry.
            CUtils.LOGD(TAG, "Permanent exception.", pe);
            CSyncUtils.logException(m_cdb, acct, pe);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.AUTH_FAILURE, null);
            m_acctmgr.invalidateAuthToken(CUtils.BASE_ACCOUNT_TYPE, auth);
            m_acctmgr.setPassword(ABaseGlue.asAndroidAccount(acct), null);
            result.stats.numAuthExceptions++;
            return true;
        }
    }

    private final String grabAuth(CAccount acct, SyncResult result)
    {
        String auth;
        try {
            auth = m_acctmgr.blockingGetAuthToken
                (ABaseGlue.asAndroidAccount(acct), CUtils.AUTH_ACCESS, true);
            //CUtils.LOGD(TAG, "auth-token: "+auth);
            return auth;
        }
        catch (IOException ioe) {
            // Considered a temporary failure.
            CUtils.LOGD(TAG, "token-io-failure", ioe);
            CSyncUtils.logException(m_cdb, acct, ioe);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.IO_RETRY, null);
            result.stats.numIoExceptions++;
            return null;
        }
        catch (OperationCanceledException oce) {
            // Considered a hard failure, I guess?
            CUtils.LOGD(TAG, "token-cancelled", oce);
            CSyncUtils.logException(m_cdb, acct, oce);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.AUTH_FAILURE,
                 "cancelled: "+oce.getMessage());
            result.stats.numAuthExceptions++;
            return null;
        }
        catch (AuthenticatorException ae) {
            // Hard failure.
            CUtils.LOGD(TAG, "token failure", ae);
            CSyncUtils.logException(m_cdb, acct, ae);
            CSyncUtils.setResult
                (m_cdb, acct, CSyncUtils.Result.AUTH_FAILURE,
                 "failed with: "+ae.getMessage());
            result.stats.numAuthExceptions++;
            return null;
        }
    }

    private final void updateLastRun(CAccount acct)
    {
        long prev = optLong(acct, CUtils.KEY_LAST_RUN, 0l);
        CMap.put
            (m_cdb.getDb(), acct.getId(),
             CUtils.KEY_PREV_RUN, String.valueOf(prev));
        CMap.put
            (m_cdb.getDb(), acct.getId(),
             CUtils.KEY_LAST_RUN, String.valueOf(System.currentTimeMillis()));
    }

    // Set up a suitable delay before we get run again.
    private final void updateBackoff(CAccount acct, SyncResult result)
    {
        long prev = optLong(acct, PREV_RUN, 0l);
        long cur = optLong(acct, LAST_RUN, 0l);

        int delta = (int) ((cur - prev)/1000l);

        if (delta < 1) { delta = 1; } // sanity.

        if (delta > RESET_BACKOFF_SEC) {
            result.delayUntil = MIN_BACKOFF_SEC;
        }
        else {
            delta *= 2;
            if (delta > MAX_BACKOFF_SEC) {
                result.delayUntil = MAX_BACKOFF_SEC;
            }
            else {
                result.delayUntil = delta;
            }
        }
        CUtils.LOGD(TAG, "backoff-delay: "+result.delayUntil+" secs");
    }

    private final long optLong(CAccount acct, String key, long dflt)
    { return CMap.optLong(m_cdb.getDb(), acct.getId(), key, dflt); }


    private final ContentResolver m_resolver;
    private final AccountManager m_acctmgr;
    private final Context m_appctx;
    private final CDb m_cdb;

    private final static String LAST_RUN =
        "org.savemypics.android.sync.last_run";
    private final static String PREV_RUN =
        "org.savemypics.android.sync.prev_run";

    private final static int MIN_BACKOFF_SEC = 5;
    private final static int MAX_BACKOFF_SEC = 300;
    private final static int RESET_BACKOFF_SEC = 600;

    private final static String TAG = CUtils.makeLogTag(CSyncAdapter.class);
}
