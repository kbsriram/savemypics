package org.savemypics.plugin.snapfish;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;

public final class CSnapfishAlbum
{
    // Get an album whose title matches the provided string, optionally
    // creating it.
    // NB:
    // Expensive on the server side; so use getAlbumById() once
    // you do this for the first time.
    public final static CPlugin.AlbumResult getAlbumByTitle
        (String auth, String title, boolean create)
        throws IOException, CPlugin.AuthorizationException
    {
        try {
            JSONObject access = new JSONObject(auth);
            URL target = new URL
                (access.getString
                 (CSnapfishUtils.REST_END_POINT)+"/albums/@me/@self");
            String token = access.getString(CSnapfishUtils.ACCESS_TOKEN);

            // 1. List all owned albums.
            JSONArray entries = CSnapfishUtils.getEntry
                (CSnapfishUtils.doOAuthGet(target, token));

            for (int i=0; i<entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (title.equals(entry.optString("title"))) {
                    return asAlbumResult(entry);
                }
            }

            // 2. Doesn't exist - create if needed.
            if (!create) { return null; }

            entries = CSnapfishUtils.getEntry
                (CSnapfishUtils.doOAuthPost
                 (target, token, "{\"caption\":\""+title+"\"}",
                  CSnapfishUtils.JSON_TYPE));
            if (entries.length() != 1) {
                // woops.
                throw new IOException("No album created: "+entries);
            }
            return asNewAlbumResult(entries.getJSONObject(0));
        }
        catch (JSONException jse) {
            // Probably should be a permanent failure, but mark it
            // transient for the moment.
            throw CIOUtils.asIOException(jse);
        }
    }

    final static CPlugin.AlbumResult getAlbumById(String auth, String id)
        throws IOException, CPlugin.AuthorizationException
    {
        try {
            JSONObject access = new JSONObject(auth);
            URL target = new URL
                (access.getString
                 (CSnapfishUtils.REST_END_POINT)+"/albums/@me/@self/"+id);
            String token = access.getString(CSnapfishUtils.ACCESS_TOKEN);

            JSONArray entries = CSnapfishUtils.getEntry
                (CSnapfishUtils.doOAuthGet(target, token));

            if ((entries == null) || (entries.length() == 0)) { return null; }
            return asAlbumResult(entries.getJSONObject(0));
        }
        catch (JSONException jse) {
            // Probably should be a permanent failure, but mark it
            // transient for the moment.
            throw CIOUtils.asIOException(jse);
        }
    }

    public final static CPlugin.ImageResult uploadToAlbum
        (String auth, String aid, File source)
        throws IOException, CPlugin.AuthorizationException
    {
        try {
            long size = source.length();
            if (size <= 0) {
                throw new IOException
                    ("Missing or zero-length file: "+source);
            }
            JSONObject access = new JSONObject(auth);

            // 1. Generate a sha-machinetag
            String shatag = CIOUtils.shaTagFor(source);

            // 2. Check if we've already got this tag.
            CPlugin.ImageResult ret = findByShaTag(access, aid, shatag);
            if (ret != null) {
                CUtils.LOGD(TAG, "Skip upload - found "+shatag);
                return ret;
            }

            URL target = new URL
                (access.getString
                 (CSnapfishUtils.UPLOAD_END_POINT)+
                 "/mediaItems/@me/@self/"+aid+
                 "?fileSize="+size+"&autoCorrection=N");
            String token = access.getString("access_token");

            JSONArray entries = CSnapfishUtils.getEntry
                (CSnapfishUtils.doOAuthUpload
                 (target, token, source, source.getName()));
            if ((entries == null) || (entries.length() == 0)) {
                throw new IOException("Unable to upload: "+entries);
            }
            ret = asImageResult(entries.getJSONObject(0));

            // Update userTag with sha-sum. Best effort only, ignore
            // the result, errors, etc.
            try {
                updateUserTag(access, aid, ret.getId(), shatag);
                maybeAddShaCache(aid, shatag, ret);
            }
            catch (Throwable ign) {
                CUtils.LOGD(TAG, "ignoring error", ign);
            }
            return ret;
        }
        catch (JSONException jse) {
            throw CIOUtils.asIOException(jse);
        }
    }

    // Don't use this yet - only used for test cases.
    final static boolean deleteAlbum(String auth, String id)
        throws IOException, CPlugin.AuthorizationException
    {
        try {
            JSONObject access = new JSONObject(auth);
            URL target = new URL
                (access.getString
                 (CSnapfishUtils.REST_END_POINT)+"/albums/@me/@self/"+id);
            String token = access.getString(CSnapfishUtils.ACCESS_TOKEN);
            JSONObject resp = CSnapfishUtils.doOAuthDelete(target, token);
            return "ok".equals(resp.optString("status"));
        }
        catch (JSONException jse) {
            // Probably should be a permanent failure, but mark it
            // transient for the moment.
            throw CIOUtils.asIOException(jse);
        }
    }

    // exposed for testing.
    /* private */ final static CPlugin.ImageResult findByShaTag
        (JSONObject access, String aid, String shatag)
        throws IOException, JSONException, CPlugin.AuthorizationException
    {
        // Crude and approximate hack; until there's a real search api
        CPlugin.ImageResult ret;
        synchronized (s_shacache) {
            ret = s_shacache.get(shatag);
            if (ret == null) {
                // a chance to refill the cache
                if (maybeRefillShaCache(access, aid)) {
                    ret = s_shacache.get(shatag);
                }
            }
        }
        return ret;
    }

    private final static void maybeAddShaCache
        (String aid, String shatag, CPlugin.ImageResult value)
    {
        synchronized (s_shacache) {
            if (aid.equals(s_shacache_last_aid)) {
                s_shacache.put(shatag, value);
                // Just to avoid the heavy request.
                s_shacache_last_ts = System.currentTimeMillis();
            }
        }
    }

    private final static boolean maybeRefillShaCache
        (JSONObject access, String aid)
        throws IOException, JSONException, CPlugin.AuthorizationException
    {
        long now = System.currentTimeMillis();

        // Decide if we want to refresh the cache.
        if ((aid.equals(s_shacache_last_aid)) &&
            ((now - s_shacache_last_ts) < SHACACHE_EXPIRE_MSEC)) {
            return false;
        }

        s_shacache_last_aid = null;
        s_shacache_last_ts = 0l;
        s_shacache.clear();

        URL target = new URL
            (access.getString
             (CSnapfishUtils.REST_END_POINT)+
             "/mediaItems/@me/@self/"+aid);
        String token = access.getString("access_token");

        JSONArray entries = CSnapfishUtils.getEntry
            (CSnapfishUtils.doOAuthGet(target, token));

        // Update timestamps, etc.
        s_shacache_last_aid = aid;
        s_shacache_last_ts = System.currentTimeMillis();

        // Copy all available entries into the cache.
        if (entries != null) {
            int len = entries.length();
            // limit cache size, even if we miss entries.
            if (len > SHACACHE_HARD_LIMIT) {
                len = SHACACHE_HARD_LIMIT;
            }
            for (int i=0; i<len; i++) {
                // Filter for jpeg only.
                JSONObject pic = entries.getJSONObject(i);
                if (!"image/jpeg".equals(pic.optString("mimeType"))) {
                    continue;
                }
                String shatag = getShaTagFromEntry(pic);
                if (shatag == null) {
                    continue;
                }
                s_shacache.put(shatag, asImageResultFromEntry(pic));
            }
        }
        return true;
    }

    private final static String getShaTagFromEntry(JSONObject pic)
        throws JSONException
    {
        JSONArray tags = pic.optJSONArray("userTag");
        if (tags == null) { return null; }
        int len = tags.length();
        for (int i=0; i<len; i++) {
            String tag = tags.getString(i);
            if (tag.startsWith("hash:sha1=")) {
                return tag;
            }
        }
        return null;
    }

    private final static CPlugin.ImageResult asImageResultFromEntry
        (JSONObject entry)
        throws JSONException
    {
        return new CPlugin.ImageResult
            (entry.getString("id"),
             entry.getString("title"),
             Long.parseLong(entry.getString("created")));
    }

    private final static void updateUserTag
        (JSONObject access, String aid, String mediaid, String shatag)
        throws IOException, JSONException, CPlugin.AuthorizationException
    {
        String token = access.getString("access_token");
        URL target =
            new URL
            (access.getString
             (CSnapfishUtils.REST_END_POINT)+
             "/mediaItems/@me/@self/"+aid+"/"+mediaid);

        JSONArray entries =
            CSnapfishUtils.getEntry
            (CSnapfishUtils.doOAuthPut
             (target, token,
              "{\"typeName\":\"MediaItem\",\"userTag\":["+
              "\""+shatag+"\",\""+CIOUtils.TAG_SMPICS+"\"]}",
              CSnapfishUtils.JSON_TYPE));
    }

    private final static CPlugin.ImageResult asImageResult(JSONObject json)
        throws JSONException
    {
        return new CPlugin.ImageResult
            (json.getString("id"),
             json.getString("caption"),
             Long.parseLong(json.getString("created")));
    }

    private final static CPlugin.AlbumResult asAlbumResult(JSONObject json)
        throws JSONException
    {
        return new CPlugin.AlbumResult
            (json.getString("id"), json.getString("title"),
             parseUpdateDate(json.optString("lastUpdateDate")));
    }

    private final static CPlugin.AlbumResult asNewAlbumResult(JSONObject json)
        throws JSONException
    {
        return new CPlugin.AlbumResult
            (json.getString("id"), json.getString("caption"),
             System.currentTimeMillis());
    }

    private final static long parseUpdateDate(String d)
    {
        if (d == null) { return System.currentTimeMillis(); }
        // 7:36:07 PM Dec 7, 2013 GMT
        SimpleDateFormat sdf = new SimpleDateFormat
            ("h:mm:ss aa MMM d, yyyy zz", Locale.US);
        try { return sdf.parse(d).getTime(); }
        catch (ParseException pe) {
            CUtils.LOGW(TAG, "Skip unparseable date: "+d);
            return System.currentTimeMillis();
        }
    }
    private final static Map<String,CPlugin.ImageResult> s_shacache =
        new HashMap<String,CPlugin.ImageResult>();
    private static long s_shacache_last_ts = 0l;
    private static String s_shacache_last_aid = null;
    private final static long SHACACHE_EXPIRE_MSEC = 300l*1000l; // 5 mins
    private final static int SHACACHE_HARD_LIMIT = 1000;
    private final static String TAG = CUtils.makeLogTag(CSnapfishAlbum.class);
}
