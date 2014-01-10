/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.keyguard;

import java.util.ArrayList;

import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.HorizontalListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.aokp.RibbonAdapter;
import com.android.internal.util.aokp.RibbonAdapter.RibbonItem;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;

public class KeyguardSelectorView extends LinearLayout implements KeyguardSecurityView, OnItemClickListener, OnItemLongClickListener {
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final String TAG = "SecuritySelectorView";
    private static final String ASSIST_ICON_METADATA_NAME =
        "com.android.systemui.action_assist_icon";

    private KeyguardSecurityCallback mCallback;
    private GlowPadView mGlowPadView;
    private ObjectAnimator mAnim;
    private View mFadeView;
    private boolean mIsBouncing;
    private boolean mCameraDisabled;
    private boolean mSearchDisabled;
    private LockPatternUtils mLockPatternUtils;
    private SecurityMessageDisplay mSecurityMessageDisplay;
    private Drawable mBouncerFrame;
    private ArrayList<RibbonItem> mItems = new ArrayList<RibbonItem>();
    private RibbonAdapter mRibbonAdapter;
    private HorizontalListView mRibbon;
    private UnlockReceiver mUnlockReceiver;
    private IntentFilter mUnlockFilter;
    private float mBatteryLevel;
    private GestureDetector mDoubleTapGesture;

    OnTriggerListener mOnTriggerListener = new OnTriggerListener() {

        public void onTrigger(View v, int target) {
            final int resId = mGlowPadView.getResourceIdForTarget(target);

            switch (resId) {
                case R.drawable.ic_action_assist_generic:
                    Intent assistIntent =
                            ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                            .getAssistIntent(mContext, true, UserHandle.USER_CURRENT);
                    if (assistIntent != null) {
                        mActivityLauncher.launchActivity(assistIntent, false, true, null, null);
                    } else {
                        Log.w(TAG, "Failed to get intent for assist activity");
                    }
                    mCallback.userActivity(0);
                    break;

                case R.drawable.ic_lockscreen_camera:
                    mActivityLauncher.launchCamera(null, null);
                    mCallback.userActivity(0);
                    break;

                case R.drawable.ic_lockscreen_unlock_phantom:
                case R.drawable.ic_lockscreen_unlock:
                    mCallback.userActivity(0);
                    mCallback.dismiss(false);
                break;
            }
        }

        public void onReleased(View v, int handle) {
            if (!mIsBouncing) {
                doTransition(mFadeView, 1.0f);
            }
        }

        public void onGrabbed(View v, int handle) {
            mCallback.userActivity(0);
            doTransition(mFadeView, 0.0f);
        }

        public void onGrabbedStateChange(View v, int handle) {

        }

        public void onFinishFinalAnimation() {

        }

        public void onTargetChange(View v, final int target) {

        }

    };

    KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onDevicePolicyManagerStateChanged() {
            updateTargets();
        }

        @Override
        public void onSimStateChanged(State simState) {
            updateTargets();
        }

        @Override
        public void onRefreshBatteryInfo(KeyguardUpdateMonitor.BatteryStatus batStatus) {
            updateLockscreenBattery(batStatus);
        }
    };

    private final KeyguardActivityLauncher mActivityLauncher = new KeyguardActivityLauncher() {

        @Override
        KeyguardSecurityCallback getCallback() {
            return mCallback;
        }

        @Override
        LockPatternUtils getLockPatternUtils() {
            return mLockPatternUtils;
        }

        @Override
        Context getContext() {
            return mContext;
        }};

    public KeyguardSelectorView(Context context) {
        this(context, null);
    }

    public KeyguardSelectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLockPatternUtils = new LockPatternUtils(getContext());
        if (mUnlockFilter == null) {
            mUnlockFilter = new IntentFilter();
            mUnlockFilter.addAction(UnlockReceiver.ACTION_UNLOCK_RECEIVER);
        }
        if (mUnlockReceiver == null) mUnlockReceiver = new UnlockReceiver();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mGlowPadView = (GlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mOnTriggerListener);
        mRibbon = (HorizontalListView) findViewById(R.id.ribbon_list);
        updateTargets();
        mItems.clear();
        ArrayList<String> list = Settings.AOKP.getArrayList(mContext.getContentResolver(), Settings.AOKP.AOKP_LOCKSCREEN_RIBBON[AokpRibbonHelper.HORIZONTAL_RIBBON_ITEMS]);
        for (String item : list) {
            mItems.add(new RibbonItem(item));
        }
        mRibbonAdapter = new RibbonAdapter(mContext, mItems);
        mRibbonAdapter.setOrientation(false);
        int size = Settings.AOKP.getInt(mContext.getContentResolver(), Settings.AOKP.AOKP_LOCKSCREEN_RIBBON[AokpRibbonHelper.HORIZONTAL_RIBBON_SIZE], 30);
        int newSize = (int) (((size * 0.01f) * 150) + 150);
        mRibbonAdapter.setSize(newSize);
        ViewGroup.LayoutParams params = mRibbon.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        params.height = newSize;
        mRibbon.setLayoutParams(params);
        //    mRibbon.setMargin(100);
        mRibbon.setAdapter(mRibbonAdapter);
        mRibbon.setOnItemClickListener(this);
        mRibbon.setOnItemLongClickListener(this);
        mRibbon.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mCallback.userActivity(0);
                return false;
            }
        });

        mSecurityMessageDisplay = new KeyguardMessageArea.Helper(this);
        View bouncerFrameView = findViewById(R.id.keyguard_selector_view_frame);
        mBouncerFrame = bouncerFrameView.getBackground();

        mDoubleTapGesture = new GestureDetector(mContext,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                if (pm != null) pm.goToSleep(e.getEventTime());
                return true;
            }
        });

        if (Settings.AOKP.getInt(mContext.getContentResolver(),
                    Settings.AOKP.DOUBLE_TAP_SLEEP_GESTURE, 0) == 1) {
            mGlowPadView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return mDoubleTapGesture.onTouchEvent(event);
                }
            });
        }
    }

    public void setCarrierArea(View carrierArea) {
        mFadeView = carrierArea;
    }

    public boolean isTargetPresent(int resId) {
        return mGlowPadView.getTargetPosition(resId) != -1;
    }

    @Override
    public void showUsabilityHint() {
        mGlowPadView.ping();
    }

    private void updateTargets() {
        int currentUserHandle = mLockPatternUtils.getCurrentUser();
        DevicePolicyManager dpm = mLockPatternUtils.getDevicePolicyManager();
        int disabledFeatures = dpm.getKeyguardDisabledFeatures(null, currentUserHandle);
        boolean secureCameraDisabled = mLockPatternUtils.isSecure()
                && (disabledFeatures & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) != 0;
        boolean cameraDisabledByAdmin = dpm.getCameraDisabled(null, currentUserHandle)
                || secureCameraDisabled;
        final KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(getContext());
        boolean disabledBySimState = monitor.isSimLocked();
        boolean cameraTargetPresent =
            isTargetPresent(R.drawable.ic_lockscreen_camera);
        boolean searchTargetPresent =
            isTargetPresent(R.drawable.ic_action_assist_generic);

        if (cameraDisabledByAdmin) {
            Log.v(TAG, "Camera disabled by Device Policy");
        } else if (disabledBySimState) {
            Log.v(TAG, "Camera disabled by Sim State");
        }
        boolean currentUserSetup = 0 != Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE,
                0 /*default */,
                currentUserHandle);
        boolean searchActionAvailable =
                ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, false, UserHandle.USER_CURRENT) != null;
        mCameraDisabled = cameraDisabledByAdmin || disabledBySimState || !cameraTargetPresent
                || !currentUserSetup;
        mSearchDisabled = disabledBySimState || !searchActionAvailable || !searchTargetPresent
                || !currentUserSetup;
        updateResources();
        updateLockscreenBattery(null);
    }

    public void updateResources() {
        // Update the search icon with drawable from the search .apk
        if (!mSearchDisabled) {
            Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .getAssistIntent(mContext, false, UserHandle.USER_CURRENT);
            if (intent != null) {
                // XXX Hack. We need to substitute the icon here but haven't formalized
                // the public API. The "_google" metadata will be going away, so
                // DON'T USE IT!
                ComponentName component = intent.getComponent();
                boolean replaced = mGlowPadView.replaceTargetDrawablesIfPresent(component,
                        ASSIST_ICON_METADATA_NAME + "_google", R.drawable.ic_action_assist_generic);

                if (!replaced && !mGlowPadView.replaceTargetDrawablesIfPresent(component,
                            ASSIST_ICON_METADATA_NAME, R.drawable.ic_action_assist_generic)) {
                        Slog.w(TAG, "Couldn't grab icon from package " + component);
                }
            }
        }

        mGlowPadView.setEnableTarget(R.drawable.ic_lockscreen_camera, !mCameraDisabled);
        mGlowPadView.setEnableTarget(R.drawable.ic_action_assist_generic, !mSearchDisabled);
    }

    void doTransition(View view, float to) {
        if (mAnim != null) {
            mAnim.cancel();
        }
        mAnim = ObjectAnimator.ofFloat(view, "alpha", to);
        mAnim.start();
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    @Override
    public void reset() {
        mGlowPadView.reset(false);
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void onPause() {
        KeyguardUpdateMonitor.getInstance(getContext()).removeCallback(mUpdateCallback);
    }

    @Override
    public void onResume(int reason) {
        KeyguardUpdateMonitor.getInstance(getContext()).registerCallback(mUpdateCallback);
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void showBouncer(int duration) {
        mIsBouncing = true;
        KeyguardSecurityViewHelper.
                showBouncer(mSecurityMessageDisplay, mFadeView, mBouncerFrame, duration);
    }

    @Override
    public void hideBouncer(int duration) {
        mIsBouncing = false;
        KeyguardSecurityViewHelper.
                hideBouncer(mSecurityMessageDisplay, mFadeView, mBouncerFrame, duration);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.unregisterReceiver(mUnlockReceiver);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContext.registerReceiver(mUnlockReceiver, mUnlockFilter);
    }
    public class UnlockReceiver extends BroadcastReceiver {
        public static final String ACTION_UNLOCK_RECEIVER = "com.android.lockscreen.ACTION_UNLOCK_RECEIVER";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_UNLOCK_RECEIVER)) {
                mCallback.userActivity(0);
                mCallback.dismiss(false);
            }
        }
    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String action = ((RibbonItem) mRibbonAdapter.getItem(position)).mShortAction;
        mCallback.userActivity(0);
        mCallback.dismiss(false);
        AwesomeAction.launchAction(mContext, action);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        String action = ((RibbonItem) mRibbonAdapter.getItem(position)).mLongAction;
        mCallback.userActivity(0);
        mCallback.dismiss(false);
        AwesomeAction.launchAction(mContext, action);
        return true;
    }

    public void updateLockscreenBattery(KeyguardUpdateMonitor.BatteryStatus status) {
        if (Settings.AOKP.getIntForUser(
                mContext.getContentResolver(),
                Settings.AOKP.BATTERY_ARC_LOCKSCREEN_HANDLE,
                0 /*default */,
                UserHandle.USER_CURRENT) == 1) {
            if (status != null) mBatteryLevel = status.level;
            float cappedBattery = mBatteryLevel;

            if (mBatteryLevel < 15) {
                cappedBattery = 15;
            }
            else if (mBatteryLevel > 90) {
                cappedBattery = 90;
            }

            final float hue = (cappedBattery - 15) * 1.6f;
            mGlowPadView.setArc(mBatteryLevel * 3.6f, Color.HSVToColor(0x80, new float[]{ hue, 1.f, 1.f }));
        } else {
            mGlowPadView.setArc(0, 0);
        }
    }
}
