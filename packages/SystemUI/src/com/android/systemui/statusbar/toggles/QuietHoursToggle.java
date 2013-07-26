
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;;
import android.view.View;

import com.android.systemui.R;

public class QuietHoursToggle extends StatefulToggle {

    private static final String STOP_SERVICE_COMMAND =
            "com.android.settings.service.STOP_SERVICE_COMMAND";

    private static final String SCHEDULE_SERVICE_COMMAND =
            "com.android.settings.service.SCHEDULE_SERVICE_COMMAND";

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        scheduleViewUpdate();
    }

    @Override
    protected void doEnable() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 1);
        autoSmsIntentBroadcast(SCHEDULE_SERVICE_COMMAND);
    }

    @Override
    protected void doDisable() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0);
        autoSmsIntentBroadcast(STOP_SERVICE_COMMAND);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClassName("com.android.settings", "com.android.settings.Settings$QuietHoursSettingsActivity");
        intent.addCategory("android.intent.category.LAUNCHER");
        startActivity(intent);
        return super.onLongClick(v);
    }

    @Override
    protected void updateView() {
        boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0) == 1;
        setEnabledState(enabled);
        setIcon(enabled ? R.drawable.ic_qs_quiet_hours_on : R.drawable.ic_qs_quiet_hours_off);
        setLabel(enabled ? R.string.quick_settings_quiet_hours_on_label
                : R.string.quick_settings_quiet_hours_off_label);
        super.updateView();
    }

    private void autoSmsIntentBroadcast(String action) {
        Intent scheduleSms = new Intent();
        scheduleSms.setAction(action);
        mContext.sendBroadcast(scheduleSms);
    }
}
