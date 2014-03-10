package org.savemypics.plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.savemypics.android.util.CUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class CIOUtils
{
    public final static String TAG_SMPICS = "uploaded:by=savemypics";

    public interface MapFunc<K,V,E extends Throwable>
    { public void apply(int idx, K k, V v) throws E; }

    public final static <K,V,E extends Throwable> void apply
        (Map<K,V> map, MapFunc<K,V,E> func)
        throws E
    {
        if (map == null) { return; }
        int idx = 0;
        for (K key: map.keySet()) {
            func.apply(idx++, key, map.get(key));
        }
    }

    public final static String shaTagFor(File file)
        throws IOException
    { return "hash:sha1="+CUtils.toHex(sha(file)); }

    public final static byte[] sha(File file)
        throws IOException
    {
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            return sha(fin);
        }
        finally {
            CUtils.quietlyClose(fin);
        }
    }

    public final static byte[] sha(InputStream stream)
        throws IOException
    {
        byte[] buf = new byte[8192];
        int nread;
        synchronized (s_digest) {
            s_digest.reset();
            while ((nread = stream.read(buf)) > 0) {
                s_digest.update(buf, 0, nread);
            }
            return s_digest.digest();
        }
    }

    public final static byte[] sha(byte[] data)
    {
        synchronized (s_digest) {
            s_digest.reset();
            return s_digest.digest(data);
        }
    }

    public final static Map<String,String> queryToMap(String post)
    {
        Map<String,String> ret = new HashMap<String,String>();
        if (post == null) { return ret; }
        String[] fields = post.split("\\&");
        for (int i=0; i<fields.length; i++) {
            String kv = fields[i];
            int idx = kv.indexOf('=');
            if (idx > 0) {
                ret.put
                    (kv.substring(0, idx), kv.substring(idx+1));
            }
        }
        return ret;
    }

    public final static IOException asIOException(Throwable th)
    { return asIOException(null, th); }

    public final static IOException asIOException(String msg, Throwable th)
    {
        CUtils.LOGW(TAG, "Wrapping into IOException", th);
        if (msg == null) { msg = th.getMessage(); }
        IOException ret = new IOException(msg);
        ret.initCause(th);
        return ret;
    }

    public final static CPlugin.AuthorizationException
        asAuthorizationException(String msg, Throwable th)
    {
        CUtils.LOGW(TAG, "Wrapping into AuthorizationException", th);
        return new CPlugin.AuthorizationException(msg, th);
    }

    public final static String asFormURLEncoded(final Map<String,String> params)
        throws UnsupportedEncodingException
    {
        final StringBuilder sb = new StringBuilder();
        apply(params, new MapFunc<String,String,UnsupportedEncodingException>(){
                public void apply(int idx, String k, String v)
                    throws UnsupportedEncodingException
                {
                    if (idx > 0) { sb.append("&"); }
                    sb.append(k);
                    sb.append("=");
                    sb.append(URLEncoder.encode(params.get(k), "utf-8"));
                }
            });
        return sb.toString();
    }

    public final static long copy(InputStream inp, OutputStream out)
        throws IOException
    {
        int nread;
        byte [] buf = new byte[4096];
        long total = 0;
        while ((nread = inp.read(buf)) > 0) {
            total += nread;
            out.write(buf, 0, nread);
        }
        return total;
    }

    public final static void writeMultipart
        (HttpURLConnection con, Set<Part> parts)
        throws IOException
    {
        long total = 0l;

        StringBuilder prefix = new StringBuilder();
        for (Part part: parts) {
            if (part instanceof StringPart) {
                ((StringPart) part).addContent(prefix);
            }
            else if (part instanceof FilePart) {
                // Just update the content size; we'll
                // stream the contents later on.
                total += ((FilePart) part).getContentSize();
            }
        }

        StringBuilder suffix = new StringBuilder();
        suffix.append(MARKER);
        suffix.append(BOUNDARY);
        suffix.append(MARKER);
        suffix.append(CRNL);

        byte[] prebytes = prefix.toString().getBytes("utf-8");
        total += prebytes.length + suffix.length();

        con.setFixedLengthStreamingMode((int) total);
        con.setRequestProperty
            ("Content-type", "multipart/form-data; boundary="+BOUNDARY);

        OutputStream out = null;
        boolean ok = false;
        try {
            out = con.getOutputStream();
            out.write(prebytes);
            for (Part part: parts) {
                if (part instanceof FilePart) {
                    ((FilePart) part).write(out);
                }
            }
            out.write(suffix.toString().getBytes("utf-8"));
            out.flush();
            ok = true;
        }
        finally {
            if (!ok && (out != null)) {
                CUtils.quietlyClose(out);
            }
        }
    }

    public abstract static class Part {}

    public final static class StringPart extends Part
    {
        public StringPart(String name, String value)
        {
            m_name = name;
            m_value = value;
        }

        private void addContent(StringBuilder sb)
        {
            sb.append(MARKER);
            sb.append(BOUNDARY);
            sb.append(CRNL);
            sb.append
                ("Content-disposition: form-data; name=\"");
            sb.append(m_name);
            sb.append("\"");
            sb.append(CRNL);
            sb.append(CRNL);
            sb.append(m_value);
            sb.append(CRNL);
        }

        private final String m_name;
        private final String m_value;
    }

    public final static class FilePart extends Part
    {
        public FilePart(String name, File source, String iname, String mime)
        {
            m_name = name;
            m_iname = iname;
            m_source = source;
            m_mime = mime;
        }

        private long getContentSize()
            throws IOException
        {
            long v = makeHeader().toString().getBytes("utf-8").length;
            v += m_source.length();
            // and, for crnl at the end of content.
            v += CRNL.length();
            return v;
        }

        private void write(OutputStream out)
            throws IOException
        {
            out.write(makeHeader().toString().getBytes("utf-8"));
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(m_source);
                copy(fin, out);
                out.write(CRNL.getBytes("utf-8"));
            }
            finally {
                CUtils.quietlyClose(fin);
            }
        }

        private StringBuilder makeHeader()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(MARKER);
            sb.append(BOUNDARY);
            sb.append(CRNL);
            sb.append
                ("Content-disposition: form-data; name=\"");
            sb.append(m_name);
            sb.append("\"; filename=\"");
            sb.append(m_iname.replaceAll("\"", " "));
            sb.append("\"");
            sb.append(CRNL);
            sb.append("Content-Type: ");
            sb.append(m_mime);
            sb.append(CRNL);
            sb.append(CRNL);
            return sb;
        }

        private final String m_name;
        private final String m_iname;
        private final File m_source;
        private final String m_mime;
    }

    // Generic xml helpers
    public final static Element mustHaveFirstChild
        (Element root, String ns, String path)
        throws CPlugin.PermanentException
    {
        Element ret = firstChild(root, ns, path);
        if (ret == null) {
            throw new CPlugin.PermanentException
                ("Unable to get path: "+path+" from "+root);
        }
        return ret;
    }

    // First child Element matching this name, or null.
    public final static Element firstChild(Element root, String ns, String path)
    {
        String names[] = path.split("/");
        for (int i=0; i<names.length; i++) {
            String name = names[i];
            NodeList nl = root.getElementsByTagNameNS(ns, name);
            if (nl == null) { return null; }
            int len = nl.getLength();
            if (len < 1) { return null; }
            root = (Element) (nl.item(0));
        }
        return root;
    }

    public final static Document parseDocument(String content)
        throws CPlugin.PermanentException, IOException
    {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder(); 
            return db.parse
                (new InputSource(new StringReader(content)));
        }
        catch (ParserConfigurationException pce) {
            throw new CPlugin.PermanentException(pce);
        }
        catch (SAXException se) {
            // Treat as a transient ioexception - this is probably
            // a dumb idea.
            throw CIOUtils.asIOException(se);
        }
    }



    // After you've added all the headers, written out
    // any data, etc etc
    public final static Response getResponse(HttpURLConnection con)
        throws IOException
    {
        int rcode;
        // android bug workaround.
        try { rcode = con.getResponseCode(); }
        catch (IOException ioe) {
            rcode = con.getResponseCode();
            if (rcode == -1) { throw ioe; }
        }
        if (rcode == -1) {
            throw new IOException("Unable to connect");
        }
        if (rcode >= 400) {
            return new Response(rcode, asString(con.getErrorStream()));
        }
        else {
            return new Response(rcode, asString(con.getInputStream()));
        }
    }

    public final static HttpURLConnection setTimeout(HttpURLConnection con)
    {
        con.setConnectTimeout(CONNECT_TIMEOUT_MSEC);
        con.setReadTimeout(READ_TIMEOUT_MSEC);
        return con;
    }

    private final static String asString(InputStream in)
        throws IOException
    {
        if (in == null) { return ""; }

        ByteArrayOutputStream baout = new ByteArrayOutputStream();
        try {
            copy(in, baout);
            baout.close();
        }
        finally {
            CUtils.quietlyClose(in);
        }
        String ret = new String(baout.toByteArray(), "utf-8");
        CUtils.LOGD(TAG, ret);
        return ret;
    }

    public final static class Response
    {
        private Response(int code, String content)
        {
            m_code = code;
            m_content = content;
        }
        public final int getCode()
        { return m_code; }
        public final String getContent()
        { return m_content; }
        public final String toString()
        { return "code: "+m_code+"\n"+m_content+"\n"; }
        private final int m_code;
        private final String m_content;
    }

    private static final String BOUNDARY = "boundary_ED7BCn5sAonDslXm";
    private static final String MARKER = "--";
    private static final String CRNL = "\r\n";
    private final static int CONNECT_TIMEOUT_MSEC = 15*1000;
    private final static int READ_TIMEOUT_MSEC = 30*1000;
    private final static MessageDigest s_digest;
    static
    {
        // Just crash miserably - we can't do much.
        try { s_digest = MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException nse) {
            throw new ExceptionInInitializerError(nse);
        }
    }
    private final static String TAG = CUtils.makeLogTag(CIOUtils.class);
}
