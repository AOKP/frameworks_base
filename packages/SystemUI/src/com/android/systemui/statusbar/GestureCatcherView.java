package com.android.systemui.statusbar;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
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
    private Resources res;
    private Handler mHandler;
    private ImageView mDragButton;
    private View mSpacerLeft,mSpacerRight;
    long mDowntime;
    int mTimeOut;
    private int mButtonWeight;
    private int mGestureHeight;
    private boolean mDragButtonVisible;

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
        mSpacerLeft = new View (mContext);
        mSpacerRight = new View (mContext);
        res = mContext.getResources();
        mGestureHeight = res.getDimensionPixelSize(R.dimen.drag_handle_height);
        updateLayout();

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();

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
        updateLayout();
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {

        WindowManager.LayoutParams lp  = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = (mSwapXY ? Gravity.CENTER_VERTICAL | Gravity.RIGHT : Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lp.setTitle("GesturePanel");
        return lp;
    }

    private void updateLayout () {
        LinearLayout.LayoutParams lp;
        final int sides = (100 - mButtonWeight) / 2;
        removeAllViews();
        if (mSwapXY) { // Landscape Mode
            lp = new LinearLayout.LayoutParams(mGestureHeight, 0, sides);
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button_land));
            mDragButton.setLayoutParams (new LinearLayout.LayoutParams(mGestureHeight,0,mButtonWeight));
            mSpacerLeft.setLayoutParams(lp);
            mSpacerRight.setLayoutParams(lp);
            setOrientation(VERTICAL);
        } else {
            lp = new LinearLayout.LayoutParams(0,mGestureHeight, sides);
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button));
            mDragButton.setLayoutParams (new LinearLayout.LayoutParams(0, mGestureHeight,mButtonWeight));
            mSpacerLeft.setLayoutParams(lp);
            mSpacerRight.setLayoutParams(lp);
            setOrientation(HORIZONTAL);
        }
        addView(mSpacerLeft);
        addView(mDragButton);
        addView(mSpacerRight);
        
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DRAG_HANDLE_WEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DRAG_HANDLE_VISIBLE), false, this);
        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        mDragButtonVisible = Settings.System.getBoolean(cr, Settings.System.DRAG_HANDLE_VISIBLE, true);

        if (!mDragButtonVisible) {
            mDragButton.setVisibility(View.INVISIBLE);
            mDragButton.setClickable(true);
        } else {
            mDragButton.setVisibility(View.VISIBLE);
            mDragButton.setClickable(true);
        }

        mButtonWeight = Settings.System.getInt(cr, Settings.System.DRAG_HANDLE_WEIGHT, 0);
        updateLayout();
    }
}
