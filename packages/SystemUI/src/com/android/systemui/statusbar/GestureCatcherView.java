package com.android.systemui.statusbar;

import com.android.systemui.R;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

public class GestureCatcherView extends LinearLayout{

    private Context mContext;
    private Handler mHandler;
    long mDowntime;
    int mTimeOut;

    private int mTriggerThreshhold = 20;
    private float[] mDownPoint = new float[2];
    private boolean mSwapXY = false;
    private boolean mNavBarSwipeStarted = false;;

    private BaseStatusBar mBar;

    final static String TAG = "PopUpNav";

    public GestureCatcherView(Context context, AttributeSet attrs, BaseStatusBar sb) {
        super(context, attrs);

        Log.d(TAG,"NavPopupView Constructor");
        mContext = context;
        mHandler = new Handler();
        mBar = sb;
        this.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                Log.d(TAG,"got Gesture Action:");
                if (action == MotionEvent.ACTION_DOWN) {
                    if (mNavBarSwipeStarted) {
                    } else {
                        Log.d(TAG,"got initial down");
                        mDownPoint[0] = event.getX();
                        mDownPoint[1] = event.getY();
                        mNavBarSwipeStarted = true;
                    }
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    mNavBarSwipeStarted = false;
                }
                if (action == MotionEvent.ACTION_MOVE && mNavBarSwipeStarted) {
                    final int historySize = event.getHistorySize();
                    for (int k = 0; k < historySize + 1; k++) {
                        float x = k < historySize ? event.getHistoricalX(k) : event.getX();
                        float y = k < historySize ? event.getHistoricalY(k) : event.getY();
                        float distance = 0f;
                        distance = mSwapXY ? (mDownPoint[0] - x) : (mDownPoint[1] - y);
                        if (distance > mTriggerThreshhold) {
                            mNavBarSwipeStarted = false;
                            mBar.showBar();
                        }
                    }
                }
                return false;
            }
        });
    }

    public void setSwapXY(boolean swap) {
        mSwapXY = swap;
    }
}
