
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

import com.android.systemui.R;

public class DarkUIToggle extends StatefulToggle {

    SettingsObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mObserver = new SettingsObserver(mHandler);
        mObserver.observe();
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.UI_INVERTED_MODE, 2);
    }

    @Override
    protected void doDisable() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.UI_INVERTED_MODE, 1);
    }

    @Override
    protected void updateView() {
        final boolean enabled = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.UI_INVERTED_MODE, 1) == 2;
        setEnabledState(enabled);
        setIcon(enabled ? R.drawable.ic_qs_darkui_on : R.drawable.ic_qs_darkui_off);
        setLabel(enabled ? R.string.quick_settings_darkui_on_label
                : R.string.quick_settings_darkui_off_label);
        super.updateView();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.Secure.UI_INVERTED_MODE), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

}
