/*
 * Copyright (C) 2008, The Android Open Source Project
 * Copyright (c) 2011-2012, The Linux Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth;

import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.bluetooth.IBluetoothHealthCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHealthAppConfiguration;
import android.os.ParcelUuid;
import android.os.ParcelFileDescriptor;
import android.bluetooth.IBluetoothGattService;
import  android.bluetooth.IBluetoothGattCallback;
import android.bluetooth.BluetoothGattAppConfiguration;
import android.bluetooth.IBluetoothPreferredDeviceListCallback;

/**
 * System private API for talking with the Bluetooth service.
 *
 * {@hide}
 */
interface IBluetooth
{
    boolean isEnabled();
    boolean isServiceRegistered(in ParcelUuid uuid);
    boolean registerService(in ParcelUuid uuid, boolean enable);

    int getBluetoothState();
    boolean enable();
    boolean enableNoAutoConnect();
    boolean disable(boolean persistSetting);

    String getAddress();
    String getName();
    String getCOD();
    boolean setName(in String name);
    ParcelUuid[] getUuids();

    int getScanMode();
    boolean setScanMode(int mode, int duration);

    int getDiscoverableTimeout();
    boolean setDiscoverableTimeout(int timeout);

    boolean startDiscovery();
    boolean cancelDiscovery();
    boolean isDiscovering();
    byte[] readOutOfBandData();

    int getAdapterConnectionState();
    int getProfileConnectionState(int profile);
    boolean changeApplicationBluetoothState(boolean on,
                                in IBluetoothStateChangeCallback callback, in
                                IBinder b);

    boolean createBond(in String address);
    boolean createBondOutOfBand(in String address, in byte[] hash, in byte[] randomizer);
    boolean cancelBondProcess(in String address);
    boolean removeBond(in String address);
    String[] listBonds();
    int getBondState(in String address);
    boolean setDeviceOutOfBandData(in String address, in byte[] hash, in byte[] randomizer);
    boolean setBluetoothClass(String address, int classOfDevice);
    boolean registerRssiUpdateWatcher(in String address, in int rssiThreshold, in int interval,
                                      in boolean updateOnThreshExceed);
    boolean unregisterRssiUpdateWatcher(in String address);
    boolean setLEConnectionParams(in String address, in byte prohibitRemoteChg, in byte filterPolicy,
    in int scanInterval, in int scanWindow, in int intervalMin, in int intervalMax, in int latency,
    in int superVisionTimeout, in int minCeLen, in int maxCeLen, in int connTimeOut);
    boolean updateLEConnectionParams(in String address, in byte prohibitRemoteChg,
    in int intervalMin, in int intervalMax, in int slaveLatency, in int supervisionTimeout);
    String getRemoteName(in String address);
    String getRemoteAlias(in String address);
    boolean setRemoteAlias(in String address, in String name);
    int getRemoteClass(in String address);
    ParcelUuid[] getRemoteUuids(in String address);
    boolean fetchRemoteUuids(in String address, in ParcelUuid uuid, in IBluetoothCallback callback);
    int getRemoteServiceChannel(in String address, in ParcelUuid uuid);
    int getRemoteL2capPsm(in String address, in ParcelUuid uuid);
    String getRemoteFeature(String address, String feature);
    boolean setPin(in String address, in byte[] pin);
    boolean setPasskey(in String address, int passkey);
    boolean setPairingConfirmation(in String address, boolean confirm);
    boolean setRemoteOutOfBandData(in String addres);
    boolean cancelPairingUserInput(in String address);

    boolean setTrust(in String address, in boolean value);
    boolean getTrustState(in String address);
    boolean isBluetoothDock(in String address);

    int addRfcommServiceRecord(in String serviceName, in ParcelUuid uuid, int channel, IBinder b);
    void removeServiceRecord(int handle);
    boolean allowIncomingProfileConnect(in BluetoothDevice device, boolean value);

    boolean connectHeadset(String address);
    boolean disconnectHeadset(String address);
    boolean notifyIncomingConnection(String address, boolean rejected);

    // HID profile APIs
    boolean connectInputDevice(in BluetoothDevice device);
    boolean disconnectInputDevice(in BluetoothDevice device);
    List<BluetoothDevice> getConnectedInputDevices();
    List<BluetoothDevice> getInputDevicesMatchingConnectionStates(in int[] states);
    int getInputDeviceConnectionState(in BluetoothDevice device);
    boolean setInputDevicePriority(in BluetoothDevice device, int priority);
    int getInputDevicePriority(in BluetoothDevice device);

    boolean isTetheringOn();
    void setBluetoothTethering(boolean value);
    int getPanDeviceConnectionState(in BluetoothDevice device);
    List<BluetoothDevice> getConnectedPanDevices();
    List<BluetoothDevice> getPanDevicesMatchingConnectionStates(in int[] states);
    boolean connectPanDevice(in BluetoothDevice device);
    boolean disconnectPanDevice(in BluetoothDevice device);

    // HDP profile APIs
    boolean registerAppConfiguration(in BluetoothHealthAppConfiguration config,
        in IBluetoothHealthCallback callback);
    boolean unregisterAppConfiguration(in BluetoothHealthAppConfiguration config);
    boolean connectChannelToSource(in BluetoothDevice device, in BluetoothHealthAppConfiguration config);
    boolean connectChannelToSink(in BluetoothDevice device, in BluetoothHealthAppConfiguration config,
        int channelType);
    boolean disconnectChannel(in BluetoothDevice device, in BluetoothHealthAppConfiguration config, int id);
    ParcelFileDescriptor getMainChannelFd(in BluetoothDevice device, in BluetoothHealthAppConfiguration config);
    List<BluetoothDevice> getConnectedHealthDevices();
    List<BluetoothDevice> getHealthDevicesMatchingConnectionStates(in int[] states);
    int getHealthDeviceConnectionState(in BluetoothDevice device);

    void sendConnectionStateChange(in BluetoothDevice device, int profile, int state, int prevState);
    int registerEl2capConnection(in IBluetoothCallback callback, in int ampPolicy);
    void deregisterEl2capConnection(in int handle);
    int getEffectiveAmpPolicy(in int policy);
    boolean setDesiredAmpPolicy(in int handle, in int policy);
    void setUseWifiForBtTransfers(in boolean useWifi);

    // GATT client APIs
    boolean getGattServices(in String address, in ParcelUuid uuid);
    int gattConnect(in String address, in String path, in byte prohibitRemoteChg, in byte filterPolicy,
     in int scanInterval, in int scanWindow, in int intervalMin, in int intervalMax, in int latency,
     in int superVisionTimeout, in int minCeLen, in int maxCeLen, in int connTimeOut);
    boolean gattConnectCancel(in String address, in String path);
    String getGattServiceName(in String path);
    boolean discoverCharacteristics(in String path);
    String getGattServiceProperty(in String path, in String property);
    String[] getCharacteristicProperties(in String path);
    boolean setCharacteristicProperty(in String path, in String key, in byte[] value,
        boolean reliable);
    boolean registerCharacteristicsWatcher(in String path, in IBluetoothGattService gattCallback);
    boolean updateCharacteristicValue(in String path);
    boolean deregisterCharacteristicsWatcher(in String path);
    boolean startRemoteGattService(in String path, IBluetoothGattService gattCallback);
    void closeRemoteGattService(in String path);

    // GATT server APIs
    boolean registerGattAppConfiguration(in BluetoothGattAppConfiguration config,
                                         in IBluetoothGattCallback callback);
    boolean unregisterGattAppConfiguration(in BluetoothGattAppConfiguration config);
    boolean closeGattLeConnection(in BluetoothGattAppConfiguration config, String address);
    boolean sendIndication(in BluetoothGattAppConfiguration config,
                           in int handle, in byte[] value, in boolean notify, in int sessionHandle);
    boolean discoverPrimaryResponse(in BluetoothGattAppConfiguration config, in ParcelUuid uuid,
                        in int handle, in int end, in int status, in int reqHandle);
    boolean discoverPrimaryByUuidResponse(in BluetoothGattAppConfiguration config,
                        in int handle, in int end, in int status, in int reqHandle);
    boolean findIncludedResponse(in BluetoothGattAppConfiguration config, in ParcelUuid uuid,
                        in int handle, in int start, in int end, in int status, in int reqHandle);
    boolean discoverCharacteristicResponse(in BluetoothGattAppConfiguration config, in ParcelUuid uuid,
                        in int handle, in byte property, in int valueHandle, in int status, in int reqHandle);
    boolean findInfoResponse(in BluetoothGattAppConfiguration config, in ParcelUuid uuid,
                        in int handle, in int status, in int reqHandle);
    boolean readByTypeResponse(in BluetoothGattAppConfiguration config, in int handle, in ParcelUuid uuid,
                        in byte[] payload, in int status, in int reqHandle);
    boolean readResponse(in BluetoothGattAppConfiguration config, in ParcelUuid uuid,
                        in byte[] payload, in int status, in int reqHandle);
    boolean writeResponse(in BluetoothGattAppConfiguration config, in ParcelUuid uuid,
                        in int status, in int reqHandle);
    void disconnectSap();
    boolean isHostPatchRequired(in BluetoothDevice btDevice, in int patch_id);
    void disconnectDUN();
    boolean disableDUN();
    boolean enableDUN();
    // WhiteList APIs
    boolean addToPreferredDeviceList(in String address, in IBluetoothPreferredDeviceListCallback pListCallBack);
    boolean removeFromPreferredDeviceList(in String address, in IBluetoothPreferredDeviceListCallback pListCallBack);
    boolean clearPreferredDeviceList(in IBluetoothPreferredDeviceListCallback pListCallBack);
    boolean gattConnectToPreferredDeviceList(in IBluetoothPreferredDeviceListCallback pListCallBack);
    boolean gattCancelConnectToPreferredDeviceList(in IBluetoothPreferredDeviceListCallback pListCallBack);
    boolean addToPreferredDeviceListWrapper(in BluetoothDevice btDevObj, in IBluetoothPreferredDeviceListCallback pListCallBack,
            in String caller);
    boolean gattCancelConnectToPreferredDeviceListWrapper(in IBluetoothPreferredDeviceListCallback pListCallBack,
            in BluetoothDevice btDevice, in String caller);
}
