package org.savemypics.plugin.flickr;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;

import static org.junit.Assert.*;

public class CFlickrAlbumTest extends ABase
{
    @Test public void testUpload()
        throws Exception
    {
        if (!haveSecrets()) { return; }

        // Check that the de-duping code works.
        File orig = new File("test/test.jpg");
        String shatag = "hash:sha1="+CUtils.toHex(CIOUtils.sha(orig));
        CPlugin.ImageResult result = CFlickrAlbum.findByShaTag
            (m_appid, m_appsecret, m_access, shatag);
        assertNotNull(result);

        // Create a temporary distinct image so we actually upload
        // it.
        BufferedImage tmp = ImageIO.read(orig);
        Graphics2D g2d = tmp.createGraphics();
        g2d.setPaint(new Color(0xcc, 0x0, 0x0));
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        FontMetrics fm = g2d.getFontMetrics();
        String text = (new Date()).toString();
        int x = (tmp.getWidth() - fm.stringWidth(text))/2;
        int y = (tmp.getHeight() + fm.getHeight())/2;
        g2d.drawString(text, x, y);
        g2d.dispose();

        File src = new File("test/test_cur.jpg");
        if (src.canRead()) { src.delete(); }
        ImageIO.write(tmp, "jpg", src);

        String nasty_title = "test's a few \u5b50\u66f0";

        shatag = "hash:sha1="+CUtils.toHex(CIOUtils.sha(src));
        result = CFlickrAlbum.findByShaTag
            (m_appid, m_appsecret, m_access, shatag);
        assertNull(result);

        result = CFlickrAlbum.upload
            (m_appid, m_appsecret, m_access, src, nasty_title);
        assertNotNull(result);
        assertEquals(nasty_title, result.getTitle());
    }

    @Test public void testFeed()
        throws Exception
    {
        if (!haveSecrets()) { return; }
        CPlugin.Feed feed = CFlickrAlbum.getFeed
            (m_appid, m_appsecret, m_access, 10, null);
        assertNotNull(feed);
        assertEquals(10, feed.getImages().size());
        assertFalse(feed.nothingChanged());

        System.out.println("old-mark: "+feed.getMark());
        String[] fields = feed.getMark().split(":");
        assertEquals(4, fields.length);
        // format = lastid:lastuploadts:count:total
        assertEquals("10", fields[2]);

        feed = CFlickrAlbum.getFeed
            (m_appid, m_appsecret, m_access, 10, feed.getMark());
        assertNotNull(feed);
        assertTrue(feed.nothingChanged());
        System.out.println("new-mark: "+feed.getMark());
    }
}
