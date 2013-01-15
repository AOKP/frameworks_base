package com.android.systemui.statusbar;

import com.android.systemui.R;
import com.android.systemui.WidgetSelectActivity;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PopupNavView extends LinearLayout {

    private static int SWIPE_WINDOW_HEIGHT = 80; // 100 pixel high window
    private static int HIDE_NAVBAR_DELAY = 3000;  //ms to hold NavBar before hiding.
    private Context mContext;
    private Handler mHandler;
    private FrameLayout mPopupView;
    private NavigationBarView mNavBarView;
    private FrameLayout mSwipeCatcher;
    public WindowManager mWindowManager;
    int originalHeight = 0;
    int widgetIds[];
    float mFirstMoveY;
    int mCurrentWidgetPage = 0;
    long mDowntime;
    boolean showing = false;
    int mWindowHeight;
    int mTimeOut;

    private int mTriggerThreshhold = 40;
    private int mTriggerWindow = 20;
    private float[] mDownPoint = new float[2];
    private int screenHeight;
    private int screenWidth;
    private boolean mSwapXY = false;
    private boolean mNavBarSwipeStarted;

    final static String TAG = "PopUpNav";

    public PopupNavView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mHandler = new Handler();
        Log.d(TAG,"NavPopupView Constructor");
        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();
        createSwipeCatcher();
        createPopUpView();
    }

    private Runnable delayHide = new Runnable() {
        public void run() {
            hidePopupView();
        }
    };

    private void show() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                mWindowHeight,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                 | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                 | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                 | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM;
            params.setTitle("NavBar");
            if (mWindowManager != null && !showing){
                mWindowManager.addView(mNavBarView, params);
                showing = true;
                if (mTimeOut > 0) {
                    mHandler.postDelayed(delayHide, mTimeOut);
                }
                // Start the timer to hide the NavBar;
            } else {
                Log.e(TAG,"WTF - PopupNav when no window manager exist?");
            }
            mNavBarSwipeStarted = false;
    }

    private void hidePopupView() {
        Log.d(TAG,"Removing NavBar");
        if (mNavBarView != null) {
            mWindowManager.removeView(mNavBarView);
            showing = false;
        }
    }

    private void createSwipeCatcher() {
        Log.d(TAG,"Creating SwipeCatcher");
        mSwipeCatcher = new FrameLayout(mContext);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                mWindowHeight,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                 | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                 | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                  PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;
        params.setTitle("NavCatcher");
        mSwipeCatcher.setOnTouchListener(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d(TAG,"Got Touch Event");
                    int action = event.getAction();
                    if (!showing){
                    if (action == MotionEvent.ACTION_DOWN) {
                        mDownPoint[0] = event.getX();
                        mDownPoint[1] = event.getY();
                        mNavBarSwipeStarted = true;
                    }
                    if (action == MotionEvent.ACTION_MOVE && mNavBarSwipeStarted) {
                        final int historySize = event.getHistorySize();
                        for (int k = 0; k < historySize + 1; k++) {
                            float x = k < historySize ? event.getHistoricalX(k) : event.getX();
                            float y = k < historySize ? event.getHistoricalY(k) : event.getY();
                            float distance = 0f;
                            distance = mSwapXY ? (mDownPoint[0] - x) : (mDownPoint[1] - y);
                            if (distance > mTriggerThreshhold) {
                                Log.d(TAG,"Showing NavBar");
                                show();
                                return true;
                            }
                        }
                    }
                    }
                    return false;
                }
            });
        if (mWindowManager != null){
            Log.d(TAG,"Adding Swipecatcher to WindowManager");
            mWindowManager.addView(mSwipeCatcher, params);
        }
    }

    private void createPopUpView() {
        Log.d(TAG,"Creating PopupView");
        //mPopupView = new FrameLayout(mContext);
        mNavBarView = (NavigationBarView) View.inflate(mContext, R.layout.navigation_bar, null);
        //mPopupView.addView(mNavBarView);

        mNavBarView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG,"Got NavBar Touch");
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    // Action is outside the NavBar, immediately hide the NavBar
                    mHandler.removeCallbacks(delayHide);
                    mNavBarSwipeStarted = false;
                    hidePopupView();
                    return false;
                } else {
                    // Action must be inside the View - reset the timer;
                    if (mTimeOut > 0) {
                        mHandler.removeCallbacks(delayHide); // reset
                        mHandler.postDelayed(delayHide,mTimeOut);
                    }
                    return false; // still need to pass the touch to the NavBar
                }
            }
        });
    }
   class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_HEIGHT), false, this);
        }

         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            createSwipeCatcher();
            createPopUpView();
        }
    }
    protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();

        mTimeOut = Settings.System.getInt(cr,
                Settings.System.NAV_HIDE_TIMEOUT, HIDE_NAVBAR_DELAY);

        mWindowHeight = Settings.System.getInt(cr,
                Settings.System.NAV_HIDE_HEIGHT, SWIPE_WINDOW_HEIGHT);
    }
}
