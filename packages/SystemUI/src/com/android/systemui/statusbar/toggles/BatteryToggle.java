
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.BatteryMeterView;
import com.android.systemui.BatteryCircleMeterView;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryToggle extends BaseToggle implements BatteryStateChangeCallback {

    private BatteryMeterView mBattery;
    private BatteryCircleMeterView mCircleBattery;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
    }

    @Override
    public void onClick(View v) {
        vibrateOnTouch();
        startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_DREAM_SETTINGS);
        return super.onLongClick(v);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        if (mBattery == null) {
            return;
        }
        mCircleBattery.updateSettings();
        mCircleBattery.setColors(true);
        mBattery.updateSettings();
        mBattery.setColors(true);
        if (level == 100) {
            setLabel(R.string.quick_settings_battery_charged_label);
        } else {
            setLabel(pluggedIn ?
                    mContext.getString(R.string.quick_settings_battery_charging_label,
                            level)
                    : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                            level));
        }
        scheduleViewUpdate();
    }

    @Override
    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile_battery, null);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        mBattery = (BatteryMeterView) quick.findViewById(R.id.battery);
        mBattery.setColors(true);
        mBattery.setVisibility(View.GONE);
        mCircleBattery = (BatteryCircleMeterView) quick.findViewById(R.id.circle_battery);
        mCircleBattery.setColors(true);
        mLabel = (TextView) quick.findViewById(R.id.label);
        return quick;
    }

    @Override
    public View createTraditionalView() {
        View root = View.inflate(mContext, R.layout.toggle_traditional_battery, null);
        root.setOnClickListener(this);
        root.setOnLongClickListener(this);
        mBattery = (BatteryMeterView) root.findViewById(R.id.battery);
        mBattery.setColors(true);
        mBattery.setVisibility(View.GONE);
        mCircleBattery = (BatteryCircleMeterView) root.findViewById(R.id.circle_battery);
        mCircleBattery.setColors(true);
        mLabel = null;
        mIcon = null;
        return root;
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_battery_71;
    }
}
