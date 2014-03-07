
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.text.TextUtils;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class ClockToggle extends BaseToggle {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        onAlarmChanged(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_ALARM_CHANGED);
        registerBroadcastReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onAlarmChanged(intent.getBooleanExtra("alarmSet", false));
            }
        }, filter);
    }

    void onAlarmChanged(boolean enabled) {
        boolean state = enabled;
        final String alarmText = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED,
                UserHandle.USER_CURRENT);
        state =! TextUtils.isEmpty(alarmText); // this may look silly, but is necessary
        setInfo(state ? alarmText : mContext.getString(
                R.string.quick_settings_clock_alarm_off), getDefaultIconResId());
        scheduleViewUpdate();
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        vibrateOnTouch();
        collapseStatusBar();
        dismissKeyguard();
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(new Intent(Intent.ACTION_QUICK_CLOCK));
        return super.onLongClick(v);
    }

    @Override
    public View createTraditionalView() {
        View root = View.inflate(mContext, R.layout.toggle_traditional_time, null);
        root.setOnClickListener(this);
        root.setOnLongClickListener(this);
        return root;
    }

    @Override
    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile_time, null);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        mLabel = (TextView) quick.findViewById(R.id.clock_textview);
        return quick;
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_clock_circle;
    }

}
