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

package android.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattAppConfiguration;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothGattCallback;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.DeadObjectException;
import android.util.Log;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This handles all the operations on the Bluetooth Gatt profile (server side).
 *
 * @hide
 */
final class BluetoothGattProfileHandler {
    private static final String TAG = "BluetoothGattProfileHandler";
    private static final boolean DBG = false;

    private static BluetoothGattProfileHandler sInstance;
    private BluetoothService mBluetoothService;
    private HashMap <String, Boolean> mRegisteredServers;
    private HashMap <String, BluetoothGattAppConfiguration> mAppConfigs;
    private HashMap <BluetoothGattAppConfiguration, IBluetoothGattCallback> mCallbacks;

    private static final int MESSAGE_REGISTER_APPLICATION = 1;
    private static final int MESSAGE_UNREGISTER_APPLICATION = 2;
    private static final int MESSAGE_SEND_INDICATION = 3;
    private static final int MESSAGE_DISCOVER_PRIMARY_SERVICE_RESP = 4;
    private static final int MESSAGE_DISCOVER_PRIMARY_SERVICE_BY_UUID_RESP = 5;
    private static final int MESSAGE_FIND_INCLUDED_SERVICE_RESP = 6;
    private static final int MESSAGE_DISCOVER_CHARACTERISTICS_RESP = 7;
    private static final int MESSAGE_FIND_INFO_RESP = 8;
    private static final int MESSAGE_READ_BY_TYPE_RESP = 9;
    private static final int MESSAGE_READ_RESP = 10;
    private static final int MESSAGE_WRITE_RESP = 11;
    private static final int MESSAGE_DISCONNECT_LE = 12;
    private static final int MESSAGE_ADD_TO_PREFERRED_DEVICE_LIST = 13;
    private static final int MESSAGE_REMOVE_FROM_PREFERRED_DEVICE_LIST = 14;
    private static final int MESSAGE_CLEAR_PREFERRED_DEVICE_LIST = 15;
    private static final int MESSAGE_CREATE_CONN_REQ_PREFERRED_DEVICE_LIST = 16;
    private static final int MESSAGE_CANCEL_CREATE_CONN_REQ_PREFERRED_DEVICE_LIST = 17;

    private static final String UUID = "uuid";
    private static final String HANDLE = "handle";
    private static final String END = "end";
    private static final String START = "start";
    private static final String REQUEST_HANDLE = "request_handle";
    private static final String ERROR = "error";
    private static final String VALUE_HANDLE = "value_handle";
    private static final String PROPERTY = "property";
    private static final String PAYLOAD = "payload";
    private static final String SESSION = "session";
    private static final String NOTIFY = "notify";
    private static final String PATH = "PATH";

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattAppConfiguration config = (BluetoothGattAppConfiguration) msg.obj;
            int status, handle, start, end, reqHandle, errorCode, valueHandle;
            byte[] payload;
            byte property;
            String uuid;
            boolean result = true;
            String path = null;
            if(config != null) {
                path = config.getPath();
            }
            int payloadLen = 0;
            String errorString;

            switch (msg.what) {
            case MESSAGE_REGISTER_APPLICATION:
                int range = config.getRange();
                boolean isNew;

                /* Nothing to do, just return success for status */
                if (mAppConfigs.containsKey(path))
                    callGattApplicationStatusCallback(config,
                           BluetoothGatt.GATT_CONFIG_REGISTRATION_SUCCESS);

                if (mRegisteredServers.isEmpty()) {
                    String[] servers = null;
                    servers  = (String[]) mBluetoothService.getGattServersNative();
                    if ((servers != null) && (servers.length > 0))
                        loadRegisteredServers(servers);
                }

                isNew = mRegisteredServers.isEmpty() || !mRegisteredServers.containsKey(path);
                result  = mBluetoothService.registerGattServerNative(path, range, isNew);

                if (!result) {
                    callGattApplicationStatusCallback(config,
                           BluetoothGatt.GATT_CONFIG_REGISTRATION_FAILURE);
                    mCallbacks.remove(config);
                } else {
                    mAppConfigs.put(path, config);
                    serverEnable(path);
                    callGattApplicationStatusCallback(config,
                           BluetoothGatt.GATT_CONFIG_REGISTRATION_SUCCESS);
                }

                break;

           case MESSAGE_UNREGISTER_APPLICATION:
                Log.d(TAG, "GATT: MESSAGE_UNREGISTER_APPLICATION");

                result = mBluetoothService.unregisterGattServerNative(path, true);

                if (!result) {
                    callGattApplicationStatusCallback(config,
                           BluetoothGatt.GATT_CONFIG_UNREGISTRATION_FAILURE);
                } else {
                    callGattApplicationStatusCallback(config,
                           BluetoothGatt.GATT_CONFIG_UNREGISTRATION_SUCCESS);
                }

                mCallbacks.remove(config);
                mAppConfigs.remove(path);

                break;

            case MESSAGE_DISCONNECT_LE:
                Log.d(TAG, "GATT: MESSAGE_DISCONNECT_LE");

                String devPath = msg.getData().getString(PATH);

                result = mBluetoothService.gattLeDisconnectRequestNative(devPath);

                if (!result)
                    Log.e(TAG, "Failed to handle GATT LE disconnect request for " + devPath);

                break;

             case MESSAGE_SEND_INDICATION:
                boolean notify;
                int sessionHandle;
                Log.d(TAG, "GATT: MESSAGE_SEND_INDICATION");

                sessionHandle = msg.getData().getInt(SESSION);
                handle = msg.getData().getInt(HANDLE);
                payload = msg.getData().getByteArray(PAYLOAD);
                notify = msg.getData().getBoolean(NOTIFY);

                if (notify)
                    result = mBluetoothService.notifyNative(path, sessionHandle, handle, payload, payload.length);
                else
                    result = mBluetoothService.indicateNative(path, sessionHandle, handle, payload, payload.length);

                if (!result)
                    status = BluetoothGatt.GATT_FAILURE;
                else
                    status = BluetoothGatt.GATT_SUCCESS;

                callGattActionCompleteCallback(config, "SEND_INDICATION", status);
                break;

            case MESSAGE_DISCOVER_PRIMARY_SERVICE_RESP:
                uuid = msg.getData().getString(UUID);
                handle = msg.getData().getInt(HANDLE);
                end = msg.getData().getInt(END);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                errorString = errorStatusToString(errorCode, handle);

                result  = mBluetoothService.discoverPrimaryResponseNative(uuid, errorString, handle, end, reqHandle);

                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;
            case MESSAGE_DISCOVER_PRIMARY_SERVICE_BY_UUID_RESP:
                handle = msg.getData().getInt(HANDLE);
                end = msg.getData().getInt(END);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                errorString = errorStatusToString(errorCode, handle);
                result  = mBluetoothService.discoverPrimaryByUuidResponseNative(errorString, handle,
                                                                                end, reqHandle);

                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_FIND_INCLUDED_SERVICE_RESP:
                uuid = msg.getData().getString(UUID);
                handle = msg.getData().getInt(HANDLE);
                start = msg.getData().getInt(START);
                end = msg.getData().getInt(END);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                errorString = errorStatusToString(errorCode, handle);

                result  = mBluetoothService.findIncludedResponseNative(uuid, errorString,
                                                                       handle, start, end, reqHandle);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

             case MESSAGE_DISCOVER_CHARACTERISTICS_RESP:
                uuid = msg.getData().getString(UUID);
                handle = msg.getData().getInt(HANDLE);
                valueHandle = msg.getData().getInt(VALUE_HANDLE);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                property = msg.getData().getByte(PROPERTY);
                errorString = errorStatusToString(errorCode, handle);

                result  = mBluetoothService.discoverCharacteristicsResponseNative(uuid, errorString,
                                                                                  handle, (int) property,
                                                                                  valueHandle, reqHandle);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_FIND_INFO_RESP:
                uuid = msg.getData().getString(UUID);
                handle = msg.getData().getInt(HANDLE);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                errorString = errorStatusToString(errorCode, handle);

                result  = mBluetoothService.findInfoResponseNative(uuid,
                                                                   errorString, handle,
                                                                   reqHandle);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_READ_BY_TYPE_RESP:
                uuid = msg.getData().getString(UUID);
                handle = msg.getData().getInt(HANDLE);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                errorString = errorStatusToString(errorCode, handle);
                payload = msg.getData().getByteArray(PAYLOAD);
                if(payload != null)
                    payloadLen = payload.length;
                else
                    payloadLen = 0;
                result  = mBluetoothService.readByTypeResponseNative(uuid,  errorString,
                                                                     handle, payload,
                                                                     payloadLen, reqHandle);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_READ_RESP:
                uuid = msg.getData().getString(UUID);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                errorString = errorStatusToString(errorCode, -1);
                payload = msg.getData().getByteArray(PAYLOAD);
                if(payload != null)
                    payloadLen = payload.length;
                else
                    payloadLen = 0;
                result  = mBluetoothService.readResponseNative(uuid, errorString,
                                                               payload, payloadLen,
                                                               reqHandle);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_WRITE_RESP:
                uuid = msg.getData().getString(UUID);
                reqHandle = msg.getData().getInt(REQUEST_HANDLE);
                errorCode = msg.getData().getInt(ERROR);
                errorString = errorStatusToString(errorCode, -1);

                result  = mBluetoothService.writeResponseNative(uuid, errorString, reqHandle);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_ADD_TO_PREFERRED_DEVICE_LIST:
                path = msg.getData().getString(PATH);
                result  = mBluetoothService.addToPreferredDeviceListNative(path);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_REMOVE_FROM_PREFERRED_DEVICE_LIST:
                path = msg.getData().getString(PATH);

                result  = mBluetoothService.removeFromPreferredDeviceListNative(path);
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_CLEAR_PREFERRED_DEVICE_LIST:
                result  = mBluetoothService.clearPreferredDeviceListNative();
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_CREATE_CONN_REQ_PREFERRED_DEVICE_LIST:
                result  = mBluetoothService.gattConnectToPreferredDeviceListNative();
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            case MESSAGE_CANCEL_CREATE_CONN_REQ_PREFERRED_DEVICE_LIST:
                result  = mBluetoothService.gattCancelConnectToPreferredDeviceListNative();
                if (!result)
                    status = BluetoothGatt.GATT_SUCCESS;
                else
                    status = BluetoothGatt.GATT_FAILURE;
                break;

            }
        }
    };

    private BluetoothGattProfileHandler(Context context, BluetoothService service) {
        mBluetoothService = service;
        mAppConfigs = new HashMap<String, BluetoothGattAppConfiguration>();
        mCallbacks = new HashMap<BluetoothGattAppConfiguration, IBluetoothGattCallback>();
        mRegisteredServers = new HashMap<String, Boolean>();
    }

    static synchronized BluetoothGattProfileHandler getInstance(Context context,
            BluetoothService service) {
        if (sInstance == null) sInstance = new BluetoothGattProfileHandler(context, service);
        return sInstance;
    }

    boolean registerAppConfiguration(BluetoothGattAppConfiguration config,
                                     IBluetoothGattCallback callback) {

        Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_APPLICATION);
        msg.obj = config;
        mHandler.sendMessage(msg);
        mCallbacks.put(config, callback);

        return true;
    }

   boolean unregisterAppConfiguration(BluetoothGattAppConfiguration config) {
       String path = config.getPath();
       if (mAppConfigs.containsKey(path)) {
               Message msg = mHandler.obtainMessage(MESSAGE_UNREGISTER_APPLICATION);
               msg.obj = config;
               mHandler.sendMessage(msg);
               removeRegisteredServer(path);
               return true;
       } else {
           Log.e(TAG, "unregisterAppConfiguration: GATT app not registered");
           return false;
       }
    }

    boolean closeGattLeConnection(BluetoothGattAppConfiguration config,
                                  String devPath) {
       String path = config.getPath();

       Log.d(TAG, "closeGattLeConnection");
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "sendIndication: GATT app not registered");
            return false;
        }

        Bundle b = new Bundle();
        b.putString(PATH, devPath);

        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT_LE);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean sendIndication(BluetoothGattAppConfiguration config,
                           int handle, byte[] value, boolean notify, int sessionHandle) {

       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "sendIndication: GATT app not registered");
            return false;
        }

        Bundle b = new Bundle();
        b.putInt(SESSION, sessionHandle);
        b.putInt(HANDLE, handle);
        b.putByteArray(PAYLOAD, value);
        b.putBoolean(NOTIFY, notify);

        Message msg = mHandler.obtainMessage(MESSAGE_SEND_INDICATION);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean discoverPrimaryResponse(BluetoothGattAppConfiguration config,
                                       String uuid, int handle, int end, int status, int reqHandle) {

       Log.d(TAG, "discoverPrimaryResponse uuid : " + uuid +
             " handle : " + handle + " end: " + end + " reqHandle : " + reqHandle);

       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "discoverPrimaryResponse: GATT app not registered");
            return false;
        }

        Bundle b = new Bundle();

        b.putString(UUID, uuid);
        b.putInt(HANDLE, handle);
        b.putInt(END, end);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_DISCOVER_PRIMARY_SERVICE_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean discoverPrimaryByUuidResponse(BluetoothGattAppConfiguration config,
                                          int handle, int end, int status, int reqHandle) {
       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "discoverPrimaryByUuidResponse: GATT app not registered");
            return false;
        }

       Log.d(TAG, "discoverPrimaryByUuidResponse " + " handle : " + handle
             + " end: " + end + " reqHandle : " + reqHandle);

        Bundle b = new Bundle();
        b.putInt(HANDLE, handle);
        b.putInt(END, end);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_DISCOVER_PRIMARY_SERVICE_BY_UUID_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean findIncludedResponse(BluetoothGattAppConfiguration config, String uuid,
                                 int handle, int start, int end, int status, int reqHandle) {
       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "findIncludedResponse: GATT app not registered");
            return false;
        }

       Log.d(TAG, "findIncludedResponse uuid : " + uuid +
             " handle : " + handle + " end: " + end + " reqHandle : " + reqHandle);

        Bundle b = new Bundle();
        b.putString(UUID, uuid);
        b.putInt(HANDLE, handle);
        b.putInt(START, start);
        b.putInt(END, end);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_FIND_INCLUDED_SERVICE_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean discoverCharacteristicsResponse(BluetoothGattAppConfiguration config, String uuid,
                                            int handle, byte property, int valueHandle,
                                            int status, int reqHandle) {
       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "discoverCharacteristicsResponse: GATT app not registered");
            return false;
        }
       Log.d(TAG, " discoverCharacteristicsResponse uuid : " + uuid + " handle : " + handle
             + " property : " + property +  " valHandle : " + valueHandle +
             " reqHandle : " + reqHandle);

        Bundle b = new Bundle();
        b.putString(UUID, uuid);
        b.putInt(HANDLE, handle);
        b.putByte(PROPERTY, property);
        b.putInt(VALUE_HANDLE, valueHandle);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_DISCOVER_CHARACTERISTICS_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean findInfoResponse(BluetoothGattAppConfiguration config, String uuid,
                             int handle, int status, int reqHandle) {
        String path = config.getPath();
        if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "findInfoResponse: GATT app not registered");
            return false;
        }
       Log.d(TAG, "findInfoResponse uuid : " + uuid + " handle : " + handle
             + " reqHandle : " + reqHandle);

        Bundle b = new Bundle();
        b.putString(UUID, uuid);
        b.putInt(HANDLE, handle);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_FIND_INFO_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean readByTypeResponse(BluetoothGattAppConfiguration config, String uuid, int handle,
                               byte[] payload, int status, int reqHandle) {
       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "readByTypeResponse: GATT app not registered");
            return false;
        }
       Log.d(TAG, " readByTypeResponse uuid : " + uuid + " handle : " + handle
             + " reqHandle : " + reqHandle);

        Bundle b = new Bundle();
        b.putString(UUID, uuid);
        b.putInt(HANDLE, handle);
        b.putByteArray(PAYLOAD, payload);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_READ_BY_TYPE_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean readResponse(BluetoothGattAppConfiguration config, String uuid,
                         byte[] payload, int status, int reqHandle) {
       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "readResponse: GATT app not registered");
            return false;
        }
        Log.d(TAG, " readResponse uuid : " + uuid + " reqHandle : " + reqHandle);
        Log.d(TAG, "payload " + payload);

        Bundle b = new Bundle();
        b.putString(UUID, uuid);
        b.putByteArray(PAYLOAD, payload);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_READ_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean writeResponse(BluetoothGattAppConfiguration config, String uuid, int status,
                          int reqHandle) {
       String path = config.getPath();
       if (!mAppConfigs.containsKey(path)) {
            Log.e(TAG, "writeResponse: GATT app not registered");
            return false;
        }
       Log.d(TAG, " writeResponse uuid : " + uuid + " reqHandle : " + reqHandle);

        Bundle b = new Bundle();
        b.putString(UUID, uuid);
        b.putInt(ERROR, status);
        b.putInt(REQUEST_HANDLE, reqHandle);

        Message msg = mHandler.obtainMessage(MESSAGE_WRITE_RESP);
        msg.obj = config;
        msg.setData(b);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean addToPreferredDeviceList(String  path) {
        Log.d(TAG, " addToPreferredDeviceList path : " + path);
        Bundle b = new Bundle();
        b.putString(PATH, path);

        Message msg = mHandler.obtainMessage(MESSAGE_ADD_TO_PREFERRED_DEVICE_LIST);
        msg.setData(b);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean removeFromPreferredDeviceList(String  path) {
        Log.d(TAG, " removeFromPreferredDeviceList path : " + path);
        Bundle b = new Bundle();
        b.putString(PATH, path);

        Message msg = mHandler.obtainMessage(MESSAGE_REMOVE_FROM_PREFERRED_DEVICE_LIST);
        msg.setData(b);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean clearPreferredDeviceList() {
        Log.d(TAG, " clearPreferredDeviceList  : ");
        Message msg = mHandler.obtainMessage(MESSAGE_CLEAR_PREFERRED_DEVICE_LIST);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean gattConnectToPreferredDeviceList() {
        Log.d(TAG, " gattConnectToPreferredDeviceList  : ");
        Message msg = mHandler.obtainMessage(MESSAGE_CREATE_CONN_REQ_PREFERRED_DEVICE_LIST);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean gattCancelConnectToPreferredDeviceList() {
        Log.d(TAG, " gattCancelConnectToPreferredDeviceList : ");
        Message msg = mHandler.obtainMessage(MESSAGE_CANCEL_CREATE_CONN_REQ_PREFERRED_DEVICE_LIST);
        mHandler.sendMessage(msg);
        return true;
    }


    /*package*/ synchronized void onGattDiscoverPrimaryRequest(String path, int start, int end, int reqHandle) {
         Log.d(TAG, "onGattDiscoverPrimaryRequest - path : "  + path + "start :  " + start + " end : " + end );
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattDiscoverPrimaryServiceRequest(config, start, end, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

    /*package*/ synchronized void onIndicateResponse(String path, boolean result) {
        Log.d(TAG, "Indicate response object path : "  + path + "result :" + result );
        BluetoothGattAppConfiguration config = mAppConfigs.get(path);
        Log.d(TAG, "Config " + config);
        if (config != null) {
            IBluetoothGattCallback callback = mCallbacks.get(config);
            if (callback != null) {
                try {
                    callback.onGattIndicateResponse(config, result);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                }
            }
        }
    }

     /*package*/ synchronized void onGattDiscoverPrimaryByUuidRequest(String path,
                                                                      int start, int end,
                                                                      String uuidStr,
                                                                      int reqHandle) {
         Log.d(TAG, "onGattDiscoverPrimaryByUuidRequest - path : "  + path + "uuid : " + uuidStr +
               "start :  " + start + " end : " + end );
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    ParcelUuid uuid = ParcelUuid.fromString(uuidStr);
                    Log.d(TAG, "Convert string to parceluuid : " + uuid);
                    callback.onGattDiscoverPrimaryServiceByUuidRequest(config, start, end, uuid, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

     /*package*/ synchronized void onGattDiscoverIncludedRequest(String path,
                                                                 int start, int end,
                                                                 int reqHandle) {
         Log.d(TAG, "onGattDiscoverIncludedRequest - path : "  + path +
               "start :  " + start + " end : " + end );
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattFindIncludedServiceRequest(config, start, end, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

     /*package*/ synchronized void onGattDiscoverCharacteristicsRequest(String path,
                                                                 int start, int end,
                                                                 int reqHandle) {
         Log.d(TAG, "onGattDiscoverCharacteristicsRequest - path : "  + path +
               "start :  " + start + " end : " + end );
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattDiscoverCharacteristicRequest(config, start, end, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

     /*package*/ synchronized void onGattFindInfoRequest(String path,
                                                         int start, int end,
                                                         int reqHandle) {
         Log.d(TAG, "Gatt object path : "  + path +
               "start :  " + start + " end : " + end );
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattFindInfoRequest(config, start,
                                                   end, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

     /*package*/ synchronized void onGattReadByTypeRequest(String path, int start, int end,
                                                           String uuidStr, String auth, int reqHandle) {
         Log.d(TAG, "onGattReadByTypeRequest - path : "  + path + "uuid : " + uuidStr +
               "start :  " + start + " end : " + end + " auth : " + auth);
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    ParcelUuid uuid = ParcelUuid.fromString(uuidStr);
                    Log.d(TAG, "Convert string to parceluuid : " + uuid);
                    callback.onGattReadByTypeRequest(config, uuid, start, end, auth, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

     /*package*/ synchronized void onGattReadRequest(String path, String auth,
                                                     int handle, int reqHandle) {
         Log.d(TAG, "onGattReadRequest - path : "  + "handle :  " + handle + " auth : " + auth);
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattReadRequest(config, handle, auth, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

     /*package*/ synchronized void onGattWriteCommand(String path, String auth,
                                                      int attrHandle, byte[] value) {
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         Log.d(TAG, "onGattWriteRequest - path : "  + path + ", config " + config + ", auth " + auth);

         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattWriteCommand(config, attrHandle, value, auth);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

     /*package*/ synchronized void onGattWriteRequest(String path, String auth,
                                                      int attrHandle, byte[] value,
                                                      int sessionHandle, int reqHandle) {
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         Log.d(TAG, "onGattReliableWriteRequest - path : "  + path + ", config " + config);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattWriteRequest(config, attrHandle, value, auth, sessionHandle, reqHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

    /*package*/ synchronized void onGattSetClientConfigDescriptor(String path,
                                                                  int sessionHandle, int attrHandle, byte[] value) {
         BluetoothGattAppConfiguration config = mAppConfigs.get(path);
         Log.d(TAG, "onGattSetClientConfigDescriptor - path : "  + path + ", config " + config);
         if (config != null) {
             IBluetoothGattCallback callback = mCallbacks.get(config);
             if (callback != null && isServerEnabled(path)) {
                try {
                    callback.onGattSetClientConfigDescriptor(config, attrHandle, value, sessionHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote Exception:" + e);
                    if (e instanceof DeadObjectException)
                        serverDisable(path);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Exception:" + e);
                }
             }
         }
     }

    private void callGattApplicationStatusCallback(
            BluetoothGattAppConfiguration config, int status) {
        Log.d(TAG, "GATT Application: " + config + " State Change: status:"
                + status);
        IBluetoothGattCallback callback = mCallbacks.get(config);
        if (callback != null) {
            try {
                callback.onGattAppConfigurationStatusChange(config, status);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote Exception:" + e);
                if (e instanceof DeadObjectException)
                    serverDisable(config.getPath());
            } catch (NullPointerException e) {
                Log.e(TAG, "Exception:" + e);
            }
        }
    }

   private void callGattActionCompleteCallback(
                                               BluetoothGattAppConfiguration config, String action, int status) {
        Log.d(TAG, "GATT Action: " + action + " status:" + status);
        IBluetoothGattCallback callback = mCallbacks.get(config);
        if (callback != null && isServerEnabled(config.getPath())) {
            try {
                callback.onGattActionComplete(config, action, status);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote Exception:" + e);
                if (e instanceof DeadObjectException)
                    serverDisable(config.getPath());
            } catch (NullPointerException e) {
                Log.e(TAG, "Exception:" + e);
            }
        }
    }

    private String errorStatusToString(int errorCode, int handle) {

        /* This will be handled as "no error" by  JNI code */
        if (errorCode ==  BluetoothGatt.GATT_SUCCESS)
                return null;

        String errorString = new String();

        switch (errorCode) {
            /* ATT spec error codes */
            case BluetoothGatt.ATT_INVALID_HANDLE:
                errorString = "ATT_INVALID_HANDLE";
                break;
            case BluetoothGatt.ATT_WRITE_NOT_PERM:
                errorString = "ATT_WRITE_NOT_PERM";
                break;
            case BluetoothGatt.ATT_READ_NOT_PERM:
                errorString = "ATT_READ_NOT_PERM";
                break;
            case BluetoothGatt.ATT_INVALID_PDU:
                errorString = "ATT_INVALID_PDU";
                break;
            case BluetoothGatt.ATT_AUTHENTICATION:
                errorString = "ATT_INSUFF_AUTHENTICATION";
                 break;
            case BluetoothGatt.ATT_REQ_NOT_SUPP:
                errorString = "ATT_REQ_NOT_SUPP";
                break;
            case BluetoothGatt.ATT_INVALID_OFFSET:
                errorString = "ATT_INVALID_OFFSET";
                break;
            case BluetoothGatt.ATT_AUTHORIZATION:
                errorString = "ATT_INSUFF_AUTHORIZATION";
                break;
            case BluetoothGatt.ATT_PREP_QUEUE_FULL:
                errorString = "ATT_PREP_QUEUE_FULL";
                break;
            case BluetoothGatt.ATT_ATTR_NOT_FOUND:
                errorString = "ATT_ATTR_NOT_FOUND";
                break;
            case BluetoothGatt.ATT_ATTR_NOT_LONG:
                errorString = "ATT_ATTR_NOT_LONG";
                break;
            case BluetoothGatt.ATT_INSUFF_ENCR_KEY_SIZE:
                errorString = "ATT_INSUFF_ENCR_KEY_SIZE";
                break;
            case BluetoothGatt.ATT_INVAL_ATTR_VALUE_LEN:
                errorString = "ATT_INVAL_ATTR_VALUE_LEN";
                break;
            case BluetoothGatt.ATT_UNLIKELY:
                errorString = "ATT_UNLIKELY";
                break;
            case BluetoothGatt.ATT_INSUFF_ENC:
                errorString = "ATT_INSUFF_ENCRYPTION";
                break;
            case BluetoothGatt.ATT_UNSUPP_GRP_TYPE:
                errorString = "ATT_UNSUPP_GRP_TYPE";
                break;
            case BluetoothGatt.ATT_INSUFF_RESOURCES:
                errorString = "ATT_INSUFF_RESOURCES";
                break;
            default:
                /* Check if this is an application defined error */
                if (errorCode >= 0x01 && errorCode <= 0xff)
                    errorString = "ATT_0x" + Integer.toHexString(errorCode);
                else
                    errorString = "ATT_UNLIKELY";
        }

        if (handle != -1)
            errorString = errorString + "." + Integer.toHexString(handle);

        return errorString;
    }

    private void loadRegisteredServers(String[] servers) {
        for (int i = 0; i < servers.length; i++)
             mRegisteredServers.put(servers[i], true);
    }

    private void removeRegisteredServer(String path) {
        if (mRegisteredServers.containsKey(path))
            mRegisteredServers.remove(path);
    }

    private boolean isServerEnabled(String path) {
        if (mRegisteredServers.containsKey(path))
            return mRegisteredServers.get(path);
        return false;
    }

    private void serverDisable(String path) {
        if (mRegisteredServers.containsKey(path))
            mRegisteredServers.remove(path);
        mRegisteredServers.put(path, false);
        mBluetoothService.unregisterGattServerNative(path, false);
    }

    private void serverEnable(String path) {
        if (mRegisteredServers.containsKey(path))
            mRegisteredServers.remove(path);
        mRegisteredServers.put(path, true);
    }
}
