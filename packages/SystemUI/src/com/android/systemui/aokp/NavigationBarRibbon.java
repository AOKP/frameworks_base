/*
 * Copyright (C) 2013 The Android Open Kand Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.aokp;

import com.android.systemui.R;

import java.io.File;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.systemui.aokp.RibbonGestureCatcherView;

public class NavigationBarRibbon extends LinearLayout {
    public static final String TAG = "NAVIGATION BAR RIBBON";

    private Context mContext;
    private RibbonGestureCatcherView mGesturePanel;
    public FrameLayout mPopupView;
    public WindowManager mWindowManager;
    private SettingsObserver mSettingsObserver;
    private LinearLayout mRibbon;
    private boolean mText;
    private boolean showing = false;
    private int mSize;
    private String shortTargets, longTargets, mLocation;

    public NavigationBarRibbon(Context context, AttributeSet attrs, String location) {
        super(context, attrs);
        mContext = context;
        mLocation = location;
        IntentFilter filter = new IntentFilter();
        filter.addAction(RibbonReceiver.ACTION_TOGGLE_RIBBON);
        mContext.registerReceiver(new RibbonReceiver(), filter);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mSettingsObserver = new SettingsObserver(new Handler());
        updateSettings();
    }


    public void toggleRibbonView() {
        if (showing) {
            if (mPopupView != null) {
                mWindowManager.removeView(mPopupView);
                mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
                showing = false;
            }
        } else {
            WindowManager.LayoutParams params = getParams();
            params.gravity = getGravity();
            params.setTitle("Ribbon" + mLocation);
            if (mWindowManager != null){
                mWindowManager.addView(mPopupView, params);
                mWindowManager.removeView(mGesturePanel);
                showing = true;
            }
        }
    }

    private WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                mLocation.equals("bottom") ? WindowManager.LayoutParams.MATCH_PARENT
                    : WindowManager.LayoutParams.WRAP_CONTENT,
                mLocation.equals("bottom") ? WindowManager.LayoutParams.WRAP_CONTENT
                    : WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        return params;
    }

    private int getGravity() {
        int gravity = 0;
        if (mLocation.equals("bottom")) {
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        } else if (mLocation.equals("left")) {
            gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        } else {
            gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        }
        return gravity;
    }

    public void createRibbonView() {
        if (mGesturePanel == null) {
            mGesturePanel = new RibbonGestureCatcherView(mContext,null,mLocation);
        }
        mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
        mPopupView = new FrameLayout(mContext);
        View ribbonView = View.inflate(mContext, R.layout.navigation_bar_ribbon, null);
        mRibbon = (LinearLayout) ribbonView.findViewById(R.id.ribbon);
        setupRibbon();
        mPopupView.removeAllViews();
        mPopupView.addView(ribbonView);
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    toggleRibbonView();
                    return true;
                }
                return false;
            }
        });
    }

    private void setupRibbon() {
        mRibbon.removeAllViews();
        if (mLocation.equals("bottom")) {
            mRibbon.addView(AokpRibbonHelper.getRibbon(mContext,
                shortTargets, longTargets, mText, mSize));
        } else {
            mRibbon.addView(AokpRibbonHelper.getVerticalRibbon(mContext,
                shortTargets, longTargets, mText, mSize));
            mRibbon.setPadding(0,0,0,0);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.NAVBAR]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.NAVBAR]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.NAVBAR]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.NAVBAR]), false, this);

        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        shortTargets = Settings.System.getString(cr,
                 Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.NAVBAR]);
        longTargets = Settings.System.getString(cr,
                 Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.NAVBAR]);
        mText = Settings.System.getBoolean(cr,
                 Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.NAVBAR], true);
        mSize = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.NAVBAR], 0);
        createRibbonView();
    }

    public class RibbonReceiver extends BroadcastReceiver {
        public static final String ACTION_TOGGLE_RIBBON = "com.android.systemui.ACTION_TOGGLE_RIBBON";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String location = intent.getStringExtra("action");
            if (ACTION_TOGGLE_RIBBON.equals(action)) {
                if (location.equals(mLocation)) {
                    toggleRibbonView();
                }
            }
        }
    }
}
