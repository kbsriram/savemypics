package org.savemypics.plugin.snapfish;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;

public final class CSnapfishUser
{
    public final static class AppCredentials
    {
        public AppCredentials(String aid, String asecret)
        {
            m_aid = aid;
            m_asecret = asecret;
        }
        private final String m_aid;
        private final String m_asecret;
    }

    public final static CPlugin.Tokens login
        (AppCredentials acred, String email, String pass)
        throws IOException, CPlugin.AuthorizationException
    {
        Map<String,String> params = new HashMap<String,String>();
        params.put("grant_type", "password");
        params.put("username", email);
        params.put("password", pass);
        addAppParams(params, acred);
        return handleRefresh(params);
    }

    public final static CPlugin.Tokens refresh
        (AppCredentials acred, String refresh)
        throws IOException, CPlugin.AuthorizationException
    {
        Map<String,String> params = new HashMap<String,String>();
        String rtok;
        try {
            rtok = (new JSONObject(refresh)).getString("refresh_token");
        }
        catch (JSONException jse) {
            throw CIOUtils.asAuthorizationException(refresh, jse);
        }

        params.put("grant_type", "refresh_token");
        params.put("refresh_token", rtok);
        addAppParams(params, acred);
        return handleRefresh(params);
    }

    private final static CPlugin.Tokens handleRefresh
        (Map<String,String> params)
        throws IOException, CPlugin.AuthorizationException
    {
        JSONObject refresh = CSnapfishUtils.doOAuthPost
            (new URL(TOKEN_URL), null, params,
             "application/x-www-form-urlencoded");
        // Stringify the json and use that as the 'permanent' token.
        // Copy just the necessary bits of the response and use that
        // as the 'temporary' token
        try {
            JSONObject access = new JSONObject();
            copyString("access_token", refresh, access);
            copyString("upload_end_point", refresh, access);
            copyString("rest_end_point", refresh, access);
            return new CPlugin.Tokens(refresh.toString(), access.toString());
        }
        catch (JSONException jse) {
            // This is unexpected. But treat it as a transient
            // failure for the moment until proved otherwise.
            throw CIOUtils.asIOException(refresh.toString(), jse);
        }
    }

    private static void addAppParams
        (Map<String,String> params, AppCredentials acred)
    {
        params.put("client_id", acred.m_aid);
        params.put("client_secret", acred.m_asecret);
    }

    private final static void copyString
        (String k, JSONObject src, JSONObject dest)
        throws JSONException
    { dest.put(k, src.getString(k)); }

    private final static String TOKEN_URL =
        "https://openapi.snapfish.com/services/access_token/v2";
}
