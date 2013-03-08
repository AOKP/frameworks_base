package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.R;

public class RebootToggle extends BaseToggle {

    private PowerManager pm;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_reboot);
        setLabel(R.string.quick_settings_reboot);

        pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void onClick(View v) {
        collapseStatusBar();
        dismissKeyguard();
        Intent intent = new Intent(Intent.ACTION_REBOOTMENU);
        mContext.sendBroadcast(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        // Turn screen off so doesn't appear as lockup and go bye-bye
        pm.goToSleep(SystemClock.uptimeMillis());
        pm.reboot(null);

        return super.onLongClick(v);
    }

}
