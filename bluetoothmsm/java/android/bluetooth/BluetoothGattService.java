/*
 * Copyright (c) 2011-2012 The Linux Foundation. All rights reserved.
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

package android.bluetooth;

import android.os.Handler;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;

import java.util.ArrayList;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Public API for controlling the Bluetooth GATT based services.
 *
 * @hide
 */

public class BluetoothGattService {
    private static final String TAG = "BluetoothGattService";
    private ParcelUuid mUuid;
    private String mObjPath;
    private BluetoothDevice mDevice;
    private String mName = null;
    private boolean watcherRegistered = false;
    private IBluetoothGattProfile profileCallback = null;

    private final HashMap<String, Map<String, String>> mCharacteristicProperties;
    private final ArrayList<String> mUpdateCharacteristicsTracker;
    private String[] characteristicPaths = null;

    private static final int DISCOVERY_NONE = 0;
    private static final int DISCOVERY_FINISHED = 1;
    private static final int DISCOVERY_IN_PROGRESS = 2;

    private int discoveryState;

    private boolean mClosed;
    private final ReentrantReadWriteLock mLock;

    private final IBluetooth mService;

    private final ServiceHelper mHelper;

    private final Handler mRemoteGattServiceHandler;

    public BluetoothGattService(BluetoothDevice device, ParcelUuid uuid, String path,
                                IBluetoothGattProfile callback) {
        mDevice = device;
        mUuid = uuid;
        mObjPath = path;
        profileCallback = callback;
        mClosed = false;
        mLock = new ReentrantReadWriteLock();

        mCharacteristicProperties = new HashMap<String, Map<String, String>>();
        mUpdateCharacteristicsTracker = new ArrayList<String>();

        mHelper = new ServiceHelper();
        mService = BluetoothDevice.getService();

        mRemoteGattServiceHandler = new Handler();
        boolean hasGattServiceStarted = mRemoteGattServiceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Inside run for disc char");
                mHelper.startRemoteGattService();
            }
        }, 1000);
        Log.d(TAG, "Remote Gatt service started : " + hasGattServiceStarted);
    }

    public boolean gattConnect(byte prohibitRemoteChg,
                               byte filterPolicy,
                               int scanInterval,
                               int scanWindow,
                               int intervalMin,
                               int intervalMax,
                               int latency,
                               int superVisionTimeout,
                               int minCeLen,
                               int maxCeLen, int connTimeout) {
        return mHelper.gattConnect(prohibitRemoteChg, filterPolicy, scanInterval,
                                 scanWindow, intervalMin, intervalMax, latency,
                                 superVisionTimeout, minCeLen, maxCeLen, connTimeout);
    }

    public boolean gattConnectCancel() {
        return mHelper.gattConnectCancel();
    }

    public ParcelUuid getServiceUuid() {
        return mUuid;
    }

    public String getServiceName() throws Exception {

        if (mName != null)
            return  mName;

        mLock.readLock().lock();
        try {
            if (mClosed) throw new IOException("GATT service closed");
            return mService.getGattServiceName(mObjPath);
        } finally {
            mLock.readLock().unlock();
        }
    }

    public String[] getCharacteristics() {
        if (!mHelper.discoveryDone())
            return null;

        return characteristicPaths;
    }

    public boolean discoverCharacteristics() {
        return mHelper.doDiscovery();
    }

    public boolean isDiscoveryDone() {
        return mHelper.discoveryDone();
    }

    public String[] getCharacteristicPaths() {

            String value = "";
            String[] paths = null;
            try {
                value = mService.getGattServiceProperty(mObjPath, "Characteristics");
                if (value == null) {
                    Log.d(TAG, "value is null");
                    return null;
                }
                Log.d(TAG, "value is : " + value);

                // The Charateristic paths are stored as a "," separated string.
                paths = value.split(",");

            } catch (Exception e) {
                Log.e(TAG, "!!!Error while calling getGattServiceProperty");
            }
            return paths;
    }

    public ParcelUuid[] getCharacteristicUuids() {

        ArrayList<ParcelUuid>  uuidList = new ArrayList<ParcelUuid>();

        if (!mHelper.discoveryDone())
            return null;

        if (characteristicPaths == null)
            return null;

        int count  = characteristicPaths.length;

        for(int i = 0; i< count; i++) {

            String value = getCharacteristicProperty(characteristicPaths[i], "UUID");

            if (value != null)
                uuidList.add(ParcelUuid.fromString(value));

            Log.d (TAG, "Characteristic UUID: " + value);

        }

        ParcelUuid[] uuids = new ParcelUuid[count];

        uuidList.toArray(uuids);

        return uuids;

    }

    public ParcelUuid getCharacteristicUuid(String path) {

        ParcelUuid uuid = null;

        if (!mHelper.discoveryDone())
            return null;

        String value = getCharacteristicProperty(path, "UUID");

        if (value != null) {
                uuid = ParcelUuid.fromString(value);

                Log.d (TAG, "Characteristic UUID: " + value);
        }
        return uuid;
    }

    public String getCharacteristicDescription(String path) {
        if (!mHelper.discoveryDone())
            return null;

        return getCharacteristicProperty(path, "Description");

    }

    public byte[] readCharacteristicRaw(String path)
    {
        Log.d (TAG, "readCharacteristicValue for " + path);

        if (!mHelper.discoveryDone())
            return null;

        if (characteristicPaths == null)
            return null;

        String value = getCharacteristicProperty(path, "Value");

        if (value == null) {
            return null;
        }
        byte[] ret = value.getBytes();
        return ret;
    }

    public boolean updateCharacteristicValue(String path) throws Exception {
        Log.d (TAG, "updateCharacteristicValue for " + path);

        if (!mHelper.discoveryDone())
            return false;

        if (characteristicPaths == null)
            return false;

        mLock.readLock().lock();
        try {
            if (mClosed) throw new Exception ("GATT service closed");
            return mHelper.fetchCharValue(path);
        } finally {
            mLock.readLock().unlock();
        }
    }

    public String getCharacteristicClientConf(String path)
    {
        if (!mHelper.discoveryDone())
            return null;

        if (characteristicPaths == null)
            return null;

        String value = (String) getCharacteristicProperty(path, "ClientConfiguration");

        if (value == null) {
            return null;
        }

        return value;
    }

    public boolean writeCharacteristicRaw(String path, byte[] value,
                                          boolean reliable) throws Exception {

        Log.d (TAG, "writeCharacteristicRaw " + path);

        if (!mHelper.discoveryDone())
            return false;

        if (characteristicPaths == null)
            return false;

        mLock.readLock().lock();
        try {
            if (mClosed) throw new Exception ("GATT service closed");
            return mHelper.setCharacteristicProperty(path, "Value", value, reliable);
        }  finally {
            mLock.readLock().unlock();
        }
    }

    public boolean setCharacteristicClientConf(String path, int config) throws Exception {

        if (!mHelper.discoveryDone())
            return false;

        if (characteristicPaths == null)
            return false;

        // Client Conf is 2 bytes
        byte[] value = new byte[2];
        value[1] = (byte)(config & 0xFF);
        value[0] = (byte)((config >> 8) & 0xFF);

        mLock.readLock().lock();
        try {
            if (mClosed) throw new Exception ("GATT service closed");
            return mHelper.setCharacteristicProperty(path, "ClientConfiguration", value, true);
        }  finally {
            mLock.readLock().unlock();
        }
    }

    public boolean registerWatcher() throws Exception {
        if (watcherRegistered == false) {
            mLock.readLock().lock();
            try {
                if (mClosed) throw new Exception ("GATT service closed");

                watcherRegistered = mHelper.registerCharacteristicsWatcher();
                return watcherRegistered;
            }  finally {
                mLock.readLock().unlock();
            }
       } else {
            return true;
        }
    }

    public boolean deregisterWatcher()  throws Exception {
        if (watcherRegistered == true) {
            watcherRegistered = false;

            mLock.readLock().lock();
            try {
                if (mClosed) throw new Exception ("GATT service closed");
                return mHelper.deregisterCharacteristicsWatcher();
            }  finally {
                mLock.readLock().unlock();
            }
        }
        return true;
    }

    public void close() throws Exception{

        mLock.writeLock().lock();
        if (mClosed) {
            mLock.writeLock().unlock();
            return;
        }

        deregisterWatcher();

        try {
            mClosed = true;
            mService.closeRemoteGattService(mObjPath);
        } finally {
            mLock.writeLock().unlock();
        }
    }

    /** @hide */
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private String getCharacteristicProperty(String path, String property) {

        Map<String, String> properties = mCharacteristicProperties.get(path);

        if (properties != null)
            return properties.get(property);

        return null;
    }

    private void addCharacteristicProperties(String path, String[] properties) {
        Map<String, String> propertyValues = mCharacteristicProperties.get(path);
        if (propertyValues == null) {
            propertyValues = new HashMap<String, String>();
        }

        for (int i = 0; i < properties.length; i++) {
            String name = properties[i];
            String newValue = null;

            if (name == null) {
                Log.e(TAG, "Error: Gatt Characterisitc Property at index" + i + "is null");
                continue;
            }

            newValue = properties[++i];

            propertyValues.put(name, newValue);
        }

        mCharacteristicProperties.put(path, propertyValues);
    }

    private void updateCharacteristicPropertyCache(String path) {
        String[] properties = null;

        try {
            properties = mService.getCharacteristicProperties(path);
        } catch (Exception e) {Log.e(TAG, "", e);}

        if (properties != null) {
            addCharacteristicProperties(path, properties);
        }
    }

    /**
     * Helper to perform Service Characteristic discovery
     */
    private class ServiceHelper extends IBluetoothGattService.Stub {

        private void setDiscoveryState(int state) {
            Log.d(TAG, "Discovery State " + discoveryState + " to " + state);
            discoveryState = state;
        }

        public boolean discoveryDone() {
            return (discoveryState == DISCOVERY_FINISHED);
        }

        /**
         * Throws IOException on failure.
         */
        public boolean doDiscovery() {

            Log.d(TAG, "doDiscovery " + mObjPath);

            if(discoveryState == DISCOVERY_IN_PROGRESS) {
                Log.d(TAG, "Characteristic discovery is already in progress for " + mObjPath);
                return true;
            }

            setDiscoveryState(DISCOVERY_IN_PROGRESS);

            try {
                return mService.discoverCharacteristics(mObjPath);
            } catch (RemoteException e) {Log.e(TAG, "", e);}

            return false;

        }

        public void startRemoteGattService() {
            setDiscoveryState(DISCOVERY_NONE);

            try {
                mService.startRemoteGattService(mObjPath, this);

                int state = mService.getBondState(mDevice.getAddress());
                Log.d(TAG, "Bond state of remote device : " + mDevice.getAddress() + " is " + state);
                if(state == BluetoothDevice.BOND_BONDED) {
                    characteristicPaths =  getCharacteristicPaths();
                    if(characteristicPaths != null) {

                       for(int i = 0; i< characteristicPaths.length; i++) {
                           Log.d(TAG, "Update value for characteristics path : " +
                                 characteristicPaths[i]);
                           try {
                               mHelper.fetchCharValue(characteristicPaths[i]);
                               mUpdateCharacteristicsTracker.add(characteristicPaths[i]);
                           } catch (Exception e) {Log.e(TAG, "", e);}
                       }

                    } else {
                        Log.d(TAG, "doDiscovery for bonded device");
                        doDiscovery();
                    }
                } else {
                    Log.d(TAG, "doDiscovery as device is not bonded");
                    doDiscovery();
                }
            } catch (RemoteException e) {Log.e(TAG, "", e);}
        }

        public synchronized void onCharacteristicsDiscovered(String[] paths, boolean result)
        {
            Log.d(TAG, "onCharacteristicsDiscovered: " + paths);

            if (mClosed)
                return;

            if (paths !=null) {

                int count = paths.length;

                Log.d(TAG, "Discovered  " + count + " characteristics for service " + mObjPath + " ( " + mName + " )");

                characteristicPaths = paths;

                for (int i = 0; i < count; i++) {

                    String[] properties = null;

                    try {
                        properties = mService.getCharacteristicProperties(paths[i]);
                    } catch (RemoteException e) {Log.e(TAG, "", e);}

                    if (properties != null) {
                        addCharacteristicProperties(paths[i], properties);
                    }
                }
            }

            setDiscoveryState(DISCOVERY_FINISHED);

            if (profileCallback != null) {
                try {
                    profileCallback.onDiscoverCharacteristicsResult(mObjPath, result);
                } catch (Exception e) {Log.e(TAG, "", e);}
            }

            this.notify();
        }

        public synchronized boolean gattConnect(byte prohibitRemoteChg,
                                          byte filterPolicy,
                                          int scanInterval,
                                          int scanWindow,
                                          int intervalMin,
                                          int intervalMax,
                                          int latency,
                                          int superVisionTimeout,
                                          int minCeLen,
                                          int maxCeLen, int connTimeout) {
             Log.d(TAG, "gattConnect");
             try {
                 int result =  mService.gattConnect(mDevice.getAddress(), mObjPath, prohibitRemoteChg, filterPolicy, scanInterval,
                                  scanWindow, intervalMin, intervalMax, latency,
                                  superVisionTimeout, minCeLen, maxCeLen, connTimeout);
                if (result != BluetoothDevice.GATT_RESULT_SUCCESS)
                     return false;
                 else return true;
             } catch (RemoteException e) {Log.e(TAG, "", e);}

             return false;
        }

        public synchronized boolean gattConnectCancel() {
             Log.d(TAG, "gattConnectCancel");
             try {
                 return mService.gattConnectCancel(mDevice.getAddress(), mObjPath);
             } catch (RemoteException e) {Log.e(TAG, "", e);}

             return false;
        }

        public synchronized void onSetCharacteristicProperty(String path, String property, boolean result)
        {
            Log.d(TAG, "onSetCharacteristicProperty: " + path + " property " + property + " result " + result);
            if (mClosed)
                return;

            if ((path == null) || (property == null)) {
                return;
            }
            if (property.equals("Value")) {
                try {
                    if(result) {
                        updateCharacteristicPropertyCache(path);
                    }
                    if (profileCallback != null)
                        profileCallback.onSetCharacteristicValueResult(path, result);
                } catch (RemoteException e) {Log.e(TAG, "", e);}
            }
            if (property.equals("ClientConfiguration")) {
                try {
                    if(result) {
                        updateCharacteristicPropertyCache(path);
                    }
                    if (profileCallback != null)
                        profileCallback.onSetCharacteristicCliConfResult(path, result);
                } catch (RemoteException e) {Log.e(TAG, "", e);}

            }
        }

        public synchronized void onValueChanged(String path, String value)
        {
            if (mClosed)
                return;

            if (path == null) {
                return;
            }
            Log.d(TAG, "WatcherValueChanged = " + path + value);

            if (profileCallback == null) {
                deregisterCharacteristicsWatcher();
                return;
            }
            try {
                profileCallback.onValueChanged(path, value);
            } catch (Exception e) {Log.e(TAG, "", e);}
        }

        public synchronized boolean setCharacteristicProperty(String path, String key,
                byte[] value, boolean reliable) {
            Log.d(TAG, "setCharacteristicProperty");
            try {
                return mService.setCharacteristicProperty(path, key, value, reliable);
            } catch (RemoteException e) {Log.e(TAG, "", e);}

            return false;
        }

        public synchronized void onCharacteristicValueUpdated(String path, boolean result)
        {

            if (mClosed)
                return;

            if (result) {
                updateCharacteristicPropertyCache(path);
            }

            if(mUpdateCharacteristicsTracker.contains(path)) {
                Log.d(TAG, "Char path present in update tracker: " + path);
                mUpdateCharacteristicsTracker.remove(path);
                if(mUpdateCharacteristicsTracker.isEmpty() &&
                   discoveryState == DISCOVERY_NONE) {
                    Log.d(TAG, "retrieved char Paths from the cache and updated value");
                    onCharacteristicsDiscovered(getCharacteristicPaths(), true);
                }
                return;
            }

            if (profileCallback != null) {
                try {
                    profileCallback.onUpdateCharacteristicValueResult(path, result);
                } catch (RemoteException e) {Log.e(TAG, "", e);}
            }
        }

        public synchronized boolean fetchCharValue(String path) {
            try {
                return mService.updateCharacteristicValue(path);
            } catch (RemoteException e) {Log.e(TAG, "", e);}

            return false;
        }

        public synchronized boolean registerCharacteristicsWatcher() {
            Log.d(TAG, "registerCharacteristicsWatcher: ");

            try {
                if (mService.registerCharacteristicsWatcher(mObjPath, this) == true) {
                    return true;
                }
            } catch (RemoteException e) {Log.e(TAG, "", e);}

            return false;
        }

        public synchronized boolean deregisterCharacteristicsWatcher() {
            Log.d(TAG, "deregisterCharacteristicsWatcher: ");
            try {
               return mService.deregisterCharacteristicsWatcher(mObjPath);
             } catch (RemoteException e) {Log.e(TAG, "", e);}
            return false;
        }

        public synchronized void waitDiscoveryDone()
        {
            try {
                this.wait(60000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Characteristics discovery takes too long");
            }
        }
    }
}
