
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryToggle extends BaseToggle implements BatteryStateChangeCallback {

    LevelListDrawable mBatteryLevels;
    LevelListDrawable mChargingBatteryLevels;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        mBatteryLevels = (LevelListDrawable) c.getResources()
                .getDrawable(R.drawable.qs_sys_battery);
        mChargingBatteryLevels =
                (LevelListDrawable) c.getResources()
                        .getDrawable(R.drawable.qs_sys_battery_charging);
    }

    @Override
    public void onClick(View v) {
        startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        Drawable d = pluggedIn ? mChargingBatteryLevels
                : mBatteryLevels;
        d.setLevel(level);
        setIcon(d);

        if (level == 100) {
            setLabel(R.string.quick_settings_battery_charged_label);
        } else {
            setLabel(pluggedIn ?
                    mContext.getString(R.string.quick_settings_battery_charging_label,
                            level)
                    : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                            level));
        }

    }
}
