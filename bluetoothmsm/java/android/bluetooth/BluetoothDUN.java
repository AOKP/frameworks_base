
/*
* Copyright (c) 2012, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*        * Redistributions of source code must retain the above copyright
*            notice, this list of conditions and the following disclaimer.
*        * Redistributions in binary form must reproduce the above copyright
*            notice, this list of conditions and the following disclaimer in the
*            documentation and/or other materials provided with the distribution.
*        * Neither the name of Code Aurora nor
*            the names of its contributors may be used to endorse or promote
*            products derived from this software without specific prior written
*            permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
* CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
* EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
* PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
* OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
* OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
* ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package android.bluetooth;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;
import android.os.SystemProperties;

/**
 *
 * Public API for controlling the Bluetooth Dun Service.
 *
 * @hide
 */
public class BluetoothDUN {

    private static final String TAG = "BluetoothDUN";
    private static final boolean DBG = false;
    private IBluetooth mService;

    public BluetoothDUN() {
        IBinder b = ServiceManager.getService(BluetoothAdapter.BLUETOOTH_SERVICE);
        if (b != null) {
            mService = IBluetooth.Stub.asInterface(b);
        } else {
            Log.i(TAG, "Failed to get the Bluetooth Interface");
        }
    }
     /**
     * Initiate the disconnection from DUN server.
     * Status of the DUN  server can be determined by the signal emitted
     * from org.qcom.sap
     */
    public boolean disconnect() {
        Log.i(TAG, "->disconnect");
        try {
            mService.disconnectDUN();
            return true;
        } catch(RemoteException e) {
            Log.e(TAG, "", e);
        }

        return false;
    }

    /**
    * Starts the DUN daemon and adds DUN to sdp record
    */
    public boolean Enable() {
        Log.i(TAG, "->enableDUN");

        if (SystemProperties.getBoolean("ro.qualcomm.bluetooth.dun", false) == false) {
            return false;
        }

        try {
            mService.enableDUN();
            return true;
        } catch(RemoteException e) {
            Log.e(TAG, "", e);
        }

        return false;
    }

   /**
   * Stops the DUN daemon and removes DUN from sdp record
   */
    public boolean Disable() {
        Log.i(TAG, "->disableDUN");

        if (SystemProperties.getBoolean("ro.qualcomm.bluetooth.dun", false) == false) {
            return false;
        }

        try {
            mService.disableDUN();
            return true;
        } catch(RemoteException e) {
            Log.e(TAG, "", e);
        }

        return false;
    }
}

