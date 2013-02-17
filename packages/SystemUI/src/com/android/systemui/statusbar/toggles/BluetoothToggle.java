
package com.android.systemui.statusbar.toggles;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.toggles.StatefulToggle.State;

public class BluetoothToggle extends StatefulToggle {

    public BluetoothToggle() {
    }

    public void init(Context c, int style) {
        super.init(c, style);

        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF);

                String label = null;
                int iconId = 0;
                State newState = getState();
                switch (state) {
                    case BluetoothAdapter.STATE_CONNECTED:
                    case BluetoothAdapter.STATE_CONNECTING:
                        // don't care about these right now
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        iconId = R.drawable.ic_qs_bluetooth_not_connected;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        iconId = R.drawable.ic_qs_bluetooth_not_connected;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        newState = State.ENABLED;
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTED:
                    case BluetoothAdapter.STATE_DISCONNECTING:
                        // don't care about these right now
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        iconId = R.drawable.ic_qs_bluetooth_off;
                        label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                        newState = State.DISABLED;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        iconId = R.drawable.ic_qs_bluetooth_off;
                        label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                        break;
                }
                if (label != null && iconId > 0) {
                    setInfo(label, iconId);
                    scheduleViewUpdate();
                    updateCurrentState(newState);
                }
            }
        });
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        BluetoothAdapter.getDefaultAdapter().enable();
    }

    @Override
    protected void doDisable() {
        BluetoothAdapter.getDefaultAdapter().disable();
    }

}
