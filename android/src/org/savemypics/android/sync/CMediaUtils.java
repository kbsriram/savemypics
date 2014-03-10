package org.savemypics.android.sync;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.savemypics.android.db.CDb;
import org.savemypics.android.db.CLocalImage;
import org.savemypics.android.db.CRemoteImage;
import org.savemypics.android.util.CUtils;

public class CMediaUtils
{
    final static void addMedia
        (Context ctx, final SQLiteDatabase db, final long aid,
         final String rid, final File path, final String name, final long ts)
        throws FileNotFoundException
    {
        final Context actx = ctx.getApplicationContext();
        MediaScannerConnection.scanFile
            (ctx, new String[] { path.getAbsolutePath() }, null,
             new MediaScannerConnection.OnScanCompletedListener() {
                 public void onScanCompleted(String p, Uri uri) {
                     CUtils.LOGD(TAG, "scan-complete: "+p+","+uri);
                     updateMeta(actx, uri, name, ts);
                     addRemoteToDb(db, aid, rid, uri, ts);
                 }
             });
    }

    // return true if we already had it.
    final static boolean checkOrAddMedia
        (Context ctx, final SQLiteDatabase db, final long aid,
         final String rid, final File path, final String name, final long ts)
        throws FileNotFoundException
    {
        final Context actx = ctx.getApplicationContext();

        // Check it's in the mediastore
        String uri = getUri(ctx, path);
        if (uri == null) {
            addMedia(actx, db, aid, rid, path, name, ts);
            return false;
        }
        // Verify it in our db
        if (!CRemoteImage.exists(db, aid, uri)) {
            CRemoteImage.addOrReplace(db, aid, rid, uri, ts);
            return false;
        }

        // checks passed.
        return true;
    }

    private final static String getUri(Context ctx, final File path)
    {
        Cursor cursor = null;
        try {
            cursor = ctx.getContentResolver().query
                (MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                 new String[] { MediaStore.MediaColumns._ID },
                 BY_DATA,
                 new String[] { path.toString() }, null);
            if (cursor.moveToFirst()) {
                return idToUri(cursor.getString(0));
            }
            else {
                return null;
            }
        }
        finally {
            CDb.close(cursor);
        }
    }

    private final static String getUri
        (ContentProviderClient provider, final File path)
        throws RemoteException
    {
        Cursor cursor = null;
        try {
            cursor = provider.query
                (MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                 new String[] { MediaStore.MediaColumns._ID },
                 BY_DATA,
                 new String[] { path.toString() }, null);
            if (cursor.moveToFirst()) {
                return idToUri(cursor.getString(0));
            }
            else {
                return null;
            }
        }
        finally {
            CDb.close(cursor);
        }
    }

    final static void removeMedia
        (ContentProviderClient provider, SQLiteDatabase db,
         final long aid, final File path)
        throws RemoteException
    {
        CUtils.LOGD(TAG, "Attempting to remove "+path);

        try {
            // First try to get a URI.
            String uriS = getUri(provider, path);
            if (uriS != null) {
                CUtils.LOGD(TAG, "Deleting "+uriS);
                Uri uri = Uri.parse(uriS);
                provider.delete(uri, null, null);
                removeRemoteFromDb(db, aid, uri);
            }
        }
        finally {
            path.delete();
        }
    }

    public final static List<Info> newMediaAddedBetween
        (Context ctx, SQLiteDatabase db, long aid, long start, long end)
    {
        // This is a bit tricker than it appears, primarily because
        // there are potentially multiple entries in MediaStore with
        // the same "added" timestamp [which is in seconds.]
        Cursor cursor = null;
        List<Info> ret = new ArrayList<Info>();
        CUtils.LOGD(TAG, "New images between "+start+" and "+end);
        try {
            cursor = ctx.getContentResolver().query
                (MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                 PROJECTION,
                 BETWEEN_TIME_ADDED_SEC,
                 new String[] {
                    String.valueOf(start/1000l),
                    String.valueOf(end/1000l)},
                 SORT_TIME);

            if (cursor.moveToFirst()) {
                do { maybeAddInfo(ret, db, aid, start, asInfo(cursor)); }
                while (cursor.moveToNext());
            }
            return ret;
        }
        finally {
            CDb.close(cursor);
        }
    }

    public final static boolean looksLikeJPEG(Info candidate)
    { return matchSuffix(candidate, ".jpg", ".jpeg"); }

    public final static boolean looksLikeImage(Info candidate)
    { return matchSuffix(candidate, ".jpg", ".jpeg", "png", "gif"); }

    private final static void maybeAddInfo
        (List<CMediaUtils.Info> list, SQLiteDatabase db, long aid, long start,
         Info info)
    {
        // Skip files we've created ourserves
        if (info.getPath().contains(CUtils.SAVE_MY_PICS_BASEDIR_COMPONENT)) {
            return;
        }
        // Skip anything except jpeg files for now
        if (!looksLikeJPEG(info)) {
            return;
        }

        // If our timestamp happens to match the start date,
        // check if we've already handled it before.
        if (start == info.getAdded()) {
            if (CLocalImage.existsStatus
                (db, aid, info.getUri(), CLocalImage.OK)) {
                return;
            }
        }

        list.add(info);
    }

    private final static boolean matchSuffix(Info candidate, String... m)
    {
        String p = candidate.getPath();
        int v = p.lastIndexOf('.');
        if (v < 0) { return false; }
        String sfx = p.substring(v, p.length()).toLowerCase(Locale.ENGLISH);

        for (int i=0; i<m.length; i++) {
            if (sfx.equals(m[i])) { return true; }
        }
        return false;
    }

    private final static void updateMeta
        (Context ctx, Uri uri, String name, long ts)
    {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.ImageColumns.DATE_TAKEN, ts);
        cv.put(MediaStore.Images.ImageColumns.IS_PRIVATE, 1);
        cv.put(MediaStore.Images.ImageColumns.TITLE, name);
        ctx.getContentResolver().update(uri, cv, null, null);
    }

    private final static void addRemoteToDb
        (SQLiteDatabase db, long aid, String rid, Uri uri, long ts)
    { CRemoteImage.addOrReplace(db, aid, rid, uri.toString(), ts); }

    private final static void removeRemoteFromDb
        (SQLiteDatabase db, long aid, Uri uri)
    { CRemoteImage.deleteByUri(db, aid, uri.toString()); }

    private final static Info asInfo(Cursor c)
    {
        return new Info
            (idToUri(c.getString(0)),
             c.getString(1),
             c.getLong(2),
             c.getLong(3)*1000l); // sec -> msec
    }

    private final static String idToUri(String id)
    { return MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()+"/"+id; }

    public final static class Info
    {
        private Info(String uri, String path, long created, long added)
        {
            m_uri = uri;
            m_path = path;
            m_created = created;
            m_added = added;
        }
        public String getUri()
        { return m_uri; }
        public String getPath()
        { return m_path; }
        public long getCreated()
        { return m_created; }
        public long getAdded()
        { return m_added; }
        public String toString()
        { return m_path+" ("+m_created+":"+m_added+":"+m_uri+")"; }
        private final String m_uri;
        private final String m_path;
        private final long m_created;
        private final long m_added;
    }

    private final static String[] PROJECTION = {
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATA,
        MediaStore.Images.ImageColumns.DATE_TAKEN,
        MediaStore.Images.ImageColumns.DATE_ADDED
    };

    private final static String BY_DATA =
        MediaStore.MediaColumns.DATA+"=?";

    private final static String BETWEEN_TIME_ADDED_SEC =
        MediaStore.MediaColumns.DATE_ADDED+">=? and "+
        MediaStore.MediaColumns.DATE_ADDED+"<=?";

    private final static String SORT_TIME =
        MediaStore.Images.ImageColumns.DATE_ADDED+" asc";

    private final static String TAG = CUtils.makeLogTag(CMediaUtils.class);
}
