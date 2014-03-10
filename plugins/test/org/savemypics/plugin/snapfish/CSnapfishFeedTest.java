package org.savemypics.plugin.snapfish;

import java.io.File;
import org.junit.Test;
import org.savemypics.plugin.CPlugin;
import static org.junit.Assert.*;

public class CSnapfishFeedTest extends ABase
{
    @Test public void testFeed()
        throws Exception
    {
        if (!haveSecrets()) { return; }

        CPlugin.Feed feed1 = CSnapfishFeed.getFeed
            (s_access, 1, false, "foo");
        assertNotNull(feed1);
        assertEquals(1, feed1.getImages().size());
        CPlugin.Feed feed2 = CSnapfishFeed.getFeed
            (s_access, 1, false, feed1.getMark());
        assertNotNull(feed2);
        assertTrue(feed2.nothingChanged());
        assertEquals(feed1.getMark(), feed2.getMark());
    }
}
