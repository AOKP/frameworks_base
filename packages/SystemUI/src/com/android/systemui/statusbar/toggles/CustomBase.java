
package com.android.systemui.statusbar.toggles;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
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
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import static com.android.internal.util.aokp.AwesomeConstants.*;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.R;

import java.io.File;
import java.util.ArrayList;

public abstract class CustomBase extends BaseToggle {

    public static final String TAG = "CustomBase";

    public String[] mClickActions = new String[5];
    public String[] mLongActions = new String[5];
    public String[] mToggleIcons = new String[5];
    public String[] mToggleText = new String[5];

    private int mDoubleClick;
    private int mToggleNum;
    private int mNumberOfActions;
    private int mCollapseShade;
    private int toggleState = 0;
    private int mMatchState = 0;
    private int doubleClickCounter = 0;
    private boolean mBootAction;
    private boolean mMatchAction;

    protected int mStyle;

    private Drawable mIconDrawable = null;
    private CharSequence mLabelText = null;
    private int mTextSize = 12;

    protected CompoundButton mToggleButton = null;
    protected TextView mLabel = null;
    protected ImageView mIcon = null;
    private int mIconId = -1;

    public static final int NO_ACTION = 0;
    public static final int REVERSE_ONE = 1;
    public static final int STATE_ONE = 2;
    public static final int SKIP_BACK = 3;
    public static final int SKIP_FORWARD = 4;

    public static final int NO_COLLAPSE = 10;
    public static final int ON_CLICK = 11;
    public static final int ON_LONG = 12;
    public static final int ON_BOTH = 13;

    public final static String[] StockClickActions = {
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value() };

    private SettingsObserver mObserver = null;

    public void getToggle(int toggleNum) {
    mToggleNum = toggleNum;
    }
    public boolean shouldBootAction(int toggleNum) {
        getToggle(toggleNum);
        return mBootAction;
    }

    public void launchAction(int toggleNum, int state) {
        getToggle(toggleNum);
        AwesomeAction.launchAction(mContext, "**null**".equals(mClickActions[state])
                ? mLongActions[state] : mClickActions[state]);
    }

    public void launchClick(int state) {
        if (mMatchAction) {
            AwesomeAction.launchAction(mContext, mClickActions[mMatchState]);
        } else {
            AwesomeAction.launchAction(mContext, mClickActions[state]);
        }
        shouldCollapse();
    }

    public void launchLongClick(int toggleNum, int state) {
        getToggle(toggleNum);
        AwesomeAction.launchAction(mContext, mLongActions[state]);
        switch (mCollapseShade) {
            case NO_COLLAPSE:
            case ON_CLICK:
                break;
            case ON_LONG:
            case ON_BOTH:
                collapseStatusBar();
                break;
        }
    }

    public int startCounting(int toggleNum, int state) {
        getToggle(toggleNum);
        if (state < mNumberOfActions-1) {
            state += 1;
            mMatchState = state-1;
        } else {
            state = 0;
            mMatchState = mNumberOfActions-1;
        }
        launchClick(state);
        return state;
    }

    public int startDoubleAction(int toggleNum, int state) {
        getToggle(toggleNum);
        switch (mDoubleClick) {
            case REVERSE_ONE:
                if (state > 0) {
                    state--;
                } else {
                    state = mNumberOfActions-1;
                }
                AwesomeAction.launchAction(mContext, mClickActions[state]);
                shouldCollapse();
                break;
            case STATE_ONE:
                state = 0;
                AwesomeAction.launchAction(mContext, mClickActions[state]);
                shouldCollapse();
                break;
            case SKIP_BACK:
                if (state > 0) {
                    state--;
                } else {
                    state = mNumberOfActions-1;
                }
                break;
            case SKIP_FORWARD:
                if (state < mNumberOfActions-1) {
                    state += 1;
                } else {
                    state = 0;
                }
                break;
        }
        return state;
    }

    private void shouldCollapse() {
        switch (mCollapseShade) {
            case NO_COLLAPSE:
            case ON_LONG:
                break;
            case ON_CLICK:
            case ON_BOTH:
                collapseStatusBar();
                break;
        }
    }

    public boolean shouldDouble(int toggleNum, int state) {
        boolean doubleAction = false;
        switch (mDoubleClick) {
            case NO_ACTION:
                break;
            case REVERSE_ONE:
            case STATE_ONE:
            case SKIP_BACK:
            case SKIP_FORWARD:
                doubleAction = true;
                break;
        }
        return doubleAction;
    }

    public Drawable customIcon(int toggleNum, int state) {
        getToggle(toggleNum);
        String iconUri = "";
        Drawable icon = null;
        iconUri = mToggleIcons[state];
        if (iconUri != null && iconUri.length() > 0) {
            File f = new File(Uri.parse(iconUri).getPath());
            if (f.exists()) {
                icon = new BitmapDrawable(mContext.getResources(), f.getAbsolutePath());
            }
        } else {
            icon = NavBarHelpers.getIconImage(mContext, "**null**".equals(mClickActions[state])
                    ? mLongActions[state] : mClickActions[state]);
        }
        return icon;
    }

    public String customText(int toggleNum, int state) {
        String toggleText = NavBarHelpers.getProperSummary(mContext,
                "**null**".equals(mClickActions[state])
                ? mLongActions[state] : mClickActions[state]);
        return toggleText;
    }


    @Override
    public final void onClick(View v) {
        returnClickAction();
    }

    protected abstract void returnClickAction();

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mBootAction = Settings.System.getBoolean(resolver,
                Settings.System.CUSTOM_TOGGLE_REVERT, false);

        mMatchAction = Settings.System.getBoolean(resolver,
                Settings.System.MATCH_ACTION_ICON, false);

        mCollapseShade = Settings.System.getInt(resolver,
                Settings.System.COLLAPSE_SHADE, 10);

        mNumberOfActions = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_TOGGLE_QTY, 3);

        mDoubleClick = Settings.System.getInt(resolver,
                Settings.System.DCLICK_TOGGLE_REVERT, 0);

        for (int j = 0; j < 5; j++) {
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
                    .getUriFor(Settings.System.MATCH_ACTION_ICON),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.COLLAPSE_SHADE),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_QTY),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.DCLICK_TOGGLE_REVERT),
                    false, this);

            for (int j = 0; j < 5; j++) {
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
