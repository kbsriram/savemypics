package org.savemypics.plugin.flickr;

import android.util.Base64;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONException;
import org.json.JSONObject;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class CFlickrUtils
{
    final static String REST_URL = "https://api.flickr.com/services/rest";
    final static String UPLOAD_URL = "https://up.flickr.com/services/upload";
    final static String REQUEST_URL =
        "https://www.flickr.com/services/oauth/request_token";
    final static String ACCESS_URL =
        "https://www.flickr.com/services/oauth/access_token";

    public final static class RequestToken
    {
        private RequestToken
            (String token, String secret, String callback)
        {
            m_token = token;
            m_secret = secret;
            m_callback = callback;
        }
        public final String getToken()
        { return m_token; }
        public final String getSecret()
        { return m_secret; }
        public final String getCallback()
        { return m_callback; }
        private final String m_token;
        private final String m_secret;
        private final String m_callback;
    }

    public final static class AccessToken
    {
        private AccessToken
            (String token, String secret, String uname)
        {
            m_token = token;
            m_secret = secret;
            m_uname = uname;
        }
        public final String getToken()
        { return m_token; }
        public final String getSecret()
        { return m_secret; }
        public final String getUserName()
        { return m_uname; }
        public final String toString()
        { return m_token+"|"+m_secret+"|"+m_uname; }

        public final static AccessToken fromString(String s)
        {
            String[] fields = s.split("\\|");
            if (fields.length != 3) {
                throw new IllegalArgumentException("bad token: '"+s+"'");
            }
            return new AccessToken(fields[0], fields[1], fields[2]);
        }

        private final String m_token;
        private final String m_secret;
        private final String m_uname;
    }

    public final static RequestToken requestToken(String akey, String asecret)
        throws IOException, CPlugin.PermanentException
    {
        Map<String,String> params = new HashMap<String,String>();
        String cb = URI_TOKEN_CALLBACK+makeNonce();
        params.put("oauth_nonce", makeNonce());
        params.put("oauth_consumer_key", akey);
        params.put("oauth_callback", cb);
        signParams("POST", REQUEST_URL, params, asecret, null);

        CIOUtils.Response resp = getResponseForPost
            (new URL(REQUEST_URL), params);
        if (resp.getCode() != 200) {
            throw new IOException(resp.toString());
        }
        Map<String,String> retvalues = CIOUtils.queryToMap(resp.getContent());
        if (!"true".equals(retvalues.get("oauth_callback_confirmed"))) {
            throw new IOException("Missing callback confirmed: "+ resp);
        }
        String otok = retvalues.get("oauth_token");
        String osec = retvalues.get("oauth_token_secret");
        if ((otok == null) || (osec == null)) {
            throw new IOException("Missing oauth: "+resp);
        }
        return new RequestToken(otok, osec, cb);
    }

    public final static AccessToken accessToken
        (String akey, String asecret, String otok, String osec,
         String ver)
        throws IOException, CPlugin.PermanentException
    {
        Map<String,String> params = new HashMap<String,String>();
        params.put("oauth_nonce", makeNonce());
        params.put("oauth_consumer_key", akey);
        params.put("oauth_verifier", ver);
        params.put("oauth_token", otok);
        signParams("POST", ACCESS_URL, params, asecret, osec);

        CIOUtils.Response resp = getResponseForPost
            (new URL(ACCESS_URL), params);
        if (resp.getCode() != 200) {
            throw new IOException(resp.toString());
        }
        Map<String,String> retvalues = CIOUtils.queryToMap(resp.getContent());
        String utok = retvalues.get("oauth_token");
        String usec = retvalues.get("oauth_token_secret");
        String uname = retvalues.get("username");
        if ((utok == null) || (usec == null)) {
            throw new IOException("Missing oauth: "+resp);
        }
        return new AccessToken(utok, usec, uname);
    }

    final static Map<String,String> initParams
        (String method, String akey, String utok)
    {
        Map<String,String> params = new HashMap<String,String>();
        params.put("oauth_nonce", makeNonce());
        params.put("oauth_consumer_key", akey);
        params.put("oauth_token", utok);
        if (method != null) { params.put("method", method); }
        return params;
    }

    final static Element doPost
        (URL target, Map<String,String> params)
        throws IOException, CPlugin.PermanentException
    { return check(getResponseForPost(target, params)); }


    final static CIOUtils.Response getResponseForPost
        (URL target, Map<String,String> params)
        throws IOException
    {
        CUtils.LOGD(TAG, "post to: "+target);

        HttpURLConnection con =
            CIOUtils.setTimeout((HttpURLConnection) target.openConnection());
        con.setDoOutput(true);
        con.setRequestMethod("POST");

        // Build up the auth string and post values from
        // the map.
        StringBuilder postsb = new StringBuilder();
        StringBuilder oauthsb = new StringBuilder("OAuth ");
        boolean pfirst = true;
        boolean ofirst = true;
        for (String k: params.keySet()) {
            if (k.startsWith("oauth_")) {
                if (ofirst) { ofirst = false; }
                else { oauthsb.append(", "); }
                oauthsb.append(k);
                oauthsb.append("=\"");
                oauthsb.append(params.get(k));
                oauthsb.append("\"");
            }
            else {
                if (pfirst) { pfirst = false; }
                else { postsb.append("&"); }
                postsb.append(k);
                postsb.append("=");
                postsb.append(params.get(k));
            }
        }
        con.setRequestProperty("Authorization", oauthsb.toString());
        OutputStream out = con.getOutputStream();
        CUtils.LOGD(TAG, "Post: "+postsb);
        out.write(postsb.toString().getBytes("utf-8"));
        out.flush();
        return CIOUtils.getResponse(con);
    }

    final static Element doUpload
        (URL target, Map<String,String> params, File src)
        throws IOException, CPlugin.PermanentException
    {
        Set<CIOUtils.Part> parts = new HashSet<CIOUtils.Part>();
        for (String k: params.keySet()) {
            if (!k.startsWith("oauth_")) {
                parts.add(new CIOUtils.StringPart(k, params.get(k)));
            }
        }

        parts.add
            (new CIOUtils.FilePart
             ("photo", src, src.getName(), "image/jpeg"));

        HttpURLConnection con =
            CIOUtils.setTimeout((HttpURLConnection) target.openConnection());
        con.setDoOutput(true);
        con.setRequestMethod("POST");

        // Build up the auth string from the map.
        StringBuilder oauthsb = new StringBuilder("OAuth ");
        boolean ofirst = true;
        for (String k: params.keySet()) {
            if (k.startsWith("oauth_")) {
                if (ofirst) { ofirst = false; }
                else { oauthsb.append(", "); }
                oauthsb.append(k);
                oauthsb.append("=\"");
                oauthsb.append(params.get(k));
                oauthsb.append("\"");
            }
        }
        con.setRequestProperty("Authorization", oauthsb.toString());
        CIOUtils.writeMultipart(con, parts);
        return check(CIOUtils.getResponse(con));
    }

    final static JSONObject asJSON(String content)
        throws IOException
    {
        try { return new JSONObject(content); }
        catch (JSONException jse) {
            // Treat as a transient exception, which might be
            // a pretty dumb idea
            throw CIOUtils.asIOException(jse);
        }
    }

    final static void signParams
        (String method, String url, Map<String,String> params,
         String app_secret, String token_secret)
        throws UnsupportedEncodingException, CPlugin.PermanentException
    {
        // Add standard values.
        params.put("oauth_version", "1.0");
        params.put("oauth_timestamp",
                   String.valueOf(System.currentTimeMillis()/1000l));
        params.put("oauth_signature_method", "HMAC-SHA1");

        signStandardParams(method, url, params, app_secret, token_secret);
    }

    final static void signStandardParams
        (String method, String url, Map<String,String> params,
         String app_secret, String token_secret)
        throws UnsupportedEncodingException, CPlugin.PermanentException
    {
        // Base string.
        StringBuilder sb = new StringBuilder();
        sb.append(method);
        sb.append("&");
        sb.append(encode(url));
        sb.append("&");
        sb.append(encode(sortedParams(params)));

        StringBuilder kb = new StringBuilder(encode(app_secret));
        kb.append("&");
        if (token_secret != null) { kb.append(encode(token_secret)); }

        // Signature
        params.put("oauth_signature",
                   encode(sign(sb.toString(), kb.toString())));
    }

    final static String makeNonce()
    {
        byte buf[] = new byte[12];
        synchronized (s_rand) {
            s_rand.nextBytes(buf);
        }
        return CUtils.toHex(buf);
    }

    private final static Element check(CIOUtils.Response resp)
        throws IOException,
               CPlugin.PermanentException
    {
        if (resp.getCode() == 401) {
            // Since oauth tokens are 'permanent' - this unfortunately
            // counts as a permenant failure
            throw new CPlugin.PermanentException
                (resp.getContent());
        }
        else if (resp.getCode() >= 500) {
            // This is a remote system failure -- assumed to
            // be a transient issue.
            throw new IOException
                ("Remote issue: "+resp.getCode()+","+
                 resp.getContent());
        }
        if (resp.getCode() >= 400) {
            // Considered a permanent failure.
            throw new CPlugin.PermanentException(resp.getContent());
        }
        else if (resp.getCode() >= 300) {
            // Treated as transient.
            throw new IOException
                ("Transient redirect: "+resp.getCode()+","+
                 resp.getContent());
        }

        Document doc = CIOUtils.parseDocument(resp.getContent());
        Element rsp = doc.getDocumentElement();
        String stat = rsp.getAttribute("stat");
        if (stat == null) {
            // Unexpected.
            throw new IOException("Remote issue: "+resp);
        }
        if ("ok".equals(stat)) {
            // ok, that's good enough for us.
            return rsp;
        }
        // Attempt to get some potentially nicer errors out.
        String msg;
        Element err = CIOUtils.firstChild(rsp, "*", "err");
        if (err != null) { msg = err.getAttribute("msg"); }
        else { msg = resp.toString(); }
        throw new IOException(msg);
    }

    private final static String sortedParams(Map<String,String> params)
        throws UnsupportedEncodingException
    {
        String vals[] = new String[params.size()];
        int idx = 0;
        for (String key: params.keySet()) {
            vals[idx++] = key;
        }
        Arrays.sort(vals);
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<vals.length; i++) {
            if (i > 0) { sb.append("&"); }
            sb.append(vals[i]);
            sb.append("=");
            sb.append(encode(params.get(vals[i])));
        }
        return sb.toString();
    }

    final static String encode(String s)
        throws UnsupportedEncodingException
    {
        byte buf[] = s.getBytes("utf-8");
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<buf.length; i++) {
            int cur = (buf[i] & 0xff);
            if (isReserved(cur)) {
                sb.append("%");
                if (cur < 0x10) { sb.append("0"); }
                sb.append(Integer.toHexString(cur).toUpperCase());
            }
            else {
                sb.append((char) (cur));
            }
        }
        return sb.toString();
    }

    private final static boolean isReserved(int v)
    {
        if (((v >= 'a') && (v <= 'z')) ||
            ((v >= 'A') && (v <= 'Z')) ||
            ((v >= '0') && (v <= '9')) ||
            (v == '-') || (v == '_') ||
            (v == '.') || (v == '~')) {
            return false;
        }
        else {
            return true;
        }
    }

    private static String sign(String base, String key)
        throws UnsupportedEncodingException, CPlugin.PermanentException
    {
        try {
            SecretKey skey = new SecretKeySpec
                (key.getBytes("utf-8"), "HmacSHA1");

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(skey);
            return Base64.encodeToString
                (mac.doFinal(base.getBytes("utf-8")), Base64.NO_WRAP);
        }
        catch (NoSuchAlgorithmException nse) {
            throw new CPlugin.PermanentException(nse);
        }
        catch (InvalidKeyException ike) {
            throw new CPlugin.PermanentException(ike);
        }
    }
    private final static SecureRandom s_rand = new SecureRandom();
    private final static String URI_TOKEN_CALLBACK =
        "savemypics://flickr/cb/";
    private final static String TAG = CUtils.makeLogTag(CFlickrUtils.class);
}
