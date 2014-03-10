package org.savemypics.android.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CDbTest
{
    @Before
    public void before()
        throws IOException
    {
        m_root = makeTempDir();
        m_ctx = new Context(m_root);
        m_save = false;
    }

    @After
    public void after()
        throws IOException
    { if (!m_save) {delTree(m_root);} }

    @Test
    public void checkBasics()
        throws IOException
    {
        CDb cdb = new CDb(m_ctx);
        SQLiteDatabase db = cdb.getDb();

        CAccount acct1 =
            CAccount.findOrAdd(db, "footype", "hello");
        assertNotNull(acct1);
        assertEquals(1, acct1.getId());
        assertEquals("footype", acct1.getType());
        assertEquals("hello", acct1.getName());
        CAccount acct2 = CAccount.findOrAdd(db, "footype", "hello");
        assertEquals(acct2.getId(), acct1.getId());
        assertEquals(acct2.getType(), "footype");
        assertEquals(acct2.getName(), "hello");
        acct2 = CAccount.findOrAdd(db, "footype", "bye");
        assertEquals(2, acct2.getId());
        assertEquals("bye", acct2.getName());

        assertNull(CMap.get(db, acct1.getId(), "key1"));
        assertTrue(CMap.put(db, acct1.getId(), "key1", "value1"));
        assertEquals("value1", CMap.get(db, acct1.getId(), "key1"));
        assertTrue(CMap.put(db, acct1.getId(), "key1", "valueX"));
        assertEquals("valueX", CMap.get(db, acct1.getId(), "key1"));
        assertEquals("valueX", CMap.get(db, acct1.getId(), "key1"));
        assertTrue(CMap.put(db, acct1.getId(), "key1", "value1"));
        assertEquals("value1", CMap.get(db, acct1.getId(), "key1"));

        assertNull(CMap.get(db, acct2.getId(), "key1"));
        assertTrue(CMap.put(db, acct1.getId(), "key1", "value2"));
        assertTrue(CMap.put(db, acct1.getId(), "key1", "value2"));
        assertEquals("value2", CMap.get(db, acct1.getId(), "key1"));
        assertTrue(CMap.put(db, acct2.getId(), "key1", "value1"));
        assertEquals("value1", CMap.get(db, acct2.getId(), "key1"));
        assertEquals("value2", CMap.get(db, acct1.getId(), "key1"));
        assertEquals(3l, CMap.optLong(db, acct1.getId(), "long1", 3l));
        assertTrue(CMap.put(db, acct1.getId(), "long1", "5"));
        assertEquals(5l, CMap.optLong(db, acct1.getId(), "long1", 3l));

        assertEquals(35l, CLocalImage.getMaxCreatedBetween
                     (db, acct1.getId(), 35l, System.currentTimeMillis()));
        CLocalImage limg1 = CLocalImage.addOrReplace
            (db, acct1.getId(), "content://foo", CLocalImage.OK, 10l, 20l);
        assertNotNull(limg1);
        assertTrue(CLocalImage.exists(db, acct1.getId(), "content://foo"));
        assertFalse(CLocalImage.exists(db, acct2.getId(), "content://foo"));
        assertFalse(CLocalImage.exists(db, acct1.getId(), "content://bar"));
        assertEquals("content://foo", limg1.getUri());
        assertEquals(10l, limg1.getCreated());
        assertEquals(20l, limg1.getUploaded());

        assertEquals
            (10l, CLocalImage.getMaxCreatedBetween
             (db, acct1.getId(), 0, System.currentTimeMillis()));
        assertEquals
            (0l, CLocalImage.getMaxCreatedBetween
             (db, acct2.getId(), 0, System.currentTimeMillis()));

        CLocalImage limg2 = CLocalImage.addOrReplace
            (db, acct2.getId(), "content://foo", CLocalImage.OK, 30l, 40l);
        assertEquals
            (10l,
             CLocalImage.getMaxCreatedBetween
             (db, acct1.getId(), 0, System.currentTimeMillis()));
        assertEquals
            (30l,
             CLocalImage.getMaxCreatedBetween
             (db, acct2.getId(), 0, System.currentTimeMillis()));
        db.close();
    }

    private File m_root;
    private boolean m_save;
    private Context m_ctx;

    private final static File makeTempDir()
        throws IOException
    {
        File root = File.createTempFile("testdb", "dir");
        if (!root.delete()) {
            throw new IOException("Could not delete temp dir: "+root);
        }
        if (!root.mkdir()) {
            throw new IOException("Could not create temp dir: "+root);
        }
        return root;
    }

    private final static void delTree(File root)
    {
        if (root.isFile()) {
            root.delete();
            return;
        }

        File children[] = root.listFiles();
        if (children != null) {
            for (int i=0; i<children.length; i++) {
                delTree(children[i]);
            }
        }
        root.delete();
    }

}
