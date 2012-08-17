package com.android.internal.policy.impl;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.android.internal.R;

public class CalendarEntry extends LinearLayout {

    private static final String TAG = "CalendarEntry";
    TextView mTitle;
    TextView mDetails;
    int mWidth;
    String title;
    String details;
    Context mContext;
    
    public CalendarEntry(Context context, String title, String details, int width) {
        super(context);
        mContext = context;
        this.title = title;
        this.details = details;
        mWidth = width;
    }

    public void setColor(int color) {
        mTitle.setTextColor(color);
        mDetails.setTextColor(color);
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        View view = LayoutInflater.from(mContext).inflate(R.layout.calendar_entry, this, true);
        this.mTitle = (TextView) view.findViewById(R.id.event_title);
        this.mDetails = (TextView) view.findViewById(R.id.event_details);
        this.setLayoutParams(new FrameLayout.LayoutParams(-2, -2, 5));
        mTitle.setText(title);
        mDetails.setText(details);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Adjust width as necessary
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        if(mWidth > 0 && mWidth < measuredWidth) {
            int measureMode = MeasureSpec.getMode(widthMeasureSpec);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mWidth, measureMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

