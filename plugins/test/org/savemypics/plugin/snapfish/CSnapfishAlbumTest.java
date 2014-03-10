package org.savemypics.plugin.snapfish;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import javax.imageio.ImageIO;
import org.json.JSONObject;
import org.junit.Test;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CIOUtils;
import org.savemypics.plugin.CPlugin;
import static org.junit.Assert.*;

public class CSnapfishAlbumTest extends ABase
{
    @Test public void testMakeAlbum()
        throws Exception
    {
        if (!haveSecrets()) { return; }

        CPlugin.AlbumResult result = CSnapfishAlbum.getAlbumByTitle
            (s_access, "Hello, world", true);
        assertNotNull(result);

        CPlugin.AlbumResult check = CSnapfishAlbum.getAlbumById
            (s_access, result.getId());
        assertNotNull(check);
        assertEquals(check.getId(), result.getId());
        assertEquals(check.getTitle(), result.getTitle());

        assertTrue(CSnapfishAlbum.deleteAlbum(s_access, result.getId()));

        result = CSnapfishAlbum.getAlbumByTitle
            (s_access, m_test_album_title, false);
        assertNotNull(result);

        // Check that the de-duping code works.
        File orig = new File("test/test.jpg");
        String shatag = "hash:sha1="+CUtils.toHex(CIOUtils.sha(orig));
        CPlugin.ImageResult ir = CSnapfishAlbum.findByShaTag
            (new JSONObject(s_access), result.getId(), shatag);
        assertNotNull(ir);

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

        shatag = "hash:sha1="+CUtils.toHex(CIOUtils.sha(src));
        ir = CSnapfishAlbum.findByShaTag
            (new JSONObject(s_access), result.getId(), shatag);
        assertNull(ir);

        ir = CSnapfishAlbum.uploadToAlbum(s_access, result.getId(), src);
        assertNotNull(ir);
        assertEquals("test_cur", ir.getTitle());
    }
}
