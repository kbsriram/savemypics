package org.savemypics.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("serial")
public final class CAccount
{
    public final static String TABLE = "account";
    public final static String _ID = "_id";
    private final static String TYPE = "atype";
    private final static String NAME = "aname";

    private final static String TABLE_CREATE =
        "create table "+TABLE+"("+
        _ID+" integer primary key,"+
        TYPE+" text not null,"+
        NAME+" text not null,"+
        "unique ("+TYPE+","+NAME+"))";

    private final static String SELECT_BY_TYPE_NAME =
        "select "+_ID+" from "+TABLE+
        " where "+TYPE+"=? and "+NAME+"=?";

    public final static CAccount findOrAdd
        (SQLiteDatabase db, String atype, String name)
    {
        CAccount ret = find(db, atype, name);
        if (ret != null) { return ret; }

        ContentValues cv = new ContentValues();
        cv.put(TYPE, atype);
        cv.put(NAME, name);

        long id = db.insertWithOnConflict
            (TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        if (id <= 0) { return null; }
        return cache(id, atype, name);
    }

    public final static CAccount find
        (SQLiteDatabase db, String atype, String name)
    {
        String key = makeKey(atype, name);
        CAccount ret;
        synchronized (s_cache) {
            ret = s_cache.get(key);
        }
        if (ret != null) { return ret; }

        Cursor c = db.rawQuery
            (SELECT_BY_TYPE_NAME, new String[]{atype, name});
        try {
            if (c.moveToNext()) {
                return cache(c.getLong(0), atype, name);
            }
            else {
                return null;
            }
        }
        finally {
            CDb.close(c);
        }
    }

    private final static CAccount cache(long id, String atype, String name)
    {
        CAccount ret = new CAccount(id, atype, name);
        String k = makeKey(atype, name);
        synchronized (s_cache) {
            s_cache.put(k, ret);
        }
        return ret;
    }

    private final static String makeKey(String atype, String name)
    { return atype+"^"+name; }

    private CAccount(long id, String atype, String name)
    {
        m_id = id;
        m_atype = atype;
        m_name = name;
    }
    public long getId()
    { return m_id; }
    public String getType()
    { return m_atype; }
    public String getName()
    { return m_name; }
    public String toString()
    { return "CAccount["+m_id+","+m_atype+","+m_name+"]"; }

    private final long m_id;
    private final String m_atype;
    private final String m_name;

    final static void makeSchema(SQLiteDatabase db)
    {
        db.execSQL(TABLE_CREATE);
    }

    private final static Map<String, CAccount> s_cache =
        (new LinkedHashMap<String, CAccount>() {
            @Override protected boolean removeEldestEntry(Map.Entry e) {
                return size() > 5;
            }
        });
}
