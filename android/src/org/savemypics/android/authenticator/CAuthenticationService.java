package org.savemypics.android.authenticator;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class CAuthenticationService extends Service
{
    @Override
    public void onCreate()
    { m_auth = new CAuthenticator(this); }

    @Override
    public void onDestroy()
    { m_auth = null; }

    @Override
    public IBinder onBind(Intent intent)
    { return m_auth.getIBinder(); }

    private CAuthenticator m_auth = null;
}
