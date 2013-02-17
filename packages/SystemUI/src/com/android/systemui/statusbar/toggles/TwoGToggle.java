
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;

public class TwoGToggle extends StatefulToggle {

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);

        new SettingsObserver(new Handler()).observe();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.Global.PREFERRED_NETWORK_MODE), false,
                    this);
            scheduleViewUpdate();
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    protected void updateView() {
        boolean enabled = getCurrentPreferredNetworkMode(mContext) == Phone.NT_MODE_GSM_ONLY;
        setLabel(enabled ? R.string.quick_settings_twog_on_label
                : R.string.quick_settings_twog_off_label);
        setIcon(enabled ? R.drawable.ic_qs_2g_on : R.drawable.ic_qs_2g_off);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        super.updateView();
    }

    private static int getCurrentPreferredNetworkMode(Context context) {
        int network = -1;
        try {
            network = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        return network;
    }

    @Override
    protected void doEnable() {
        TelephonyManager tm = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        tm.toggle2G(true);
    }

    @Override
    protected void doDisable() {
        TelephonyManager tm = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        tm.toggle2G(false);
    }
}
