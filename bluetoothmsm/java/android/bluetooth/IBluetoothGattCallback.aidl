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

import android.bluetooth.BluetoothGattAppConfiguration;
import android.os.ParcelUuid;

/**
 *@hide
 */
interface IBluetoothGattCallback
{
    void onGattAppConfigurationStatusChange(in BluetoothGattAppConfiguration config, in int status);
    void onGattActionComplete(in BluetoothGattAppConfiguration config, in String action, in int status);
    void onGattDiscoverPrimaryServiceRequest(in BluetoothGattAppConfiguration config,
                                             in int start, in int end, in int requestHandle);
    void onGattDiscoverPrimaryServiceByUuidRequest(in BluetoothGattAppConfiguration config,
                                                   in int start, in int end, in ParcelUuid uuid, in int requestHandle);
    void onGattFindIncludedServiceRequest(in BluetoothGattAppConfiguration config,
                                          in int start, in int end, in int requestHandle);
    void onGattFindInfoRequest(in BluetoothGattAppConfiguration config,
                               in int start, in int end, in int requestHandle);
    void onGattDiscoverCharacteristicRequest(in BluetoothGattAppConfiguration config,
                                             in int start, in int end, in int requestHandle);
    void onGattReadByTypeRequest(in BluetoothGattAppConfiguration config,
                                 in ParcelUuid uuid, in int start, in int end, in String authentication, in int requestHandle);
    void onGattReadRequest(in BluetoothGattAppConfiguration config,
                           in int handle, in String authentication, in int requestHandle);
    void onGattWriteCommand(in BluetoothGattAppConfiguration config, in int handle, in byte[] value,
                            in String authentication);
    void onGattWriteRequest(in BluetoothGattAppConfiguration config, in int handle, in byte[] value,
                            in String authentication, in int sessionHandle, in int requestHandle);
    void onGattSetClientConfigDescriptor(in BluetoothGattAppConfiguration config, in int handle, in byte[] value,
                                     in int sessionHandle);
    void onGattIndicateResponse(in BluetoothGattAppConfiguration config, in boolean result);
}
