package org.savemypics.plugin.snapfish;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;

public final class CSnapfishUtils
{
    final static String REST_END_POINT = "rest_end_point";
    final static String UPLOAD_END_POINT = "upload_end_point";
    final static String ACCESS_TOKEN = "access_token";
    final static String JSON_TYPE = "application/json";

    final static JSONArray getEntry(JSONObject root)
    {
        if (root == null) { return null; }
        Object v = root.opt("entry");
        if (v == null) { return new JSONArray(); }
        if (v instanceof JSONArray) { return (JSONArray) v; }
        if (!(v instanceof JSONObject)) { return null; }
        return new JSONArray().put((JSONObject) v);
    }

    final static JSONObject doOAuthGet(URL url, String token)
        throws IOException, CPlugin.AuthorizationException
    {
        HttpURLConnection con =
            setStandardProperties
            ((HttpURLConnection) url.openConnection(), token, null);
        return check(CIOUtils.getResponse(con));
    }

    final static JSONObject doOAuthDelete(URL url, String token)
        throws IOException, CPlugin.AuthorizationException
    {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("DELETE");
        setStandardProperties(con, token, null);
        return check(CIOUtils.getResponse(con));
    }

    final static JSONObject doOAuthPost
        (URL url, String token, Map<String,String> params, String ctype)
        throws IOException, CPlugin.AuthorizationException
    {return doOAuthPost(url, token, CIOUtils.asFormURLEncoded(params), ctype);}

    final static JSONObject doOAuthPost
        (URL url, String token, String data, String ctype)
        throws IOException, CPlugin.AuthorizationException
    { return doOAuthMethod(url, "POST", token, data, ctype); }

    final static JSONObject doOAuthPut
        (URL url, String token, Map<String,String> params, String ctype)
        throws IOException, CPlugin.AuthorizationException
    {return doOAuthPut(url, token, CIOUtils.asFormURLEncoded(params), ctype);}

    final static JSONObject doOAuthPut
        (URL url, String token, String data, String ctype)
        throws IOException, CPlugin.AuthorizationException
    { return doOAuthMethod(url, "PUT", token, data, ctype); }

    final static JSONObject doOAuthMethod
        (URL url, String method, String token, String data, String ctype)
        throws IOException, CPlugin.AuthorizationException
    {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestMethod(method);
        setStandardProperties(con, token, ctype);
        con.getOutputStream().write(data.getBytes("utf-8"));
        con.getOutputStream().flush();
        return check(CIOUtils.getResponse(con));
    }

    final static JSONObject doOAuthUpload
        (URL url, String token, File source, String name)
        throws IOException, CPlugin.AuthorizationException
    {
        Set<CIOUtils.Part> parts = new HashSet<CIOUtils.Part>();
        parts.add
            (new CIOUtils.FilePart
             ("upload", source, name, "image/jpeg"));

        HttpURLConnection con = setStandardProperties
            ((HttpURLConnection) url.openConnection(), token, null);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        CIOUtils.writeMultipart(con, parts);
        return check(CIOUtils.getResponse(con));
    }

    private final static HttpURLConnection setStandardProperties
        (HttpURLConnection con, String token, String ctype)
    {
        CIOUtils.setTimeout(con);
        if (token != null) {
            con.setRequestProperty("Authorization", "OAuth "+token);
        }
        if (ctype != null) {
            con.setRequestProperty("Content-type", ctype);
        }
        return con;
    }

    private final static JSONObject check(CIOUtils.Response resp)
        throws CPlugin.AuthorizationException, IOException
    {
        try {
            JSONObject ret = new JSONObject(resp.getContent());

            // Trap authorization issues into its own little section.
            if ((resp.getCode() == 401) ||
                ("token_expired".equals(ret.optString("errorCode"))) ||
                (ret.optInt("status_code", 200) == 401)) {
                throw new CPlugin.AuthorizationException
                    (ret.optString("error_code", resp.getContent()));
            }

            // If the status code looks good, and we don't have an
            // explict failure marked, be happy.
            if ((resp.getCode() < 300) &&
                (ret.optInt("status_code", 200) < 300)) {
                return ret;
            }
            // This is probably wrong; but let's go with a transient
            // error for everything else until proven otherwise.
            throw new IOException(resp.toString());
        }
        catch (JSONException jse) {
            // We'll do [the potentially wrong thing] by treating
            // this as effectively a transient error.
            throw CIOUtils.asIOException
                ("transient issue: "+resp, jse);
        }
    }
}
