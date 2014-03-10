package org.savemypics.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

public final class CLocalImage
{
    public final static String OK = "ok";
    public final static String SKIP = "skip";
    public final static String FAILED = "failed";

    private final static String TABLE = "local_image";
    private final static String ACCOUNT_ID = "account_id";
    private final static String URI = "uri";
    private final static String STATUS = "status";
    private final static String CREATED = "created";
    private final static String UPLOADED = "uploaded";

    private final static String TABLE_CREATE =
        "create table "+TABLE+"("+
        ACCOUNT_ID+" integer not null,"+
        URI+" text not null,"+
        STATUS+" text not null,"+
        CREATED+" integer not null,"+
        UPLOADED+" integer not null,"+
        "primary key ("+ACCOUNT_ID+","+URI+"),"+
        "foreign key ("+ACCOUNT_ID+") references "+
        CAccount.TABLE+"("+CAccount._ID+"))";

    private final static String TABLE_INDEX_CREATED =
        "create index "+TABLE+"_"+CREATED+"_index on "+
        TABLE+"("+CREATED+" desc)";

    private final static String TABLE_INDEX_UPLOADED =
        "create index "+TABLE+"_"+UPLOADED+"_index on "+
        TABLE+"("+UPLOADED+" desc)";

    private final static String SELECT_MAX_CREATED_BY_ACCOUNT_BETWEEN =
        "select max("+CREATED+") from "+TABLE+
        " where "+ACCOUNT_ID+"=? and "+
        CREATED+">? and "+CREATED+"<=?";

    private final static String SELECT_COUNT_BY_ACCOUNT_URI =
        "select count(1) from "+TABLE+
        " where "+ACCOUNT_ID+"=? and "+URI+"=?";

    private final static String SELECT_COUNT_BY_ACCOUNT_URI_STATUS =
        "select count(1) from "+TABLE+
        " where "+ACCOUNT_ID+"=? and "+URI+"=? and "+STATUS+"=?";

    private final static String SELECT_COUNT_BY_ACCOUNT_STATUS =
        "select count(1) from "+TABLE+
        " where "+ACCOUNT_ID+"=? and "+STATUS+"=?";

    private final static String SELECT_FIELDS =
        "select "+ACCOUNT_ID+","+
        URI+","+
        STATUS+","+
        CREATED+","+
        UPLOADED;

    private final static String SELECT_BY_ACCOUNT_STATUS_COUNT =
        SELECT_FIELDS+" from "+TABLE+" where "+
        ACCOUNT_ID+"=? and "+STATUS+"=? order by "+UPLOADED+" desc limit ?";

    public final static long getMaxCreatedBetween
        (SQLiteDatabase db, long aid, long start, long end)
    {
        Cursor c = db.rawQuery
            (SELECT_MAX_CREATED_BY_ACCOUNT_BETWEEN,
             new String[] {
                String.valueOf(aid),
                String.valueOf(start),
                String.valueOf(end) });
        try {
            long ret;
            if (c.moveToNext()) {
                ret = c.getLong(0);
                if (ret < start) { ret = start; }
            }
            else {
                ret = start;
            }
            return ret;
        }
        finally {
            CDb.close(c);
        }
    }

    public final static long getCountByStatus
        (SQLiteDatabase db, long aid, String status)
    {
        return DatabaseUtils.longForQuery
            (db, SELECT_COUNT_BY_ACCOUNT_STATUS,
             new String[] {String.valueOf(aid), status});
    }

    public final static List<CLocalImage> getByStatus
        (SQLiteDatabase db, long aid, String status, int limit)
    {
        List<CLocalImage> ret = new ArrayList<CLocalImage>();

        Cursor c = db.rawQuery
            (SELECT_BY_ACCOUNT_STATUS_COUNT,
             new String[] {
                String.valueOf(aid), status, String.valueOf(limit)
            });
        try {
            while (c.moveToNext()) {
                ret.add(fromCursor(c));
            }
            return ret;
        }
        finally {
            CDb.close(c);
        }
    }

    public final static boolean exists(SQLiteDatabase db, long aid, String uri)
    {
        return 1l == DatabaseUtils.longForQuery
            (db, SELECT_COUNT_BY_ACCOUNT_URI,
             new String[] {String.valueOf(aid), uri});
    }

    public final static boolean existsStatus
        (SQLiteDatabase db, long aid, String uri, String status)
    {
        return 1l == DatabaseUtils.longForQuery
            (db, SELECT_COUNT_BY_ACCOUNT_URI_STATUS,
             new String[] {String.valueOf(aid), uri, status});
    }

    public final static CLocalImage addOrReplace
        (SQLiteDatabase db, long aid, String uri, String status,
         long created, long uploaded)
    {
        ContentValues cv = new ContentValues();
        cv.put(ACCOUNT_ID, aid);
        cv.put(URI, uri);
        cv.put(STATUS, status);
        cv.put(CREATED, created);
        cv.put(UPLOADED, uploaded);

        long id = db.insertWithOnConflict
            (TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        if (id <= 0) { return null; }
        return new CLocalImage
            (aid, uri, status, created, uploaded);
    }

    private final static CLocalImage fromCursor(Cursor c)
    {
        return new CLocalImage
            (c.getLong(0),
             c.getString(1),
             c.getString(2),
             c.getLong(3),
             c.getLong(4));
    }

    private CLocalImage
        (long aid, String uri, String status, long created, long uploaded)
    {
        m_aid = aid;
        m_uri = uri;
        m_status = status;
        m_created = created;
        m_uploaded = uploaded;
    }
    public long getCreated()
    { return m_created; }
    public long getUploaded()
    { return m_uploaded; }
    public String getUri()
    { return m_uri; }
    public long getAccountId()
    { return m_aid; }
    public String getStatus()
    { return m_status; }
    private final long m_aid;
    private final String m_uri;
    private final String m_status;
    private final long m_created;
    private final long m_uploaded;

    final static void makeSchema(SQLiteDatabase db)
    {
        db.execSQL(TABLE_CREATE);
        db.execSQL(TABLE_INDEX_CREATED);
        db.execSQL(TABLE_INDEX_UPLOADED);
    }
}
