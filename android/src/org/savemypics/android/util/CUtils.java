package org.savemypics.android.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.savemypics.android.R;

public class CUtils
{
    public final static boolean IS_PRODUCTION = true;

    public final static String PREF_ONLY_WIFI = "pref_only_wifi";
    public final static String PREF_ACCOUNT_INSTALLED_ON =
        "pref_account_installed_on";
    public final static String PREF_UPLOAD_ALL =
        "pref_upload_all";
    public final static String PREF_UPLOAD_ENABLED =
        "pref_upload_enabled";
    public final static String PREF_DOWNLOAD_ENABLED =
        "pref_download_enabled";
    public final static String PREF_DEBUG_ENABLED =
        "pref_debug_enabled";
    public final static String PREF_DOWNLOAD_SHARED_SNAPFISH =
        "pref_download_shared_snapfish";
    public final static String SAVE_MY_PICS_BASEDIR = "savemypics";
    public final static String SAVE_MY_PICS_BASEDIR_COMPONENT =
        "/"+SAVE_MY_PICS_BASEDIR+"/";

    public final static int DOWNLOAD_COUNT = 50;

    public final static String KEY_LAST_RUN = "key_last_run";
    public final static String KEY_PREV_RUN = "key_prev_run";
    public final static String KEY_LAST_RESULT = "key_last_result";
    public final static String KEY_LAST_EXCEPTION = "key_last_exception";

    public final static String AUTH_ACCESS =
        "org.savemypics.android.auth.access";

    public final static String BASE_ACCOUNT_TYPE =
        "org.savemypics.android.account";

    public final static String SNAPFISH_ACCOUNT_TYPE =
        "org.savemypics.android.account.snapfish";
    public final static String FLICKR_ACCOUNT_TYPE =
        "org.savemypics.android.account.flickr";

    public static boolean isNetworkAvailable
        (Context ctx, String atype, String aname)
    {
        // Check settings first.
        SharedPreferences pref = getSharedPreferences(ctx, atype, aname);
        if (pref.getBoolean(PREF_ONLY_WIFI, true)) {
            return isWifiAvailable(ctx);
        }
        else {
            return isAnyNetworkAvailable(ctx);
        }
    }

    public final synchronized static Typeface getIconTypeface(Context ctx)
    {
        if (s_icon_tf == null) {
            s_icon_tf = Typeface.createFromAsset
                (ctx.getAssets(), "fonts/SaveMyPics.ttf");
        }
        return s_icon_tf;
    }

    public final static String truncate(String s, char c)
    {
        int idx = s.indexOf(c);
        if (idx > 0) { return s.substring(0, idx); }
        else { return s; }
    }

    public final static byte[] getBytes(String s)
    {
        if (s == null) { return null; }
        try { return s.getBytes("utf-8"); }
        catch (UnsupportedEncodingException uee){
            throw new RuntimeException(uee);
        }
    }

    public final static String sha(String in)
    {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return toHex(md.digest(getBytes(in)));
        }
        catch (NoSuchAlgorithmException nse) {
            throw new RuntimeException(nse);
        }
    }

    public final static String toHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<bytes.length; i++) {
            int unsignedb = (bytes[i] & 0xff);
            sb.append(TOHEX[(unsignedb>>4)&0xf]);
            sb.append(TOHEX[unsignedb&0xf]);
        }
        return sb.toString();
    }

    public static SharedPreferences getSharedPreferences
        (Context ctx, String atype, String aname)
    {
        return ctx.getSharedPreferences
            (getSharedPreferencesName(atype, aname), Context.MODE_PRIVATE);
    }

    public static String getSharedPreferencesName(String atype, String aname)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(atype.substring(atype.lastIndexOf('.')+1));
        sb.append("_");
        sb.append(aname);
        return safeFileName(sb.toString());
    }

    public static File makeDownloadRoot
        (Context ctx, String atype, String aname)
        throws IOException
    {
        if (!Environment.MEDIA_MOUNTED.equals
            (Environment.getExternalStorageState())) {
            throw new IOException("External storage not currently available");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(safeFileName(aname));
        if (SNAPFISH_ACCOUNT_TYPE.equals(atype)) {
            sb.append(" Snapfish");
        }
        else if (FLICKR_ACCOUNT_TYPE.equals(atype)) {
            sb.append(" Flickr");
        }
        else {
            throw new IllegalArgumentException("Unexpected type: "+atype);
        }
        File root = Environment.getExternalStoragePublicDirectory
            (Environment.DIRECTORY_PICTURES);
        File sroot = new File(root, SAVE_MY_PICS_BASEDIR);
        File ret = new File(sroot, sb.toString());
        if (!ret.isDirectory()) {
            if (!ret.mkdirs()) {
                throw new IOException("Unable to create directory");
            }
        }
        return ret;
    }

    public final static Dialog makeAlertDialog
        (final Context ctx, String title, String msg,
         DialogInterface.OnClickListener onclick)
    {
        return
            new AlertDialog.Builder(ctx)
            .setCancelable(false)
            .setTitle(title)
            .setMessage(msg)
            .setNeutralButton(android.R.string.ok, onclick)
            .create();
    }

    public final static Dialog makeYesCancelDialog
        (final Context ctx, int titleid, int msgid, int yesid,
         DialogInterface.OnClickListener onyes,
         DialogInterface.OnClickListener oncancel)
    {
        return
            new AlertDialog.Builder(ctx)
            .setCancelable(true)
            .setTitle(titleid)
            .setMessage(msgid)
            .setNegativeButton(android.R.string.cancel, oncancel)
            .setPositiveButton(yesid, onyes)
            .create();
    }

    public final static Dialog makeEnableWifiDialog
        (final Context ctx, DialogInterface.OnClickListener oncancel)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder
            .setCancelable(false)
            .setTitle(R.string.no_wifi_title)
            .setMessage(R.string.no_wifi_message);

        final Intent wifi_intent=new Intent(Settings.ACTION_WIFI_SETTINGS);
        wifi_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        if (hasIntent(ctx, wifi_intent)) {
            builder
                .setNegativeButton(android.R.string.cancel, oncancel)
                .setPositiveButton
                (R.string.wifi_settings,
                 new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface d, int id) {
                         ctx.startActivity(wifi_intent);
                     }
                 });
        }
        else {
            builder
                .setNeutralButton(android.R.string.ok, oncancel);
        }
        return builder.create();
    }
    public final static boolean hasIntent(Context ctx, Intent check)
    {
        List<ResolveInfo> rlist =
            ctx.getPackageManager()
            .queryIntentActivities(check, PackageManager.MATCH_DEFAULT_ONLY);
        return ((rlist != null) && (rlist.size() > 0));
    }

    public static boolean isWifiAvailable(Context ctx)
    {
        WifiManager wifi =
            (WifiManager)ctx.getSystemService(Context.WIFI_SERVICE);
        return wifi.isWifiEnabled();
    }

    public static boolean isAnyNetworkAvailable(Context ctx)
    {
        ConnectivityManager cm = (ConnectivityManager)
            ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
 
        NetworkInfo active = cm.getActiveNetworkInfo();
        return ((active != null) && (active.isConnected()));
    }

    public final static void quietlyClose(Closeable c)
    {
        if (c != null) {
            try { c.close(); }
            catch (Throwable ign) {}
        }
    }

    public static String makeLogTag(Class cls)
    {
        String str = cls.getSimpleName();
        if (str.length() > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            return LOG_PREFIX + str.substring
                (0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1);
        }
        return LOG_PREFIX + str;
    }

    public static void TLOGD(final String tag, String message)
    {
        if (!IS_PRODUCTION || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, Thread.currentThread()+": "+message);
        }
    }

    public static void LOGD(final String tag, String message)
    {
        if (!IS_PRODUCTION || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    public static void LOGD(final String tag, String message, Throwable cause)
    {
        if (!IS_PRODUCTION || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message, cause);
        }
    }

    public static void LOGW(final String tag, String message)
    { Log.w(tag, message); }

    public static void LOGW(final String tag, String message, Throwable cause)
    { Log.w(tag, message, cause); }

    public final static String safeFileName(String s)
    {
        StringBuilder sb = new StringBuilder();
        char[] v = s.toCharArray();
        for (int i=0; i<v.length; i++) {
            char cur = v[i];
            if ((cur >= '0') && (cur <= '9'))
                { sb.append(cur); }
            else if ((cur >= 'A') && (cur <= 'Z'))
                { sb.append(cur); }
            else if ((cur >= 'a') && (cur <= 'z'))
                { sb.append(cur); }
            else if ((cur == '_') || (cur == '.') || (cur == ' '))
                { sb.append(cur); }
            else
                { sb.append('-'); }
        }
        return sb.toString();
    }

    private final static char[] TOHEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static Typeface s_icon_tf = null;
    private static final String LOG_PREFIX = "smpics_";
    private static final int LOG_PREFIX_LENGTH = LOG_PREFIX.length();
    private static final int MAX_LOG_TAG_LENGTH = 23;
}
