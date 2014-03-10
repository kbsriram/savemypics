package org.savemypics.android.sync;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.savemypics.android.db.CAccount;
import org.savemypics.android.db.CDb;
import org.savemypics.android.db.CLocalImage;
import org.savemypics.android.db.CMap;
import org.savemypics.android.db.CRemoteImage;
import org.savemypics.android.event.AEvent;
import org.savemypics.android.event.CEventBus;
import org.savemypics.android.glue.ABaseGlue;
import org.savemypics.android.service.CTaskQueue;
import org.savemypics.android.util.CUtils;

public class CSyncUtils
{
    public enum Result {
        DONE, NO_USABLE_NETWORK, UNEXPECTED_RETRY,
        IO_RETRY, UPLOADS_IN_PROGRESS, DOWNLOADS_IN_PROGRESS,
        AUTH_FAILURE
    };

    public final static class ProgressEvent
        extends AEvent
    {
        public enum Status {
            STARTED, FINISHED, UPDATED
        };

        public interface Listener
            extends AEvent.Listener
        {
            public void onSyncProgress(ProgressEvent ev);
        }
        public final static void subscribe(final Listener l)
        { doSubscribe(l, CEventBus.EVENT_SYNC_PROGRESS); }

        public final static void unsubscribe(final Listener l)
        { doUnsubscribe(l, CEventBus.EVENT_SYNC_PROGRESS); }

        private final static void publish
            (String atype, String aname, Status s)
        {
            ProgressEvent pe = new ProgressEvent(atype, aname, s);
            pe.doPublish(CEventBus.EVENT_SYNC_PROGRESS);
        }

        protected final void onUpdate(AEvent.Listener l)
        { ((Listener) l).onSyncProgress(this); }

        private ProgressEvent
            (String atype, String aname, Status s)
        {
            m_atype = atype;
            m_aname = aname;
            m_status = s;
        }
        public String getType()
        { return m_atype; }
        public String getName()
        { return m_aname; }
        public Status getStatus()
        { return m_status; }
        private final String m_atype;
        private final String m_aname;
        private final Status m_status;
    }


    public final static class InfoEvent
        extends AEvent
    {
        public interface Listener
            extends AEvent.Listener
        {
            public void onSyncInfo(InfoEvent ev);
        }

        public final static void subscribe(final Listener l)
        { doSubscribe(l, CEventBus.EVENT_SYNC_INFO); }

        public final static void unsubscribe(final Listener l)
        { doUnsubscribe(l, CEventBus.EVENT_SYNC_INFO); }

        private final static void publish
            (Object tag, String atype, String aname,
             long upload_count, long pending_count,
             String last_result, String last_exception,
             List<CLocalImage> recents, List<CMediaUtils.Info> pendings,
             List<CRemoteImage> downloads)
        {
            long result_time;
            Result result;
            String extra;

            if (last_result == null) {
                result = null;
                result_time = 0l;
                extra = null;
            }
            else {
                int lastidx = 0;
                int curidx = last_result.indexOf(RESULT_SEPARATOR);
                result_time = Long.parseLong
                    (last_result.substring(lastidx, curidx));
                lastidx = curidx+1;

                curidx = last_result.indexOf(RESULT_SEPARATOR, lastidx);
                if (curidx > 0) {
                    try {
                        result = Result.valueOf
                            (last_result.substring(lastidx, curidx));
                    }
                    catch (Throwable th) {
                        result = Result.DONE;
                    }

                    curidx++;
                    if (curidx < last_result.length()) {
                        extra = last_result.substring(curidx);
                    }
                    else {
                        extra = null;
                    }
                }
                else {
                    try {
                        result = Result.valueOf
                            (last_result.substring(lastidx));
                    }
                    catch (Throwable th) {
                        result = Result.DONE;
                    }
                    extra = null;
                }
            }

            long exception_time;
            String exception_trace;
            if (last_exception == null) {
                exception_time = 0l;
                exception_trace = null;
            }
            else {
                int curidx = last_exception.indexOf(RESULT_SEPARATOR);
                exception_time = Long.parseLong
                    (last_exception.substring(0, curidx));
                exception_trace = last_exception.substring(curidx+1);
            }

            InfoEvent ie = new InfoEvent
                (tag, atype, aname, upload_count, pending_count,
                 result_time, result, extra,
                 exception_time, exception_trace,
                 recents, pendings, downloads);
            ie.doPublish(CEventBus.EVENT_SYNC_INFO);
        }

        protected final void onUpdate(AEvent.Listener l)
        { ((Listener) l).onSyncInfo(this); }

        private InfoEvent
            (Object tag, String atype, String aname,
             long upload_count, long pending_count,
             long result_time, Result result, String extra,
             long exception_time, String exception_trace,
             List<CLocalImage> recents, List<CMediaUtils.Info> pending,
             List<CRemoteImage> downloads)
        {
            m_tag = tag;
            m_atype = atype;
            m_aname = aname;
            m_upload_count = upload_count;
            m_pending_count = pending_count;
            m_result_time = result_time;
            m_result = result;
            m_result_extra = extra;
            m_recents = recents;
            m_exception_time = exception_time;
            m_exception_trace = exception_trace;
            m_pending = pending;
            m_downloads = downloads;
        }

        public final Object getTag()
        { return m_tag; }
        public final String getType()
        { return m_atype; }
        public final String getName()
        { return m_aname; }
        public final long getUploadCount()
        { return m_upload_count; }
        public final long getPendingCount()
        { return m_pending_count; }
        public final long getResultTime()
        { return m_result_time; }
        public final Result getResult()
        { return m_result; }
        public final String getResultExtra()
        { return m_result_extra; }
        public final long getLastExceptionTime()
        { return m_exception_time; }
        public final String getLastExceptionTrace()
        { return m_exception_trace; }
        public final List<CLocalImage> getRecents()
        { return m_recents; }
        public final List<CMediaUtils.Info> getPendings()
        { return m_pending; }
        public final List<CRemoteImage> getDownloads()
        { return m_downloads; }

        private final Object m_tag;
        private final String m_atype;
        private final String m_aname;
        private final long m_upload_count;
        private final long m_pending_count;
        private final long m_result_time;
        private final Result m_result;
        private final String m_result_extra;
        private final long m_exception_time;
        private final String m_exception_trace;
        private final List<CLocalImage> m_recents;
        private final List<CMediaUtils.Info> m_pending;
        private final List<CRemoteImage> m_downloads;
    }

    public final static void startSyncManually(String atype, String aname)
    {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        CUtils.LOGD(TAG, "start-manual-sync: "+atype+","+aname);
        ContentResolver.requestSync
            (ABaseGlue.asAndroidAccount(atype, aname),
             MediaStore.AUTHORITY, extras);
    }

    public final static void asyncGetSyncInfo
        (final Context ctx, final Object tag,
         final String atype, final String aname)
    {
        CTaskQueue.enqueueLocalTask(ctx, new CTaskQueue.Task() {
                protected void runTask() {
                    getSyncInfo(getContext(), getDb(), tag, atype, aname);
                }
            });
    }

    public final static boolean syncInProgress(String atype, String aname)
    {
        String key = inProgressKey(atype, aname);
        synchronized (s_inprogress) {
            Integer v = s_inprogress.get(key);
            if (v == null) { return false; }
            if (v.intValue() <= 0) { return false; }
            return true;
        }
    }

    final static void setResult
        (CDb cdb, CAccount acct, Result result, String extra)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(System.currentTimeMillis()));
        sb.append(RESULT_SEPARATOR);
        sb.append(result.toString());
        if (extra != null) {
            sb.append(RESULT_SEPARATOR);
            sb.append(extra);
        }

        CMap.put
            (cdb.getDb(), acct.getId(),
             CUtils.KEY_LAST_RESULT, sb.toString());
    }

    final static void logException
        (CDb cdb, CAccount acct, Throwable cause)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(System.currentTimeMillis()));
        sb.append(RESULT_SEPARATOR);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        cause.printStackTrace(pw);
        pw.flush();
        sb.append(sw.toString());
        CMap.put
            (cdb.getDb(), acct.getId(),
             CUtils.KEY_LAST_EXCEPTION, sb.toString());
    }

    final static void incrementInProgress(String atype, String aname)
    {
        String key = inProgressKey(atype, aname);
        synchronized (s_inprogress) {
            Integer v = s_inprogress.remove(key);
            int cur;
            if (v == null) {
                cur = 1;
            }
            else {
                cur = v.intValue() + 1;
            }
            s_inprogress.put(key, cur);
            if (cur == 1) {
                ProgressEvent.publish
                    (atype, aname, ProgressEvent.Status.STARTED);
            }
        }
    }

    final static void updateInProgress(String atype, String aname)
    {
        long now = System.currentTimeMillis();
        boolean publish;
        synchronized (s_inprogress) {
            publish = (now - s_last_progress_ts) > PROGRESS_INTERVAL_MSEC;
            if (publish) { s_last_progress_ts = now; }
        }
        if (publish) {
            ProgressEvent.publish(atype, aname, ProgressEvent.Status.UPDATED);
        }
    }

    final static void decrementInProgress(String atype, String aname)
    {
        String key = inProgressKey(atype, aname);
        synchronized (s_inprogress) {
            Integer v = s_inprogress.remove(key);
            if (v == null) {
                // Unexpected
                CUtils.LOGW(TAG, "Unexpected decr: "+atype+","+aname);
                return;
            }
            int cur = v.intValue() - 1;
            if (cur <= 0) {
                ProgressEvent.publish
                    (atype, aname, ProgressEvent.Status.FINISHED);
                return;
            }
            cur = v.intValue() - 1;
            s_inprogress.put(key, cur);
        }
    }

    final static List<CMediaUtils.Info> getPendingUploads
        (Context ctx, CDb cdb, CAccount acct)
    {
        return getPendingUploads
            (ctx, cdb,
             CUtils.getSharedPreferences(ctx, acct.getType(), acct.getName()),
             acct);
    }

    private final static List<CMediaUtils.Info> getPendingUploads
        (Context ctx, CDb cdb, SharedPreferences prefs, CAccount acct)
    {
        if (!prefs.getBoolean(CUtils.PREF_UPLOAD_ENABLED, true)) {
            return new ArrayList<CMediaUtils.Info>();
        }

        // We compute this in two ranges, to allow for situations
        // where the user may flip the "upload-all" setting multiple
        // times, before we've uploaded everything in the pre-existing
        // range.
        long account_added = prefs.getLong
            (CUtils.PREF_ACCOUNT_INSTALLED_ON, 0l);
        List<CMediaUtils.Info> pending;

        // First add the pre-existing image range
        if (prefs.getBoolean(CUtils.PREF_UPLOAD_ALL, true)) {
            // Range #1 : max(before account_added) -> account_added
            long end = account_added;
            long start =
                CLocalImage.getMaxCreatedBetween
                (cdb.getDb(), acct.getId(), 0, end);
            //CUtils.LOGD(TAG, atype+":"+aname+":r1="+start+"-"+end);
            pending = CMediaUtils.newMediaAddedBetween
                (ctx, cdb.getDb(), acct.getId(), start, end);
        }
        else {
            pending = new ArrayList<CMediaUtils.Info>();
        }

        // Next add the after-installed image range

        // Range #2 : max(after account_added) -> now
        long end = System.currentTimeMillis();
        long start =
            CLocalImage.getMaxCreatedBetween
            (cdb.getDb(), acct.getId(), account_added, end);
        //CUtils.LOGD(TAG, atype+":"+aname+":r2="+start+"-"+end);
        pending.addAll
            (CMediaUtils.newMediaAddedBetween
             (ctx, cdb.getDb(), acct.getId(), start, end+10*1000l));
        // end has cushion to avoid seconds to msec roundoff errors.
        return pending;
    }

    private final static String inProgressKey(String atype, String aname)
    { return atype+":"+aname; }

    // Generate info that's needed by the UI, except for thumbnails.
    private final static void getSyncInfo
        (final Context ctx, final CDb db, final Object tag,
         final String atype, final String aname)
    {
        CAccount acct = CAccount.find(db.getDb(), atype, aname);
        if (acct == null) {
            // Unexpected
            CUtils.LOGW(TAG, "Unexpected - missing account: "+atype+","+aname);
            InfoEvent.publish
                (tag, atype, aname, -1, -1, null, null,
                 new ArrayList<CLocalImage>(),
                 new ArrayList<CMediaUtils.Info>(),
                 new ArrayList<CRemoteImage>());
            return;
        }

        SharedPreferences prefs =
            CUtils.getSharedPreferences(ctx, acct.getType(), acct.getName());
        List<CMediaUtils.Info> newimages =
            getPendingUploads(ctx, db, prefs, acct);

        int pending_count = newimages.size();
        int max =
            (pending_count > UI_LAST_TN_COUNT)?UI_LAST_TN_COUNT:pending_count;
        List<CMediaUtils.Info> pending = new ArrayList<CMediaUtils.Info>();
        for (int i=0; i<max; i++) {
            pending.add(newimages.get(i));
        }
        newimages = null; // gc as quickly as possible.

        List<CRemoteImage> downloads;
        if (prefs.getBoolean(CUtils.PREF_DOWNLOAD_ENABLED, true)) {
            downloads = CRemoteImage.getRecents
                (db.getDb(), acct.getId(), UI_LAST_TN_COUNT);
        }
        else {
            downloads = new ArrayList<CRemoteImage>();
        }

        InfoEvent.publish
            (tag, atype, aname,
             CLocalImage.getCountByStatus
             (db.getDb(), acct.getId(), CLocalImage.OK),
             pending_count,
             CMap.get(db.getDb(), acct.getId(), CUtils.KEY_LAST_RESULT),
             CMap.get(db.getDb(), acct.getId(),CUtils.KEY_LAST_EXCEPTION),
             CLocalImage.getByStatus
             (db.getDb(),acct.getId(),CLocalImage.OK, UI_LAST_TN_COUNT),
             pending, downloads);
    }

    private final static Map<String,Integer> s_inprogress =
        new HashMap<String,Integer>();
    private static long s_last_progress_ts = 0l;
    private final static long PROGRESS_INTERVAL_MSEC = 8000l;
    private final static int UI_LAST_TN_COUNT = 6;
    private final static char RESULT_SEPARATOR = '|';
    private final static String TAG = CUtils.makeLogTag(CSyncUtils.class);
}
