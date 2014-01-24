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

package com.android.systemui;

import android.animation.LayoutTransition;
import android.app.ActivityOptions;
import android.app.SearchManager;
import android.app.ActivityManagerNative;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Vibrator;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.EventLog;
import android.util.Slog;
import android.util.Log;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.FrameLayout;

import static com.android.internal.util.aokp.AwesomeConstants.*;

import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.aokp.NavRingHelpers;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.systemui.statusbar.phone.KeyguardTouchDelegate;

import com.android.systemui.EventLogTags;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.StatusBarPanel;

import java.util.ArrayList;

public class AOKPSearchPanelView extends FrameLayout implements
        StatusBarPanel, ActivityOptions.OnAnimationStartedListener {
    private static final int SEARCH_PANEL_HOLD_DURATION = 0;
    static final String TAG = "SearchPanelView";
    static final boolean DEBUG = PhoneStatusBar.DEBUG || false;
    public static final boolean DEBUG_GESTURES = true;

    private final Context mContext;
    private BaseStatusBar mBar;

    private boolean mShowing;
    private View mSearchTargetsContainer;
    private GlowPadView mGlowPadView;
    private IWindowManager mWm;
    private Resources mResources;
    private SettingsObserver mSettingsObserver;
    private ContentResolver mContentResolver;
    private String[] targetActivities = new String[5];
    private String[] longActivities = new String[5];
    private String[] customIcons = new String[5];
    private int startPosOffset;

    private int mNavRingAmount;
    private boolean mBoolLongPress;
    private boolean mSearchPanelLock;
    private int mTarget;
    private boolean mLongPress = false;

    //need to make an intent list and an intent counter
    String[] intent;
    ArrayList<String> intentList = new ArrayList<String>();
    ArrayList<String> longList = new ArrayList<String>();

    public AOKPSearchPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AOKPSearchPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        mResources = mContext.getResources();
        mContentResolver = mContext.getContentResolver();
        mSettingsObserver = new SettingsObserver(new Handler());
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        mContentResolver.unregisterContentObserver(mSettingsObserver);
        super.onDetachedFromWindow();
    }

    private void startAssistActivity() {
        if (!mBar.isDeviceProvisioned()) return;

        // Close Recent Apps if needed
        mBar.animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_SEARCH_PANEL);
        boolean isKeyguardShowing = false;
        try {
            isKeyguardShowing = mWm.isKeyguardLocked();
        } catch (RemoteException e) {

        }

        if (isKeyguardShowing) {
            // Have keyguard show the bouncer and launch the activity if the user succeeds.
            KeyguardTouchDelegate.getInstance(getContext()).showAssistant();
            onAnimationStarted();
        } else {
            // Otherwise, keyguard isn't showing so launch it from here.
            Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .getAssistIntent(mContext, true, UserHandle.USER_CURRENT);
            if (intent == null) return;

            try {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            } catch (RemoteException e) {
                // too bad, so sad...
            }

            try {
                ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                        R.anim.search_launch_enter, R.anim.search_launch_exit,
                        getHandler(), this);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(intent, opts.toBundle(),
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Activity not found for " + intent.getAction());
                onAnimationStarted();
            }
        }
    }

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
            }
        }
    }

    private H mHandler = new H();

    class GlowPadTriggerListener implements GlowPadView.OnTriggerListener {
        boolean mWaitingForLaunch;

       final Runnable SetLongPress = new Runnable () {
            public void run() {
                if (!mLongPress) {
                    vibrate();
                    mLongPress = true;
                }
            }
        };

        public void onGrabbed(View v, int handle) {
            mSearchPanelLock = false;
        }

        public void onReleased(View v, int handle) {
            if (!mSearchPanelLock && mLongPress) {
                mSearchPanelLock = true;
                if (shouldUnlock(longList.get(mTarget))) {
                    maybeSkipKeyguard();
                }
                AwesomeAction.launchAction(mContext, longList.get(mTarget));
                mBar.hideSearchPanel();
            }
        }

        public void onTargetChange(View v, final int target) {
            if (target == -1) {
                mHandler.removeCallbacks(SetLongPress);
                mLongPress = false;
            } else {
                if (mBoolLongPress && !TextUtils.isEmpty(longList.get(target)) && !longList.get(target).equals(AwesomeConstant.ACTION_NULL.value())) {
                    mTarget = target;
                    mHandler.postDelayed(SetLongPress, ViewConfiguration.getLongPressTimeout());
                }
            }
        }

        public void onGrabbedStateChange(View v, int handle) {
            if (!mWaitingForLaunch && OnTriggerListener.NO_HANDLE == handle) {
                mBar.hideSearchPanel();
                mHandler.removeCallbacks(SetLongPress);
                mLongPress = false;
            }
        }

        public void onTrigger(View v, final int target) {
            mTarget = target;
            if (!mLongPress) {
                if (AwesomeConstant.ACTION_ASSIST.equals(intentList.get(target))) {
                    startAssistActivity();
                } else {
                    if (shouldUnlock(intentList.get(target))) {
                        maybeSkipKeyguard();
                    }
                    AwesomeAction.launchAction(mContext, intentList.get(target));
                }
                mHandler.removeCallbacks(SetLongPress);
            }
        }

        public void onFinishFinalAnimation() {
        }
    }
    final GlowPadTriggerListener mGlowPadViewListener = new GlowPadTriggerListener();

    @Override
    public void onAnimationStarted() {
        postDelayed(new Runnable() {
            public void run() {
                mGlowPadViewListener.mWaitingForLaunch = false;
                mBar.hideSearchPanel();
            }
        }, SEARCH_PANEL_HOLD_DURATION);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSearchTargetsContainer = findViewById(R.id.search_panel_container);
        mGlowPadView = (GlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mGlowPadViewListener);

        updateSettings();
        setDrawables();
    }


    private boolean shouldUnlock(String action) {
        if (TextUtils.isEmpty(action))
            return false;

        if (action.equals(AwesomeConstant.ACTION_SILENT_VIB.value()) ||
            action.equals(AwesomeConstant.ACTION_VIB.value()) ||
            action.equals(AwesomeConstant.ACTION_POWER.value()) ||
            action.equals(AwesomeConstant.ACTION_TORCH.value()) ||
            action.equals(AwesomeConstant.ACTION_NOTIFICATIONS.value()) ||
            action.equals(AwesomeConstant.ACTION_SILENT.value())) {
            return false;
        }

        return true;
    }

    private void maybeSkipKeyguard() {
        try {
            if (mWm.isKeyguardSecure()) {
                KeyguardTouchDelegate.getInstance(getContext()).dismiss();
            }
        } catch (RemoteException e) {

        }
        Intent u = new Intent();
        u.setAction("com.android.lockscreen.ACTION_UNLOCK_RECEIVER");
        mContext.sendBroadcastAsUser(u, UserHandle.ALL);
    }

    private void setDrawables() {
        mLongPress = false;
        mSearchPanelLock = false;

        String tgtCenter = Settings.AOKP.getString(mContentResolver, Settings.AOKP.SYSTEMUI_NAVRING[0]);
        if (TextUtils.isEmpty(tgtCenter)) {
            Settings.AOKP.putString(mContentResolver, Settings.AOKP.SYSTEMUI_NAVRING[0], AwesomeConstant.ACTION_ASSIST.value());
        }

        // Custom Targets
        ArrayList<TargetDrawable> storedDraw = new ArrayList<TargetDrawable>();

        int endPosOffset = 0;
        int middleBlanks = 0;

        if (isScreenPortrait()) { // NavRing on Bottom
            startPosOffset =  1;
            endPosOffset =  (mNavRingAmount) + 1;
        } else {
            startPosOffset =  (Math.min(1,mNavRingAmount / 2)) + 2;
            endPosOffset =  startPosOffset - 1;
        }

        intentList.clear();
        longList.clear();

         int middleStart = mNavRingAmount;
         int tqty = middleStart;
         int middleFinish = 0;

         if (middleBlanks > 0) {
             middleStart = (tqty/2) + (tqty%2);
             middleFinish = (tqty/2);
         }

         // Add Initial Place Holder Targets
        for (int i = 0; i < startPosOffset; i++) {
            storedDraw.add(NavRingHelpers.getTargetDrawable(mContext, null));
            intentList.add(AwesomeConstant.ACTION_NULL.value());
            longList.add(AwesomeConstant.ACTION_NULL.value());
        }
        // Add User Targets
        for (int i = 0; i < middleStart; i++) {
            intentList.add(targetActivities[i]);
            longList.add(longActivities[i]);
            if (!TextUtils.isEmpty(customIcons[i])) {
                storedDraw.add(NavRingHelpers.getCustomDrawable(mContext, customIcons[i]));
            } else {
                storedDraw.add(NavRingHelpers.getTargetDrawable(mContext, targetActivities[i]));
            }
        }

        // Add middle Place Holder Targets
        for (int j = 0; j < middleBlanks; j++) {
            storedDraw.add(NavRingHelpers.getTargetDrawable(mContext, null));
            intentList.add(AwesomeConstant.ACTION_NULL.value());
            longList.add(AwesomeConstant.ACTION_NULL.value());
        }

        // Add Rest of User Targets for leftys
        for (int j = 0; j < middleFinish; j++) {
            int i = j + middleStart;
            intentList.add(targetActivities[i]);
            longList.add(longActivities[i]);
            if (!TextUtils.isEmpty(customIcons[i])) {
                storedDraw.add(NavRingHelpers.getCustomDrawable(mContext, customIcons[i]));
            } else {
                storedDraw.add(NavRingHelpers.getTargetDrawable(mContext, targetActivities[i]));
            }
        }

        // Add End Place Holder Targets
        for (int i = 0; i < endPosOffset; i++) {
            storedDraw.add(NavRingHelpers.getTargetDrawable(mContext, null));
            intentList.add(AwesomeConstant.ACTION_NULL.value());
            longList.add(AwesomeConstant.ACTION_NULL.value());
        }

        mGlowPadView.setTargetResources(storedDraw);
    }

    private void maybeSwapSearchIcon() {
        Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, false, UserHandle.USER_CURRENT);
        if (intent != null) {
            ComponentName component = intent.getComponent();
            if (component == null || !mGlowPadView.replaceTargetDrawablesIfPresent(component,
                    ASSIST_ICON_METADATA_NAME,
                    com.android.internal.R.drawable.ic_action_assist_generic)) {
                if (DEBUG) Slog.v(TAG, "Couldn't grab icon for component " + component);
            }
        }
    }

    private boolean pointInside(int x, int y, View v) {
        final int l = v.getLeft();
        final int r = v.getRight();
        final int t = v.getTop();
        final int b = v.getBottom();
        return x >= l && x < r && y >= t && y < b;
    }

    public boolean isInContentArea(int x, int y) {
        return pointInside(x, y, mSearchTargetsContainer);
    }

    private final OnPreDrawListener mPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        public boolean onPreDraw() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mGlowPadView.resumeAnimations();
            return false;
        }
    };

    private void vibrate() {

        if (Settings.System.getIntForUser(mContentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT) != 0) {
            Resources res = mContext.getResources();
            Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(res.getInteger(R.integer.config_search_panel_view_vibration_duration));
        }
    }

    private boolean hasValidTargets() {
        for (String target : targetActivities) {
            if (!TextUtils.isEmpty(target) && !target.equals(AwesomeConstant.ACTION_NULL.value())) {
                return true;
            }
        }
        return false;
    }

    public void show(final boolean show, boolean animate) {
        if (!show) {
            final LayoutTransition transitioner = animate ? createLayoutTransitioner() : null;
            ((ViewGroup) mSearchTargetsContainer).setLayoutTransition(transitioner);
        }
        mShowing = show;
        if (show && hasValidTargets()) {
            maybeSwapSearchIcon();
            if (getVisibility() != View.VISIBLE) {
                setVisibility(View.VISIBLE);
                // Don't start the animation until we've created the layer, which is done
                // right before we are drawn
                mGlowPadView.suspendAnimations();
                mGlowPadView.ping();
                getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
                vibrate();
            }
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        } else {
            setVisibility(View.INVISIBLE);
        }
    }

    public void hide(boolean animate) {
        if (mBar != null) {
            // This will indirectly cause show(false, ...) to get called
            mBar.animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
        } else {
            setVisibility(View.INVISIBLE);
        }
    }

    /**
     * We need to be aligned at the bottom.  LinearLayout can't do this, so instead,
     * let LinearLayout do all the hard work, and then shift everything down to the bottom.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // setPanelHeight(mSearchTargetsContainer.getHeight());
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Ignore hover events outside of this panel bounds since such events
        // generate spurious accessibility events with the panel content when
        // tapping outside of it, thus confusing the user.
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            return super.dispatchHoverEvent(event);
        }
        return true;
    }

    /**
     * Whether the panel is showing, or, if it's animating, whether it will be
     * when the animation is done.
     */
    public boolean isShowing() {
        return mShowing;
    }

    public void setBar(BaseStatusBar bar) {
        mBar = bar;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (DEBUG_GESTURES) {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                EventLog.writeEvent(EventLogTags.SYSUI_SEARCHPANEL_TOUCH,
                        event.getActionMasked(), (int) event.getX(), (int) event.getY());
            }
        }
        return super.onTouchEvent(event);
    }

    private LayoutTransition createLayoutTransitioner() {
        LayoutTransition transitioner = new LayoutTransition();
        transitioner.setDuration(200);
        transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
        return transitioner;
    }

    public boolean isAssistantAvailable() {
        return ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, false, UserHandle.USER_CURRENT) != null;
    }

    public boolean isScreenPortrait() {
        return mResources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    public class TargetObserver extends ContentObserver {
        public TargetObserver(Handler handler) {
            super(handler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            setDrawables();
            updateSettings();
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContentResolver.registerContentObserver(Settings.AOKP.getUriFor(
                    Settings.AOKP.SYSTEMUI_NAVRING_AMOUNT), false, this);
            mContentResolver.registerContentObserver(Settings.AOKP.getUriFor(
                    Settings.AOKP.SYSTEMUI_NAVRING_LONG_ENABLE), false, this);

            for (int i = 0; i < 5; i++) {
	            mContentResolver.registerContentObserver(
                    Settings.AOKP.getUriFor(Settings.AOKP.SYSTEMUI_NAVRING[i]), false, this);
                mContentResolver.registerContentObserver(
                    Settings.AOKP.getUriFor(Settings.AOKP.SYSTEMUI_NAVRING_LONG[i]), false, this);
                mContentResolver.registerContentObserver(
                    Settings.AOKP.getUriFor(Settings.AOKP.SYSTEMUI_NAVRING_ICON[i]), false, this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            setDrawables();
        }
    }

    public void updateSettings() {

        for (int i = 0; i < 5; i++) {
            targetActivities[i] = Settings.AOKP.getString(
                    mContentResolver, Settings.AOKP.SYSTEMUI_NAVRING[i]);
            longActivities[i] = Settings.AOKP.getString(
                    mContentResolver, Settings.AOKP.SYSTEMUI_NAVRING_LONG[i]);
            customIcons[i] = Settings.AOKP.getString(
                    mContentResolver, Settings.AOKP.SYSTEMUI_NAVRING_ICON[i]);
        }

        mBoolLongPress = (Settings.AOKP.getBoolean(mContentResolver,
                Settings.AOKP.SYSTEMUI_NAVRING_LONG_ENABLE, false));

        mNavRingAmount = Settings.AOKP.getInt(mContentResolver,
                         Settings.AOKP.SYSTEMUI_NAVRING_AMOUNT, 1);

    }
}
