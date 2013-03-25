package com.android.systemui.aokp;

import com.android.systemui.R;
import com.android.systemui.aokp.NavigationBarRibbon;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class RibbonGestureCatcherView extends LinearLayout{

    private Context mContext;
    private Resources res;
    private ImageView mDragButton;
    long mDowntime;
    int mTimeOut;
    private int mButtonWeight = 50;
    private int mGestureHeight;
    private int mDragButtonOpacity;
    private boolean mRightSide;

    private int mTriggerThreshhold = 20;
    private float[] mDownPoint = new float[2];
    private boolean mVerticalLayout = true;
    private boolean mRibbonSwipeStarted = false;
    private boolean mRibbonLocked = false;
    private int mScreenWidth, mScreenHeight;
    private String mAction;

    private SettingsObserver mSettingsObserver;

    final static String TAG = "PopUpRibbon";

    public RibbonGestureCatcherView(Context context, AttributeSet attrs, String action) {
        super(context, attrs);

        mContext = context;
        mAction = action;
        mVerticalLayout = !mAction.equals("bottom");
        mRightSide = mAction.equals("right");
        mDragButton = new ImageView(mContext);
        res = mContext.getResources();
        mGestureHeight = res.getDimensionPixelSize(R.dimen.drag_handle_height);
        updateLayout();
        Point size = new Point();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(size);
        mScreenHeight = size.y;
        mScreenWidth = size.x;

        mSettingsObserver = new SettingsObserver(new Handler());
        updateSettings();

        mDragButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG, "Registered Touch event");
                int action = event.getAction();
                switch (action) {
                case  MotionEvent.ACTION_DOWN :
                    if (!mRibbonSwipeStarted) {
                        mDownPoint[0] = event.getX();
                        mDownPoint[1] = event.getY();
                        Log.d(TAG, "mRibbonSwipedSet true, X point " + String.valueOf(mDownPoint[0]) + " Y point " + String.valueOf(mDownPoint[1]));
                        mRibbonSwipeStarted = true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL :
                    mRibbonSwipeStarted = false;
                    mRibbonLocked = false;
                    break;
                case MotionEvent.ACTION_MOVE :
                    Log.d(TAG, "Tracking Move");
                    if (mRibbonSwipeStarted && !mRibbonLocked) {
                        final int historySize = event.getHistorySize();
                        for (int k = 0; k < historySize + 1; k++) {
                            float x = k < historySize ? event.getHistoricalX(k) : event.getX();
                            float y = k < historySize ? event.getHistoricalY(k) : event.getY();
                            float distance = 0f;
                            if (mVerticalLayout) {
                                distance = mRightSide ? (mDownPoint[0] - x) : (x - mDownPoint[0]);
                            } else {
                                distance = mDownPoint[1] - y;
                            }
                            if (distance > mTriggerThreshhold) {
                                mRibbonSwipeStarted = false;
                                mRibbonLocked = true;
                                Intent toggleRibbon = new Intent(
                                    NavigationBarRibbon.RibbonReceiver.ACTION_TOGGLE_RIBBON);
                                toggleRibbon.putExtra("action", mAction);
                                Log.d(TAG, "Sending broadcast for" + mAction);
                                mContext.sendBroadcast(toggleRibbon);
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    mRibbonSwipeStarted = false;
                    mRibbonLocked = false;
                    break;
                }
                return false;
            }
        });

        mDragButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                Log.d(TAG, "Long pressed sending broadcast");
                Intent toggleRibbon = new Intent(
                        NavigationBarRibbon.RibbonReceiver.ACTION_TOGGLE_RIBBON);
                toggleRibbon.putExtra("action", mAction);
                mContext.sendBroadcast(toggleRibbon);
                return true;
                }
            });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        super.onDetachedFromWindow();
    }

    private int getGravity() {
        int gravity = 0;
        if (mAction.equals("bottom")) {
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        } else if (mAction.equals("left")) {
            gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        } else {
            gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        }
        return gravity;
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {
        WindowManager.LayoutParams lp  = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = getGravity();
        lp.setTitle("RibbonGesturePanel" + mAction);
        return lp;
    }

    private void updateLayout() {
        LinearLayout.LayoutParams dragParams;
        float dragSize = 0;
        removeAllViews();
        if (mVerticalLayout) {
            dragSize = ((mScreenHeight) * (mButtonWeight*0.02f)) / getResources().getDisplayMetrics().density;
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button_land));
            dragParams = new LinearLayout.LayoutParams(mGestureHeight,(int) dragSize);
            setOrientation(VERTICAL);
        } else {
            dragSize = ((mScreenWidth) * (mButtonWeight*0.02f)) / getResources().getDisplayMetrics().density;
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button));
            dragParams = new LinearLayout.LayoutParams((int) dragSize, mGestureHeight);
            setOrientation(HORIZONTAL);
        }
        mDragButton.setScaleType(ImageView.ScaleType.FIT_XY);
        mDragButton.setImageAlpha(50);
        addView(mDragButton,dragParams);
        invalidate();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            //resolver.registerContentObserver(Settings.System.getUriFor(
            //        Settings.System.DRAG_HANDLE_WEIGHT), false, this);
        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        //mDragButtonOpacity = Settings.System.getInt(cr, Settings.System.DRAG_HANDLE_OPACITY, 50);
        updateLayout();
    }
}
