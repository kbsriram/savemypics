package org.savemypics.android.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.savemypics.android.R;
import org.savemypics.android.db.CLocalImage;
import org.savemypics.android.db.CRemoteImage;
import org.savemypics.android.glue.ABaseGlue;
import org.savemypics.android.sync.CMediaUtils;
import org.savemypics.android.sync.CSyncUtils;
import org.savemypics.android.util.CBitmapUtils;
import org.savemypics.android.util.CUtils;
import org.savemypics.android.view.CRefreshButton;
import org.savemypics.android.view.CRoundedBitmapView;
import org.savemypics.android.view.CStatusCounterView;

public class CAccountInfoListAdapter extends BaseAdapter
    implements AccountManagerCallback<Bundle>,
               CSyncUtils.InfoEvent.Listener,
               CSyncUtils.ProgressEvent.Listener,
               CRoundedBitmapView.Loader,
               CBitmapUtils.BitmapLoadedEvent.Listener
{
    public CAccountInfoListAdapter
        (CStartActivity start, List<Account> accounts)
    {
        m_start = start;
        m_inflater = start.getLayoutInflater();
        resetAccounts(accounts);
    }

    @Override
    public void run(final AccountManagerFuture<Bundle> amfuture)
    {
        //CUtils.LOGD(TAG, "run-amfuture: "+amfuture);

        StringBuilder err = new StringBuilder("Unable to talk to server");
        try {
            final Bundle res = amfuture.getResult();
            final Intent intent = (Intent)
                res.getParcelable(AccountManager.KEY_INTENT);
            if (intent != null) {
                intent.setFlags
                    (intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
                m_start.startAddAccount(intent);
            }
            else {
                CUtils.LOGD(TAG, "missing return intent???");
            }
        }
        catch (OperationCanceledException e) {
            CUtils.LOGD(TAG, "Cancelled operation", e);
        }
        catch (Throwable problem) {
            CUtils.LOGD(TAG, "Error while adding account", problem);
        }
    }

    public void requestBitmap
        (CRoundedBitmapView view, Uri uri, int width, int height)
    {
        //CUtils.LOGD(TAG, "bitmap requested: "+uri+" at "+width+"x"+height);
        addPendingRequest(view, uri);
        CBitmapUtils.asyncLoadBitmap
            (m_start.getApplicationContext(), uri,
             CUtils.safeFileName(uri.getPath()), width, height);
    }

    public void onBitmapLoaded(CBitmapUtils.BitmapLoadedEvent ev)
    {
        //CUtils.LOGD(TAG, "got bitmap for "+ev.getUri());
        List<CRoundedBitmapView> rbvs = removePendingRequests(ev.getUri());
        if (rbvs != null) {
            for (CRoundedBitmapView rbv: rbvs) {
                rbv.setBitmapFor(ev.getUri(), ev.getBitmap());
            }
        }
    }

    @Override
    public int getViewTypeCount()
    {
        // - actual account info
        // - add-account button
        // - explanatory text if there are no accounts
        return 3;
    }

    @Override
    public int getItemViewType(int pos)
    {
        // CUtils.LOGD(TAG, "get-item-view-type: "+pos);

        if (haveNoAccounts()) {
            if (pos == 0) { return VIEW_TYPE_HELP; }
            else { return VIEW_TYPE_ADD_BUTTON; }
        }
        else {
            if (pos < m_accounts.size()) { return VIEW_TYPE_INFO; }
            else { return VIEW_TYPE_ADD_BUTTON; }
        }
    }

    @Override
    public int getCount()
    {
        // CUtils.LOGD(TAG, "get-count");
        if (haveNoAccounts()) {
            return 2;
        }
        else {
            return m_accounts.size() + 1;
        }
    }

    @Override
    public Object getItem(int pos)
    { return pos; }
    @Override
    public long getItemId(int pos)
    { return pos; }

    @Override
    public View getView(int pos, View convert, ViewGroup parent)
    {
        // CUtils.LOGD(TAG, "get-view: "+pos+"("+convert+")");
        if (convert != null) {
            switch (convert.getId()) {

            case R.id.account_item_help:
                return convert; // Static view.

            case R.id.account_item_add:
                return convert; // Static view.

            default:
                return setAccountInfo(pos, convert);
            }
        }

        // Figure out what view type we want to create.
        switch (getItemViewType(pos)) {
        case VIEW_TYPE_HELP:
            return setHelpText
                (m_inflater.inflate(R.layout.account_item_help, null));

        case VIEW_TYPE_ADD_BUTTON:
            return setAddAccount
                (m_inflater.inflate(R.layout.account_item_add, null));

        default:
            return setAccountInfo
                (pos, m_inflater.inflate(R.layout.account_item_info, null));
        }
    }

    // CSyncUtils.ProgressEvent.Listener
    public void onSyncProgress(CSyncUtils.ProgressEvent ev)
    {
        CUtils.LOGD(TAG, "on-sync-progress: "+ev.getType()+":"+
                    ev.getName()+":"+ev.getStatus());

        final int size = m_accounts.size();
        int pos = -1;
        Info target = null;
        for (int i=0; i<size; i++) {
            Info info = m_accounts.get(i);
            if (ev.getName().equals(info.getName()) &&
                ev.getType().equals(info.getType())) {
                pos = i;
                target = info;
                break;
            }
        }
        if (target == null) { return; }

        CRefreshButton rbutton = getRefreshButton(ev.getType(), ev.getName());

        switch (ev.getStatus()) {

        case UPDATED:
            if (rbutton != null) { rbutton.setInProgress(true); }
            asyncGetInfo(pos, target);
            break;

        case FINISHED:
            if (rbutton != null) { rbutton.setInProgress(false); }
            // refetch info; this triggers a uirefresh if needed.
            asyncGetInfo(pos, target);
            break;

        case STARTED:
            if (rbutton != null) { rbutton.setInProgress(true); }
            break;
        }
    }

    // CSyncUtils.InfoEvent.Listener
    public void onSyncInfo(CSyncUtils.InfoEvent ev)
    {
        //CUtils.LOGD(TAG, "on-sync-info: "+ev.getTag()+":"+
        //            ev.getUploadCount()+":"+ev.getRecents()+":"+
        //            ev.getPendingCount()+":"+ev.getPendings());
        int pos = (Integer) (ev.getTag());
        if (pos >= m_accounts.size()) {
            // skip
            return;
        }
        Info info = m_accounts.get(pos);
        // check again.
        if (info.getName().equals(ev.getName()) &&
            info.getType().equals(ev.getType())) {
            if (info.maybeUpdate(m_start, ev)) {
                // changed
                notifyDataSetChanged();
                return;
            }
        }
    }

    final void onPause()
    {
        clearPendingRequests();
        CSyncUtils.InfoEvent.unsubscribe(this);
        CSyncUtils.ProgressEvent.unsubscribe(this);
        CBitmapUtils.BitmapLoadedEvent.unsubscribe(this);
    }

    final void onResume(List<Account> accounts)
    {
        CSyncUtils.InfoEvent.subscribe(this);
        CSyncUtils.ProgressEvent.subscribe(this);
        CBitmapUtils.BitmapLoadedEvent.subscribe(this);
        // Check for changes, and update as necessary.
        if (accounts.size() != m_accounts.size()) {
            resetAccounts(accounts);
            notifyDataSetChanged();
            return;
        }
        boolean changed = false;
        for (int i=0; i<accounts.size(); i++) {
            Info mine = m_accounts.get(i);
            if (accounts.get(i).name.equals(mine.getAccount().name)) {
                if (mine.maybeUpdateSettings(m_start)) {
                    changed = true;
                }
                asyncGetInfo(i, mine);
                continue;
            }
            changed = true;
            Info ninfo = new Info(m_start, accounts.get(i));
            m_accounts.set(i, ninfo);
            asyncGetInfo(i, ninfo);
        }
        if (changed) {
            notifyDataSetChanged();
        }
    }

    private synchronized void addPendingRequest
        (CRoundedBitmapView rbv, Uri uri)
    {
        List<CRoundedBitmapView> rbvs = m_pending_requests.get(uri);
        if (rbvs == null) {
            rbvs = new ArrayList<CRoundedBitmapView>();
            m_pending_requests.put(uri, rbvs);
        }
        rbvs.add(rbv);
    }

    private synchronized List<CRoundedBitmapView> removePendingRequests(Uri uri)
    { return m_pending_requests.remove(uri); }

    private synchronized void clearPendingRequests()
    { m_pending_requests.clear(); }

    private final String refreshKey(String atype, String aname)
    { return atype+":"+aname; }

    private synchronized void removeRefreshButton(String atype, String aname)
    { m_refresh_map.remove(refreshKey(atype,aname)); }

    private synchronized CRefreshButton
        getRefreshButton(String atype, String aname)
    { return m_refresh_map.get(refreshKey(atype, aname)); }

    private synchronized void
        setRefreshButton(String atype, String aname, CRefreshButton b)
    {
        String k = atype+":"+aname;

        // remove both keys and values.
        for (Iterator<String> it = m_refresh_map.keySet().iterator();
             it.hasNext(); ) {
            String v = it.next();
            if (v.equals(k)) { it.remove(); }
            else if (b.equals(m_refresh_map.get(v))) { it.remove(); }
        }
        m_refresh_map.put(k, b);
    }

    private final void asyncGetInfo(int pos, Info info)
    {
        CSyncUtils.asyncGetSyncInfo
            (m_start.getApplicationContext(), pos,
             info.getType(), info.getName());
    }

    private final void resetAccounts(List<Account> accounts)
    {
        m_accounts.clear();

        for (Account acct: accounts) {
            Info info = new Info(m_start, acct);
            m_accounts.add(info);
            asyncGetInfo(m_accounts.size()-1, info);
        }
    }

    private void runAddAccount()
    {
        AccountManager.get
            (m_start.getApplicationContext())
            .addAccount
            (CUtils.BASE_ACCOUNT_TYPE, CUtils.AUTH_ACCESS,
             null /*features*/,
             null /*options*/,
             null /*activity*/,
             this /*callback*/,
             null /*handler*/);
    }

    private View setHelpText(View v)
    { return v; } // static view; info set in layout.

    private View setAddAccount(View v)
    {
        Button b = (Button) v.findViewById(R.id.account_item_add_button);
        b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    runAddAccount();
                }
            });
        return v;
    }

    private View setAccountInfo(int pos, View v)
    {
        ViewGroup vg = (ViewGroup) v;
        Info info = m_accounts.get(pos);

        // Title
        TextView tv =
            (TextView) (vg.findViewById(R.id.account_item_info_title));
        tv.setText(info.getName());
        if (false) {
            // change when we enable more providers
            tv.setCompoundDrawablesWithIntrinsicBounds
                (info.getIconDrawable(), 0, 0, 0);
        }

        ViewGroup content = (ViewGroup)
            vg.findViewById(R.id.account_item_info_content);
        tv = (TextView) vg.findViewById(R.id.account_item_info_disabled);

        // content - set if syncable.
        if (info.isSyncable()) {
            tv.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
            setAccountInfoContent(info, content);
        }
        else {
            tv.setVisibility(View.VISIBLE);
            content.setVisibility(View.GONE);
        }

        // update onclick on settings
        final Intent intent = ABaseGlue.makeSettingsIntent
            (m_start, info.getType(), info.getName(), false);
        vg.findViewById(R.id.account_item_info_settings_button)
            .setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        m_start.startActivity(intent);
                    }
                });
        return v;
    }

    private final void setAccountInfoContent(Info info, ViewGroup content)
    {
        final String atype = info.getType();
        final String aname = info.getName();

        TextView settings_summary = (TextView) content.findViewById
            (R.id.account_item_info_settings_summary);
        settings_summary.setText(info.getSettingsSummary());

        CStatusCounterView scv_upload =
            (CStatusCounterView) content.findViewById
            (R.id.account_item_info_counter_uploaded);
        CStatusCounterView scv_pending =
            (CStatusCounterView) content.findViewById
            (R.id.account_item_info_counter_pending);
        CRefreshButton rbutton =
            (CRefreshButton) content.findViewById
            (R.id.account_item_info_refresh_button);
        ViewGroup recents_tn = (ViewGroup) content.findViewById
            (R.id.account_item_info_recents);
        TextView recents_tv = (TextView) content.findViewById
            (R.id.account_item_info_recents_title);
        ViewGroup pendings_tn = (ViewGroup) content.findViewById
            (R.id.account_item_info_pendings);
        TextView pendings_tv = (TextView) content.findViewById
            (R.id.account_item_info_pendings_title);
        ViewGroup downloads_tn = (ViewGroup) content.findViewById
            (R.id.account_item_info_downloads);
        TextView downloads_tv = (TextView) content.findViewById
            (R.id.account_item_info_downloads_title);
        TextView result_tv = (TextView) content.findViewById
            (R.id.account_item_info_result);
        TextView debug_tv = (TextView) content.findViewById
            (R.id.account_item_info_debug);
        maybeSetSelectable(debug_tv);

        Resources res = m_start.getResources();

        int uc = (int) info.getUploadCount();
        scv_upload.setCounter(uc);

        int pc = (int) info.getPendingCount();
        if (!info.uploadEnabled()) {
            pc = -1;
        }
        scv_pending.setCounter(pc);

        if (info.downloadEnabled() ||
            (info.uploadEnabled() && (pc > 0))) {
            setRefreshButton(atype, aname, rbutton);
            rbutton.setVisibility(View.VISIBLE);
            rbutton.setInProgress
                (CSyncUtils.syncInProgress(atype, aname));
            rbutton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        CSyncUtils.startSyncManually(atype, aname);
                    }
                });
            setResult(result_tv, info);
        }
        else {
            rbutton.setVisibility(View.GONE);
            rbutton.setInProgress(false);
            rbutton.setOnClickListener(null);
            removeRefreshButton(atype, aname);
            result_tv.setVisibility(View.GONE);
        }


        if (info.debugEnabled()) { setDebug(debug_tv, info); }
        else { debug_tv.setVisibility(View.GONE); }

        updateThumbnailGrid(uc, info.getRecents(), recents_tv, recents_tn);
        updateThumbnailGrid(pc, info.getPendings(), pendings_tv, pendings_tn);
        updateThumbnailGrid
            (info.downloadEnabled()?1:0,
             info.getDownloads(), downloads_tv, downloads_tn);
    }

    @TargetApi(11)
    private final static TextView maybeSetSelectable(TextView tv)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            try { tv.setTextIsSelectable(true); }
            catch (Throwable ign) {} // should not be needed, but heck.
        }
        return tv;
    }

    private void setDebug(TextView tv, Info info)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug Data\nVersion: ");
        sb.append(m_start.getVersion());
        if ((info.getExceptionTime() > 0) &&
            (info.getExceptionTrace() != null)) {
            sb.append("\nLast trace: ");
            sb.append((new Date(info.getExceptionTime())).toString());
            sb.append("\nTrace: ");
            sb.append(info.getExceptionTrace());
        }
        tv.setText(sb.toString());
        tv.setVisibility(View.VISIBLE);
    }

    private void setResult(TextView tv, Info info)
    {
        if (info.getResultTime() <= 0) {
            tv.setVisibility(View.GONE);
            return;
        }

        tv.setVisibility(View.VISIBLE);
        Resources res = m_start.getResources();

        StringBuilder sb = new StringBuilder();
        sb.append(niceDate(info.getResultTime()));
        sb.append(" - ");
        switch (info.getResult()) {
        case NO_USABLE_NETWORK:
            sb.append(res.getString(R.string.result_no_usable_network));
            tv.setTextColor(res.getColor(R.color.gray));
            break;

        case IO_RETRY:
        case UNEXPECTED_RETRY:
            sb.append(res.getString(R.string.result_io_retry));
            tv.setTextColor(res.getColor(R.color.red_accent));
            break;

        case UPLOADS_IN_PROGRESS:
            sb.append(res.getString(R.string.result_uploads_in_progress));
            tv.setTextColor(res.getColor(R.color.gray));
            break;

        case DOWNLOADS_IN_PROGRESS:
            sb.append(res.getString(R.string.result_downloads_in_progress));
            tv.setTextColor(res.getColor(R.color.gray));
            break;

        case AUTH_FAILURE:
            sb.append(res.getString(R.string.result_auth_failure));
            tv.setTextColor(res.getColor(R.color.red_accent));
            break;

        case DONE:
            sb.append(res.getString(R.string.result_done));
            tv.setTextColor(res.getColor(R.color.gray));
            break;

        default:
            sb.append(info.getResult().toString());
            tv.setTextColor(res.getColor(R.color.red_accent));
            break;
        }
        if (info.getResultExtra() != null) {
            sb.append(" (");
            sb.append(info.getResultExtra());
            sb.append(")");
        }
        tv.setText(sb.toString());
    }

    private void updateThumbnailGrid
        (int count, List<String> uris, TextView tv, ViewGroup vg)
    {
        //CUtils.LOGD(TAG, "count="+count+", uris="+uris);
        if ((count <= 0) || (uris.size() == 0)) {
            tv.setVisibility(View.GONE);
            vg.setVisibility(View.GONE);
            return;
        }

        tv.setVisibility(View.VISIBLE);
        vg.setVisibility(View.VISIBLE);
        int availslots = vg.getChildCount();
        int availuris = uris.size();
        for (int curidx=0; curidx<availslots; curidx++) {
            CRoundedBitmapView bmv = (CRoundedBitmapView) vg.getChildAt(curidx);
            if (curidx >= availuris) {
                bmv.setVisibility(View.GONE);
                bmv.setOnClickListener(null);
                continue;
            }
            Uri uri = Uri.parse(uris.get(curidx));
            bmv.setVisibility(View.VISIBLE);
            bmv.setLoader(this);
            bmv.setUri(uri);
            bmv.setOnClickListener(m_bmv_listener);
        }
    }

    private final void launchThumbnailUri(Uri uri)
    {
        if (uri == null) { return; }
        Intent view_intent = new Intent(Intent.ACTION_VIEW, uri);
        if (CUtils.hasIntent(m_start, view_intent)) {
            m_start.startActivity(view_intent);
        }
    }

    private final boolean haveNoAccounts()
    {
        return ((m_accounts == null) ||
                (m_accounts.size() == 0));
    }

    private final static String niceDate(long v)
    {
        long deltasec = (System.currentTimeMillis() - v)/1000l;

        // Under a day
        if (deltasec < 86400) {
            return DateFormat.getTimeInstance(DateFormat.MEDIUM)
                .format(new Date(v));
        }
        else {
            return DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(new Date(v));
        }
    }

    private final List<Info> m_accounts = new ArrayList<Info>();
    private final Map<Uri, List<CRoundedBitmapView>> m_pending_requests =
        new HashMap<Uri, List<CRoundedBitmapView>>();
    private final Map<String,CRefreshButton> m_refresh_map =
        new HashMap<String,CRefreshButton>();
    private final LayoutInflater m_inflater;
    private final CStartActivity m_start;
    private final View.OnClickListener m_bmv_listener =
        new View.OnClickListener() {
            public void onClick(View v) {
                launchThumbnailUri(((CRoundedBitmapView)v).getUri());
            }
        };

    private final static int VIEW_TYPE_INFO = 0;
    private final static int VIEW_TYPE_ADD_BUTTON = 1;
    private final static int VIEW_TYPE_HELP = 2;

    private final static class Info
    {
        private Info(Context ctx, Account acct)
        {
            m_acct = acct;
            ABaseGlue.ParsedName pn = ABaseGlue.asParsedName(acct.name);
            m_name = pn.getName();
            m_type = pn.getType();

            if (m_type.equals(CUtils.SNAPFISH_ACCOUNT_TYPE)) {
                m_drawable_id = R.drawable.snapfish_icon;
            }
            else if (m_type.equals(CUtils.FLICKR_ACCOUNT_TYPE)) {
                m_drawable_id = R.drawable.flickr_icon;
            }
            else {
                throw new IllegalArgumentException("unknown type: "+acct.name);
            }
            maybeUpdateSettings(ctx);
        }
        private final boolean isSyncable()
        { return m_syncable; }
        private final int getIconDrawable()
        { return R.drawable.ic_launcher_smp; }
        private final Account getAccount()
        { return m_acct; }
        private final String getName()
        { return m_name; }
        private final String getType()
        { return m_type; }
        private final boolean uploadEnabled()
        { return m_upload_enabled; }
        private final boolean downloadEnabled()
        { return m_download_enabled; }
        private final boolean debugEnabled()
        { return m_debug_enabled; }
        private final boolean maybeUpdateSettings(Context ctx)
        {
            //CUtils.LOGD(TAG, "recheck-settings");

            boolean changed = false;
            boolean syncable = ContentResolver.getSyncAutomatically
                (m_acct, MediaStore.AUTHORITY);
            if (syncable != m_syncable) {
                m_syncable = syncable;
                changed = true;
            }

            // Build up the settings summary.
            StringBuilder sb = new StringBuilder();
            Resources res = ctx.getResources();
            SharedPreferences prefs = CUtils.getSharedPreferences
                (ctx, m_type, m_name);
            boolean add_wifi = false;
            m_upload_enabled =
                prefs.getBoolean(CUtils.PREF_UPLOAD_ENABLED, true);
            if (m_upload_enabled) {
                sb.append(res.getString(R.string.upload_enabled));
                add_wifi = true;
            }
            else {
                sb.append(res.getString(R.string.upload_disabled));
            }

            sb.append(" ");
            m_download_enabled =
                prefs.getBoolean(CUtils.PREF_DOWNLOAD_ENABLED, false);
            if (m_download_enabled) {
                sb.append(res.getString(R.string.download_enabled));
                add_wifi = true;
            }
            else {
                sb.append(res.getString(R.string.download_disabled));
            }

            if (add_wifi) {
                sb.append(" ");
                if (prefs.getBoolean(CUtils.PREF_ONLY_WIFI, true)) {
                    sb.append(res.getString(R.string.using_wifi_network));
                }
                else {
                    sb.append(res.getString(R.string.using_any_network));
                }
            }

            boolean v = prefs.getBoolean(CUtils.PREF_DEBUG_ENABLED, false);
            if (v != m_debug_enabled) {
                m_debug_enabled = v;
                changed = true;
            }

            String summary = sb.toString();
            if (!summary.equals(m_settings_summary)) {
                m_settings_summary = summary;
                changed = true;
            }
            return changed;
        }
        private final boolean maybeUpdate(Context ctx, CSyncUtils.InfoEvent ev)
        {
            boolean changed = false;
            if (m_upload_count != ev.getUploadCount()) {
                m_upload_count = ev.getUploadCount();
                changed = true;
            }
            if (m_pending_count != ev.getPendingCount()) {
                m_pending_count = ev.getPendingCount();
                changed = true;
            }
            if (m_result_time != ev.getResultTime()) {
                m_result_time = ev.getResultTime();
                m_result = ev.getResult();
                m_result_extra = ev.getResultExtra();
                changed = true;
            }
            if (m_exception_time != ev.getLastExceptionTime()) {
                m_exception_time = ev.getLastExceptionTime();
                m_exception_trace = ev.getLastExceptionTrace();
                changed = true;
            }
            List<CLocalImage> nrecents = ev.getRecents();
            long last_uploaded = 0;
            if (nrecents.size() != m_recents.size()) {
                m_recents.clear();
                for (CLocalImage lim: nrecents) {
                    if (last_uploaded < lim.getUploaded()) {
                        last_uploaded = lim.getUploaded();
                    }
                    m_recents.add(lim.getUri());
                }
                changed = true;
            }
            else {
                int idx = 0;
                for (CLocalImage nim: nrecents) {
                    if (last_uploaded < nim.getUploaded()) {
                        last_uploaded = nim.getUploaded();
                    }
                    if (!m_recents.get(idx).equals(nim.getUri())) {
                        m_recents.set(idx, nim.getUri());
                        changed = true;
                    }
                    idx++;
                }
            }
            if (last_uploaded != m_last_uploaded) {
                changed = true;
            }
            List<CMediaUtils.Info> npendings = ev.getPendings();
            if (npendings.size() != m_pendings.size()) {
                m_pendings.clear();
                for (CMediaUtils.Info nminfo: npendings) {
                    m_pendings.add(nminfo.getUri());
                }
                changed = true;
            }
            else {
                int idx = 0;
                for (CMediaUtils.Info nminfo: npendings) {
                    if (!m_pendings.get(idx).equals(nminfo.getUri())) {
                        m_pendings.set(idx, nminfo.getUri());
                        changed = true;
                    }
                    idx++;
                }
            }

            List<CRemoteImage> ndownloads = ev.getDownloads();
            if (ndownloads.size() != m_downloads.size()) {
                m_downloads.clear();
                for (CRemoteImage nr: ndownloads) {
                    m_downloads.add(nr.getUri());
                }
                changed = true;
            }
            else {
                int idx = 0;
                for (CRemoteImage nr: ndownloads) {
                    if (!m_downloads.get(idx).equals(nr.getUri())) {
                        m_downloads.set(idx, nr.getUri());
                        changed = true;
                    }
                    idx++;
                }
            }

            return changed;
        }
        public final long getResultTime()
        { return m_result_time; }
        public final CSyncUtils.Result getResult()
        { return m_result; }
        public final String getResultExtra()
        { return m_result_extra; }
        public final long getExceptionTime()
        { return m_exception_time; }
        public final String getExceptionTrace()
        { return m_exception_trace; }
        private final long getUploadCount()
        { return m_upload_count; }
        private final long getPendingCount()
        { return m_pending_count; }
        private final List<String> getRecents()
        { return m_recents; }
        private final List<String> getPendings()
        { return m_pendings; }
        private final List<String> getDownloads()
        { return m_downloads; }
        private final String getSettingsSummary()
        { return m_settings_summary; }

        private final String m_name;
        private final String m_type;
        private final int m_drawable_id;
        private boolean m_upload_enabled = false;
        private boolean m_download_enabled = false;
        private boolean m_debug_enabled = false;
        private String m_settings_summary = "";
        private long m_last_uploaded = 0;
        private long m_result_time = 0;
        private CSyncUtils.Result m_result;
        private String m_result_extra;
        private long m_exception_time = 0;
        private String m_exception_trace = null;
        private final Account m_acct;
        private final List<String> m_recents = new ArrayList<String>();
        private final List<String> m_pendings = new ArrayList<String>();
        private final List<String> m_downloads = new ArrayList<String>();
        private boolean m_syncable = true;
        private long m_upload_count = -1;
        private long m_pending_count = -1;
    }

    private final static String TAG =
        CUtils.makeLogTag(CAccountInfoListAdapter.class);
}
