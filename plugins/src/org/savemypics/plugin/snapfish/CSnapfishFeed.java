package org.savemypics.plugin.snapfish;

// Work-around lack of proper sorted feed of images.  This is an
// expensive call as it always get a list of all albums - expensive on
// the server, and to be avoided at all costs.

// Algorithm:
//
// Uses two priority heaps.
//
// 1) The album heap contains albumids, sorted by their
// last-update-date.
// 2) The picture heap contains picids, sorted by their
// create-date.
//
// To get a list of the latest N pictures, the album heap
// is first initialized with a list of all the albums.
//
// Next, the latest album is removed from the album heap,
// and pictures from that album are dropped into the picture
// heap.
//
// Then, repeat N times:
//
//   do {
//      peek at the latest picture from the picture heap.
//      if (picture is older than latest album in album heap) {
//         Remove latest album from the album heap, and drop pictures from
//         that album into the picture heap.
//      }
//   while (latest picture is older than latest album)
//   remove the latest picture and place it in the feed.

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;

public final class CSnapfishFeed
{
    public final static CPlugin.Feed getFeed
        (String auth, int count, boolean include_shared, String prevmark)
        throws IOException, CPlugin.AuthorizationException
    {
        PriorityQueue<JSONObject> album_queue =
            new PriorityQueue<JSONObject>(15, new Comparator<JSONObject>() {
                    public int compare(JSONObject lhs, JSONObject rhs) {
                        long lhsts = albumTimestamp(lhs);
                        long rhsts = albumTimestamp(rhs);
                        if (lhsts == rhsts) { return 0; }
                        else if (lhsts < rhsts) { return 1; }
                        else { return -1; }
                    }
                });

        JSONObject access;
        try { access = new JSONObject(auth); }
        catch (JSONException jse) {
            // treat as an auth-failure.
            throw CIOUtils.asAuthorizationException("bad access", jse);
        }
        addAlbums(access, album_queue, include_shared);

        // Include count and flag in the mark; or it won't quite do
        // the right thing.
        CPlugin.Feed feed =
            new CPlugin.Feed
            (calculateMark(album_queue)+":"+count+":"+include_shared);

        CUtils.LOGD(TAG, "old-mark: "+prevmark+", now="+feed.getMark());

        if (feed.getMark().equals(prevmark)) {
            // no changes.
            feed.setNothingChanged(true);
            return feed;
        }

        // Add pics from latest album.
        JSONObject cur_album = album_queue.poll();

        // No albums? oh well.
        if (cur_album == null) { return feed; }

        PriorityQueue<JSONObject> pic_queue =
            new PriorityQueue<JSONObject>(100, new Comparator<JSONObject>() {
                    public int compare(JSONObject lhs, JSONObject rhs) {
                        long lhsts = pictureTimestamp(lhs);
                        long rhsts = pictureTimestamp(rhs);
                        if (lhsts == rhsts) { return 0; }
                        else if (lhsts < rhsts) { return 1; }
                        else { return -1; }
                    }
                });

        if (!addPicturesFrom(access, cur_album, pic_queue)) { return null; }

        cur_album = album_queue.peek();
        long cur_album_ts = albumTimestamp(cur_album);

        while (feed.getImages().size() < count) {
            JSONObject cur_pic;
            long cur_pic_ts;
            do {
                cur_pic = pic_queue.peek();
                if (cur_pic == null) { return feed; }

                cur_pic_ts = pictureTimestamp(cur_pic);
                if (cur_pic_ts < cur_album_ts) {

                    // remove album and place all its pictures into
                    // the picture heap.
                    cur_album = album_queue.poll();
                    if (cur_album == null) {
                        // Unexpected. But just return current stuff.
                        return feed;
                    }
                    if (!addPicturesFrom(access, cur_album, pic_queue)) {
                        return null;
                    }
                    // Update our various highwater marks.
                    cur_pic = pic_queue.peek();
                    cur_pic_ts = pictureTimestamp(cur_pic);
                    cur_album = album_queue.peek();
                    cur_album_ts = albumTimestamp(cur_album);
                }
            } while (cur_pic_ts < cur_album_ts);

            JSONObject feed_pic = pic_queue.poll();
            if (feed_pic == null) { return feed; }
            CPlugin.RemoteImage ri = asRemoteImage(feed_pic);
            if (ri != null) { feed.add(ri); }
        }
        return feed;
    }

    private final static CPlugin.RemoteImage asRemoteImage(JSONObject pic)
    {
        JSONObject meta = pic.optJSONObject("metaData");
        if (meta == null) { return null; }

        String hr = meta.optString("hiresUrl");
        if (hr == null) { return null; }

        String id = pic.optString("id");
        if (id == null) { return null; }

        String tsS = pic.optString("created");
        long created;
        if (tsS == null) { created = System.currentTimeMillis(); }
        else { created = Long.parseLong(tsS); }
        String title = pic.optString("title", id);

        return new CPlugin.RemoteImage(id, hr, title, created);
    }

    // Use the timestamp from the most recent album a a highwatermark.
    private final static String calculateMark(PriorityQueue<JSONObject> albums)
    {
        if ((albums == null) || (albums.size() == 0)) { return "0"; }

        JSONObject latest = albums.peek();
        return latest.optString(CACHE_UPDATE_TS, "0");
    }

    private final static boolean addPicturesFrom
        (JSONObject access, JSONObject album, PriorityQueue<JSONObject> q)
        throws IOException, CPlugin.AuthorizationException
    {
        try {
            URL target = new URL
                (access.getString
                 (CSnapfishUtils.REST_END_POINT)+
                 "/mediaItems/@me/@self/"+album.getString("id"));

            String token = access.getString(CSnapfishUtils.ACCESS_TOKEN);

            JSONArray entries = CSnapfishUtils.getEntry
                (CSnapfishUtils.doOAuthGet(target, token));

            for (int i=0; i<entries.length(); i++) {
                // Filter for jpeg only.
                JSONObject pic = entries.getJSONObject(i);
                if ("image/jpeg".equals(pic.optString("mimeType"))) {
                    q.offer(pic);
                }
            }
            return true;
        }
        catch (JSONException jse) {
            throw CIOUtils.asIOException(jse);
        }
    }

    private final static void addAlbums
        (JSONObject access, PriorityQueue<JSONObject> q,
         boolean include_shared)
        throws IOException, CPlugin.AuthorizationException
    {
        try {
            URL target = new URL
                (access.getString
                 (CSnapfishUtils.REST_END_POINT)+"/albums/@me/"+
                 (include_shared?"@all":"@self"));

            String token = access.getString(CSnapfishUtils.ACCESS_TOKEN);

            JSONArray entries = CSnapfishUtils.getEntry
                (CSnapfishUtils.doOAuthGet(target, token));

            for (int i=0; i<entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);

                if (entry.has("grantorName")) {
                    // shared album -- always included if requested.
                    if (include_shared) {
                        q.offer(addAlbumTimestamp(entry));
                    }
                    continue;
                }

                // Skip albums that we created from the device.
                String title = entry.optString("title");
                if ((title == null) ||
                    !title.startsWith("Mobile-")) {
                    q.offer(addAlbumTimestamp(entry));
                }
            }
        }
        catch (JSONException jse) {
            throw CIOUtils.asIOException(jse);
        }
    }

    private final static JSONObject addAlbumTimestamp(JSONObject js)
        throws JSONException
    {
        SimpleDateFormat sdf = new SimpleDateFormat
            ("h:mm:ss aa MMM d, yyyy zz", Locale.US);
        String sts = js.optString("lastUpdateDate");
        if (sts == null) {
            js.put(CACHE_UPDATE_TS, "0");
        }
        else {
            long v;
            try { v = sdf.parse(sts).getTime(); }
            catch (ParseException ign) { v = 0l; }
            js.put(CACHE_UPDATE_TS, String.valueOf(v));
        }
        return js;
    }

    private final static long albumTimestamp(JSONObject js)
    {
        if (js == null) { return 0l; }
        return Long.parseLong(js.optString(CACHE_UPDATE_TS, "0"));
    }

    private final static long pictureTimestamp(JSONObject js)
    {
        if (js == null) { return 0l; }
        return Long.parseLong(js.optString("created", "0"));
    }

    private final static String CACHE_UPDATE_TS = "_cache_update_ts";

    private final static String TAG = CUtils.makeLogTag(CSnapfishFeed.class);
}
