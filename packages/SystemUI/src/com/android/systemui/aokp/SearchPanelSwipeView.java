package com.android.systemui.aokp;

import com.android.systemui.AOKPSearchPanelView;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class SearchPanelSwipeView extends LinearLayout{

    private Context mContext;
    private Resources res;
    private int mButtonHeight = 50;
    private int mGestureHeight;
    private ImageView mDragButton;
    private DelegateViewHelper mDelegateHelper;

    public SearchPanelSwipeView(Context context, AOKPSearchPanelView searchPanelView, BaseStatusBar phoneStatusBar) {
        super(context);
        mContext = context;
        mDelegateHelper = new DelegateViewHelper(this);
        mDelegateHelper.setDelegateView(searchPanelView);
        mDelegateHelper.setBar(phoneStatusBar);
        mDragButton = new ImageView(mContext);
        res = mContext.getResources();
        mGestureHeight = res.getDimensionPixelSize(R.dimen.ribbon_drag_handle_height);
        updateLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        setIntialTouchArea();
        if (mDelegateHelper != null) {
            boolean ret = mDelegateHelper.onInterceptTouchEvent(event);
            if (ret) return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        setIntialTouchArea();
        return mDelegateHelper.onInterceptTouchEvent(event);
    }

    public void setIntialTouchArea() {
        mDelegateHelper.setInitialTouchRegion(mDragButton);
    }
    private int getGravity() {
        return Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {
        WindowManager.LayoutParams lp  = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = getGravity();
        lp.setTitle("SwipePanelSwipeView");
        return lp;
    }

    private void updateLayout() {
        LinearLayout.LayoutParams dragParams;
        float dragHeight = (mGestureHeight * (mButtonHeight * 0.01f));
        removeAllViews();
        mDragButton.setBackgroundColor(Color.BLACK);
        dragParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) dragHeight);
        setOrientation(HORIZONTAL);
        mDragButton.setScaleType(ImageView.ScaleType.FIT_XY);
        mDragButton.setVisibility(View.INVISIBLE);
        addView(mDragButton,dragParams);
        invalidate();
    }
}