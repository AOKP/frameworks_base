
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;

public class GpsToggle extends StatefulToggle implements LocationGpsStateChangeCallback {

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);

    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        Settings.Secure.setLocationProviderEnabled(mContext.getContentResolver(),
                LocationManager.GPS_PROVIDER, false);
    }

    @Override
    protected void doDisable() {
        Settings.Secure.setLocationProviderEnabled(mContext.getContentResolver(),
                LocationManager.GPS_PROVIDER, false);
    }

    @Override
    protected void updateView() {
        boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
        updateCurrentState(gpsEnabled ? State.ENABLED : State.DISABLED);
        setLabel(gpsEnabled ? R.string.quick_settings_gps_off_label
                : R.string.quick_settings_gps_on_label);
        setIcon(gpsEnabled ? R.drawable.ic_qs_gps_off : R.drawable.ic_qs_gps_on);
        super.updateView();
    }

    @Override
    public void onLocationGpsStateChanged(boolean inUse, boolean hasFix, String description) {
        scheduleViewUpdate();
    }
}
