package org.savemypics.plugin.flickr;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class CFlickrAlbum
{
    public final static CPlugin.ImageResult upload
        (String akey, String asecret, CFlickrUtils.AccessToken access,
         File src, String title)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        // 1. Generate a sha-machinetag
        String shatag = CIOUtils.shaTagFor(src);

        // 2. Search if we've already uploaded such a tag.
        CPlugin.ImageResult ret = findByShaTag(akey, asecret, access, shatag);
        if (ret != null) {
            return ret;
        }

        // 3. Upload after adding appropriate tags.

        Map<String,String> params = CFlickrUtils.initParams
            (null, akey, access.getToken());

        params.put("title", title);

        // tags: "mobile_smpics shatag". mobile_smpics lets us avoid
        // downloading, shatag lets us do dedups.
        params.put("tags", CIOUtils.TAG_SMPICS+" "+shatag);

        // Set to minimum viewing privileges
        params.put("is_public", "0");
        params.put("is_friend", "0");
        params.put("is_family", "0");
        params.put("hidden", "2");

        CFlickrUtils.signParams
            ("POST", CFlickrUtils.UPLOAD_URL, params,
             asecret, access.getSecret());
        Element rsp = CFlickrUtils.doUpload
            (new URL(CFlickrUtils.UPLOAD_URL), params, src);
        return new CPlugin.ImageResult
            (CIOUtils.mustHaveFirstChild(rsp, "*", "photoid").getTextContent(),
             title, System.currentTimeMillis());
    }

    public final static CPlugin.Feed getFeed
        (String akey, String asecret, CFlickrUtils.AccessToken access,
         int count, String prevmark)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        // First, fetch current mark as a quick update check.
        String curmark = currentMark(akey, asecret, access, count);
        CPlugin.Feed ret = new CPlugin.Feed(curmark);
        if (curmark.equals(prevmark)) {
            return ret.setNothingChanged(true);
        }

        // We have something new; do the long multi-call fetch to get
        // the complete feed for the count.
        fillFeed(ret, akey, asecret, access, count);
        return ret;
    }

    // Approximate indicator of changes to feed is created by fetching
    // the last photo in the user's photostream.
    // format = last_photo_id:last_photo_upload:count:total_photo_count
    private final static String currentMark
        (String akey, String asecret,
         CFlickrUtils.AccessToken access, int count)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        Map<String,String> params =
            CFlickrUtils.initParams
            ("flickr.photos.search", akey, access.getToken());
        params.put("user_id", "me");
        params.put("media", "photos");
        params.put("per_page", "1");
        params.put("page", "1");

        // Request upload field used to calculate the mark.
        params.put("extras", "date_upload");

        CFlickrUtils.signParams
            ("POST", CFlickrUtils.REST_URL, params,
             asecret, access.getSecret());

        URL target = new URL(CFlickrUtils.REST_URL);
        Element rsp = CFlickrUtils.doPost(target, params);

        String last_upload_ts = null;
        String last_id = null;
        String total = null;

        Element photos = CIOUtils.firstChild(rsp, "*", "photos");
        if (photos != null) {
            total = photos.getAttribute("total");
            NodeList nl = photos.getElementsByTagNameNS("*", "photo");
            if (nl != null) {
                int len = nl.getLength();
                if (len > 0) {
                    Element n = (Element) nl.item(0);
                    last_upload_ts = n.getAttribute("dateupload");
                    last_id = n.getAttribute("id");
                }
            }
        }

        last_upload_ts = fixup(last_upload_ts, "0");
        last_id = fixup(last_id, "0");
        total = fixup(total, "0");
        return last_id+":"+last_upload_ts+":"+count+":"+total;
    }

    private final static String fixup(String base, String dflt)
    {
        if ((base == null) || (base.length() == 0)) { return dflt; }
        return base;
    }

    // exposed for testing
    /* private */ final static CPlugin.ImageResult findByShaTag
        (String akey, String asecret, CFlickrUtils.AccessToken access,
         String shatag)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        Map<String,String> params =
            CFlickrUtils.initParams
            ("flickr.photos.search", akey, access.getToken());
        params.put("user_id", "me");
        params.put("media", "photos");
        params.put("per_page", "1");
        params.put("page", "1");

        // add the shatag query
        params.put("machine_tags", shatag);

        // Request upload field so we can correctly fill in the actual
        // upload date.
        params.put("extras", "date_upload");

        CFlickrUtils.signParams
            ("POST", CFlickrUtils.REST_URL, params,
             asecret, access.getSecret());

        URL target = new URL(CFlickrUtils.REST_URL);
        Element rsp = CFlickrUtils.doPost(target, params);

        String id = null;
        String title = null;
        String uploadS = null;

        Element photos = CIOUtils.firstChild(rsp, "*", "photos");
        if (photos != null) {
            NodeList nl = photos.getElementsByTagNameNS("*", "photo");
            if (nl != null) {
                int len = nl.getLength();
                if (len > 0) {
                    // Found something.
                    Element n = (Element) nl.item(0);
                    id = n.getAttribute("id");
                    title = n.getAttribute("title");
                    uploadS = n.getAttribute("dateupload");
                }
            }
        }

        // Found it - carefully return back info.
        if ((id != null) && (uploadS != null)) {
            long upload_ts;
            try { upload_ts = Long.parseLong(uploadS)*1000l; }
            catch (NumberFormatException ign) {
                upload_ts = System.currentTimeMillis();
            }
            return new CPlugin.ImageResult(id, title, upload_ts);
        }

        // nothing found.
        return null;
    }

    private final static void fillFeed
        (CPlugin.Feed feed, String akey, String asecret,
         CFlickrUtils.AccessToken access, int count)
        throws IOException, CPlugin.AuthorizationException,
               CPlugin.PermanentException
    {
        int pagenum = 1;

        while (feed.getImages().size() < count) {
            Map<String,String> params =
                CFlickrUtils.initParams
                ("flickr.photos.search", akey, access.getToken());
            params.put("user_id", "me");
            params.put("media", "photos");
            params.put("per_page", String.valueOf(BATCH_COUNT));
            params.put("page", String.valueOf(pagenum++));

            // We need to filter tags by ourselves to eliminate items
            // that we've uploaded via the app.
            // Unfortunately, this doesn't work; hence the loop.
            // https://secure.flickr.com/groups/api/discuss/72157625456418732/
            // params.put("tags", "-"+TAG_SMPICS);

            // Request additional fields needed to process our data.
            params.put("extras", "tags,date_taken,date_upload,original_format");

            CFlickrUtils.signParams
                ("POST", CFlickrUtils.REST_URL, params,
                 asecret, access.getSecret());

            URL target = new URL(CFlickrUtils.REST_URL);
            Element rsp = CFlickrUtils.doPost(target, params);

            Element photos = CIOUtils.firstChild(rsp, "*", "photos");
            if (photos == null) { break; }

            NodeList nl = photos.getElementsByTagNameNS("*", "photo");
            if (nl != null) {
                int len = nl.getLength();
                for (int i=0; i<len; i++) {
                    maybeAdd(feed, (Element) nl.item(i));
                    if (feed.getImages().size() >= count) { break; }
                }
            }
            String pagesS = photos.getAttribute("pages");
            if ((pagesS == null) || (pagesS.length() == 0)) {
                pagesS = "0";
            }
            int pages = Integer.parseInt(pagesS);
            if (pagenum > pages) { break; }
        }
    }

    private final static void maybeAdd(CPlugin.Feed feed, Element n)
    {
        String id = n.getAttribute("id");
        String title = n.getAttribute("title");
        String farmid = n.getAttribute("farm");
        String serverid = n.getAttribute("server");
        String osecret = n.getAttribute("originalsecret");
        String fmt = n.getAttribute("originalformat");
        String created = n.getAttribute("datetaken");
        String tags = n.getAttribute("tags");

        if ((id != null) &&
            (farmid != null) &&
            (serverid != null) &&
            (osecret != null) &&
            (fmt != null) &&
            (!hasTag(tags, CIOUtils.TAG_SMPICS))) {
            feed.add
                (new CPlugin.RemoteImage
                 (id, "https://farm"+farmid+".staticflickr.com/"+
                  serverid+"/"+id+"_"+osecret+"_o."+fmt, title,
                  parseDate(created)));
        }
    }

    private final static boolean hasTag(String tags, String tag)
    {
        if (tags == null) { return false; }
        String fields[] = tags.split("\\s+");
        for (int i=0; i<fields.length; i++) {
            if (tag.equals(fields[i])) { return true; }
        }
        return false;
    }

    private final static long parseDate(String d)
    {
        if (d == null) { return System.currentTimeMillis(); }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try { return sdf.parse(d).getTime(); }
        catch (ParseException pe) {
            CUtils.LOGD(TAG, "unparseable date: "+d);
            return System.currentTimeMillis();
        }
    }

    private final static int BATCH_COUNT = 500;
    private final static String TAG = CUtils.makeLogTag(CFlickrAlbum.class);
}
