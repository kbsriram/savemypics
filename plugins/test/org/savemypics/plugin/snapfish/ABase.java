package org.savemypics.plugin.snapfish;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.savemypics.android.util.CUtils;
import org.savemypics.plugin.CPlugin;

public abstract class ABase
{
    @Before public void before()
        throws IOException
    {
        BufferedReader br = null;
        try {
            File f = new File("test/app.properties");
            if (!f.canRead()) { return; }
            br = new BufferedReader(new FileReader(f));
            Properties p = new Properties();
            p.load(br);
            m_appcred = new CSnapfishUser.AppCredentials
                (p.getProperty("snapfish.appid"),
                 p.getProperty("snapfish.appsecret"));
            m_refresh = p.getProperty("snapfish.refresh");
            m_email = p.getProperty("snapfish.testemail");
            m_pass = p.getProperty("snapfish.testpass");
            m_test_album_title = p.getProperty("snapfish.testalbumtitle");
        }
        finally {
            CUtils.quietlyClose(br);
        }
    }

    @After public void after()
    {
        m_appcred = null;
        m_refresh = null;
        m_email = null;
        m_pass = null;
    }

    protected boolean haveSecrets()
        throws IOException, CPlugin.AuthorizationException
    {
        if ((m_appcred == null) ||
            (m_refresh == null) ||
            (m_email == null) ||
            (m_pass == null) ||
            (m_test_album_title == null)) {
            System.err.println("Skip test - no app credentials");
            return false;
        }

        // Cached access token per test-run.
        if (s_access == null) {
            // fetch it!
            CPlugin.Tokens tokens = CSnapfishUser.refresh
                (m_appcred, m_refresh);
            s_access = tokens.getTemporary();
        }
        return true;
    }

    protected CSnapfishUser.AppCredentials m_appcred = null;
    protected String m_refresh = null;
    protected String m_email = null;
    protected String m_pass = null;
    protected String m_test_album_title = null;

    // cached for a given test run.
    protected static String s_access = null;
}
