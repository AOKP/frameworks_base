/*
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
 *          the names of its contributors may be used to endorse or promote
 *          products derived from this software without specific prior written
 *          permission.
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

import android.util.Log;
import android.os.ParcelUuid;

/**
 * This abstract class is used to implement BluetoothGatt callbacks.
 * @hide
 */
public abstract class BluetoothGattCallback {
    private static final String TAG = "BluetoothGattCallback";

    /**
     * Callback to inform change in registration state of the GATT profile
     * application.
     *
     * @param config Gatt profile app configuration
     * @param status Success or failure of the registration or unregistration
     *            calls. Can be one of
     *            GATT_CONFIG_REGISTRATION_SUCCESS or
     *            GATT_CONFIG_REGISTRATION_FAILURE or
     *            GATT_CONFIG_UNREGISTRATION_SUCCESS or
     *            GATT_CONFIG_UNREGISTRATION_FAILURE
     */
    public void onGattAppConfigurationStatusChange(BluetoothGattAppConfiguration config,
            int status) {
        Log.d(TAG, "onGattAppConfigurationStatusChange: " + config + "Status: " + status);
    }

    /**
     * Callback to inform completion of outstanding GATT profile action
     *
     * @param config Gatt profile app configuration
     * @param status Can be one of
     *            GATT_SUCCESS or
     *            GATT_ACTION_FAILURE or
     *            GATT_ACTION_INVALID_ARGUMENTS
     */
    public void onGattActionComplete(BluetoothGattAppConfiguration config, String action,
                                     int status) {
        Log.d(TAG, "onGattActionComplete: " + action + "Status: " + status);
    }

    /* Respond with description for just one service */
    public void onGattDiscoverPrimaryServiceRequest(BluetoothGattAppConfiguration config,
                                                    int start, int end, int requestHandle) {
        Log.d(TAG, "onGattDiscoverPrimaryServiceRequest: " + config + " range " + start + " - " + end);
    }

    public void onGattDiscoverPrimaryServiceByUuidRequest(BluetoothGattAppConfiguration config,
                                                          int start, int end, ParcelUuid uuid, int requestHandle) {
        Log.d(TAG, "onGattDiscoverPrimaryServiceByUuidRequest: " + config + "UUID" + uuid .toString() + " range " + start + " - " + end);
    }

    /* Respond with description for just one service */
    public void onGattFindIncludedServiceRequest(BluetoothGattAppConfiguration config,
                                                 int start, int end, int requestHandle) {
        Log.d(TAG, "onGattFindIncludedServiceRequest: " + config + " range " + start + " - " + end);
    }


    public void onGattFindInfoRequest(BluetoothGattAppConfiguration config,
                                      int start, int end, int requestHandle) {
        Log.d(TAG, "onGattFindInfoRequest: " + config + " range " + start + " - " + end);
    }

    public void onGattDiscoverCharacteristicRequest(BluetoothGattAppConfiguration config,
                                             int start, int end, int requestHandle) {
        Log.d(TAG, "onGattDiscoverCharacteristicRequest: " + config + " range " + start + " - " + end);
    }

    public void onGattReadByTypeRequest(BluetoothGattAppConfiguration config,
                                        ParcelUuid uuid, int start, int end, String authentication, int requestHandle) {
        Log.d(TAG, "onGattReadByTypeRequest: " + config + " range " + start + " - " + end
              + " link authentication: " + authentication);
    }

    public void onGattReadRequest(BluetoothGattAppConfiguration config,
                                  int handle, String authentication, int requestHandle) {
        Log.d(TAG, "onGattReadRequest: " + config + " handle " + handle +
              " link authentication: " + authentication);
    }

    public void onGattWriteCommand(BluetoothGattAppConfiguration config, int handle, byte[] value,
                                   String authentication) {

        Log.d(TAG, "onGattWriteCommand: " + config + " handle " + handle +
              " link authentication: " + authentication);
    }

    public void onGattWriteRequest(BluetoothGattAppConfiguration config, int handle, byte[] value,
                                   String authentication, int sessionHandle, int requestHandle) {

        Log.d(TAG, "onGattWriteRequest: " + config + " handle " + handle +
              " link authentication: " + authentication + ", session " + sessionHandle);
    }

    public void onGattSetClientConfigDescriptor(BluetoothGattAppConfiguration config, int handle, byte[] value,
                                            int sessionHandle) {
        Log.d(TAG, "onGattSetClientConfigDescriptor: " + config + " handle " + handle + " session " + sessionHandle);
    }

    public void onGattIndicateResponse(BluetoothGattAppConfiguration config, boolean result) {
        Log.d(TAG, "onGattIndicateResponse: " + config + " result " + result);
    }
}