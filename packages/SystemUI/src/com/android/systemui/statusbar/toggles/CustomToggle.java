
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
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

import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.R;

import java.io.File;
import java.io.IOException;

public class CustomToggle extends BaseToggle {

    public static final int ACTION_ONE = 0;
    public static final int ACTION_TWO = 1;
    public static final int ACTION_THREE = 2;

    public static final int ACTION_REVERT = 3;

    public String[] mClickActions = new String[3];
    public String[] mToggleIcons = new String[3];
    public String[] mToggleText = new String[3];
    public String mLongClickAction;

    int mNumberOfActions = 1;
    private int mCustomState;
    private boolean mActionRevert;

    public final static String[] StockClickActions = {
        AwesomeAction.ACTION_NULL,
        AwesomeAction.ACTION_NULL,
        AwesomeAction.ACTION_NULL };

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        startMagicTricks();
    }

    @Override
    public void onClick(View v) {
        switch (mCustomState++ mod mNumberOfActions) {
            case ACTION_ONE:
                startMagicTricks();
                break;
            case ACTION_TWO:
                startMagicTricks();
                break;
            case ACTION_THREE:
                startMagicTricks();
                break;
        }
    }
    @Override
    public boolean onLongClick(View v) {
        Intent lAction = Intent.parseUri(mLongClickAction);
        lAction.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(lAction);
        return true;
    }

    private void startMagicTricks() {
        final Resources r = mContext.getResources();
        String iconUri = "";
        String toggleText = mToggleText[mCustomState];
        iconUri = mToggleIcons[mCustomState];
        Intent action = Intent.parseUri(mClickActions[mCustomState]);
        action.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(action);
        if (iconUri != null && iconUri.length() > 0) {
            File f = new File(Uri.parse(iconUri).getPath());
            if (f.exists()) {
                iconUri.setImageDrawable(new BitmapDrawable(getResources(), f.getAbsolutePath()));
            }
        } else {
            iconUri.setImageDrawable(AwesomeAction.getInstance(mContext).getIconImage(mClickActions[mCustomState]));
        }
    setIcon(iconUri);
    setLabel(toggleText);
    scheduleViewUpdate();
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        final Resources r = mContext.getResources();

        String mDefaultText = r.getString(
                R.string.quick_settings_quickrecord + " " + mCustomState);

        mActionRevert = Settings.System.getBoolean(resolver,
                Settings.System.CUSTOM_TOGGLE_REVERT, false);

        mNumberOfActions = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_TOGGLE_QTY, 3);

        for (int j = 0; j < 3; j++) {
            mClickActions[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_PRESS_TOGGLE[j]);
            if (mClickActions[j] == null) {
                mClickActions[j] = StockClickActions[j];
                Settings.System.putString(resolver,
                        Settings.System.CUSTOM_PRESS_TOGGLE[j], mClickActions[j]);
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

        mLongClickAction = Settings.System.getString(resolver,
                Settings.System.CUSTOM_LONGPRESS_TOGGLE);
        startMagicTricks();
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
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_QTY),
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
            }
            
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_LONGPRESS_TOGGLE),
                    false, this);

        updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
}
