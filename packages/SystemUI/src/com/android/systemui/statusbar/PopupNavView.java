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

public class PopupNavView extends LinearLayout{

    private static int SWIPE_WINDOW_HEIGHT = 2; // 2 pixel high window
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
    boolean mNavBarShowing = true;
    int mStockNavBarHeight;
    int mNavBarHeight;
    int mTimeOut;

    private int mTriggerThreshhold = 20;
    private float[] mDownPoint = new float[2];
    private int screenHeight;
    private int screenWidth;
    private boolean mSwapXY = false;
    private boolean mNavBarSwipeStarted;
    private WindowManager.LayoutParams mSwipeParams, mNavBarParams;

    public DelegateViewHelper mDelegateHelper;

    final static String TAG = "PopUpNav";

    public PopupNavView(Context context, AttributeSet attrs, BaseStatusBar sb) {
        super(context, attrs);

        Log.d(TAG,"NavPopupView Constructor");
        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mHandler = new Handler();

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();

        mStockNavBarHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);

        mSwipeParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                SWIPE_WINDOW_HEIGHT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                 | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                 | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                  PixelFormat.TRANSLUCENT);
        mSwipeParams.gravity = Gravity.BOTTOM;
        mSwipeParams.setTitle("NavCatcher");

        mNavBarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                mNavBarHeight,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT);
        mNavBarParams.gravity = Gravity.BOTTOM;
        mNavBarParams.setTitle("NavBar");

        createSwipeCatcher();
        createPopUpView();
        if (mNavBarView != null && sb != null) {
            mNavBarView.setBar(sb);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        Log.d(TAG,"Got NavBar Touch");
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            // Action is outside the NavBar, immediately hide the NavBar
            mHandler.removeCallbacks(delayHide);
            mNavBarSwipeStarted = false;
            hidePopupView();
        } else {
            // Action must be inside the View - reset the timer;
            if (mTimeOut > 0) {
                mHandler.removeCallbacks(delayHide); // reset
                mHandler.postDelayed(delayHide,mTimeOut);
            }
        }
        return false;
    }

    private Runnable delayHide = new Runnable() {
        public void run() {
            hidePopupView();
        }
    };

    private void show() {
        if (mWindowManager != null && !showing){
            mWindowManager.addView(this, mNavBarParams);
            mWindowManager.removeView(mSwipeCatcher);
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
            mWindowManager.removeView(this);
            mWindowManager.addView(mSwipeCatcher, mSwipeParams);
            showing = false;
        }
    }

    private void createSwipeCatcher() {
        Log.d(TAG,"Creating SwipeCatcher");
        mSwipeCatcher = new FrameLayout(mContext);
        mSwipeCatcher.setOnTouchListener(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {
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
                                if (!mNavBarShowing) {
                                    show();
                                }
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
            mWindowManager.addView(mSwipeCatcher, mSwipeParams);
        }
    }

    private void createPopUpView() {
        Log.d(TAG,"Creating PopupView");
        mNavBarView = (NavigationBarView) View.inflate(mContext, R.layout.navigation_bar, null);
        addView(mNavBarView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT));
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
                    Settings.System.NAVIGATION_BAR_HEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_ENABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_SHOW_NOW), false, this);
        }

         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();

        mTimeOut = Settings.System.getInt(cr,
                Settings.System.NAV_HIDE_TIMEOUT, HIDE_NAVBAR_DELAY);

        mNavBarHeight = Settings.System.getInt(cr,
                Settings.System.NAVIGATION_BAR_HEIGHT, mStockNavBarHeight);

        mNavBarShowing = Settings.System.getBoolean(cr,
                Settings.System.NAVIGATION_BAR_SHOW_NOW, true);
    }
   
   public void disablePopup () {
       if (showing) {
           hidePopupView();
           mWindowManager.removeView(mSwipeCatcher);
       }
   }
}
