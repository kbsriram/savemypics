package org.savemypics.android.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class CSyncService extends Service
{
    @Override
    public void onCreate()
    {
        synchronized (s_lock) {
            if (s_adapter == null) {
                s_adapter = new CSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    { return s_adapter.getSyncAdapterBinder(); }

    private static CSyncAdapter s_adapter = null;
    private static final Object s_lock = new Object();
}
