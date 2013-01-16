package com.android.systemui.statusbar;

import com.android.systemui.R;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class GestureCatcherView extends LinearLayout{

    private Context mContext;
    private Handler mHandler;
    private ImageView mDragButton;
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
        mDragButton = new ImageView(mContext);
        mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button));
        addView(mDragButton);
        mDragButton.setOnTouchListener(new View.OnTouchListener() {

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
                            mBar.showBar(false);
                        }
                    }
                }
             // fake a touch on the NavRing
                mBar.mSearchPanelView.dispatchTouchEvent(event);
                return false;
            }
        });
        mDragButton.setOnLongClickListener(new View.OnLongClickListener() {
        
            @Override
            public boolean onLongClick(View v) {
                mBar.showBar(true);
                return true;
            }
        });
    }

    public void setSwapXY(boolean swap) {
        mSwapXY = swap;
        if (mSwapXY) {
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button_land));
        } else {
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button));
        }
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {
        boolean onSide = (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) 
            && (Settings.System.getInt(mContext.getContentResolver(),Settings.System.CURRENT_UI_MODE, 0) == 0);
        WindowManager.LayoutParams lp  = new WindowManager.LayoutParams( 
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = (onSide ? Gravity.CENTER_VERTICAL | Gravity.RIGHT : Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lp.setTitle("GesturePanel");
        return lp;
    }
}
