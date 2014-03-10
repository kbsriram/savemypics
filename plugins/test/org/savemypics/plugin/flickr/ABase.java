package org.savemypics.plugin.flickr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.savemypics.android.util.CUtils;

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
            m_appid = p.getProperty("flickr.appid");
            m_appsecret = p.getProperty("flickr.appsecret");
            String usertoken = p.getProperty("flickr.usertoken");
            String usersecret = p.getProperty("flickr.usersecret");
            String usernsid = p.getProperty("flickr.usernsid");
            m_access = CFlickrUtils.AccessToken.fromString
                (usertoken+"|"+usersecret+"|"+usernsid);
        }
        finally {
            CUtils.quietlyClose(br);
        }
    }

    @After public void after()
    {
        m_appid = null;
        m_appsecret = null;
        m_access = null;
    }

    protected boolean haveSecrets()
    {
        if ((m_appid == null) ||
            (m_appsecret == null) ||
            (m_access == null)) {
            System.err.println("Skip test - no app credentials");
            return false;
        }
        else {
            return true;
        }
    }

    protected String m_appid = null;
    protected String m_appsecret = null;
    protected CFlickrUtils.AccessToken m_access = null;
}
