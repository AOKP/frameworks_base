/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (c) 2013 The Linux Foundation. All rights reserved
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.content.Context;
import android.content.ContentResolver;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;


import android.bluetooth.IBluetoothManager;
import android.server.BluetoothA2dpService;
import android.server.BluetoothService;
import android.bluetooth.BluetoothAdapter;


class BluetoothManagerService extends IBluetoothManager.Stub {
    private static final String TAG = "BluetoothManagerService";
    private static final boolean DBG = true;
    BluetoothService bluetooth = null;
    BluetoothA2dpService bluetoothA2dp = null;

    //private final Context mContext;
	private final ContentResolver mContentResolver;


   /* private final IBluetoothCallback mBluetoothCallback =  new IBluetoothCallback.Stub() {
        @Override
        public void onBluetoothStateChange(int prevState, int newState) throws RemoteException  {
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_STATE_CHANGE,prevState,newState);
            mHandler.sendMessage(msg);
        }
    };*/

    BluetoothManagerService(Context context) {
        Slog.i(TAG, "BluetoothManagerService started.Start Bluetooth Service");
        mContentResolver = context.getContentResolver();
        bluetooth = new BluetoothService(context);
        ServiceManager.addService(BluetoothAdapter.BLUETOOTH_SERVICE, bluetooth);
        bluetooth.initAfterRegistration();
        bluetoothA2dp = new BluetoothA2dpService(context, bluetooth);
        ServiceManager.addService(BluetoothA2dpService.BLUETOOTH_A2DP_SERVICE, bluetoothA2dp);
        bluetooth.initAfterA2dpRegistration();
        int airplaneModeOn = Settings.System.getInt(mContentResolver,
               Settings.System.AIRPLANE_MODE_ON, 0);
        int bluetoothOn = Settings.Secure.getInt(mContentResolver,
            Settings.Secure.BLUETOOTH_ON, 0);
        if (airplaneModeOn == 0 && bluetoothOn != 0) {
            bluetooth.enable();
        }
    }

    public boolean isEnabled() {
        return false;
    }

    public boolean disable(boolean saveSetting) {
        return bluetooth.disable(saveSetting);
    }

    public boolean enableNoAutoConnect() {
        return false;
    }

    public String getAddress() {
        return null;
    }

    public String getName() {
        return null;
    }
}

