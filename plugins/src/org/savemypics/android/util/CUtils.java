package org.savemypics.android.util;

// Mock class - the real implementation is over
// under android.
import java.io.Closeable;

public class CUtils
{
    public final static String toHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<bytes.length; i++) {
            int unsignedb = (bytes[i] & 0xff);
            sb.append(TOHEX[(unsignedb>>4)&0xf]);
            sb.append(TOHEX[unsignedb&0xf]);
        }
        return sb.toString();
    }

    public final static void quietlyClose(Closeable c)
    {
        if (c != null) {
            try { c.close(); }
            catch (Throwable ign) {}
        }
    }

    public static String makeLogTag(Class cls)
    { return cls.getSimpleName(); }

    public static void LOGD(final String tag, String message)
    { System.err.println(tag+": "+message); }

    public static void LOGD(final String tag, String message, Throwable cause)
    {
        System.err.println(tag+": "+message);
        cause.printStackTrace();
    }

    public static void LOGW(final String tag, String message)
    { LOGD(tag, message); }

    public static void LOGW(final String tag, String message, Throwable cause)
    { LOGD(tag, message, cause); }

    private final static char[] TOHEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
}
