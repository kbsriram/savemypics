package org.savemypics.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("serial")
public final class CMap
{
    private final static String TABLE = "map";
    private final static String ACCOUNT_ID = "account_id";
    private final static String KEY = "mkey";
    private final static String VALUE = "mvalue";

    private final static String TABLE_CREATE =
        "create table "+TABLE+"("+
        ACCOUNT_ID+" integer not null,"+
        KEY+" text not null,"+
        VALUE+" text not null,"+
        "primary key ("+ACCOUNT_ID+","+KEY+"),"+
        "foreign key ("+ACCOUNT_ID+") references "+
        CAccount.TABLE+"("+CAccount._ID+"))";

    private final static String SELECT_BY_ACCOUNT_KEY =
        "select "+VALUE+" from "+TABLE+" where "+ACCOUNT_ID+"=? and "+KEY+"=?";

    public final static long optLong
        (SQLiteDatabase db, long aid, String key, long dflt)
    {
        String value = get(db, aid, key);
        if ((value == null) || (value.length() == 0)) { return dflt; }
        return Long.parseLong(value);
    }

    public final static String optString
        (SQLiteDatabase db, long aid, String key, String dflt)
    {
        String value = get(db, aid, key);
        if ((value == null) || (value.length() == 0)) { return dflt; }
        else { return value; }
    }

    public final static String get(SQLiteDatabase db, long aid, String key)
    {
        String k = makeKey(aid, key);
        String ret;
        synchronized (s_cache) {
            ret = s_cache.get(k);
        }
        if (ret != null) { return ret; }

        Cursor result = db.rawQuery
            (SELECT_BY_ACCOUNT_KEY, new String[] {String.valueOf(aid), key});
        try {
            if (result.moveToNext()) { return cache(k, result.getString(0)); }
            else { return null; }
        }
        finally {
            CDb.close(result);
        }
    }

    // return true if the insert worked.
    public final static boolean put
        (SQLiteDatabase db, long aid, String key, String value)
    {
        String k = makeKey(aid, key);
        ContentValues cv = new ContentValues();
        cv.put(ACCOUNT_ID, aid);
        cv.put(KEY, key);
        cv.put(VALUE, value);
        boolean ret = db.insertWithOnConflict
            (TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE) > 0;
        if (ret) { cache(k, value); }
        else { cache(k, null); }
        return ret;
    }

    final static void makeSchema(SQLiteDatabase db)
    {
        db.execSQL(TABLE_CREATE);
    }

    private final static String makeKey(long id, String k)
    { return id+"^"+k; }

    private final static String cache(String k, String v)
    {
        synchronized (s_cache) {
            if (v != null) { s_cache.put(k, v); }
            else { s_cache.remove(k); }
        }
        return v;
    }

    private final static Map<String, String> s_cache =
        (new LinkedHashMap<String, String>() {
            @Override protected boolean removeEldestEntry(Map.Entry e) {
                return size() > 25;
            }
        });
}
