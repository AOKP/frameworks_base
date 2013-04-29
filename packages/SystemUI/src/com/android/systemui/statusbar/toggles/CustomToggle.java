
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

import java.util.ArrayList;

public class CustomToggle extends CustomToggleBase {

    private int mNumberOfActions;
    private int mCollapseShade;
    private int mCustomState= 0;
    private int mMatchState = 0;
    private int doubleClickCounter = 0;
    private boolean mActionRevert;

    public static final int toggleNumber = 0;

    private static final String KEY_TOGGLE_STATE = "toggle_state";

    private SettingsObserver mObserver = null;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        mObserver = new SettingsObserver(mHandler);
        mObserver.observe();
        SharedPreferences p = mContext.getSharedPreferences(KEY_TOGGLE_STATE, Context.MODE_PRIVATE);
        mCustomState = p.getInt("state", 0);
        startMagicTricks();
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                getToggle(toggleNumber);
                if (mActionRevert) {
                    mHandler.postDelayed(delayBootAction, 25000);
                }
            }
        }, new IntentFilter(Intent.ACTION_BOOT_COMPLETED));
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.cleanup();
    }

    final Runnable delayBootAction = new Runnable () {
        public void run() {
            mCustomState = 0;
            commitState();
            getAction(toggleNumber, mCustomState);
            startMagicTricks();
        }
    };

    final Runnable DelayShortPress = new Runnable () {
        public void run() {
            doubleClickCounter = 0;
            startCounting();
        }
    };

    final Runnable ResetDoubleClickCounter = new Runnable () {
        public void run() {
            doubleClickCounter = 0;
        }
    };

    private void checkForDoubleClick() {
        int mState = mCustomState;
        if (doubleClickCounter > 0) {
            mHandler.removeCallbacks(DelayShortPress);
            mCustomState = getDoubleAction(toggleNumber, mState);
            commitState();
            startMagicTricks();
            mHandler.postDelayed(ResetDoubleClickCounter, 20);
        } else {
            doubleClickCounter = doubleClickCounter + 1;
            mHandler.postDelayed(DelayShortPress, 300);
        }
    }

    private void startCounting() {
        getToggle(toggleNumber);
        int mState = mCustomState;
        mCustomState = getCounting(toggleNumber, mState);
        mMatchState = getMatch();
        commitState();
        getClickActions(toggleNumber, mCustomState, mMatchState);
        startMagicTricks();
    }

    private void commitState() {
        SharedPreferences p = mContext.getSharedPreferences(KEY_TOGGLE_STATE, Context.MODE_PRIVATE);
        p.edit().putInt("state", mCustomState).commit();
    }

    private void startMagicTricks() {
        String myText = toggleText(toggleNumber, mCustomState);
        Drawable myIcon = userIcon(toggleNumber, mCustomState);
        setLabel(myText);
        setIcon(myIcon);
        scheduleViewUpdate();
    }

    @Override
    public void onClick(View v) {
        getToggle(toggleNumber);
        boolean doubleClick = shouldDouble(toggleNumber);
        if (doubleClick) {
            checkForDoubleClick();
        } else {
            startCounting();
        }
    }
    @Override
    public boolean onLongClick(View v) {
        getLongActions(toggleNumber, mCustomState);
        return true;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.CUSTOM_PRESS_TOGGLE[toggleNumber]),
                    false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.CUSTOM_TOGGLE_ICONS[toggleNumber]),
                    false,
                    this);
            resolver.registerContentObserver(
                    Settings.System
                            .getUriFor(Settings.System.CUSTOM_TOGGLE_TEXT[toggleNumber]),
                    false,
                    this);
            resolver.registerContentObserver(
                    Settings.System
                            .getUriFor(Settings.System.CUSTOM_LONGPRESS_TOGGLE[toggleNumber]),
                    false,
                    this);

            startMagicTricks();
        }

        @Override
        public void onChange(boolean selfChange) {
            startMagicTricks();
        }
    }
}
