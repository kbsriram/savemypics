package org.savemypics.plugin.flickr;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import org.savemypics.plugin.CIOUtils;
import static org.junit.Assert.*;

public class CFlickrUtilsTest extends ABase
{
    @Test public void testSignature()
        throws Exception
    {
        Map<String,String> params = new HashMap<String,String>();

        // Known test cases from spec.
        params.put("oauth_consumer_key","dpf43f3p2l4k3l03");
        params.put("oauth_token","nnch734d00sl2jdk");
        params.put("oauth_nonce", "kllo9940pd9333jh");
        params.put("oauth_timestamp", "1191242096");
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_version", "1.0");
        params.put("size", "original");
        params.put("file", "vacation.jpg");

        CFlickrUtils.signStandardParams
            ("GET", "http://photos.example.net/photos", params,
             "kd94hf93k423kf44", "pfkkdhi9sl3r4s00");
        assertEquals("tR3%2BTy81lMeYAr%2FFid0kMTYa%2FWM%3D",
                     params.get("oauth_signature"));
        params.clear();


        // test case from twitter.
        params.put("oauth_nonce", "ea9ec8429b68d6b77cd5600adbbb0456");
        params.put("oauth_consumer_key", "cChZNFj6T5R0TigYB9yd1w");
        params.put("oauth_callback", "http://localhost/sign-in-with-twitter/");
        params.put("oauth_timestamp", "1318467427");
        params.put("oauth_version", "1.0");
        params.put("oauth_signature_method", "HMAC-SHA1");

        CFlickrUtils.signStandardParams
            ("POST", "https://api.twitter.com/oauth/request_token", params,
             "L8qq9PZyRg6ieKGEKhZolGC0vJWLw8iEJ88DRdyOg", null);
        assertEquals("F1Li3tvehgcraF8DMJ7OyxO4w9Y%3D",
                     params.get("oauth_signature"));
        params.clear();

        params.put("oauth_consumer_key", "cChZNFj6T5R0TigYB9yd1w");
        params.put("oauth_nonce", "a9900fe68e2573b27a37f10fbad6a755");
        params.put("oauth_version", "1.0");
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", "1318467427");
        params.put("oauth_token",
                   "NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0");
        params.put("oauth_verifier",
                   "uw7NjWHT6OJ1MpJOXsHfNxoAhPKpgI8BlYDhxEjIBY");
        CFlickrUtils.signStandardParams
            ("POST", "https://api.twitter.com/oauth/access_token", params,
             "L8qq9PZyRg6ieKGEKhZolGC0vJWLw8iEJ88DRdyOg",
             "veNRnAWe6inFuo8o2u8SLLZLjolYDmDP7SzL0YfYI");
        assertEquals("39cipBtIOHEEnybAR4sATQTpl2I%3D",
                     params.get("oauth_signature"));
        params.clear();

        // hue-universe
        params.put("oauth_consumer_key", "dpf43f3++p+#2l4k3l03");
        params.put("oauth_nonce", "kllo~9940~pd9333jh");
        params.put("oauth_version", "1.0");
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", "1191242096");
        params.put("type", "\u5b50");
        params.put("scenario", "\u66f0");
        params.put("oauth_token", "nnch734d(0)0sl2jdk");
        CFlickrUtils.signStandardParams
            ("POST", "http://photos.example.net/Photos", params,
             "kd9@4h%%4f93k423kf44",
             "pfkkd#hi9_sl-3r=4s00");
        assertEquals("H%2BRi9TUItAKNdS273%2BaloV9I6CI%3D",
                     params.get("oauth_signature"));
    }

    /*
    @Test public void testSimpleCall()
        throws Exception
    {
        if (!haveSecrets()) { return; }
        Map<String,String> params = new HashMap<String, String>();
        params.put("oauth_consumer_key", m_appid);
        params.put("oauth_nonce", CFlickrUtils.makeNonce());
        params.put("oauth_token", m_usertoken);
        params.put("method", "flickr.test.login");
        params.put("nojsoncallback", "1");
        params.put("format", "json");

        String urlS = "https://api.flickr.com/services/rest";

        CFlickrUtils.signParams("GET", urlS, params, m_appsecret, m_usersecret);
        StringBuilder usb = new StringBuilder(urlS);
        StringBuilder authb = new StringBuilder("OAuth ");
        boolean ufirst = true;
        boolean afirst = true;
        for (String k: params.keySet()) {
            if (k.startsWith("oauth_")) {
                if (afirst) { afirst = false; }
                else { authb.append(", "); }
                authb.append(k);
                authb.append("=\"");
                authb.append(params.get(k));
                authb.append("\"");
            }
            else {
                if (ufirst) {
                    ufirst = false;
                    usb.append("?");
                }
                else { usb.append("&"); }
                usb.append(k);
                usb.append("=");
                usb.append(params.get(k));
            }
        }

        URL target = new URL(usb.toString());
        HttpURLConnection con = CIOUtils.setTimeout
            ((HttpURLConnection) target.openConnection());
        con.setRequestProperty("Authorization", authb.toString());

        CIOUtils.Response resp = CIOUtils.getResponse(con);
        assertEquals(200, resp.getCode());

        JSONObject uinfojs = CFlickrUtils.asJSON(resp.getContent());
        assertEquals("ok", uinfojs.getString("stat"));
        assertEquals(m_usernsid, uinfojs.getJSONObject("user").getString("id"));
    }
    */

    @Test public void testRequestToken()
        throws Exception
    {
        CFlickrUtils.RequestToken rtok =
            CFlickrUtils.requestToken(m_appid, m_appsecret);
        System.out.println(rtok.getToken());
        System.out.println(rtok.getSecret());
        System.out.println(rtok.getCallback());
    }
}
