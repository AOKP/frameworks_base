package com.android.systemui.statusbar.toggles;

import android.app.KeyguardManager;
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
    private KeyguardManager mKeyguard;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_reboot);
        setLabel(R.string.quick_settings_reboot);
        mKeyguard = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void onClick(View v) {
        if (!mKeyguard.isKeyguardLocked()) {
            Intent intent = new Intent(Intent.ACTION_REBOOTMENU);
            mContext.sendBroadcast(intent);
            collapseStatusBar();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        // Turn screen off so doesn't appear as lockup
        pm.goToSleep(SystemClock.uptimeMillis());
        if (!mKeyguard.isKeyguardLocked()) {
            // bye bye
            pm.reboot(null);
        }
        return super.onLongClick(v);
    }
}
