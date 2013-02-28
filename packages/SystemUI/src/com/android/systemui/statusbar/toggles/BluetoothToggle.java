
package com.android.systemui.statusbar.toggles;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;

import com.android.systemui.R;

public class BluetoothToggle extends StatefulToggle {

    public void init(Context c, int style) {
        super.init(c, style);

        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) {
            return;
        }
        onBluetoothChanged();
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                onBluetoothChanged();
            }
        }, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    private void onBluetoothChanged() {
        final BluetoothAdapter bt = (BluetoothAdapter) mContext
                .getSystemService(Context.BLUETOOTH_SERVICE);
        String label = null;
        int iconId = 0;
        State newState = getState();
        switch (bt.getState()) {
            case BluetoothAdapter.STATE_CONNECTED:
                iconId = R.drawable.ic_qs_bluetooth_on;
                label = mContext.getString(R.string.quick_settings_bluetooth_label);
                newState = State.ENABLED;
                break;
            case BluetoothAdapter.STATE_ON:
                iconId = R.drawable.ic_qs_bluetooth_not_connected;
                label = mContext.getString(R.string.quick_settings_bluetooth_label);
                newState = State.ENABLED;
                break;
            case BluetoothAdapter.STATE_CONNECTING:
            case BluetoothAdapter.STATE_TURNING_ON:
                iconId = R.drawable.ic_qs_bluetooth_not_connected;
                label = mContext.getString(R.string.quick_settings_bluetooth_label);
                newState = State.ENABLING;
                break;
            case BluetoothAdapter.STATE_DISCONNECTED:
                iconId = R.drawable.ic_qs_bluetooth_not_connected;
                label = mContext.getString(R.string.quick_settings_bluetooth_label);
                newState = State.DISABLED;
                break;
            case BluetoothAdapter.STATE_DISCONNECTING:
                iconId = R.drawable.ic_qs_bluetooth_not_connected;
                label = mContext.getString(R.string.quick_settings_bluetooth_label);
                newState = State.DISABLING;
                break;
            case BluetoothAdapter.STATE_OFF:
                iconId = R.drawable.ic_qs_bluetooth_off;
                label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                newState = State.DISABLED;
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                iconId = R.drawable.ic_qs_bluetooth_off;
                label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                newState = State.DISABLING;
                break;
        }
        setInfo(label, iconId);
        scheduleViewUpdate();
        updateCurrentState(newState);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
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
