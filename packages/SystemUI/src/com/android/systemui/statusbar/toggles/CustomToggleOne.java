
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.R;

import java.io.File;
import java.io.IOException;

public class CustomToggleOne extends CustomToggleBase {

    private int mNumberOfActions;
    private int mCollapseShade;
    private int mCustomState= 0;
    private int mMatchState = 0;
    private int doubleClickCounter = 0;
    private boolean mActionRevert;

    public static final int toggleNumber = 0;
    public static final int toggleMin = 0;
    public static final int toggleMax = 5;

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
                if (mActionRevert[toggleNumber]) {
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
            getAction(mCustomState);
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
            mCustomState = getDoubleAction(toggleNumber, mState, toggleMin, toggleMax);
            commitState();
            startMagicTricks();
            mHandler.postDelayed(ResetDoubleClickCounter, 20);
        } else {
            doubleClickCounter = doubleClickCounter + 1;
            mHandler.postDelayed(DelayShortPress, 300);
        }
    }

    private void startCounting() {
        int mState = mCustomState;
        mCustomState = getCounting(toggleNumber, mState, toggleMin, toggleMax);
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
        String toggleText = mToggleText[mCustomState];
        Drawable myIcon = userIcon(mCustomState);
        setLabel(toggleText);
        setIcon(myIcon);
        scheduleViewUpdate();
    }

    @Override
    public void onClick(View v) {
        boolean doubleClick = shouldDouble();
        if (doubleClick) {
            checkForDoubleClick();
        } else {
            startCounting();
        }
    }
    @Override
    public boolean onLongClick(View v) {
        getLongActions(toggleNumber, mCustomState)
        return true;
    }
