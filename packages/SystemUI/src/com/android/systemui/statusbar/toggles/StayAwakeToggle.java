package com.android.systemui.statusbar.toggles;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.R;

public class StayAwakeToggle extends StatefulToggle {

    private static final String TAG = "StayAwake";
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private long currentTimeout = (Settings.System.getLong(mContext.getContentResolver(),
            Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE));
    private long storedUserTimeout;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
    }

    @Override
    public boolean onLongClick(View v) {
       /* Do nothing yet */
       /* Thinking user time out for w/l set in RC */
       /* 1 min, 5 min, 10 min, 20, 30 */
       return super.onLongClick(v);
    }

    @Override
    protected void updateView() {
       boolean enabled = checkIfStayingAwake(currentTimeout);
       setLabel(enabled ? R.string.quick_settings_stayawake_on
               : R.string.quick_settings_stayawake_off);
       setIcon(enabled ? R.drawable.ic_qs_stayawake_on : R.drawable.ic_qs_stayawake_off);
       super.updateView();
    }

    private static boolean checkIfStayingAwake(long timeout) {
        boolean stayAwake;
        if (timeout == -1) {
            /** User was raving out and now wants to shut it down */
            stayAwake = false;
        } else {
            /** Time to stay awake */
            stayAwake = true;
        }
        return stayAwake;
    }

    @Override
    protected void doEnable() {
        storedUserTimeout = currentTimeout;
        Settings.System.putLong(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, -1);
    }

    @Override
    protected void doDisable() {
        if (currentTimeout != storedUserTimeout) {
              /** Verify that timeouts dont match, else reset to fallback */
                 currentTimeout = storedUserTimeout;
          } else {
                 currentTimeout = FALLBACK_SCREEN_TIMEOUT_VALUE;
          }
          Settings.System.putLong(mContext.getContentResolver(),
                  Settings.System.SCREEN_OFF_TIMEOUT, currentTimeout);
     }
}
