package org.savemypics.android.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import org.savemypics.android.R;
import org.savemypics.android.util.CUtils;

public class CRefreshButton extends View
{
    public CRefreshButton(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        setEnabled(true);
        setFocusable(true);
        setClickable(true);
        Resources res = ctx.getResources();
        m_textpaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        m_textpaint.setTextAlign(Paint.Align.CENTER);
        m_textpaint.setTypeface(CUtils.getIconTypeface(ctx));

        m_bluecolor = res.getColor(R.color.blue_dark_accent);
        m_dividercolor = res.getColor(R.color.divider_color);
        m_graycolor = res.getColor(R.color.gray);
        m_presscolor = 0xff000000;

        m_arcpaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        m_arcpaint.setStyle(Paint.Style.STROKE);
        int gray_transparent = 0x00ffffff & m_graycolor;
        m_arcpaint.setShader
            (new SweepGradient
             (0, 0,
              new int[] {gray_transparent, gray_transparent, m_graycolor},
              new float[] { 0f, 0.15f, 1f }));
        m_arcpaint.setStrokeWidth
            (res.getDimension(R.dimen.grid_b_12));
    }

    public void setInProgress(boolean v)
    {
        if (m_inprogress == v) { return; }

        m_inprogress = v;
        post(new Runnable() {
                public void run() {
                    setEnabled(!m_inprogress);
                    invalidate();
                }
            });
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
        if (m_showglow != pressed) {
            m_showglow = pressed;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int wspec, int hspec)
    {
        if ((MeasureSpec.getMode(wspec) != MeasureSpec.EXACTLY) ||
            (MeasureSpec.getMode(hspec) != MeasureSpec.EXACTLY)) {
            throw new IllegalArgumentException("I need exact dimensions.");
        }
        setMeasuredDimension
            (MeasureSpec.getSize(wspec), MeasureSpec.getSize(hspec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b)
    {
        int w = r-l;
        int h = b-t;

        int min = (w <= h)?w:h;

        m_cx = w/2f;
        m_cy = h/2f;
        m_textpaint.setTextSize(min*0.6f);
        m_radius = min*0.2f;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        if ((m_cx < 0) || (m_cy < 0)) { return; }

        if (!m_inprogress) {
            m_textpaint.setColor(m_showglow?m_presscolor:m_bluecolor);
            canvas.drawText(SPINNER, m_cx, m_cy, m_textpaint);
            return;
        }

        long now = System.currentTimeMillis();
        if (m_start == 0) {
            m_start = now;
        }
        long tdelta = now - m_start;
        float rot = tdelta*DEG_PER_MSEC;
        canvas.save();
        try {
            canvas.translate(m_cx, m_cy);
            canvas.rotate(rot);
            canvas.drawCircle(0, 0, m_radius, m_arcpaint);
        }
        finally {
            canvas.restore();
        }
        postInvalidateDelayed(FRAME_DURATION_MSEC);
    }

    private long m_start = 0l;
    private float m_cx = -1f;
    private float m_cy = -1f;
    private float m_radius = 0f;
    private boolean m_inprogress = false;
    private boolean m_showglow = false;
    private final Paint m_textpaint;
    private final Paint m_arcpaint;
    private final int m_bluecolor;
    private final int m_presscolor;
    private final int m_graycolor;
    private final int m_dividercolor;

    private final static float DEG_PER_MSEC = 80f/1000f;
    private final static long FRAME_DURATION_MSEC = 80l;
    private final static String SPINNER = "M";
    private final static String TAG = CUtils.makeLogTag(CRefreshButton.class);
}
