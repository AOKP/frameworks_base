
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.view.RotationPolicy;
import com.android.internal.view.RotationPolicy.RotationPolicyListener;
import com.android.systemui.R;
import com.android.systemui.statusbar.toggles.StatefulToggle.State;

public class TurnRightToggle extends StatefulToggle {

    private RotationPolicyListener mListener = null;
    private static final String TAG = TurnLeftToggle.class.getSimpleName();

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        RotationPolicy.registerRotationPolicyListener(mContext,
                mListener = new RotationPolicyListener() {

                    @Override
                    public void onChange() {
                        scheduleViewUpdate();
                    }
                }, UserHandle.USER_ALL);
    }

    @Override
    protected void cleanup() {
        if (mListener != null) {
            RotationPolicy.unregisterRotationPolicyListener(mContext, mListener);
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
    	rotateScreen();
    }

    @Override
    protected void doDisable() {
    	rotateScreen();
    }

    @Override
    protected void updateView() {
        boolean lock = RotationPolicy.isRotationLocked(mContext);
        setIcon(lock ? R.drawable.ic_qs_turn_right_locked : R.drawable.ic_qs_turn_right);
        setLabel(R.string.quick_settings_turn_right);
        updateCurrentState(lock ? State.ENABLED : State.DISABLED);
        super.updateView();
    }
    
    private void rotateScreen() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
                    Log.e(TAG, "*****************");
                    Log.e(TAG, "*****************");
                    Log.e(TAG, "*****************");
                    Log.e(TAG, "Old rotation: " + wm.getRotation());
                    int newRotation = (wm.getRotation() + 1) % 4;
                    wm.freezeRotation(newRotation);
                    Log.e(TAG, "New rotation: " + wm.getRotation());
                } catch (RemoteException exc) {
                    Log.e(TAG, "Unable to turn left");
                }
            }
        });
    }

    @Override
    public boolean onLongClick(View v) {
    	if (RotationPolicy.isRotationLocked(mContext)) {
            RotationPolicy.setRotationLock(mContext, false);
            updateCurrentState(State.DISABLED);
    	}
    	else {
            RotationPolicy.setRotationLock(mContext, true);
            updateCurrentState(State.ENABLED);
    	}
        return super.onLongClick(v);
    }

}
