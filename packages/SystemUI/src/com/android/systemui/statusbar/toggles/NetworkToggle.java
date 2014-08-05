
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.view.View;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.systemui.R;

import java.util.ArrayList;

public class NetworkToggle extends StatefulToggle {

    SettingsObserver mObserver;
    TelephonyManager mTelephonyManager;
    private ArrayList<Integer> mMobileNetworks = new ArrayList<Integer>();

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        mObserver = new SettingsObserver(new Handler());
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
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
        return super.onLongClick(v);
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
            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.NETWORK_MODES_TOGGLE), false,
                    this);
            scheduleViewUpdate();
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    protected void doEnable() {
        cycleSwitchNetworkMode();
    }

    @Override
    protected void doDisable() {
        cycleSwitchNetworkMode();
    }

    @Override
    protected void updateView() {
        int network = getCurrentPreferredNetworkMode();
        switch (network) {
            case Phone.NT_MODE_GLOBAL:
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_lte_pref_label);
                setIcon(R.drawable.ic_qs_lte_on);
                break;
            case Phone.NT_MODE_LTE_WCDMA:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_lte_threeg_label);
                setIcon(R.drawable.ic_qs_lte_on);
                break;
            case Phone.NT_MODE_LTE_ONLY:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_lte_only_label);
                setIcon(R.drawable.ic_qs_lte_on);
                break;
            case Phone.NT_MODE_WCDMA_PREF:
            case Phone.NT_MODE_CDMA:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_treeg_pref_label);
                setIcon(R.drawable.ic_qs_2g3g_on);
                break;
            case Phone.NT_MODE_GSM_UMTS:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_treeg_auto_label);
                setIcon(R.drawable.ic_qs_2g3g_on);
                break;
            case Phone.NT_MODE_WCDMA_ONLY:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_treeg_only_label);
                setIcon(R.drawable.ic_qs_3g_on);
                break;
            case Phone.NT_MODE_GSM_ONLY:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_twog_label);
                setIcon(R.drawable.ic_qs_2g_on);
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_twog_cdma_label);
                setIcon(R.drawable.ic_qs_2g_on);
                break;
            case Phone.NT_MODE_EVDO_NO_CDMA:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_twog_evdo_label);
                setIcon(R.drawable.ic_qs_2g_on);
                break;
        }
        super.updateView();
    }

    private int getCurrentPreferredNetworkMode() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, Phone.PREFERRED_NT_MODE);
    }

    private void cycleSwitchNetworkMode() {
        int network = getCurrentPreferredNetworkMode();
        getSwitchableModes();
        mTelephonyManager.toggleMobileNetwork(getNextNetworkMode(network));
    }

    private void getSwitchableModes() {
        String default_modes = "";
        if (isDeviceGSM()) {
            default_modes = deviceSupportsLTE() ? "9|0|1|2" : "0|1|2";
        } else if (isDeviceCDMA()) {
            default_modes = deviceSupportsLTE() ? "8|4|5" : "4|5";
        }
        if (!default_modes.equals("")) {
            String saved_toggles = Settings.AOKP.getString(mContext.getContentResolver(),
                Settings.AOKP.NETWORK_MODES_TOGGLE);
            String toggles_string = (saved_toggles != null) ? saved_toggles : default_modes;
            for (String mode : toggles_string.split("\\|")) {
                mMobileNetworks.add(Integer.parseInt(mode));
            }
        }
    }

    private int getNextNetworkMode(int current) {
        int position = 0;
        boolean found =  false;
        for (int i = 0; i < mMobileNetworks.size(); i++) {
            if (current == mMobileNetworks.get(i)) {
                position = i;
                found = true;
                break;
            }
        }
        if (!found) {
            position = mMobileNetworks.size() - 1;
        }
        return (position + 1) == mMobileNetworks.size() ? mMobileNetworks.get(0) :
                mMobileNetworks.get(position + 1);
    }

    private boolean isDeviceCDMA() {
        return (mTelephonyManager.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA);
    }

    private boolean isDeviceGSM() {
        return (mTelephonyManager.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM);
    }

    private boolean deviceSupportsLTE() {
        return (mTelephonyManager.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE
                    || mTelephonyManager.getLteOnGsmMode() != 0);
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_lte_on;
    }
}
