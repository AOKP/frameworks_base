
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.aokp.NavBarHelpers;
import com.android.systemui.R;

import java.io.File;
import java.io.IOException;

public class CustomToggle extends BaseToggle {

    public String[] mClickActions = new String[3];
    public String[] mLongActions = new String[3];
    public String[] mToggleIcons = new String[3];
    public String[] mToggleText = new String[3];

    private int mDoubleClick;
    private int mNumberOfActions;
    private int mCustomState;
    private int doubleClickCounter = 0;
    private boolean mActionRevert;
    private boolean mAdvancedToggle;

    public static final int NO_ACTION = 0;
    public static final int REVERSE_ONE = 1;
    public static final int STATE_ONE = 2;

    public final static String[] StockClickActions = {
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value() };

    private SettingsObserver mObserver = null;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        mObserver = new SettingsObserver(mHandler);
        mObserver.observe();
        startMagicTricks();
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                mHandler.postDelayed(delayBootAction, 25000);
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
            if (mActionRevert) {
                mCustomState = 0;
                startActions();
            }
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

    private void startCounting() {
        if (mCustomState < mNumberOfActions-1) {
            mCustomState += 1;
        } else {
            mCustomState = 0;
        }
        startActions();
    }

    private void startReverse() {
        if (mCustomState > 0) {
            mCustomState--;
        } else {
            mCustomState = mNumberOfActions-1;
        }
        startActions();
    }

    private void startActions() {
        if (mAdvancedToggle) {
        AwesomeAction.launchAction(mContext, mClickActions[mCustomState]);
        }
        startMagicTricks();
    }

    private void startMagicTricks() {
        final Resources r = mContext.getResources();
        String iconUri = "";
        Drawable myIcon = null;
        String toggleText = mToggleText[mCustomState];
        iconUri = mToggleIcons[mCustomState];
        if (iconUri != null && iconUri.length() > 0) {
            File f = new File(Uri.parse(iconUri).getPath());
            if (f.exists()) {
                myIcon = new BitmapDrawable(mContext.getResources(), f.getAbsolutePath());
            }
        } else {
            myIcon = NavBarHelpers.getIconImage(mContext, mClickActions[mCustomState]);
        }
    setLabel(toggleText);
    setIcon(myIcon);
    scheduleViewUpdate();
    };

    @Override
    public void onClick(View v) {
            switch (mDoubleClick) {
                case NO_ACTION:
                    startCounting();
                    break;
                case REVERSE_ONE:
                    if (doubleClickCounter > 0) {
                        mHandler.removeCallbacks(DelayShortPress);
                        startReverse();
                        mHandler.postDelayed(ResetDoubleClickCounter, 20);
                    } else {
                        doubleClickCounter = doubleClickCounter + 1;
                        mHandler.postDelayed(DelayShortPress, 400);
                    }
                    break;
                case STATE_ONE:
                    if (doubleClickCounter > 0) {
                        mHandler.removeCallbacks(DelayShortPress);
                        mCustomState = 0;
                        startActions();
                        mHandler.postDelayed(ResetDoubleClickCounter, 20);
                    } else {
                        doubleClickCounter = doubleClickCounter + 1;
                        mHandler.postDelayed(DelayShortPress, 400);
                    }
                    break;
            }
    }
    @Override
    public boolean onLongClick(View v) {
        if (mAdvancedToggle) {
        AwesomeAction.launchAction(mContext, mLongActions[mCustomState]);
        } else {
        AwesomeAction.launchAction(mContext, mClickActions[mCustomState]);
        }
        return true;
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        final Resources r = mContext.getResources();

        String mDefaultText = "CUSTOM";

        mCustomState = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_TOGGLE_STATE, 0);

        mActionRevert = Settings.System.getBoolean(resolver,
                Settings.System.CUSTOM_TOGGLE_REVERT, false);

        mAdvancedToggle = Settings.System.getBoolean(resolver,
                Settings.System.CUSTOM_TOGGLE_ADVANCED, false);

        mNumberOfActions = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_TOGGLE_QTY, 3);

        mDoubleClick = Settings.System.getInt(resolver,
                Settings.System.DCLICK_TOGGLE_REVERT, 0);

        for (int j = 0; j < 3; j++) {
            mClickActions[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_PRESS_TOGGLE[j]);
            if (mClickActions[j] == null) {
                mClickActions[j] = StockClickActions[j];
                Settings.System.putString(resolver,
                        Settings.System.CUSTOM_PRESS_TOGGLE[j], mClickActions[j]);
            }

            mLongActions[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_LONGPRESS_TOGGLE[j]);
            if (mLongActions[j] == null) {
                mLongActions[j] = StockClickActions[j];
                Settings.System.putString(resolver,
                        Settings.System.CUSTOM_LONGPRESS_TOGGLE[j], mLongActions[j]);
            }

            mToggleIcons[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_TOGGLE_ICONS[j]);
            mToggleText[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_TOGGLE_TEXT[j]);
            if (mToggleText[j] == null) {
                mToggleText[j] = mDefaultText;
                Settings.System.putString(resolver,
                        Settings.System.CUSTOM_TOGGLE_TEXT[j], mToggleText[j]);
            }
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_REVERT),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_ADVANCED),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_QTY),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.DCLICK_TOGGLE_REVERT),
                    false, this);

            for (int j = 0; j < 3; j++) {
                resolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.CUSTOM_PRESS_TOGGLE[j]),
                        false,
                        this);
                resolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.CUSTOM_TOGGLE_ICONS[j]),
                        false,
                        this);
                resolver.registerContentObserver(
                        Settings.System
                                .getUriFor(Settings.System.CUSTOM_TOGGLE_TEXT[j]),
                        false,
                        this);

                resolver.registerContentObserver(
                        Settings.System
                                .getUriFor(Settings.System.CUSTOM_LONGPRESS_TOGGLE[j]),
                        false,
                        this);
            }
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
}
