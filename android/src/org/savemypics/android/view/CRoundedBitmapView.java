package org.savemypics.android.view;

import android.view.MotionEvent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import org.savemypics.android.R;
import org.savemypics.android.util.CUtils;

public class CRoundedBitmapView extends View
{
    public interface Loader
    {
        public void requestBitmap
            (CRoundedBitmapView view, Uri uri, int width, int height);
    }

    public CRoundedBitmapView(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        setFocusable(true);
        setClickable(true);
        setEnabled(false);

        m_paint = new Paint();
        m_paint.setAntiAlias(true);

        m_presspaint = new Paint();
        m_presspaint.setAntiAlias(true);
        m_presspaint.setColor(0x80000000);

        Resources res = ctx.getResources();
        m_missingcolor = res.getColor(R.color.light_gray);
        m_radius = res.getDimension(R.dimen.grid_b_12);
    }

    public void setLoader(Loader l)
    { m_loader = l; }

    public Uri getUri()
    { return m_uri; }

    public void setUri(Uri uri)
    {
        if ((uri == null) && (m_uri == null)) {
            // nothing to do.
            return;
        }
        if ((uri != null) && (uri.equals(m_uri))) {
            // nothing to do.
            return;
        }

        m_uri = uri;
        if (m_uri == null) {
            setBitmapFor(null, null);
            return;
        }
        m_request_made = false;
        maybeRequestUri();
    }

    public void setBitmapFor(Uri uri, Bitmap bm)
    {
        if (m_uri == null) {
            setMyPaint(null);
            if (m_rectf != null) {
                invalidate();
            }
            return;
        }

        if (!m_uri.equals(uri)) {
            // reject - probably for some other request.
            return;
        }

        if (bm == null) {
            setMyPaint(null);
        }
        else {
            setMyPaint
                (new BitmapShader
                 (bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }
        if (m_rectf != null) {
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        boolean ret = super.onTouchEvent(ev);
        switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            setPressed(true);
            break;
        case MotionEvent.ACTION_UP:
            setPressed(false);
            break;
        default:
            break;
        }
        return ret;
    }

    @Override
    protected void dispatchSetPressed(boolean pressed)
    {
        if (m_showpress != pressed) {
            m_showpress = pressed;
            invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b)
    {
        int w = r-l;
        int h = b-t;

        if ((w != m_w) || (h != m_h)) {
            m_w = w;
            m_h = h;
            m_rectf = new RectF(0, 0, w, h);
            maybeRequestUri();
        }
    }

    @Override
    protected void onMeasure(int wspec, int hspec)
    {
        if ((MeasureSpec.getMode(wspec) != MeasureSpec.EXACTLY) ||
            (MeasureSpec.getMode(hspec) != MeasureSpec.EXACTLY)) {
            throw new IllegalArgumentException("I need exact dimensions.");
        }

        //CUtils.LOGD(TAG, "on-measure: "+MeasureSpec.getSize(wspec)+"x"+
        //            MeasureSpec.getSize(hspec));
        setMeasuredDimension
            (MeasureSpec.getSize(wspec), MeasureSpec.getSize(hspec));
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        if (m_rectf != null) {
            canvas.drawRoundRect(m_rectf, m_radius, m_radius, m_paint);
            if (m_showpress) {
                canvas.drawRoundRect(m_rectf, m_radius, m_radius, m_presspaint);
            }
        }
    }

    private void setMyPaint(Shader shader)
    {
        m_paint.setShader(shader);
        if (shader == null) {
            m_paint.setColor(m_missingcolor);
            setEnabled(false);
        }
        else {
            setEnabled(true);
        }
    }

    private void maybeRequestUri()
    {
        if ((m_uri == null) ||
            (m_rectf == null) ||
            (m_loader == null) ||
            m_request_made) {
            return;
        }

        m_request_made = true;
        m_loader.requestBitmap(this, m_uri, m_w, m_h);
    }

    private RectF m_rectf = null;
    private int m_h = 0;
    private int m_w = 0;
    private Loader m_loader = null;
    private Uri m_uri = null;
    private boolean m_request_made = false;
    private boolean m_showpress = false;
    private final Paint m_paint;
    private final Paint m_presspaint;
    private final float m_radius;
    private final int m_missingcolor;

    private final static String TAG =
        CUtils.makeLogTag(CRoundedBitmapView.class);
}
