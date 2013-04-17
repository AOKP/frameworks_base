package com.android.systemui.statusbar.toggles;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.internal.policy.PolicyManager;

import com.android.systemui.R;

public class StayAwakeToggle extends StatefulToggle {

    private boolean enabled;

    private static final String TAG = "AOKPInsomnia";

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        scheduleViewUpdate();
    }

    @Override
    public boolean onLongClick(View v) {
       /* Do nothing yet */
       /* Thinking user time out for w/l set in RC */
       /* 1 min, 5 min, 10 min, 20, 30 */
       return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        toggleInsomnia(true);
    }

   @Override
   protected void doDisable() {
       toggleInsomnia(false);
   }

   @Override
   protected void updateView() {
       private boolean enabled = checkCondition(state)
       setEnabledState(enabled);
       setLabel(enabled ? R.string.quick_settings_stayawake_on
               : R.string.quick_settings_stayawake_off);
       setIcon(enabled ? R.drawable.ic_qs_stayawake_on
              : R.drawable.ic_qs_stayawake_off);

       if (enabled) {
           Log.d(TAG, "updateView() ENABLED");
       } else {
           Log.d(TAG, "updateView() DISABLED");
       }
       super.updateView();
    }

    private void toggleInsomnia(boolean state) {
        Window win = PolicyManager.makeNewWindow(mContext);
        try {
            if (state) {
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d(TAG, "toggleInsomnia() + state.toString()");
            } else {
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d(TAG, "toggleInsomnia() + state.toString()");
            }
        } catch (NullPointerException npe) {
            // swallom 'em and pass out
        }
        return state;
    }

    private boolean checkCondition(boolean state) {
        

    }
}
