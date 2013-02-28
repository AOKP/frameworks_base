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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.ActivityInfo;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Vibrator;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.PowerManager;
import android.os.Process;
import android.os.ServiceManager;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.systemui.R;
import com.android.systemui.recent.StatusBarTouchProxy;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.statusbar.tablet.TabletStatusBar;
import com.android.systemui.aokp.AwesomeAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.net.URISyntaxException;

public class SearchPanelView extends FrameLayout implements
        StatusBarPanel, ActivityOptions.OnAnimationStartedListener {
    private static final int SEARCH_PANEL_HOLD_DURATION = 0;
    static final String TAG = "SearchPanelView";
    static final boolean DEBUG = TabletStatusBar.DEBUG || PhoneStatusBar.DEBUG || false;
    private static final String ASSIST_ICON_METADATA_NAME =
            "com.android.systemui.action_assist_icon";
    private final Context mContext;
    private BaseStatusBar mBar;
    private StatusBarTouchProxy mStatusBarTouchProxy;

    private boolean mShowing;
    private View mSearchTargetsContainer;
    private GlowPadView mGlowPadView;
    private IWindowManager mWm;

    private PackageManager mPackageManager;
    private Resources mResources;
    private SettingsObserver mSettingsObserver;
    private TargetObserver mTargetObserver;
    private ContentResolver mContentResolver;
    private String[] targetActivities = new String[5];
    private String[] longActivities = new String[5];
    private int startPosOffset;

    private int mNavRingAmount;
    private int mCurrentUIMode;
    private boolean mLefty;
    private boolean mLongPress;
    private boolean mSearchPanelLock;
    private int mTarget;

    //need to make an intent list and an intent counter
    String[] intent;
    ArrayList<String> intentList = new ArrayList<String>();
    ArrayList<String> longList = new ArrayList<String>();
    String mEmpty = "**assist**";

    public SearchPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        mPackageManager = mContext.getPackageManager();
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
            try {
                mWm.showAssistant();
            } catch (RemoteException e) {
                // too bad, so sad...
            }
            onAnimationStarted();
        } else {
            // Otherwise, keyguard isn't showing so launch it from here.
            Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .getAssistIntent(mContext, UserHandle.USER_CURRENT);
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
                Slog.w(TAG, "Activity not found for " + intent.getAction());
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
                if (!mSearchPanelLock) {
                    mLongPress = true;
                    Log.d(TAG,"LongPress!");
                    mBar.hideSearchPanel();
                    maybeSkipKeyguard();
                    AwesomeAction.launchAction(mContext, longList.get(mTarget));
                    mSearchPanelLock = true;
                 }
            }
        };

        public void onGrabbed(View v, int handle) {
            mSearchPanelLock = false;
        }

        public void onReleased(View v, int handle) {
        }

        public void onTargetChange(View v, final int target) {
            if (target == -1) {
                mHandler.removeCallbacks(SetLongPress);
                mLongPress = false;
            } else {
                if (longList.get(target) == null || longList.get(target).equals("") || longList.get(target).equals("**null**")) {
                //pretend like nothing happened
                } else {
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
                    maybeSkipKeyguard();
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
        mStatusBarTouchProxy = (StatusBarTouchProxy) findViewById(R.id.status_bar_touch_proxy);
        // TODO: fetch views
        mGlowPadView = (GlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mGlowPadViewListener);

        updateSettings();
        setDrawables();
    }

    private void maybeSkipKeyguard() {
        try {
            if (mWm.isKeyguardLocked() && !mWm.isKeyguardSecure()) {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            }
        } catch (RemoteException ignored) {
        }
    }

    private void setDrawables() {
        mLongPress = false;
        mSearchPanelLock = false;
        String target3 = Settings.System.getString(mContext.getContentResolver(), Settings.System.SYSTEMUI_NAVRING[0]);
        if (target3 == null || target3.equals("")) {
            Settings.System.putString(mContext.getContentResolver(), Settings.System.SYSTEMUI_NAVRING[0], "**assist**");
        }

        // Custom Targets
        ArrayList<TargetDrawable> storedDraw = new ArrayList<TargetDrawable>();

        int endPosOffset = 0;
        int middleBlanks = 0;

        switch (mCurrentUIMode) {
            case 0 : // Phone Mode
                if (isScreenPortrait()) { // NavRing on Bottom
                    startPosOffset =  1;
                    endPosOffset =  (mNavRingAmount) + 1;
                } else if (mLefty) { // either lefty or... (Ring is actually on right side of screen)
                        startPosOffset =  1 - (mNavRingAmount % 2);
                        middleBlanks = mNavRingAmount + 2;
                        endPosOffset = 0;

                } else { // righty... (Ring actually on left side of tablet)
                    startPosOffset =  (Math.min(1,mNavRingAmount / 2)) + 2;
                    endPosOffset =  startPosOffset - 1;
                }
                break;
            case 1 : // Tablet Mode
                if (mLefty) { // either lefty or... (Ring is actually on right side of screen)
                    startPosOffset =  (mNavRingAmount) + 1;
                    endPosOffset =  (mNavRingAmount *2) + 1;
                } else { // righty... (Ring actually on left side of tablet)
                    startPosOffset =  1;
                    endPosOffset = (mNavRingAmount * 3) + 1;
                }
                break;
            case 2 : // Phablet Mode - Search Ring stays at bottom
                startPosOffset =  1;
                endPosOffset =  (mNavRingAmount) + 1;
                break;
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
            storedDraw.add(getTargetDrawable(""));
            intentList.add(mEmpty);
            longList.add(mEmpty);
        }
        // Add User Targets
        for (int i = 0; i < middleStart; i++) {
            intentList.add(targetActivities[i]);
            longList.add(longActivities[i]);
            storedDraw.add(getTargetDrawable(targetActivities[i]));
        }

        // Add middle Place Holder Targets
        for (int j = 0; j < middleBlanks; j++) {
            storedDraw.add(getTargetDrawable(""));
            intentList.add(mEmpty);
            longList.add(mEmpty);
        }

        // Add Rest of User Targets for leftys
        for (int j = 0; j < middleFinish; j++) {
            int i = j + middleStart;
            intentList.add(targetActivities[i]);
            longList.add(longActivities[i]);
            storedDraw.add(getTargetDrawable(targetActivities[i]));
        }

        // Add End Place Holder Targets
        for (int i = 0; i < endPosOffset; i++) {
            storedDraw.add(getTargetDrawable(""));
            intentList.add(mEmpty);
            longList.add(mEmpty);
        }

        mGlowPadView.setTargetResources(storedDraw);
    }

    private TargetDrawable getTargetDrawable (String action){

        TargetDrawable cDrawable = new TargetDrawable(mResources, mResources.getDrawable(com.android.internal.R.drawable.ic_lockscreen_camera));
        cDrawable.setEnabled(false);

        if (action == null || action.equals("") || action.equals("**null**"))
            return cDrawable;
        if (action.equals("**ime**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_ime_switcher));
        if (action.equals("**ring_vib**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_vib));
        if (action.equals("**ring_silent**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_silent));
        if (action.equals("**ring_vib_silent**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_ring_vib_silent));
        if (action.equals("**kill**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_killtask));
        if (action.equals("**lastapp**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_lastapp));
        if (action.equals("**power**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_power));
        if (action.equals("**screenoff**"))
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_power));
        if (action.equals("**assist**"))
            return new TargetDrawable(mResources, com.android.internal.R.drawable.ic_action_assist_generic);
        try {
            Intent in = Intent.parseUri(action, 0);
            ActivityInfo aInfo = in.resolveActivityInfo(mPackageManager, PackageManager.GET_ACTIVITIES);
            Drawable activityIcon = aInfo.loadIcon(mPackageManager);
            Drawable iconBg = mResources.getDrawable(R.drawable.ic_navbar_blank);
            Drawable iconBgActivated = mResources.getDrawable(R.drawable.ic_navbar_blank_activated);
            int margin = (int)(iconBg.getIntrinsicHeight() / 3);
            LayerDrawable icon = new LayerDrawable (new Drawable[] {iconBg, activityIcon});
            icon.setLayerInset(1, margin, margin, margin, margin);
            LayerDrawable iconActivated = new LayerDrawable (new Drawable[] {iconBgActivated, activityIcon});
            iconActivated.setLayerInset(1, margin, margin, margin, margin);
            StateListDrawable selector = new StateListDrawable();
            selector.addState(new int[] {android.R.attr.state_enabled, -android.R.attr.state_active, -android.R.attr.state_focused}, icon);
            selector.addState(new int[] {android.R.attr.state_enabled, android.R.attr.state_active, -android.R.attr.state_focused}, iconActivated);
            selector.addState(new int[] {android.R.attr.state_enabled, -android.R.attr.state_active, android.R.attr.state_focused}, iconActivated);
            return new TargetDrawable(mResources, selector);
        } catch (Exception e) {
            return cDrawable;
        }
    }

    private void maybeSwapSearchIcon() {
        Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, UserHandle.USER_CURRENT);
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
        if (pointInside(x, y, mSearchTargetsContainer)) {
            return true;
        } else if (mStatusBarTouchProxy != null &&
                pointInside(x, y, mStatusBarTouchProxy)) {
            return true;
        } else {
            return false;
        }
    }

    private final OnPreDrawListener mPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        public boolean onPreDraw() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mGlowPadView.resumeAnimations();
            return false;
        }
    };
    private void vibrate() {
        Context context = getContext();
        if (Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT) != 0) {
            Resources res = context.getResources();
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(res.getInteger(R.integer.config_search_panel_view_vibration_duration));
        }
    }

    public void show(final boolean show, boolean animate) {
        if (!show) {
            final LayoutTransition transitioner = animate ? createLayoutTransitioner() : null;
            ((ViewGroup) mSearchTargetsContainer).setLayoutTransition(transitioner);
        }
        mShowing = show;
        if (show) {
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

    public void setStatusBarView(final View statusBarView) {
        if (mStatusBarTouchProxy != null) {
            mStatusBarTouchProxy.setStatusBar(statusBarView);
//            mGlowPadView.setOnTouchListener(new OnTouchListener() {
//                public boolean onTouch(View v, MotionEvent event) {
//                    return statusBarView.onTouchEvent(event);
//                }
//            });
        }
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
                .getAssistIntent(mContext, UserHandle.USER_CURRENT) != null;
    }
    public int screenLayout() {
        final int screenSize = Resources.getSystem().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        return screenSize;
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
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_LEFTY_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SYSTEMUI_NAVRING_AMOUNT), false, this);

            for (int i = 0; i < 5; i++) {
	            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SYSTEMUI_NAVRING[i]), false, this);
	            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SYSTEMUI_NAVRING_LONG[i]), false, this);
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
            targetActivities[i] = Settings.System.getString(
                    mContext.getContentResolver(), Settings.System.SYSTEMUI_NAVRING[i]);
            longActivities[i] = Settings.System.getString(
                    mContext.getContentResolver(), Settings.System.SYSTEMUI_NAVRING_LONG[i]);
        }

        mLefty = (Settings.System.getBoolean(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_LEFTY_MODE, false));

        mNavRingAmount = Settings.System.getInt(mContext.getContentResolver(),
                         Settings.System.SYSTEMUI_NAVRING_AMOUNT, 1);
        // Not using getBoolean here, because CURRENT_UI_MODE can be 0,1 or 2
        mCurrentUIMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.CURRENT_UI_MODE, 0);
    }
}
