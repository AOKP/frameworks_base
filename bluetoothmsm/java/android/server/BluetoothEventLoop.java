/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2010-2012 The Linux Foundation. All rights reserved.
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

package android.server;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHealth;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.util.Log;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;


import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


/**
 * @hide
 */
class BluetoothEventLoop {
    private static final String TAG = "BluetoothEventLoop";
    private static final boolean DBG = true;

    private int mNativeData;
    private Thread mThread;
    private boolean mStarted;
    private boolean mInterrupted;

    private final HashMap<String, Integer> mPasskeyAgentRequestData;
    private final HashMap<String, Integer> mAuthorizationAgentRequestData;
    private final HashMap<String, Integer> mAuthorizationRequestData;
    private final List<Integer> mGattRequestData;
    private final BluetoothService mBluetoothService;
    private final BluetoothAdapter mAdapter;
    private final BluetoothAdapterStateMachine mBluetoothState;
    private BluetoothA2dp mA2dp;
    private final Context mContext;
    // The WakeLock is used for bringing up the LCD during a pairing request
    // from remote device when Android is in Suspend state.
    private PowerManager.WakeLock mWakeLock;

    private static final int EVENT_PAIRING_CONSENT_DELAYED_ACCEPT = 1;
    private static final int EVENT_AGENT_CANCEL = 2;
    private static final int EVENT_PAIRING_TIMEOUT = 3;
    private static final int EVENT_SAP_USER_TIMEOUT = 4;
    private static final int EVENT_DUN_USER_TIMEOUT = 5;

    private static final int CREATE_DEVICE_ALREADY_EXISTS = 1;
    private static final int CREATE_DEVICE_SUCCESS = 0;
    private static final int CREATE_DEVICE_FAILED = -1;

    // The 1.2/2.0 incoming pairing request would timeout in 30 seconds at LMP since LMP
    // Response time out is 30 seconds. Setting the INCOMING_PAIRING_TIMEOUT to 26 seconds
    // to make sure that the pairing clean up happens from the HOST side.
    private static final int INCOMING_PAIRING_TIMEOUT = 26000;
    private static final int USER_CONFIRM_TIMEOUT = 30000;
    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    // package and class name to which we send intent to check sap access permission
    private static final String ACCESS_REQUEST_PACKAGE = "com.android.settings";
    private static final String ACCESS_REQUEST_CLASS =
                         "com.android.settings.bluetooth.BluetoothPermissionRequest";

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String address = null;
            switch (msg.what) {
            case EVENT_PAIRING_CONSENT_DELAYED_ACCEPT:
                address = (String)msg.obj;
                if (address != null) {
                    mBluetoothService.setPairingConfirmation(address, true);
                }
                break;
            case EVENT_AGENT_CANCEL:
                // Set the Bond State to BOND_NONE.
                // We always have only 1 device in BONDING state.
                String[] devices = mBluetoothService.listInState(BluetoothDevice.BOND_BONDING);
                if (devices.length == 0) {
                    break;
                } else if (devices.length > 1) {
                    Log.e(TAG, " There is more than one device in the Bonding State");
                    break;
                }
                address = devices[0];
                mBluetoothService.setBondState(address,
                        BluetoothDevice.BOND_NONE,
                        BluetoothDevice.UNBOND_REASON_REMOTE_AUTH_CANCELED);
                break;
            case EVENT_PAIRING_TIMEOUT:
                address = (String) msg.obj;
                Log.d(TAG, "Cancelling bond process");
                mBluetoothService.cancelBondProcess(address);
                break;
            case EVENT_SAP_USER_TIMEOUT:
                Log.d(TAG, "SAP user Authorization timeout");
                Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
                intent.setClassName(ACCESS_REQUEST_PACKAGE, ACCESS_REQUEST_CLASS);
                      intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                      BluetoothDevice.REQUEST_TYPE_SIM_ACCESS);
                mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
                break;
          case EVENT_DUN_USER_TIMEOUT:
                Log.d(TAG, "DUN user Authorization timeout");

                Intent intent1 = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
                intent1.setClassName(ACCESS_REQUEST_PACKAGE, ACCESS_REQUEST_CLASS);
                      intent1.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                      BluetoothDevice. REQUEST_TYPE_DUN_ACCESS);
                mContext.sendBroadcast(intent1, BLUETOOTH_ADMIN_PERM);

                break;
            }
        }
    };

    static { classInitNative(); }
    private static native void classInitNative();

    /* package */ BluetoothEventLoop(Context context, BluetoothAdapter adapter,
                                     BluetoothService bluetoothService,
                                     BluetoothAdapterStateMachine bluetoothState) {
        mBluetoothService = bluetoothService;
        mContext = context;
        mBluetoothState = bluetoothState;
        mPasskeyAgentRequestData = new HashMap<String, Integer>();
        mAuthorizationAgentRequestData = new HashMap<String, Integer>();
        mAuthorizationRequestData = new HashMap<String, Integer>();
        mGattRequestData = new ArrayList<Integer>();
        mAdapter = adapter;
        //WakeLock instantiation in BluetoothEventLoop class
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, TAG);
        mWakeLock.setReferenceCounted(false);
        initializeNativeDataNative();
    }

    /*package*/ void getProfileProxy() {
        mAdapter.getProfileProxy(mContext, mProfileServiceListener, BluetoothProfile.A2DP);
        mAdapter.getProfileProxy(mContext, mProfileServiceListener, BluetoothProfile.INPUT_DEVICE);
    }

    private BluetoothProfile.ServiceListener mProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.A2DP) {
                mA2dp = (BluetoothA2dp) proxy;
            }
        }
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) {
                mA2dp = null;
            }
        }
    };


    protected void finalize() throws Throwable {
        try {
            cleanupNativeDataNative();
        } finally {
            super.finalize();
        }
    }

    /* package */ HashMap<String, Integer> getPasskeyAgentRequestData() {
        return mPasskeyAgentRequestData;
    }

    /* package */ HashMap<String, Integer> getAuthorizationAgentRequestData() {
        return mAuthorizationAgentRequestData;
    }

    /* package */ HashMap<String, Integer> getAuthorizationRequestData() {
        return mAuthorizationRequestData;
    }

    /* package */ List<Integer> getGattRequestData() {
        return mGattRequestData;
    }

    /* package */ void start() {

        if (!isEventLoopRunningNative()) {
            if (DBG) log("Starting Event Loop thread");
            startEventLoopNative();
        }
    }

    public void stop() {
        if (isEventLoopRunningNative()) {
            if (DBG) log("Stopping Event Loop thread");
            stopEventLoopNative();
        }
    }

    public boolean isEventLoopRunning() {
        return isEventLoopRunningNative();
    }

    private void addDevice(String address, String[] properties) {
        BluetoothDeviceProperties deviceProperties =
                mBluetoothService.getDeviceProperties();
        deviceProperties.addProperties(address, properties, true);
        String rssi = deviceProperties.getProperty(address, "RSSI");
        String broadcaster = deviceProperties.getProperty(address, "Broadcaster");
        String classValue = deviceProperties.getProperty(address, "Class");
        String devType = deviceProperties.getProperty(address, "Type");
        String name = deviceProperties.getProperty(address, "Name");
        String addr = deviceProperties.getProperty(address, "Address");
        String uuids = deviceProperties.getProperty(address, "UUIDs");
        short rssiValue;
        // For incoming connections, we don't get the RSSI value. Use a default of MIN_VALUE.
        // If we accept the pairing, we will automatically show it at the top of the list.
        if (rssi != null) {
            rssiValue = (short)Integer.valueOf(rssi).intValue();
        } else {
            rssiValue = Short.MIN_VALUE;
        }
        if (classValue != null) {
            Intent intent = new Intent(BluetoothDevice.ACTION_FOUND);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
            intent.putExtra(BluetoothDevice.EXTRA_CLASS,
                    new BluetoothClass(Integer.valueOf(classValue)));
            intent.putExtra(BluetoothDevice.EXTRA_RSSI, rssiValue);
            intent.putExtra(BluetoothDevice.EXTRA_NAME, name);
            if(uuids != null) {
                intent.putExtra(BluetoothDevice.EXTRA_UUIDS, uuids);
            }

            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (devType != null) {
            if (DBG) log("Device " + addr + " type: " + devType + " Broadcaster: " + broadcaster);
            if ("LE".equals(devType) && "false".equals(broadcaster)) {
                Intent intent = new Intent(BluetoothDevice.ACTION_FOUND);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
                intent.putExtra(BluetoothDevice.EXTRA_RSSI, rssiValue);
                intent.putExtra(BluetoothDevice.EXTRA_NAME, name);
                if(uuids != null) {
                    intent.putExtra(BluetoothDevice.EXTRA_UUIDS, uuids);
                }

                mContext.sendBroadcast(intent, BLUETOOTH_PERM);
            }
        } else {
            log ("ClassValue: " + classValue + " for remote device: " + address + " is null");
        }
    }

    /**
     * Called by native code on a DeviceFound signal from org.bluez.Adapter.
     *
     * @param address the MAC address of the new device
     * @param properties an array of property keys and value strings
     *
     * @see BluetoothDeviceProperties#addProperties(String, String[])
     */
    private void onDeviceFound(String address, String[] properties) {
        if (properties == null) {
            Log.e(TAG, "ERROR: Remote device properties are null");
            return;
        }
        if (!mBluetoothService.isEnabled()) {
            Log.e(TAG, "Bluetooth is not on");
            return;
        }

        addDevice(address, properties);
    }

    /**
     * Called by native code on a DeviceDisappeared signal from
     * org.bluez.Adapter.
     *
     * @param address the MAC address of the disappeared device
     */
    private void onDeviceDisappeared(String address) {
        Intent intent = new Intent(BluetoothDevice.ACTION_DISAPPEARED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }

    /**
     * Called by native code on a DisconnectRequested signal from
     * org.bluez.Device.
     *
     * @param deviceObjectPath the object path for the disconnecting device
     */
    private void onDeviceDisconnectRequested(String deviceObjectPath) {
        String address = mBluetoothService.getAddressFromObjectPath(deviceObjectPath);
        if (address == null) {
            Log.e(TAG, "onDeviceDisconnectRequested: Address of the remote device in null");
            return;
        }
        Intent intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }

    /**
     * Called by native code for the async response to a CreatePairedDevice
     * method call to org.bluez.Adapter.
     *
     * @param address the MAC address of the device to pair
     * @param result success or error result for the pairing operation
     */
    private void onCreatePairedDeviceResult(String address, int result) {
        address = address.toUpperCase();
        mBluetoothService.onCreatePairedDeviceResult(address, result);
    }

    /**
     * Called by native code on a DeviceCreated signal from org.bluez.Adapter.
     *
     * @param deviceObjectPath the object path for the created device
     */
    private void onDeviceCreated(String deviceObjectPath) {
        String address = mBluetoothService.getAddressFromObjectPath(deviceObjectPath);
        if (address == null) {
            Log.e(TAG, "onDeviceCreated: device address null!" + " deviceObjectPath: " +
                  deviceObjectPath);
            return;
        }
        if (!mBluetoothService.isRemoteDeviceInCache(address)) {
            // Incoming connection, we haven't seen this device, add to cache.

            String[] properties = mBluetoothService.getRemoteDeviceProperties(address);
            if (properties != null) {
                addDevice(address, properties);
            }
        }
    }

    /**
     * Called by native code on a DeviceRemoved signal from org.bluez.Adapter.
     *
     * @param deviceObjectPath the object path for the removed device
     */
    private void onDeviceRemoved(String deviceObjectPath) {
        String address = mBluetoothService.getAddressFromObjectPath(deviceObjectPath);
        if (address != null) {
            mBluetoothService.setBondState(address.toUpperCase(), BluetoothDevice.BOND_NONE,
                BluetoothDevice.UNBOND_REASON_REMOVED);
            mBluetoothService.clearRemoteDeviceGattServices(address);
            mBluetoothService.setRemoteDeviceProperty(address, "UUIDs", null);
            mBluetoothService.setRemoteDeviceProperty(address, "Services", null);
            mBluetoothService.setRemoteDeviceProperty(address, "Trusted", "false");
        }
    }

    /**
     * Called by native code on a PropertyChanged signal from
     * org.bluez.Adapter. This method is also called from
     * {@link BluetoothAdapterStateMachine} to set the "Pairable"
     * property when Bluetooth is enabled.
     *
     * @param propValues a string array containing the key and one or more
     *  values.
     */
    /*package*/ void onPropertyChanged(String[] propValues) {
        BluetoothAdapterProperties adapterProperties =
                mBluetoothService.getAdapterProperties();

        if (adapterProperties.isEmpty()) {
            // We have got a property change before
            // we filled up our cache.
            adapterProperties.getAllProperties();
        }
        log("Property Changed: " + propValues[0] + " : " + propValues[1]);
        String name = propValues[0];

        if (name.equals("Name")) {
            adapterProperties.setProperty(name, propValues[1]);
            Intent intent = new Intent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_LOCAL_NAME, propValues[1]);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (name.equals("Pairable") || name.equals("Discoverable")) {
            adapterProperties.setProperty(name, propValues[1]);

            if (name.equals("Discoverable")) {
                mBluetoothState.sendMessage(BluetoothAdapterStateMachine.SCAN_MODE_CHANGED);
            }

            String pairable = name.equals("Pairable") ? propValues[1] :
                adapterProperties.getProperty("Pairable");
            String discoverable = name.equals("Discoverable") ? propValues[1] :
                adapterProperties.getProperty("Discoverable");

            // This shouldn't happen, unless Adapter Properties are null.
            if (pairable == null || discoverable == null)
                return;

            int mode = BluetoothService.bluezStringToScanMode(
                    pairable.equals("true"),
                    discoverable.equals("true"));
            if (mode >= 0) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                intent.putExtra(BluetoothAdapter.EXTRA_SCAN_MODE, mode);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                mContext.sendBroadcast(intent, BLUETOOTH_PERM);
            }
        } else if (name.equals("Discovering")) {
            Intent intent;
            adapterProperties.setProperty(name, propValues[1]);
            if (propValues[1].equals("true")) {
                intent = new Intent(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            } else {
                // Stop the discovery.
                mBluetoothService.cancelDiscovery();
                intent = new Intent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            }
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (name.equals("Devices") || name.equals("UUIDs")) {
            String value = null;
            int len = Integer.valueOf(propValues[1]);
            if (len > 0) {
                StringBuilder str = new StringBuilder();
                for (int i = 2; i < propValues.length; i++) {
                    str.append(propValues[i]);
                    str.append(",");
                }
                value = str.toString();
            }
            String adapterObjectPath = adapterProperties.getObjectPath();
            if ((value != null)  && name.equals("UUIDs")) {
                adapterProperties.setProperty(name, value);
                mBluetoothService.updateBluetoothState(value);
            } else if ((value != null) && (value.startsWith(adapterObjectPath))) {
                // Devices Prop expect value starts with obj path
                adapterProperties.setProperty(name, value);
            }
        } else if (name.equals("Powered")) {
            mBluetoothState.sendMessage(BluetoothAdapterStateMachine.POWER_STATE_CHANGED,
                propValues[1].equals("true") ? new Boolean(true) : new Boolean(false));
        } else if (name.equals("DiscoverableTimeout")) {
            adapterProperties.setProperty(name, propValues[1]);
        } else if (name.equals("Class")) {
            adapterProperties.setProperty(name, propValues[1]);
        }
    }

    /**
     * Called by native code on a PropertyChanged signal from
     * org.bluez.Device.
     *
     * @param deviceObjectPath the object path for the changed device
     * @param propValues a string array containing the key and one or more
     *  values.
     */
    private void onDevicePropertyChanged(String deviceObjectPath, String[] propValues) {
        String name = propValues[0];
        byte[] connProp = null;
        String address = mBluetoothService.getAddressFromObjectPath(deviceObjectPath);
        if (address == null) {
            Log.e(TAG, "onDevicePropertyChanged: Address of the remote device in null");
            return;
        }

        if (name.equals("Connected")) {
            int strLen = propValues[1].length();
            connProp = new byte[strLen/2];
            for(int i = 0; i < connProp.length; i++) {
                int pos = i * 2;
                int value = Integer.parseInt(propValues[1].substring(pos, pos+2), 16);
                connProp[i] = (byte) value;
                Log.d(TAG, " Connected property at index : "
                      + i + "is :" + connProp[i]);
            }
        }

        if (!mBluetoothService.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");

            if (name.equals("Connected") && (connProp[0] == 0)) {
                Intent intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                BluetoothDevice device = mAdapter.getRemoteDevice(address);
                mBluetoothService.sendDeviceConnectionStateChange(
                    device, BluetoothAdapter.STATE_DISCONNECTED);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                intent.putExtra(BluetoothDevice.EXTRA_REASON, connProp[1]);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                mContext.sendBroadcast(intent, BLUETOOTH_PERM);
            }
            return;
        }

        log("Device property changed: " + address + " property: "
            + name + " value: " + propValues[1]);

        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        if (name.equals("Name")) {
            mBluetoothService.setRemoteDeviceProperty(address, name, propValues[1]);
            Intent intent = new Intent(BluetoothDevice.ACTION_NAME_CHANGED);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothDevice.EXTRA_NAME, propValues[1]);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (name.equals("Alias")) {
            mBluetoothService.setRemoteDeviceProperty(address, name, propValues[1]);
            Intent intent = new Intent(BluetoothDevice.ACTION_ALIAS_CHANGED);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (name.equals("Class")) {
            mBluetoothService.setRemoteDeviceProperty(address, name, propValues[1]);
            Intent intent = new Intent(BluetoothDevice.ACTION_CLASS_CHANGED);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothDevice.EXTRA_CLASS,
                    new BluetoothClass(Integer.valueOf(propValues[1])));
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (name.equals("RSSI")) {
            mBluetoothService.setRemoteDeviceProperty(address, name, propValues[1]);
            Intent intent = new Intent(BluetoothDevice.ACTION_RSSI_UPDATE);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothDevice.EXTRA_RSSI, propValues[1]);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (name.equals("Connected")) {
            Log.d(TAG, "Device property Connected: " + propValues[1]);

            if(connProp[0] == 0)
                mBluetoothService.setRemoteDeviceProperty(address, name, "false");
            else
                mBluetoothService.setRemoteDeviceProperty(address, name, "true");

            Intent intent = null;
            if (connProp[0] == 1) {
                intent = new Intent(BluetoothDevice.ACTION_ACL_CONNECTED);
                // Set the link timeout to 8000 slots (5 sec timeout)
                // for bluetooth docks.
                if (mBluetoothService.isBluetoothDock(address)) {
                    mBluetoothService.setLinkTimeout(address, 8000);
                }
                mBluetoothService.sendDeviceConnectionStateChange
                    (device, BluetoothAdapter.STATE_CONNECTED);
            } else {
                // Check and clean-up if bonding is in progress
                if (mBluetoothService.getBondState(address) ==
                        BluetoothDevice.BOND_BONDING) {
                    mBluetoothService.setBondState(address,
                          BluetoothDevice.BOND_NONE);
                }

                mBluetoothService.clearGattServicesRefCount(address);

                if (mBluetoothService.getBondState(address) == BluetoothDevice.BOND_NONE)
                    mBluetoothService.clearRemoteDeviceGattServices(address);

                intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                mBluetoothService.sendDeviceConnectionStateChange(
                    device, BluetoothAdapter.STATE_DISCONNECTED);
            }
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothDevice.EXTRA_REASON, connProp[1]);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        } else if (name.equals("UUIDs")) {
            String uuid = null;
            int len = Integer.valueOf(propValues[1]);
            if (len > 0) {
                StringBuilder str = new StringBuilder();
                for (int i = 2; i < propValues.length; i++) {
                    str.append(propValues[i]);
                    str.append(",");
                }
                uuid = str.toString();
            }
            mBluetoothService.setRemoteDeviceProperty(address, name, uuid);

            // UUIDs have changed, query remote service channel and update cache.
            mBluetoothService.updateDeviceServiceChannelCache(address);

            mBluetoothService.sendUuidIntent(address);
        } else if (name.equals("Services")) {
            String services = null;
            int len = Integer.valueOf(propValues[1]);
            if (len > 0) {
                StringBuilder str = new StringBuilder();
                for (int i = 2; i < propValues.length; i++) {
                    str.append(propValues[i]);
                    str.append(",");
                }
                services = str.toString();
            }
            mBluetoothService.setRemoteDeviceProperty(address, name, services);

            mBluetoothService.sendGattIntent(address, BluetoothDevice.GATT_RESULT_SUCCESS);

        } else if (name.equals("Paired")) {
            if (propValues[1].equals("true")) {
                // If locally initiated pairing, we will
                // not go to BOND_BONDED state until we have received a
                // successful return value in onCreatePairedDeviceResult

                // There can be one outgoing and one incoming pairing at the same time
                if (!address.equals(mBluetoothService.getPendingOutgoingBonding())) {
                    mBluetoothService.setBondState(address, BluetoothDevice.BOND_BONDED);
                }
            } else {
                mBluetoothService.setBondState(address, BluetoothDevice.BOND_NONE);
                mBluetoothService.setRemoteDeviceProperty(address, "Trusted", "false");
            }
        } else if (name.equals("Trusted")) {
            if (DBG)
                log("set trust state succeeded, value is: " + propValues[1]);
            mBluetoothService.setRemoteDeviceProperty(address, name, propValues[1]);
        } else if (name.equals("LeConnParams")) {
            mBluetoothService.setRemoteDeviceProperty(address, name, propValues[1]);
            Intent intent = new Intent(BluetoothDevice.ACTION_LE_CONN_PARAMS);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothDevice.EXTRA_CONN_INTERVAL, Integer.valueOf(propValues[1]));
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    /**
     * Called by native code on a PropertyChanged signal from
     * org.bluez.Input.
     *
     * @param path the object path for the changed input device
     * @param propValues a string array containing the key and one or more
     *  values.
     */
    private void onInputDevicePropertyChanged(String path, String[] propValues) {
        String address = mBluetoothService.getAddressFromObjectPath(path);
        if (address == null) {
            Log.e(TAG, "onInputDevicePropertyChanged: Address of the remote device is null");
            return;
        }
        log("Input Device : Name of Property is: " + propValues[0]);
        boolean state = false;
        if (propValues[1].equals("true")) {
            state = true;
        }
        mBluetoothService.handleInputDevicePropertyChange(address, state);
    }

    /**
     * Called by native code on a PropertyChanged signal from
     * org.bluez.Network.
     *
     * @param deviceObjectPath the object path for the changed PAN device
     * @param propValues a string array containing the key and one or more
     *  values.
     */
    private void onPanDevicePropertyChanged(String deviceObjectPath, String[] propValues) {
        String name = propValues[0];
        String address = mBluetoothService.getAddressFromObjectPath(deviceObjectPath);
        if (address == null) {
            Log.e(TAG, "onPanDevicePropertyChanged: Address of the remote device in null");
            return;
        }
        if (DBG) {
            log("Pan Device property changed: " + address + "  property: "
                    + name + " value: "+ propValues[1]);
        }
        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        if (name.equals("Connected")) {
            if (propValues[1].equals("false")) {
                mBluetoothService.handlePanDeviceStateChange(device,
                                          BluetoothPan.STATE_DISCONNECTED,
                                          BluetoothPan.LOCAL_PANU_ROLE);
            }
        } else if (name.equals("Interface")) {
            String iface = propValues[1];
            if (!iface.equals("")) {
                mBluetoothService.handlePanDeviceStateChange(device, iface,
                                              BluetoothPan.STATE_CONNECTED,
                                              BluetoothPan.LOCAL_PANU_ROLE);
            }
        }
    }

    private String checkAuthorizationRequestAndGetAddress(String objectPath, int nativeData) {
        String address = mBluetoothService.getAddressFromObjectPath(objectPath);
        if (address == null) {
            Log.e(TAG, "Unable to get device address in checkAuthorizationRequestAndGetAddress, " +
                  "returning null");
            return null;
        }
        address = address.toUpperCase();
        mAuthorizationRequestData.put(address, new Integer(nativeData));

        if (mBluetoothService.getBluetoothState() == BluetoothAdapter.STATE_TURNING_OFF) {
            // shutdown path
            return null;
        }
        return address;
    }

    private String checkPairingRequestAndGetAddress(String objectPath, int nativeData) {
        String address = mBluetoothService.getAddressFromObjectPath(objectPath);
        if (address == null) {
            Log.e(TAG, "Unable to get device address in checkPairingRequestAndGetAddress, " +
                  "returning null");
            return null;
        }
        address = address.toUpperCase();
        mPasskeyAgentRequestData.put(address, new Integer(nativeData));

        if (mBluetoothService.getBluetoothState() == BluetoothAdapter.STATE_TURNING_OFF) {
            // shutdown path
            mBluetoothService.cancelPairingUserInput(address);
            return null;
        }
        // Set state to BONDING. For incoming connections it will be set here.
        // For outgoing connections, it gets set when we call createBond.
        // Also set it only when the state is not already Bonded, we can sometimes
        // get an authorization request from the remote end if it doesn't have the link key
        // while we still have it.
        if (mBluetoothService.getBondState(address) != BluetoothDevice.BOND_BONDED)
            mBluetoothService.setBondState(address, BluetoothDevice.BOND_BONDING);
        return address;
    }

    /**
     * Called by native code on a RequestPairingConsent method call to
     * org.bluez.Agent.
     *
     * @param objectPath the path of the device to request pairing consent for
     * @param nativeData a native pointer to the original D-Bus message
     */
    private void onRequestPairingConsent(String objectPath, int nativeData) {
        String address = checkPairingRequestAndGetAddress(objectPath, nativeData);
        if (address == null) return;

        /* The link key will not be stored if the incoming request has MITM
         * protection switched on. Unfortunately, some devices have MITM
         * switched on even though their capabilities are NoInputNoOutput,
         * so we may get this request many times. Also if we respond immediately,
         * the other end is unable to handle it. Delay sending the message.
         */
        if (mBluetoothService.getBondState(address) == BluetoothDevice.BOND_BONDED) {
            Message message = mHandler.obtainMessage(EVENT_PAIRING_CONSENT_DELAYED_ACCEPT);
            message.obj = address;
            mHandler.sendMessageDelayed(message, 1500);
            return;
        }
        // Acquire wakelock during PIN code request to bring up LCD display
        mWakeLock.acquire();
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                        BluetoothDevice.PAIRING_VARIANT_CONSENT);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
        // Release wakelock to allow the LCD to go off after the PIN popup notification.
        mWakeLock.release();
        return;
    }

    /**
     * Called by native code on a RequestConfirmation method call to
     * org.bluez.Agent.
     *
     * @param objectPath the path of the device to confirm the passkey for
     * @param passkey an integer containing the 6-digit passkey to confirm
     * @param nativeData a native pointer to the original D-Bus message
     */
    private void onRequestPasskeyConfirmation(String objectPath, int passkey, int nativeData) {
        String address = checkPairingRequestAndGetAddress(objectPath, nativeData);
        if (address == null) return;
        // Acquire wakelock during PIN code request to bring up LCD display
        mWakeLock.acquire();
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, passkey);
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
        // Release wakelock to allow the LCD to go off after the PIN popup notification.
        mWakeLock.release();
        return;
    }

    /**
     * Called by native code on a RequestPasskey method call to
     * org.bluez.Agent.
     *
     * @param objectPath the path of the device requesting a passkey
     * @param nativeData a native pointer to the original D-Bus message
     */
    private void onRequestPasskey(String objectPath, int nativeData) {
        String address = checkPairingRequestAndGetAddress(objectPath, nativeData);
        if (address == null) return;
        // Acquire wakelock during PIN code request to bring up LCD display
        mWakeLock.acquire();
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                BluetoothDevice.PAIRING_VARIANT_PASSKEY);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
        // Release wakelock to allow the LCD to go off after the PIN popup notification.
        mWakeLock.release();
        return;
    }

    private void onSapAuthorize(String objectPath, String uuid, int nativeData) {
        Log.i(TAG, "onSapAuthorize" + objectPath + uuid);
        String address = checkAuthorizationRequestAndGetAddress(objectPath, nativeData);
        if (address == null) {
            Log.e(TAG, "address is null");
            mBluetoothService.sapAuthorize(address, false);
            return;
        }
        /*Get the Trust state of the device*/
        boolean trusted = mBluetoothService.getTrustState(address);
        if (trusted) {
            /*Say as authorized to lower layers without popping up to
            user*/
            mBluetoothService.sapAuthorize(address, true);
        } else {
            BluetoothDevice remoteDevice = mBluetoothService.getRemoteDevice(address);
            Intent intent = new
                Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
            intent.setClassName(ACCESS_REQUEST_PACKAGE, ACCESS_REQUEST_CLASS);
            intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                            BluetoothDevice.REQUEST_TYPE_SIM_ACCESS);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice);
            intent.putExtra("uuid", uuid);
            intent.putExtra("name", mBluetoothService.getRemoteName(address));
            intent.putExtra("address", address);
            mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
            mHandler.sendMessageDelayed(mHandler
                   .obtainMessage(EVENT_SAP_USER_TIMEOUT),
                    USER_CONFIRM_TIMEOUT);

        }
    }

    private void onDUNAuthorize(String objectPath, String uuid, int nativeData) {
        Log.i(TAG, "onDUNAuthorize" + objectPath + uuid);
        String address = checkAuthorizationRequestAndGetAddress(objectPath, nativeData);
        if (address == null) {
            Log.e(TAG, "address is null");
            mBluetoothService.DUNAuthorize(address, false);
            return;
        }
        /*Get the Trust state of the device*/
        boolean trusted = mBluetoothService.getTrustState(address);
        if (trusted) {
            /*Say as authorized to lower layers without popping up to
            user*/
            Log.d(TAG,"Trusted device connection");
            mBluetoothService.DUNAuthorize(address, true);
        } else {
            Log.v(TAG,"Get the remote address");
            BluetoothDevice remoteDevice = mBluetoothService.getRemoteDevice(address);
            Intent intent = new
                Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
            intent.setClassName(ACCESS_REQUEST_PACKAGE, ACCESS_REQUEST_CLASS);
            intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                            BluetoothDevice. REQUEST_TYPE_DUN_ACCESS);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice);
            intent.putExtra("name", mBluetoothService.getRemoteName(address));
            intent.putExtra("address", address);
            intent.putExtra("uuid", uuid);
            mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
            mHandler.sendMessageDelayed(mHandler
                 .obtainMessage(EVENT_DUN_USER_TIMEOUT),
                 USER_CONFIRM_TIMEOUT);

        }
    }
    private void onSapStateChanged(String objectPath, String state, int nativeData) {
        Log.i(TAG, "onSapStateChanged" + objectPath + state);

        String address = mBluetoothService.getAddressFromObjectPath(objectPath);
        if (address == null) {
            Log.e(TAG, "Unable to get device address , " +
                  "returning null");
            return;
        }
        address = address.toUpperCase();

        int sapState;
        if(state.equals("Connected")) {
                /*2 corresponds to CONNECTED*/
                sapState = 2;
        } else  {
                sapState = 0;
        }
        Intent intent = new Intent(BluetoothService.SAP_STATECHANGE_INTENT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE,  mAdapter.getRemoteDevice(address));
        intent.putExtra("state", sapState);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }

    private void onDUNStateChanged(String objectPath, String state, int nativeData) {
        Log.i(TAG, "onDUNStateChanged" + objectPath + state);

        String address = mBluetoothService.getAddressFromObjectPath(objectPath);
        if (address == null) {
            Log.e(TAG, "Unable to get device address , " +
                  "returning null");
            return;
        }
        address = address.toUpperCase();

        int DUNState;
        if(state.equals("Connected")) {
                Log.v(TAG,"On DUN state changed ");
                /*2 corresponds to CONNECTED*/
                DUNState = 2;
        } else  {
                DUNState = 0;
        }
        Intent intent = new Intent(BluetoothService.DUN_STATECHANGE_INTENT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE,  mAdapter.getRemoteDevice(address));
        intent.putExtra("state", DUNState);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }

    private void onRequestPinCode(String objectPath, int nativeData, boolean secure) {
        String address = checkPairingRequestAndGetAddress(objectPath, nativeData);
        if (address == null) return;
        Log.i(TAG, "Secure pairing is "+ secure);
        String pendingOutgoingAddress =
                mBluetoothService.getPendingOutgoingBonding();
        BluetoothClass btClass = new BluetoothClass(mBluetoothService.getRemoteClass(address));
        int btDeviceClass = btClass.getDeviceClass();

        if (address.equals(pendingOutgoingAddress)) {
            // we initiated the bonding

            // Check if its a dock
            if (mBluetoothService.isBluetoothDock(address)) {
                String pin = mBluetoothService.getDockPin();
                mBluetoothService.setPin(address, BluetoothDevice.convertPinToBytes(pin));
                return;
            }

            // try 0000 once if the device looks dumb
            switch (btDeviceClass) {
            case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
            case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                if (mBluetoothService.attemptAutoPair(address)) return;
           }
        }

        if (btDeviceClass == BluetoothClass.Device.PERIPHERAL_KEYBOARD ||
            btDeviceClass == BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING) {
            // Its a keyboard. Follow the HID spec recommendation of creating the
            // passkey and displaying it to the user. If the keyboard doesn't follow
            // the spec recommendation, check if the keyboard has a fixed PIN zero
            // and pair.
            if (mBluetoothService.isFixedPinZerosAutoPairKeyboard(address)) {
                mBluetoothService.setPin(address, BluetoothDevice.convertPinToBytes("0000"));
                return;
            }

            // Generate a variable PIN. This is not truly random but good enough.
            int pin = (int) Math.floor(Math.random() * 10000);
            mBluetoothService.setBondState(address, BluetoothDevice.BOND_BONDING);
            sendDisplayPinIntent(address, pin);
            return;
        }
        // Acquire wakelock during PIN code request to bring up LCD display
        mWakeLock.acquire();
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.PAIRING_VARIANT_PIN);
        intent.putExtra(BluetoothDevice.EXTRA_SECURE_PAIRING, secure);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
        // Release wakelock to allow the LCD to go off after the PIN popup notification.
        mWakeLock.release();
        if (mHandler.hasMessages(EVENT_PAIRING_TIMEOUT)) {
            Log.d(TAG, "Ther is EVENT_PAIRING_TIMEOUT message");
            mHandler.removeMessages(EVENT_PAIRING_TIMEOUT);
        }
        if (!(address.equals(pendingOutgoingAddress))) {
            Message msg = mHandler.obtainMessage(EVENT_PAIRING_TIMEOUT);
            msg.obj = address;
            mHandler.sendMessageDelayed(msg, INCOMING_PAIRING_TIMEOUT);
            Log.d(TAG, "Queuing INCOMING_PAIRING_TIMEOUT msg");
        }

        return;
    }

    /**
     * Called by native code on a DisplayPasskey method call to
     * org.bluez.Agent.
     *
     * @param objectPath the path of the device to display the passkey for
     * @param passkey an integer containing the 6-digit passkey
     * @param nativeData a native pointer to the original D-Bus message
     */
    private void onDisplayPasskey(String objectPath, int passkey, int nativeData) {
        String address = checkPairingRequestAndGetAddress(objectPath, nativeData);
        if (address == null) return;

        // Acquire wakelock during PIN code request to bring up LCD display
        mWakeLock.acquire();
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, passkey);
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                        BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
        //Release wakelock to allow the LCD to go off after the PIN popup notification.
        mWakeLock.release();
    }

    private void sendDisplayPinIntent(String address, int pin) {
        // Acquire wakelock during PIN code request to bring up LCD display
        mWakeLock.acquire();
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, pin);
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                        BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
        //Release wakelock to allow the LCD to go off after the PIN popup notifcation.
        mWakeLock.release();
    }

    /**
     * Called by native code on a RequestOobData method call to
     * org.bluez.Agent.
     *
     * @param objectPath the path of the device requesting OOB data
     * @param nativeData a native pointer to the original D-Bus message
     */
    private void onRequestOobData(String objectPath, int nativeData) {
        String address = checkPairingRequestAndGetAddress(objectPath, nativeData);
        if (address == null) return;

        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }

    /**
     * Called by native code on an Authorize method call to org.bluez.Agent.
     *
     * @param objectPath the path of the device requesting to be authorized
     * @param deviceUuid the UUID of the requesting device
     * @param nativeData reference for native data
     */
    private void  onAgentAuthorize(String objectPath, String deviceUuid, int nativeData) {
        if (!mBluetoothService.isEnabled()) return;

        String address = mBluetoothService.getAddressFromObjectPath(objectPath);
        if (address == null) {
            Log.e(TAG, "Unable to get device address in onAuthAgentAuthorize");
            return;
        }

        boolean authorized = false;
        ParcelUuid uuid = ParcelUuid.fromString(deviceUuid);

        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        mAuthorizationAgentRequestData.put(address, new Integer(nativeData));

        // Bluez sends the UUID of the local service being accessed, _not_ the
        // remote service
        if (mA2dp != null &&
            (BluetoothUuid.isAudioSource(uuid) || BluetoothUuid.isAvrcpTarget(uuid)
              || BluetoothUuid.isAdvAudioDist(uuid)) &&
              !isOtherSinkInNonDisconnectedState(address)) {
            authorized = mA2dp.getPriority(device) > BluetoothProfile.PRIORITY_OFF;
            if (authorized && !BluetoothUuid.isAvrcpTarget(uuid)) {
                Log.i(TAG, "First check pass for incoming A2DP / AVRCP connection from " + address);
                // Some headsets try to connect AVCTP before AVDTP - against the recommendation
                // If AVCTP connection fails, we get stuck in IncomingA2DP state in the state
                // machine.  We don't handle AVCTP signals currently. We only send
                // intents for AVDTP state changes. We need to handle both of them in
                // some cases. For now, just don't move to incoming state in this case.
                mBluetoothService.notifyIncomingA2dpConnection(address, false);
            } else {
                // The below change will make sure A2DP connection is
                // established. If the remote device sends AVRCP connection
                // first DUT will accept the AVRCP connection and initiates
                // A2DP connection from handset.
                Log.i(TAG, "" + authorized +
                      "Incoming A2DP / AVRCP connection from " + address);
                mA2dp.allowIncomingConnect(device, authorized);
                mBluetoothService.notifyIncomingA2dpConnection(address, true);
            }
        } else if (BluetoothUuid.isInputDevice(uuid)) {
            // We can have more than 1 input device connected.
            authorized = mBluetoothService.getInputDevicePriority(device) >
                    BluetoothInputDevice.PRIORITY_OFF;
            if (authorized) {
                Log.i(TAG, "First check pass for incoming HID connection from " + address);
                // notify profile state change
                mBluetoothService.notifyIncomingHidConnection(address);
            } else {
                Log.i(TAG, "Rejecting incoming HID connection from " + address);
                mBluetoothService.allowIncomingProfileConnect(device, authorized);
            }
        } else if (BluetoothUuid.isBnep(uuid)) {
            // PAN doesn't go to the state machine, accept or reject from here
            authorized = mBluetoothService.allowIncomingTethering();
            mBluetoothService.allowIncomingProfileConnect(device, authorized);
        } else {
            Log.i(TAG, "Rejecting incoming " + deviceUuid + " connection from " + address);
            mBluetoothService.allowIncomingProfileConnect(device, authorized);
        }
        log("onAgentAuthorize(" + objectPath + ", " + deviceUuid + ") = " + authorized);
    }

    private boolean onAgentOutOfBandDataAvailable(String objectPath) {
        if (!mBluetoothService.isEnabled()) return false;

        String address = mBluetoothService.getAddressFromObjectPath(objectPath);
        if (address == null) return false;

        if (mBluetoothService.getDeviceOutOfBandData(
            mAdapter.getRemoteDevice(address)) != null) {
            return true;
        }
        return false;
    }

    private boolean isOtherSinkInNonDisconnectedState(String address) {
        List<BluetoothDevice> devices =
            mA2dp.getDevicesMatchingConnectionStates(new int[] {BluetoothA2dp.STATE_CONNECTED,
                                                     BluetoothA2dp.STATE_CONNECTING,
                                                     BluetoothA2dp.STATE_DISCONNECTING});

        if (devices.size() == 0) return false;
        for (BluetoothDevice dev: devices) {
            if (!dev.getAddress().equals(address)) return true;
        }
        return false;
    }

    /**
     * Called by native code on a Cancel method call to org.bluez.Agent.
     */
    private void onAgentCancel() {
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_CANCEL);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);

        mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_AGENT_CANCEL),
                   1500);

        return;
    }

    /**
     * Called by native code for the async response to a DiscoverServices
     * method call to org.bluez.Adapter.
     *
     * @param deviceObjectPath the path for the specified device
     * @param result true for success; false on error
     */
    private void onDiscoverServicesResult(String deviceObjectPath, boolean result) {
        if (!mBluetoothService.isEnabled()) {
            log("Bluetooth is not on");
            return;
        }

        String address = mBluetoothService.getAddressFromObjectPath(deviceObjectPath);
        if (address == null) return;

        // We don't parse the xml here, instead just query Bluez for the properties.
        if (address == null) {
            Log.e(TAG, "Unexpected error! address is null");
            return;
        }
        if (result) {
            mBluetoothService.updateRemoteDevicePropertiesCache(address);
        }
        mBluetoothService.sendUuidIntent(address);
        mBluetoothService.makeServiceChannelCallbacks(address);
    }

    /**
     * Called by native code for the async response to a CreateDevice
     * method call to org.bluez.Adapter.
     *
     * @param address the MAC address of the device to create
     * @param result {@link #CREATE_DEVICE_SUCCESS},
     *  {@link #CREATE_DEVICE_ALREADY_EXISTS} or {@link #CREATE_DEVICE_FAILED}}
     */
    private void onCreateDeviceResult(String address, int result) {
        if (DBG) log("Result of onCreateDeviceResult:" + result);
        BluetoothClass btClass = new BluetoothClass(mBluetoothService.getRemoteClass(address));
        int btDeviceClass = btClass.getDeviceClass();

        switch (result) {
        case CREATE_DEVICE_ALREADY_EXISTS:
            String path = mBluetoothService.getObjectPathFromAddress(address);
            Log.d(TAG, "Device exists " + path);
            if (path != null) {
                Log.d(TAG, "Start service discovery " + path);
                String devType = mBluetoothService.getUpdatedRemoteDeviceProperty(address, "Type");
                if ((devType != null)  && "LE".equals(devType))
                    mBluetoothService.discoverPrimaryServicesNative(path);
                else {
                    mBluetoothService.discoverServicesNative(path, "");
                    if (btDeviceClass == BluetoothClass.Device.PERIPHERAL_POINTING) {
                        log("The device is HID pointing device,moving pairing state");
                        mBluetoothService.setBondState(address, BluetoothDevice.BOND_BONDED);
                    }
                }
                break;
            }
            Log.w(TAG, "Device exists, but we don't have the bluez path, failing");
            // fall-through
        case CREATE_DEVICE_FAILED:
            mBluetoothService.sendUuidIntent(address);
            mBluetoothService.sendGattIntent(address, BluetoothDevice.GATT_RESULT_FAIL);
            mBluetoothService.makeServiceChannelCallbacks(address);
            if (btDeviceClass == BluetoothClass.Device.PERIPHERAL_POINTING) {
                log("The device is HID pointing device,moving pairing state to BOND_NONE");
                mBluetoothService.setBondState(address, BluetoothDevice.BOND_NONE);
            }

            break;
        case CREATE_DEVICE_SUCCESS:
            // nothing to do, UUID intent's will be sent via property changed
            if (btDeviceClass == BluetoothClass.Device.PERIPHERAL_POINTING) {
                log("The device is HID pointing device,moving pairing state");
                mBluetoothService.setBondState(address, BluetoothDevice.BOND_BONDED);
            }
        }
    }

    private void onAddToPreferredDeviceListResult(int result) {
        Log.d(TAG,"Result of onAddToPreferredDeviceListResult callback event loop java:"+result);
        try {
            mBluetoothService.onAddToPreferredDeviceListResult(result);
        }
        catch(Exception e) {
            {Log.e(TAG, "", e);}
        }
    }
    private void onRemoveFromPreferredDeviceListResult(int result) {
        Log.d(TAG,"Result of onRemoveFromPreferredDeviceListResult callback event loop java:"+result);
        try {
            mBluetoothService.onRemoveFromPreferredDeviceListResult(result);
        }
        catch(Exception e) {
            {Log.e(TAG, "", e);}
        }
    }
    private void onClearPreferredDeviceListResult(int result) {
        Log.d(TAG,"Result of onClearPreferredDeviceListResult callback event loop java:"+result);
        try {
            mBluetoothService.onClearPreferredDeviceListResult(result);
        }
        catch(Exception e) {
            {Log.e(TAG, "", e);}
        }
    }
    private void onGattConnectToPreferredDeviceListResult(int result) {
        Log.d(TAG,"Result of onGattConnectToPreferredDeviceListResult callback event loop java:"+result);
        try {
            mBluetoothService.onGattConnectToPreferredDeviceListResult(result);
        }
        catch(Exception e) {
            {Log.e(TAG, "", e);}
        }
    }
    private void onGattCancelConnectToPreferredDeviceListResult(int result) {
        Log.d(TAG,"Result of onGattCancelConnectToPreferredDeviceListResult callback event loop java:"+result);
        try {
            mBluetoothService.onGattCancelConnectToPreferredDeviceListResult(result);
        }
        catch(Exception e) {
            {Log.e(TAG, "", e);}
        }
    }

    /**
     * Called by native code for the async response to a Connect
     * method call to org.bluez.Input.
     *
     * @param path the path of the specified input device
     * @param result Result code of the operation.
     */
    private void onInputDeviceConnectionResult(String path, int result) {
        // Success case gets handled by Property Change signal
        if (result != BluetoothInputDevice.INPUT_OPERATION_SUCCESS) {
            String address = mBluetoothService.getAddressFromObjectPath(path);
            if (address == null) return;

            boolean connected = false;
            BluetoothDevice device = mAdapter.getRemoteDevice(address);
            int state = mBluetoothService.getInputDeviceConnectionState(device);
            if (state == BluetoothInputDevice.STATE_CONNECTING) {
                if (result == BluetoothInputDevice.INPUT_CONNECT_FAILED_ALREADY_CONNECTED) {
                    connected = true;
                } else {
                    connected = false;
                }
            } else if (state == BluetoothInputDevice.STATE_DISCONNECTING) {
                if (result == BluetoothInputDevice.INPUT_DISCONNECT_FAILED_NOT_CONNECTED) {
                    connected = false;
                } else {
                    // There is no better way to handle this, this shouldn't happen
                    connected = true;
                }
            } else {
                Log.e(TAG, "Error onInputDeviceConnectionResult. State is:" + state);
            }
            mBluetoothService.handleInputDevicePropertyChange(address, connected);
        }
    }

    /**
     * Called by native code for the async response to a Connect
     * method call to org.bluez.Network.
     *
     * @param path the path of the specified PAN device
     * @param result Result code of the operation.
     */
    private void onPanDeviceConnectionResult(String path, int result) {
        log ("onPanDeviceConnectionResult " + path + " " + result);
        // Success case gets handled by Property Change signal
        if (result != BluetoothPan.PAN_OPERATION_SUCCESS) {
            String address = mBluetoothService.getAddressFromObjectPath(path);
            if (address == null) return;

            boolean connected = false;
            BluetoothDevice device = mAdapter.getRemoteDevice(address);
            int state = mBluetoothService.getPanDeviceConnectionState(device);
            if (state == BluetoothPan.STATE_CONNECTING) {
                if (result == BluetoothPan.PAN_CONNECT_FAILED_ALREADY_CONNECTED) {
                    connected = true;
                } else {
                    connected = false;
                }
            } else if (state == BluetoothPan.STATE_DISCONNECTING) {
                if (result == BluetoothPan.PAN_DISCONNECT_FAILED_NOT_CONNECTED) {
                    connected = false;
                } else {
                    // There is no better way to handle this, this shouldn't happen
                    connected = true;
                }
            } else {
                Log.e(TAG, "Error onPanDeviceConnectionResult. State is: "
                        + state + " result: "+ result);
            }
            int newState = connected? BluetoothPan.STATE_CONNECTED :
                BluetoothPan.STATE_DISCONNECTED;
            mBluetoothService.handlePanDeviceStateChange(device, newState,
                                                  BluetoothPan.LOCAL_PANU_ROLE);
        }
    }

    /**
     * Called by native code for the async response to a Connect
     * method call to org.bluez.Health
     *
     * @param chanCode The internal id of the channel
     * @param result Result code of the operation.
     */
    private void onHealthDeviceConnectionResult(int chanCode, int result) {
        log ("onHealthDeviceConnectionResult " + chanCode + " " + result);
        // Success case gets handled by Property Change signal
        if (result != BluetoothHealth.HEALTH_OPERATION_SUCCESS) {
            mBluetoothService.onHealthDeviceChannelConnectionError(chanCode,
                                                 BluetoothHealth.STATE_CHANNEL_DISCONNECTED);
        }
    }

    /**
     * Called by native code on a DeviceDisconnected signal from
     * org.bluez.NetworkServer.
     *
     * @param address the MAC address of the disconnected device
     */
    private void onNetworkDeviceDisconnected(String address) {
        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        mBluetoothService.handlePanDeviceStateChange(device, BluetoothPan.STATE_DISCONNECTED,
                                                      BluetoothPan.LOCAL_NAP_ROLE);
    }

    /**
     * Called by native code on a DeviceConnected signal from
     * org.bluez.NetworkServer.
     *
     * @param address the MAC address of the connected device
     * @param iface interface of remote network
     * @param destUuid unused UUID parameter
     */
    private void onNetworkDeviceConnected(String address, String iface, int destUuid) {
        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        mBluetoothService.handlePanDeviceStateChange(device, iface, BluetoothPan.STATE_CONNECTED,
                                                      BluetoothPan.LOCAL_NAP_ROLE);
    }

    /**
     * Called by native code on a PropertyChanged signal from
     * org.bluez.HealthDevice.
     *
     * @param devicePath the object path of the remote device
     * @param propValues Properties (Name-Value) of the Health Device.
     */
    private void onHealthDevicePropertyChanged(String devicePath, String[] propValues) {
        log("Health Device : Name of Property is: " + propValues[0] + " Value:" + propValues[1]);
        mBluetoothService.onHealthDevicePropertyChanged(devicePath, propValues[1]);
    }

    /**
     * Called by native code on a ChannelCreated/Deleted signal from
     * org.bluez.HealthDevice.
     *
     * @param devicePath the object path of the remote device
     * @param channelPath the path of the health channel.
     * @param exists Boolean to indicate if the channel was created or deleted.
     */
    private void onHealthDeviceChannelChanged(String devicePath, String channelPath,
            boolean exists) {
        log("Health Device : devicePath: " + devicePath + ":channelPath:" + channelPath +
                ":exists" + exists);
        mBluetoothService.onHealthDeviceChannelChanged(devicePath, channelPath, exists);
    }

    private void onDiscoverCharacteristicsResult(String serviceObjectPath, boolean result) {

        Log.d(TAG, "onDiscoverCharacteristicsResult: " + result);

        if (result) {
            mBluetoothService.updateGattServicePropertiesCache(serviceObjectPath);
        }
        mBluetoothService.makeDiscoverCharacteristicsCallback(serviceObjectPath, result);
    }

    private void onSetCharacteristicPropertyResult(String path, String property, boolean result) {

        Log.d(TAG, "onSetCharPropResult path " + path + " property = " + property);
        Log.d(TAG, "Result = " + result);
        mBluetoothService.makeSetCharacteristicPropertyCallback(path, property, result);
    }

    private void onIndicateResponse(String path, boolean result) {
        Log.d(TAG, "onIndicateResponse path = " + path + " result : " + result);
        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onIndicateResponse(path, result);
    }

    private void onWatcherValueChanged(String characteristicPath, String value) {
        // TODO: Send this to upper layer
        mBluetoothService.makeWatcherValueChangedCallback(characteristicPath, value);

    }

    private void onUpdateCharacteristicValueResult(String charObjectPath, boolean result) {

        mBluetoothService.makeUpdateCharacteristicValueCallback(charObjectPath, result);
    }

    private void onGattDiscoverPrimaryRequest(String gattObjectPath, int start, int end, int reqHandle) {
        Log.d(TAG, "Inside onGattDiscoverPrimaryRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattDiscoverPrimaryRequest(gattObjectPath, start, end, reqHandle);
    }

    private void onGattDiscoverPrimaryByUuidRequest(String gattObjectPath, String uuid,
                                                    int start, int end,
                                                    int reqHandle) {
        Log.d(TAG, "Inside onGattDiscoverPrimaryByUuidRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattDiscoverPrimaryByUuidRequest(gattObjectPath, start, end, uuid,
                                                              reqHandle);
    }

    private void onGattDiscoverIncludedRequest(String gattObjectPath, int start,
                                                    int end, int reqHandle) {
        Log.d(TAG, "Inside onGattDiscoverIncludedRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattDiscoverIncludedRequest(gattObjectPath, start, end, reqHandle);
    }

    private void onGattDiscoverCharacteristicsRequest(String gattObjectPath, int start,
                                                    int end, int reqHandle) {
        Log.d(TAG, "Inside onGattDiscoverCharacteristicsRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattDiscoverCharacteristicsRequest(gattObjectPath, start, end, reqHandle);
    }

    private void onGattFindInfoRequest(String gattObjectPath, int start,
                                       int end, int reqHandle) {
        Log.d(TAG, "Inside onGattFindInfoRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattFindInfoRequest(gattObjectPath, start, end, reqHandle);
    }

    private void onGattReadByTypeRequest(String gattObjectPath, String uuid,
                                         String auth, int start,
                                         int end, int reqHandle) {
        Log.d(TAG, "Inside onGattReadByTypeRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattReadByTypeRequest(gattObjectPath, start, end,
                                                   uuid, auth, reqHandle);
    }

    private void onGattReadRequest(String gattObjectPath, String auth, int handle, int reqHandle) {
        Log.d(TAG, "Inside onGattReadRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattReadRequest(gattObjectPath, auth, handle, reqHandle);
    }

    private void onGattWriteRequest(String gattObjectPath, String auth,
                                      int attrHandle, byte[] value,
                                      int sessionHandle, int reqHandle) {
        Log.d(TAG, "onGattWriteRequest");

        mGattRequestData.add(new Integer(reqHandle));

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattWriteRequest(gattObjectPath, auth,
                                              attrHandle, value, sessionHandle, reqHandle);
    }

    private void onGattWriteCommand(String gattObjectPath, String auth,
                                    int attrHandle, byte[] value) {
        Log.d(TAG, "onGattWriteCommand");

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattWriteCommand(gattObjectPath, auth,
                                              attrHandle, value);
    }

    private void onGattSetClientConfigDescriptor(String gattObjectPath, int sessionHandle,
                                                 int attrHandle, byte[] value) {
        Log.d(TAG, "onGattSetClientConfigDescriptor");

        BluetoothGattProfileHandler gattProfileHandler =
            BluetoothGattProfileHandler.getInstance(mContext, mBluetoothService);
        gattProfileHandler.onGattSetClientConfigDescriptor(gattObjectPath, sessionHandle, attrHandle, value);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private native void initializeNativeDataNative();
    private native void startEventLoopNative();
    private native void stopEventLoopNative();
    private native boolean isEventLoopRunningNative();
    private native void cleanupNativeDataNative();
}
