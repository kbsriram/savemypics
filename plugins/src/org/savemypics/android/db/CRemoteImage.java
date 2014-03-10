package org.savemypics.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

public final class CRemoteImage
{
    private final static String TABLE = "remote_image";
    private final static String ACCOUNT_ID = "account_id";
    private final static String REMOTE_ID = "remote_id";
    private final static String URI = "uri";
    private final static String CREATED = "created";

    private final static String TABLE_CREATE =
        "create table "+TABLE+"("+
        ACCOUNT_ID+" integer not null,"+
        REMOTE_ID+" text not null,"+
        URI+" text not null,"+
        CREATED+" integer not null,"+
        "primary key ("+ACCOUNT_ID+","+REMOTE_ID+"),"+
        "foreign key ("+ACCOUNT_ID+") references "+
        CAccount.TABLE+"("+CAccount._ID+"))";

    private final static String TABLE_INDEX_CREATED =
        "create index "+TABLE+"_"+CREATED+"_index on "+
        TABLE+"("+CREATED+" desc)";

    private final static String TABLE_INDEX_URI =
        "create index "+TABLE+"_"+URI+"_index on "+
        TABLE+"("+URI+")";

    private final static String SELECT_FIELDS =
        "select "+ACCOUNT_ID+","+
        REMOTE_ID+","+
        URI+","+
        CREATED;

    private final static String SELECT_BY_ACCOUNT_LIMIT =
        SELECT_FIELDS+" from "+TABLE+" where "+
        ACCOUNT_ID+"=? order by "+CREATED+" desc limit ?";

    private final static String BY_ACCOUNT_URI =
        ACCOUNT_ID+"=? and "+URI+"=?";

    private final static String SELECT_COUNT_BY_ACCOUNT_URI =
        "select count(1) from "+TABLE+
        " where "+BY_ACCOUNT_URI;

    public final static int deleteByUri
        (SQLiteDatabase db, long aid, String uri)
    {
        return db.delete
            (TABLE, BY_ACCOUNT_URI, new String[] { String.valueOf(aid), uri });
    }

    public final static boolean exists
        (SQLiteDatabase db, long aid, String uri)
    {
        return 1l == DatabaseUtils.longForQuery
            (db, SELECT_COUNT_BY_ACCOUNT_URI,
             new String[] {String.valueOf(aid), uri});
    }

    public final static List<CRemoteImage> getRecents
        (SQLiteDatabase db, long aid, int limit)
    {
        List<CRemoteImage> ret = new ArrayList<CRemoteImage>();
        Cursor c = db.rawQuery
            (SELECT_BY_ACCOUNT_LIMIT,
             new String[] {
                String.valueOf(aid), String.valueOf(limit)
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

    public final static CRemoteImage addOrReplace
        (SQLiteDatabase db, long aid, String remote_id,
         String uri, long created)
    {
        ContentValues cv = new ContentValues();
        cv.put(ACCOUNT_ID, aid);
        cv.put(REMOTE_ID, remote_id);
        cv.put(URI, uri);
        cv.put(CREATED, created);

        long id = db.insertWithOnConflict
            (TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        if (id <= 0) { return null; }
        return new CRemoteImage(aid, remote_id, uri, created);
    }

    private final static CRemoteImage fromCursor(Cursor c)
    {
        return new CRemoteImage
            (c.getLong(0),
             c.getString(1),
             c.getString(2),
             c.getLong(3));
    }


    private CRemoteImage
        (long aid, String remote_id, String uri, long created)
    {
        m_aid = aid;
        m_remote_id = remote_id;
        m_uri = uri;
        m_created = created;
    }
    public long getCreated()
    { return m_created; }
    public String getUri()
    { return m_uri; }
    public long getAccountId()
    { return m_aid; }
    public String getRemoteId()
    { return m_remote_id; }
    private final long m_aid;
    private final String m_remote_id;
    private final String m_uri;
    private final long m_created;

    final static void makeSchema(SQLiteDatabase db)
    {
        db.execSQL(TABLE_CREATE);
        db.execSQL(TABLE_INDEX_CREATED);
    }
}
