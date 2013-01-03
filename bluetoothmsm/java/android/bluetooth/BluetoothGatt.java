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

import android.content.Context;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Public API for controlling the Bluetooth GATT profile/apps.
 * Currently, this implementation supports only local GATT server
 * applications. Client side is handled in BluetoothGattService.
 * TODO: merge server/client implementations.
 *
 * @hide
 */

public final class BluetoothGatt implements BluetoothProfile {

    private static final String TAG = "BluetoothGatt";

    /**
     * GATT Profile Server Role
     */
    public static final int SERVER_ROLE = 1;

    /**
     *  GATT Profile Client Role (TODO: needs to be merged)
     */
    public static final int CLIENT_ROLE = 2;


    /** GATT result codes */

    public static final byte GATT_SUCCESS = 0x0;

    /* ATT spec error codes */
    public static final int ATT_INVALID_HANDLE       = 0x01;
    public static final int ATT_READ_NOT_PERM        = 0x02;
    public static final int ATT_WRITE_NOT_PERM       = 0x03;
    public static final int ATT_INVALID_PDU          = 0x04;
    public static final int ATT_AUTHENTICATION       = 0x05;
    public static final int ATT_REQ_NOT_SUPP         = 0x06;
    public static final int ATT_INVALID_OFFSET       = 0x07;
    public static final int ATT_AUTHORIZATION        = 0x08;
    public static final int ATT_PREP_QUEUE_FULL      = 0x09;
    public static final int ATT_ATTR_NOT_FOUND       = 0x0A;
    public static final int ATT_ATTR_NOT_LONG        = 0x0B;
    public static final int ATT_INSUFF_ENCR_KEY_SIZE = 0x0C;
    public static final int ATT_INVAL_ATTR_VALUE_LEN = 0x0D;
    public static final int ATT_UNLIKELY             = 0x0E;
    public static final int ATT_INSUFF_ENC           = 0x0F;
    public static final int ATT_UNSUPP_GRP_TYPE      = 0x10;
    public static final int ATT_INSUFF_RESOURCES     = 0x11;

    /* Reserved   0x12 - 0x7f */
    /* Application error codes  0x80 - 0xff */

    /* Local status codes */
    public static final int GATT_FAILURE             = 0x101;
    public static final int GATT_INVALID_ARGUMENTS   = 0x102;
    public static final int GATT_CONFIG_REGISTRATION_SUCCESS    = 0x201;
    public static final int GATT_CONFIG_REGISTRATION_FAILURE    = 0x202;
    public static final int GATT_CONFIG_UNREGISTRATION_SUCCESS  = 0x203;
    public static final int GATT_CONFIG_UNREGISTRATION_FAILURE  = 0x204;


    private ServiceListener mServiceListener;
    private BluetoothAdapter mAdapter;
    private IBluetooth mService;

    /**
     * Create a BluetoothGatt  proxy object.
     */
    /*package*/ BluetoothGatt(Context mContext, ServiceListener l) {
        IBinder b = ServiceManager.getService(BluetoothAdapter.BLUETOOTH_SERVICE);
        mServiceListener = l;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (b != null) {
            mService = IBluetooth.Stub.asInterface(b);
            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(BluetoothProfile.GATT, this);
            }
        } else {
            Log.w(TAG, "Bluetooth Service not available!");

            // Instead of throwing an exception which prevents people from going
            // into Wireless settings in the emulator. Let it crash later when it is actually used.
            mService = null;
        }
    }

    /*package*/ void close() {
        mServiceListener = null;
    }

    /**
     * Register a GATT server configuration.
     * This is an asynchronous call and so
     * the callback is used to notify success or failure if the method returns true.
     *
     */
    public boolean registerServerConfiguration(String name, int range, BluetoothGattCallback callback) {
       if (!checkAppParam(name, range, callback)) return false;

        Log.d(TAG, "register GATT server application " + name);
        BluetoothGattCallbackWrapper wrapper = new BluetoothGattCallbackWrapper(callback);
        BluetoothGattAppConfiguration config = new BluetoothGattAppConfiguration(name, SERVER_ROLE, (int)range);
        boolean result = false;

        if (mService != null) {
            try {
                result = mService.registerGattAppConfiguration(config, wrapper);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return result;
    }

     /**
     * Unregister a GATT server configuration.
     * This is an asynchronous call and so
     * the callback is used to notify success or failure if the method returns true.
     *
     */
   public boolean unregisterServerConfiguration(BluetoothGattAppConfiguration config) {
       return unregisterAppConfiguration(config);
   }

   private boolean unregisterAppConfiguration(BluetoothGattAppConfiguration config) {
        boolean result = false;

        if (config == null)
            return result;

        if (mService != null) {
            try {
                result = mService.unregisterGattAppConfiguration(config);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;
    }

    /**
     * Establish GATT connection to LE device
     *
     * @hide
     */
     public int gattConnectLe(
                                    String address,
                                    byte prohibitRemoteChg,
                                    byte filterPolicy,
                                    int scanInterval,
                                    int scanWindow,
                                    int intervalMin,
                                    int intervalMax,
                                    int latency,
                                    int superVisionTimeout,
                                    int minCeLen,
                                    int maxCeLen, int connTimeout) {

         Log.d(TAG, "Establish LE GATT Connection");

        if (mService != null) {
            try {
                return mService.gattConnect(address, null, prohibitRemoteChg, filterPolicy, scanInterval,
                                            scanWindow, intervalMin, intervalMax, latency,
                                            superVisionTimeout, minCeLen, maxCeLen, connTimeout);
            } catch (RemoteException e) {Log.e(TAG, "", e);}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return BluetoothDevice.GATT_RESULT_FAIL;
    }

   /**
     * Cancel GATT connection creation
     *
     * @hide
     */
    public boolean gattConnectLeCancel(BluetoothGattAppConfiguration config,
                                             String address) {
        Log.d(TAG, "Cancel LE GATT Connection creation");

       if (config == null || address == null)
            return false;

       if (mService != null) {
           try {
               return mService.gattConnectCancel(address, null);
           } catch (RemoteException e) {Log.e(TAG, "", e);}
       } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

       return false;
    }

    public boolean closeGattLeConnection(BluetoothGattAppConfiguration config,
                                         String address) {
       if (config == null)
            return false;

        if (mService != null) {
            try {
                return mService.closeGattLeConnection(config, address);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return false;
    }

    public boolean sendIndication(BluetoothGattAppConfiguration config,
                                  int handle, byte[] value, boolean notify, int sessionHandle) {
        boolean result = false;

        if (config == null || !rangeCheck(handle))
            return false;

        if (mService != null) {
            try {
                result = mService.sendIndication(config, (int)handle, value, notify, sessionHandle) ;
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;
    }

    /* Respond with description for just one service */
    public boolean discoverPrimaryServiceResponse(BluetoothGattAppConfiguration config,
                                                  int requestHandle, int errorCode, int handle, int end,
                                                  ParcelUuid uuid) {
        Log.d(TAG, "discoverPrimaryServiceResponse");
          boolean result = false;

        Log.d(TAG, "discoverPrimaryServiceResponse");
        if (config == null)
            return false;

        if (mService != null) {
            try {
                /*Remove this when the api change*/
                result = mService.discoverPrimaryResponse(config, uuid, handle, end, errorCode, requestHandle);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;
    }

    public boolean discoverPrimaryServiceByUuidResponse(BluetoothGattAppConfiguration config,
                                                        int requestHandle, int errorCode, int handle, int end,
                                                        ParcelUuid uuid) {
        Log.d(TAG, "discoverPrimaryServiceByUuidResponse");
        boolean result = false;

        if (config == null)
            return false;

        if (mService != null) {
            try {
                /*Remove this when the api change*/
                result = mService.discoverPrimaryByUuidResponse(config, handle, end,
                                                                errorCode, requestHandle);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;
    }

    public boolean findIncludedServiceResponse(BluetoothGattAppConfiguration config,
                                               int requestHandle, int errorCode, int handle, int start, int end,
                                               ParcelUuid uuid) {
        Log.d(TAG, "findIncludedServiceResponse");
        boolean result = false;

        if (config == null)
            return false;

        if (mService != null) {
            try {
                /*Remove this when the api change*/
                result = mService.findIncludedResponse(config, uuid, handle, start, end,
                                                       errorCode, requestHandle);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;

    }

    public boolean findInfoResponse(BluetoothGattAppConfiguration config,
                                    int requestHandle, int errorCode, int handle, ParcelUuid uuid) {

        Log.d(TAG, "findInfoResponse");
        boolean result = false;

        if (config == null)
            return false;

        if (mService != null) {
            try {
                /*Remove this when the api change*/
                result = mService.findInfoResponse(config, uuid, handle,
                                                   errorCode, requestHandle);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;
    }


    public boolean discoverCharacteristicResponse(BluetoothGattAppConfiguration config, int errorCode,
                                                  int requestHandle, int handle, byte property, int valueHandle,
                                                  ParcelUuid uuid) {
        Log.d(TAG, "discoverCharacteristicResponse");
        boolean result = false;

        if (config == null)
            return false;

        if (mService != null) {
            try {
                /*Remove this when the api change*/
                result = mService.discoverCharacteristicResponse(config, uuid, handle, property, valueHandle,
                                                                 errorCode, requestHandle);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;
    }

    public boolean readByTypeResponse(BluetoothGattAppConfiguration config, int requestHandle,
                                      int errorCode, ParcelUuid uuid, int handle, byte[] payload) {
        Log.d(TAG, "readByTypeResponse");
        boolean result = false;

        if (config == null)
            return false;

        if (mService != null) {
            try {
                /*Remove this when the api change*/
                result = mService.readByTypeResponse(config, handle, uuid, payload, errorCode, requestHandle);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }

        return result;

    }

    public boolean readResponse(BluetoothGattAppConfiguration config, int requestHandle,
                                int errorCode, ParcelUuid uuid, byte[] payload) {
         Log.d(TAG, "readResponse");
         boolean result = false;

         if (config == null)
             return false;

         if (mService != null) {
             try {
                 /*Remove this when the api change*/
                 result = mService.readResponse(config, uuid, payload, errorCode, requestHandle);
             } catch (RemoteException e) {
                 Log.e(TAG, e.toString());
             }
             result = true;
         } else {
             Log.w(TAG, "Proxy not attached to service");
             Log.d(TAG, Log.getStackTraceString(new Throwable()));
         }
         return result;
     }

    public boolean writeResponse(BluetoothGattAppConfiguration config, int requestHandle,
                                 int errorCode, ParcelUuid uuid) {
        Log.d(TAG, "writeResponse");
        boolean result = false;

        if (config == null)
            return false;

        if (mService != null) {
            try {
                /*Remove this when the api change*/
                result = mService.writeResponse(config, uuid, errorCode, requestHandle);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            result = true;
        } else {
            Log.w(TAG, "Proxy not attached to service");
            Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return result;
    }

    @Override
    public int getConnectionState(BluetoothDevice device) {

       return STATE_DISCONNECTED;
    }

    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return new ArrayList<BluetoothDevice>();
    }

    @Override
    public List<BluetoothDevice> getConnectedDevices() {
        // TODO

        //if (mService != null) {
            //try {
                //return mService.getConnectedGattDevices();
        //} catch (RemoteException e) {
        //      Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
        //      return new ArrayList<BluetoothDevice>();
        //  }
        //}
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    private boolean rangeCheck(int value) {
        if (value < 1 || value > 0xffff)
            return false;
        else
            return true;
    }

    private boolean checkAppParam(String name, int range, BluetoothGattCallback callback) {
        if (name == null || callback == null)
            return false;

        if (!rangeCheck(range))
            return false;

        return true;
    }

    private class BluetoothGattCallbackWrapper extends IBluetoothGattCallback.Stub {
        private BluetoothGattCallback mCallback;

        public BluetoothGattCallbackWrapper(BluetoothGattCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onGattAppConfigurationStatusChange(BluetoothGattAppConfiguration config,
                                                      int status) {
            mCallback.onGattAppConfigurationStatusChange(config, status);
        }

        @Override
        public void onGattActionComplete(BluetoothGattAppConfiguration config, String action, int status) {
            mCallback.onGattActionComplete(config, action, status);
        }

        @Override
        public void onGattDiscoverPrimaryServiceRequest(BluetoothGattAppConfiguration config,
                                                        int start, int end, int requestHandle) {
            Log.d(TAG, "onGattDiscoverPrimaryServiceRequest callback : " + mCallback);
            mCallback.onGattDiscoverPrimaryServiceRequest(config, start, end, requestHandle);
        }

        @Override
        public void onGattDiscoverPrimaryServiceByUuidRequest(BluetoothGattAppConfiguration config,
                                                              int start, int end, ParcelUuid uuid, int requestHandle) {
            Log.d(TAG, "onGattDiscoverPrimaryServiceByUuidRequest callback : " + mCallback);
            mCallback.onGattDiscoverPrimaryServiceByUuidRequest(config, start, end, uuid, requestHandle);
        }

        @Override
        public void onGattFindIncludedServiceRequest(BluetoothGattAppConfiguration config,
                                                     int start, int end, int requestHandle) {
            Log.d(TAG, "onGattFindIncludedServiceRequest: " + config + " range " + start + " - " + end);
            mCallback.onGattFindIncludedServiceRequest(config, start, end, requestHandle);
        }

        @Override
        public void onGattFindInfoRequest(BluetoothGattAppConfiguration config,
                                      int start, int end, int requestHandle) {
            Log.d(TAG, "onGattFindInfoRequest: " + config + " range " + start + " - " + end);
            mCallback.onGattFindInfoRequest(config, start, end, requestHandle);
        }

        @Override
        public void onGattDiscoverCharacteristicRequest(BluetoothGattAppConfiguration config,
                                                        int start, int end, int requestHandle) {
            Log.d(TAG, "onGattDiscoverCharacteristicRequest: " + config + " range " + start + " - " + end);
            mCallback.onGattDiscoverCharacteristicRequest(config, start, end, requestHandle);
        }

        @Override
        public void onGattReadByTypeRequest(BluetoothGattAppConfiguration config,
                                            ParcelUuid uuid, int start, int end, String authentication, int requestHandle) {
            Log.d(TAG, "onGattReadByTypeRequest: " + config + ", range " + start + " - "
                  + end + ", UUID " + uuid.toString() + ", authentication" + authentication);
            mCallback.onGattReadByTypeRequest(config, uuid, start, end,
                                              authentication, requestHandle);
        }

        @Override
        public void onGattReadRequest(BluetoothGattAppConfiguration config,
                                      int handle, String authentication, int requestHandle) {
            Log.d(TAG, "onGattReadRequest: " + config + ", handle " + handle +
                  ", authentication " + authentication);
            mCallback.onGattReadRequest(config, handle, authentication, requestHandle);
        }

        @Override
        public void onGattWriteCommand(BluetoothGattAppConfiguration config, int handle, byte value[],
                                       String authentication)  {
            Log.d(TAG, "onGattWriteCommand: " + config + ", handle " + handle +
              ", authentication " + authentication);
            mCallback.onGattWriteCommand(config, handle, value, authentication);
        }

        @Override
        public void onGattWriteRequest(BluetoothGattAppConfiguration config, int handle, byte value[],
                                               String authentication, int sessionHandle, int requestHandle)  {
            Log.d(TAG, "onGattWriteRequest: " + config + ", handle " + handle +
              ", authentication " + authentication + ", session " + sessionHandle);
            mCallback.onGattWriteRequest(config, handle, value, authentication, sessionHandle, requestHandle);
        }

        @Override
        public void onGattSetClientConfigDescriptor(BluetoothGattAppConfiguration config, int handle, byte[] value,
                                                int sessionHandle) {
            Log.d(TAG, "onGattSetClientConfigDescriptor: " + config + " handle " + handle + " session " + sessionHandle);
            mCallback.onGattSetClientConfigDescriptor(config, handle, value, sessionHandle);
        }

        @Override
        public void onGattIndicateResponse(BluetoothGattAppConfiguration config, boolean result) {
            Log.d(TAG, "onGattIndicateResponse: " + config + " result " + result);
            mCallback.onGattIndicateResponse(config, result);
        }

    }
}
