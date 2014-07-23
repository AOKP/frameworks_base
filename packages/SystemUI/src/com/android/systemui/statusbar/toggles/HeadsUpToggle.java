
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class HeadsUpToggle extends StatefulToggle {
    HeadsUpObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mObserver = new HeadsUpObserver(mHandler);
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
        Settings.AOKP.putInt(mContext.getContentResolver(),
                Settings.AOKP.HEADS_UP_NOTIFICATION, 1);
    }

    @Override
    protected void doDisable() {
        Settings.AOKP.putInt(mContext.getContentResolver(),
                Settings.AOKP.HEADS_UP_NOTIFICATION, 0);
    }

    @Override
    protected void updateView() {
        boolean enabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                Settings.AOKP.HEADS_UP_NOTIFICATION, false);
        setIcon(enabled
                ? R.drawable.ic_qs_headsup_on
                : R.drawable.ic_qs_headsup_off);
        setLabel(enabled
                ? R.string.quick_settings_headsup_on_label
                : R.string.quick_settings_headsup_off_label);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        super.updateView();
    }

    protected class HeadsUpObserver extends ContentObserver {
        HeadsUpObserver(Handler handler) {
            super(handler);
            observe();
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.AOKP.getUriFor(
                    Settings.AOKP.HEADS_UP_NOTIFICATION), false, this);
            onChange(false);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_headsup_on;
    }
}
