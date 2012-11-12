package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import com.android.systemui.aokp.AokpTarget;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.R;


public class ExtensibleKeyButtonView extends KeyButtonView {

    private static final String TAG = "Key.Ext";

    public String mClickAction, mLongpress;
    private AokpTarget mAokpTarget;

    public ExtensibleKeyButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAokpTarget(AokpTarget targ){
        mAokpTarget = targ;
    }

    public void setActions(String ClickAction, String Longpress){
        mClickAction = ClickAction;
        mLongpress = Longpress;
        if (ClickAction != null){
            if (ClickAction.equals(AokpTarget.ACTION_HOME)) {
                setCode(KeyEvent.KEYCODE_HOME);
                setId(R.id.home);
            } else if (ClickAction.equals(AokpTarget.ACTION_BACK)) {
                setCode (KeyEvent.KEYCODE_BACK);
                setId(R.id.back);
            } else if (ClickAction.equals(AokpTarget.ACTION_MENU)) {
                setCode (KeyEvent.KEYCODE_MENU);
                setId(R.id.menu);
            } else if (ClickAction.equals(AokpTarget.ACTION_POWER)) {
                setCode (KeyEvent.KEYCODE_POWER);
            } else { // the remaining options need to be handled by OnClick;
                setOnClickListener(mClickListener);
                if (ClickAction.equals(AokpTarget.ACTION_RECENTS))
                    setId(R.id.recent_apps);
            }
        }
        setSupportsLongPress (false);
        if (Longpress != null)
            if ((!Longpress.equals(AokpTarget.ACTION_NULL)) || (getCode() !=0)) {
                // I want to allow long presses for defined actions, or if 
                // primary action is a 'key' and long press isn't defined otherwise
                setSupportsLongPress(true);
                setOnLongClickListener(mLongPressListener);
            }
    }
    private OnClickListener mClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
        	// the other consts were handled by keycode.  
        	
        	mAokpTarget.launchAction(mClickAction);
        }
    };

    private OnLongClickListener mLongPressListener = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
                return mAokpTarget.launchAction(mLongpress);
        }
    };
}

