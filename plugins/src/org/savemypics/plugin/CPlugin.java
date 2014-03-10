package org.savemypics.plugin;

import java.util.ArrayList;
import java.util.List;

public final class CPlugin
{
    @SuppressWarnings("serial")
    public final static class AuthorizationException
        extends Exception
    {
        public AuthorizationException(String msg)
        { super(msg); }

        public AuthorizationException(Throwable cause)
        { super(cause); }

        public AuthorizationException(String msg, Throwable cause)
        { super(msg, cause); }
    }

    @SuppressWarnings("serial")
    public final static class PermanentException
        extends Exception
    {
        public PermanentException(String msg)
        { super(msg); }

        public PermanentException(Throwable cause)
        { super(cause); }

        public PermanentException(String msg, Throwable cause)
        { super(msg, cause); }
    }

    public final static class RemoteImage
    {
        public RemoteImage(String id, String url, String title, long created)
        {
            m_id = id;
            m_url = url;
            m_title = title;
            m_created = created;
        }
        public String getId() { return m_id; }
        public String getURL() { return m_url; }
        public String getTitle() { return m_title; }
        public long getCreated() { return m_created; }
        private final String m_id;
        private final String m_url;
        private final String m_title;
        private final long m_created;
    }

    public final static class Feed
    {
        public Feed(String mark)
        {
            m_mark = mark;
            m_images = new ArrayList<RemoteImage>();
            m_nothingchanged = false;
        }

        public String getMark()
        { return m_mark; }
        public List<RemoteImage> getImages()
        { return m_images; }
        public boolean nothingChanged()
        { return m_nothingchanged; }

        public Feed setNothingChanged(boolean v)
        { m_nothingchanged = v; return this; }

        public Feed add(RemoteImage im)
        { m_images.add(im); return this; }

        private String m_mark;
        private boolean m_nothingchanged;
        private final List<RemoteImage> m_images;
    }

    public final static class Tokens
    {
        // Opaque strings - just stored within the Account
        // as "password" and "token".
        // Made available to the plugin for various calls.
        public Tokens(String permanent, String temporary)
        {
            m_permanent = permanent;
            m_temporary = temporary;
        }
        public final String getPermanent()
        { return m_permanent; }
        public final String getTemporary()
        { return m_temporary; }
        private final String m_permanent;
        private final String m_temporary;
    }

    public static class AlbumResult
    {
        public AlbumResult(String id, String title, long updated)
        {
            m_id = id;
            m_title = title;
            m_updated = updated;
        }
        public final String getId()
        { return m_id; }
        public final String getTitle()
        { return m_title; }
        public final long getUpdateTime()
        { return m_updated; }
        private final String m_id;
        private final String m_title;
        private final long m_updated;
    }

    public static class ImageResult
    {
        public ImageResult(String id, String title, long create)
        {
            m_id = id;
            m_title = title;
            m_create = create;
        }
        public final String getId()
        { return m_id; }
        public final String getTitle()
        { return m_title; }
        public final long getCreated()
        { return m_create; }
        private final String m_id;
        private final String m_title;
        private final long m_create;
    }
}
