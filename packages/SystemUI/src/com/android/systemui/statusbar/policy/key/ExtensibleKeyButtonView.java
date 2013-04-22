
package com.android.systemui.statusbar.policy.key;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.systemui.R;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.recent.RecentsActivity;
import com.android.systemui.statusbar.policy.KeyButtonView;

public class ExtensibleKeyButtonView extends KeyButtonView {

    public String mClickAction, mLongpress, mDoubleTap;
    private boolean doubleClickin;
    Handler mHandler;

    public ExtensibleKeyButtonView(Context context, AttributeSet attrs, String clickAction,
            String longPress, String doubleTap) {
        super(context, attrs);
        mHandler = new Handler();
        mClickAction = clickAction;
        mLongpress = longPress;
        mDoubleTap = longPress; /* CHANGE THIS BACK */
        setActions(clickAction, longPress);
        setLongPress();
    }

    public ExtensibleKeyButtonView(Context context, AttributeSet attrs, String clickAction,
            String longPress) {
        this(context, attrs, clickAction, longPress, null);
    }

    public void setActions(String clickAction, String longPress) {
        setOnClickListener(mClickListener);
        if (clickAction != null) {
            AwesomeConstant clickEnum = fromString(clickAction);
            switch (clickEnum) {
            case ACTION_HOME:
//                setCode(KeyEvent.KEYCODE_HOME);
                setId(R.id.home);
                break;
//            case ACTION_BACK:
//                setCode(KeyEvent.KEYCODE_BACK);
//                setId(R.id.back);
//                break;
            case ACTION_MENU:
//                setCode(KeyEvent.KEYCODE_MENU);
                setId(R.id.navbar_menu_big);
                break;
//            case ACTION_POWER:
//                setCode(KeyEvent.KEYCODE_POWER);
//                break;
//            case ACTION_SEARCH:
//                setCode(KeyEvent.KEYCODE_SEARCH);
//                break;
            case ACTION_RECENTS:
                setId(R.id.recent_apps);
                setOnClickListener(mClickListener);
                setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        int action = event.getAction() & MotionEvent.ACTION_MASK;
                        if (action == MotionEvent.ACTION_DOWN) {
                            preloadRecentTasksList();
                        } else if (action == MotionEvent.ACTION_CANCEL) {
                            cancelPreloadingRecentTasksList();
                        } else if (action == MotionEvent.ACTION_UP) {
                            if (!v.isPressed() && !doubleClickin) {
                                cancelPreloadingRecentTasksList();
                            }
                        }
                        return false;
                    }
                });
                break;
            default:
                setOnClickListener(mClickListener);
                break;
            }
            
        }
    }
    
    protected OnTouchListener mTouchListener = new OnTouchListener() {
        
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            switch(action) {
                case MotionEvent.ACTION_DOWN:
                    if(doubleClickin) {
                        doubleClickin = false;
//                        
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if(!doubleClickin) {
                        doubleClickin = true;
                        mHandler.postDelayed(mClickActionRunnable, 100);  
                        return true;
                    }
                   
            }
            
            return false;
        }
    };

    protected void setLongPress() {
        setSupportsLongPress(false);
        if (mLongpress != null) {
            if ((!mLongpress.equals(AwesomeConstant.ACTION_NULL)) || (getCode() != 0)) {
                // I want to allow long presses for defined actions, or if
                // primary action is a 'key' and long press isn't defined
                // otherwise
                setSupportsLongPress(true);
                setOnLongClickListener(mLongPressListener);
            }
        }
    }

    protected OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if(mDoubleTap == null || mDoubleTap.equals(AwesomeConstant.ACTION_NULL)) {
                if(mClickAction != null) {
                    AwesomeAction.launchAction(mContext, mClickAction);
                }
            } else {
                // check which click
                if(doubleClickin) {
                    doubleClickin = false;
                    AwesomeAction.launchAction(mContext, mDoubleTap);
                } else {
                    // launch single click action soon
                    doubleClickin = true;
                    mHandler.postDelayed(mClickActionRunnable, 150);
                }
            }
        }
    };

    protected final Runnable mClickActionRunnable = new Runnable() {
        @Override
        public void run() {
            if(doubleClickin) {
                doubleClickin = false;
                AwesomeAction.launchAction(mContext, mClickAction);
            }
        }
    };

    protected OnLongClickListener mLongPressListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            doubleClickin = false;
            return AwesomeAction.launchAction(mContext, mLongpress);
        }
    };

    protected void preloadRecentTasksList() {
        Intent intent = new Intent(RecentsActivity.PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).preloadFirstTask();
    }

    protected void cancelPreloadingRecentTasksList() {
        Intent intent = new Intent(RecentsActivity.CANCEL_PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).cancelPreloadingFirstTask();
    }
}
