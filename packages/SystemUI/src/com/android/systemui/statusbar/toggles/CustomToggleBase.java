
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

public class CustomToggleBase extends BaseToggle {

    public String[] mClickActions = new String[5];
    public String[] mLongActions = new String[5];
    public String[] mToggleIcons = new String[5];
    public String[] mToggleText = new String[5];

    private int mDoubleClick;
    private int mNumberOfActions;
    private int mCollapseShade;
    private int mCustomState= 0;
    private int matchState = 0;
    private int doubleClickCounter = 0;
    private boolean mActionRevert;
    private boolean mMatchAction;

    public static final int NO_ACTION = 0;
    public static final int REVERSE_ONE = 1;
    public static final int STATE_ONE = 2;
    public static final int SKIP_BACK = 3;
    public static final int SKIP_FORWARD = 4;

    public static final int NO_COLLAPSE = 10;
    public static final int ON_CLICK = 11;
    public static final int ON_LONG = 12;
    public static final int ON_BOTH = 13;

    private static final String KEY_TOGGLE_STATE = "toggle_state";

    public final static String[] StockClickActions = {
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value() };

    private SettingsObserver mObserver = null;

    public void getAction(int state) {
        AwesomeAction.launchAction(mContext, "**null**".equals(mClickActions[state])
                ? mLongActions[state] : mClickActions[state]);
    }

    public int getCounting(int toggle, int state, int min, int max) {
        int stateHolder = state;
        if (state < practiceKungFu(stateHolder, min, max)) {
            state += 1;
            matchState = state-1;
        } else {
            state = 0;
            matchState = practiceKungFu(state, min, max);
        }
        return state;
    }

    public int getMatch() {
        match = matchState;
        return match;
    }

    public void getClickActions(int toggle, int state, int statematch) {
        if (mMatchAction[toggle]) {
            AwesomeAction.launchAction(mContext, mClickActions[statematch]);
        } else {
            AwesomeAction.launchAction(mContext, mClickActions[state]);
        }
        collapseClick(toggle);
    }

    public void getLongActions(int toggle, int state) {
        AwesomeAction.launchAction(mContext, mLongActions[state]);
        switch (mCollapseShade[toggle]) {
            case NO_COLLAPSE:
            case ON_CLICK:
                break;
            case ON_LONG:
            case ON_BOTH:
                collapseStatusBar();
                break;
        }
    }

    public boolean shouldDouble() {
        boolean doubleClick == false;
        switch (mDoubleClick[toggle]) {
            case NO_ACTION:
                break;
            case REVERSE_ONE:
            case STATE_ONE:
            case SKIP_BACK:
            case SKIP_FORWARD:
                doubleClick == true;
                break;
        }
        return doubleClick;
    }

    public int getDoubleAction(int toggle, int state, int min, int max) {
        int stateHolder = state;
        switch (mDoubleClick[toggle]) {
            case REVERSE_ONE:
                if (stateHolder > min) {
                    state--;
                } else {
                    state = practiceKungFu(stateHolder, min, max);
                }
                getAction(state);
                collapseClick(toggle);
                break;
            case STATE_ONE:
                state = min;
                getAction(state);
                collapseClick(toggle);
                break;
            case SKIP_BACK:
                if (stateHolder > min) {
                    state--;
                } else {
                    state = practiceKungFu(stateHolder, min, max);
                }
                break;
            case SKIP_FORWARD:
                if (state < practiceKungFu(stateHolder, min, max)) {
                    state += 1;
                } else {
                    state = min;
                }
                break;
        }
        return state;
    }

    public int practiceKungFu(int state, int min, int max) {
        int stateNow = max;
        if (state == min+1) {
                stateNow-5;
        }
        if (state == min+2) {
                stateNow-4;
        }
        if (state == min+3) {
                stateNow-3;
        }
        if (state == min+4) {
                stateNow-2;
        }
        if (state == min+5) {
                stateNow-1;
        }
        return stateNow;
    }

    public void collapseClick(int toggle)
        switch (mCollapseShade[toggle]) {
            case NO_COLLAPSE:
            case ON_LONG:
                break;
            case ON_CLICK:
            case ON_BOTH:
                collapseStatusBar();
                break;
        }
    }

    public Drawable userIcon(int state) {
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
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        String mDefaultText = "CUSTOM";

        SharedPreferences p = mContext.getSharedPreferences(KEY_TOGGLE_STATE, Context.MODE_PRIVATE);
        mCustomState = p.getInt("state", 0);

        mActionRevert = Settings.System.getBoolean(resolver,
                Settings.System.CUSTOM_TOGGLE_REVERT[toggleNumber], false);

        mMatchAction = Settings.System.getBoolean(resolver,
                Settings.System.MATCH_ACTION_ICON[toggleNumber], false);

        mCollapseShade = Settings.System.getInt(resolver,
                Settings.System.COLLAPSE_SHADE[toggleNumber], 10);

        mNumberOfActions = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_TOGGLE_QTY[toggleNumber], 3);

        mDoubleClick = Settings.System.getInt(resolver,
                Settings.System.DCLICK_TOGGLE_REVERT[toggleNumber], 0);

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
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_REVERT[toggleNumber]),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.MATCH_ACTION_ICON[toggleNumber]),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.COLLAPSE_SHADE[toggleNumber]),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_QTY[toggleNumber]),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.DCLICK_TOGGLE_REVERT[toggleNumber]),
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
