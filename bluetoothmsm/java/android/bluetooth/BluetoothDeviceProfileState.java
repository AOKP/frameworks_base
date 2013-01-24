/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.bluetooth.BluetoothAdapter;
import android.os.PowerManager;
import android.server.BluetoothA2dpService;
import android.server.BluetoothService;
import android.util.Log;
import android.util.Pair;
import android.os.ParcelUuid;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.Set;
import java.util.List;

/**
 * This class is the Profile connection state machine associated with a remote
 * device. When the device bonds an instance of this class is created.
 * This tracks incoming and outgoing connections of all the profiles. Incoming
 * connections are preferred over outgoing connections and HFP preferred over
 * A2DP. When the device is unbonded, the instance is removed.
 *
 * States:
 * {@link BondedDevice}: This state represents a bonded device. When in this
 * state none of the profiles are in transition states.
 *
 * {@link OutgoingHandsfree}: Handsfree profile connection is in a transition
 * state because of a outgoing Connect or Disconnect.
 *
 * {@link IncomingHandsfree}: Handsfree profile connection is in a transition
 * state because of a incoming Connect or Disconnect.
 *
 * {@link IncomingA2dp}: A2dp profile connection is in a transition
 * state because of a incoming Connect or Disconnect.
 *
 * {@link OutgoingA2dp}: A2dp profile connection is in a transition
 * state because of a outgoing Connect or Disconnect.
 *
 * Todo(): Write tests for this class, when the Android Mock support is completed.
 * @hide
 */
public final class BluetoothDeviceProfileState extends StateMachine {
    private static final String TAG = "BluetoothDeviceProfileState";
    private static final boolean DBG = false;

    // TODO(): Restructure the state machine to make it scalable with regard to profiles.
    public static final int CONNECT_HFP_OUTGOING = 1;
    public static final int CONNECT_HFP_INCOMING = 2;
    public static final int CONNECT_A2DP_OUTGOING = 3;
    public static final int CONNECT_A2DP_INCOMING = 4;
    public static final int CONNECT_HID_OUTGOING = 5;
    public static final int CONNECT_HID_INCOMING = 6;

    public static final int DISCONNECT_HFP_OUTGOING = 50;
    private static final int DISCONNECT_HFP_INCOMING = 51;
    public static final int DISCONNECT_A2DP_OUTGOING = 52;
    public static final int DISCONNECT_A2DP_INCOMING = 53;
    public static final int DISCONNECT_HID_OUTGOING = 54;
    public static final int DISCONNECT_HID_INCOMING = 55;
    public static final int DISCONNECT_PBAP_OUTGOING = 56;

    public static final int UNPAIR = 100;
    public static final int AUTO_CONNECT_PROFILES = 101;
    public static final int TRANSITION_TO_STABLE = 102;
    public static final int CONNECT_OTHER_PROFILES = 103;
    private static final int CONNECTION_ACCESS_REQUEST_REPLY = 104;
    private static final int CONNECTION_ACCESS_REQUEST_EXPIRY = 105;
    private static final int UNPAIR_COMPLETE = 106;

    public static final int CONNECT_OTHER_PROFILES_DELAY = 4000; // 4 secs
    private static final int CONNECTION_ACCESS_REQUEST_EXPIRY_TIMEOUT = 7000; // 7 secs
    private static final int CONNECTION_ACCESS_UNDEFINED = -1;
    private static final long INIT_INCOMING_REJECT_TIMER = 1000; // 1 sec
    private static final long MAX_INCOMING_REJECT_TIMER = 3600 * 1000 * 4; // 4 hours
    private static final int UNPAIR_COMPLETE_DELAY = 2000; // 2 secs delay in bluez

    private static final String ACCESS_AUTHORITY_PACKAGE = "com.android.settings";
    private static final String ACCESS_AUTHORITY_CLASS =
        "com.android.settings.bluetooth.BluetoothPermissionRequest";
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    private BondedDevice mBondedDevice = new BondedDevice();
    private OutgoingHandsfree mOutgoingHandsfree = new OutgoingHandsfree();
    private IncomingHandsfree mIncomingHandsfree = new IncomingHandsfree();
    private IncomingA2dp mIncomingA2dp = new IncomingA2dp();
    private OutgoingA2dp mOutgoingA2dp = new OutgoingA2dp();
    private OutgoingHid mOutgoingHid = new OutgoingHid();
    private IncomingHid mIncomingHid = new IncomingHid();

    private Context mContext;
    private BluetoothService mService;
    private BluetoothA2dpService mA2dpService;
    private BluetoothHeadset  mHeadsetService;
    private BluetoothPbap     mPbapService;
    private PbapServiceListener mPbap;
    private BluetoothAdapter mAdapter;
    private boolean mPbapServiceConnected;
    private boolean mAutoConnectionPending;
    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;

    private BluetoothDevice mDevice;
    private int mHeadsetState = BluetoothProfile.STATE_DISCONNECTED;
    private int mA2dpState = BluetoothProfile.STATE_DISCONNECTED;
    private long mIncomingRejectTimer;
    private boolean mConnectionAccessReplyReceived = false;
    private Pair<Integer, String> mIncomingConnections;
    private PowerManager.WakeLock mWakeLock;
    private PowerManager mPowerManager;
    private boolean mPairingRequestRcvd = false;
    private boolean mExpectingSdpComplete = false;

    private boolean mUnpairStarted = false;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || !device.equals(mDevice)) return;

            if (action.equals(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                int newState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
                int oldState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, 0);
                // We trust this device now
                if (newState == BluetoothHeadset.STATE_CONNECTED) {
                    setTrust(BluetoothDevice.CONNECTION_ACCESS_YES);
                }
                mA2dpState = newState;
                if (oldState == BluetoothA2dp.STATE_CONNECTED &&
                    newState == BluetoothA2dp.STATE_DISCONNECTED) {
                    sendMessage(DISCONNECT_A2DP_INCOMING);
                }
                if (newState == BluetoothProfile.STATE_CONNECTED ||
                    newState == BluetoothProfile.STATE_DISCONNECTED) {
                    sendMessage(TRANSITION_TO_STABLE);
                }
            } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                int newState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
                int oldState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, 0);
                // We trust this device now
                if (newState == BluetoothHeadset.STATE_CONNECTED) {
                    setTrust(BluetoothDevice.CONNECTION_ACCESS_YES);
                }
                mHeadsetState = newState;
                if (oldState == BluetoothHeadset.STATE_CONNECTED &&
                    newState == BluetoothHeadset.STATE_DISCONNECTED) {
                    sendMessage(DISCONNECT_HFP_INCOMING);
                }
                if (newState == BluetoothProfile.STATE_CONNECTED ||
                    newState == BluetoothProfile.STATE_DISCONNECTED) {
                    sendMessage(TRANSITION_TO_STABLE);
                }
            } else if (action.equals(BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED)) {
                int newState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
                int oldState =
                    intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, 0);
                // We trust this device now
                if (newState == BluetoothHeadset.STATE_CONNECTED) {
                    setTrust(BluetoothDevice.CONNECTION_ACCESS_YES);
                }
                if (oldState == BluetoothProfile.STATE_CONNECTED &&
                    newState == BluetoothProfile.STATE_DISCONNECTED) {
                    sendMessage(DISCONNECT_HID_INCOMING);
                }
                if (newState == BluetoothProfile.STATE_CONNECTED ||
                    newState == BluetoothProfile.STATE_DISCONNECTED) {
                    sendMessage(TRANSITION_TO_STABLE);
                }
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                // This is technically not needed, but we can get stuck sometimes.
                // For example, if incoming A2DP fails, we are not informed by Bluez
                sendMessage(TRANSITION_TO_STABLE);
            } else if (action.equals(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY)) {
                mWakeLock.release();
                int val = intent.getIntExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                                             BluetoothDevice.CONNECTION_ACCESS_NO);
                Message msg = obtainMessage(CONNECTION_ACCESS_REQUEST_REPLY);
                msg.arg1 = val;
                sendMessage(msg);
            } else if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                mPairingRequestRcvd = true;
            } else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR);
                if (state == BluetoothDevice.BOND_BONDED && mPairingRequestRcvd) {
                    setTrust(BluetoothDevice.CONNECTION_ACCESS_YES);
                    mPairingRequestRcvd = false;
                } else if (state == BluetoothDevice.BOND_NONE) {
                    mPairingRequestRcvd = false;
                } else if (state == BluetoothDevice.BOND_BONDED) {
                    mExpectingSdpComplete = true;
                }
            } else if (action.equals(BluetoothDevice.ACTION_UUID)) {
                 mExpectingSdpComplete = false;
            }
        }
    };

    private boolean isPhoneDocked(BluetoothDevice autoConnectDevice) {
        // This works only because these broadcast intents are "sticky"
        Intent i = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_DOCK_EVENT));
        if (i != null) {
            int state = i.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED);
            if (state != Intent.EXTRA_DOCK_STATE_UNDOCKED) {
                BluetoothDevice device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && autoConnectDevice.equals(device)) {
                    return true;
                }
            }
        }
        return false;
    }

    public BluetoothDeviceProfileState(Context context, String address,
          BluetoothService service, BluetoothA2dpService a2dpService, boolean setTrust) {
        super("BDP:" + address);
        mContext = context;
        mDevice = new BluetoothDevice(address);
        mService = service;
        mA2dpService = a2dpService;

        addState(mBondedDevice);
        addState(mOutgoingHandsfree);
        addState(mIncomingHandsfree);
        addState(mIncomingA2dp);
        addState(mOutgoingA2dp);
        addState(mOutgoingHid);
        addState(mIncomingHid);
        setInitialState(mBondedDevice);

        IntentFilter filter = new IntentFilter();
        // Fine-grained state broadcasts
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_UUID);

        mContext.registerReceiver(mBroadcastReceiver, filter);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAdapter.getProfileProxy(mContext, mBluetoothProfileServiceListener,
                                BluetoothProfile.HEADSET);
        // TODO(): Convert PBAP to the new Profile APIs.
        mPbap = new PbapServiceListener();

        mIncomingConnections = mService.getIncomingState(address);
        mIncomingRejectTimer = readTimerValue();
        mPowerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK |
                                              PowerManager.ACQUIRE_CAUSES_WAKEUP |
                                              PowerManager.ON_AFTER_RELEASE, TAG);
        mWakeLock.setReferenceCounted(false);

        if (setTrust) {
            setTrust(BluetoothDevice.CONNECTION_ACCESS_YES);
        }
    }

    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            synchronized(BluetoothDeviceProfileState.this) {
                mHeadsetService = (BluetoothHeadset) proxy;
                mHeadsetState = BluetoothProfile.STATE_DISCONNECTED;
                if (mAutoConnectionPending) {
                    sendMessage(AUTO_CONNECT_PROFILES);
                    mAutoConnectionPending = false;
                }
            }
        }
        public void onServiceDisconnected(int profile) {
            synchronized(BluetoothDeviceProfileState.this) {
                mHeadsetService = null;
                if (mHeadsetState != BluetoothHeadset.STATE_DISCONNECTED) {
                    // It seems BluetoothHeadsetService crashed. I am the only
                    // class to know valid BluetoothHeadset state. Let me send the
                    // updated status to other listeners.
                    int prevState = mHeadsetState;
                    Intent intent = new Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
                    mHeadsetState = BluetoothHeadset.STATE_DISCONNECTED;
                    mService.sendConnectionStateChange(mDevice, BluetoothProfile.HEADSET,
                                                       mHeadsetState, prevState);
                    intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
                    intent.putExtra(BluetoothProfile.EXTRA_STATE, mHeadsetState);
                    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
                    mContext.sendBroadcast(intent, BLUETOOTH_PERM);
                }
            }
        }
    };

    private class PbapServiceListener implements BluetoothPbap.ServiceListener {
        public PbapServiceListener() {
            mPbapService = new BluetoothPbap(mContext, this);
        }
        public void onServiceConnected() {
            synchronized(BluetoothDeviceProfileState.this) {
                mPbapServiceConnected = true;
            }
        }
        public void onServiceDisconnected() {
            synchronized(BluetoothDeviceProfileState.this) {
                mPbapServiceConnected = false;
            }
        }
    }

    private class BondedDevice extends State {
        @Override
        public void enter() {
            Log.i(TAG, "Entering ACL Connected state with: " + getCurrentMessage().what);
            Message m = new Message();
            m.copyFrom(getCurrentMessage());
            sendMessageAtFrontOfQueue(m);
        }
        @Override
        public boolean processMessage(Message message) {
            log("ACL Connected State -> Processing Message: " + message.what);
            switch(message.what) {
                case CONNECT_HFP_OUTGOING:
                    if (mUnpairStarted == true) {
                        log("Discarding message " + message.what);
                    } else {
                        transitionTo(mOutgoingHandsfree);
                    }
                    break;
                case DISCONNECT_HFP_OUTGOING:
                    transitionTo(mOutgoingHandsfree);
                    break;
                case CONNECT_HFP_INCOMING:
                    if (mUnpairStarted == true) {
                        log("Discarding message " + message.what);
                    } else {
                        transitionTo(mIncomingHandsfree);
                    }
                    break;
                case DISCONNECT_HFP_INCOMING:
                    transitionTo(mIncomingHandsfree);
                    break;
                case CONNECT_A2DP_OUTGOING:
                    if (mUnpairStarted == true) {
                        log("Discarding message " + message.what);
                    } else {
                        transitionTo(mOutgoingA2dp);
                    }
                    break;
                case DISCONNECT_A2DP_OUTGOING:
                    transitionTo(mOutgoingA2dp);
                    break;
                case CONNECT_A2DP_INCOMING:
                    if (mUnpairStarted == true) {
                        log("Discarding message " + message.what);
                    } else {
                        transitionTo(mIncomingA2dp);
                    }
                    break;
                case DISCONNECT_A2DP_INCOMING:
                    transitionTo(mIncomingA2dp);
                    break;
                case CONNECT_HID_OUTGOING:
                case DISCONNECT_HID_OUTGOING:
                    if (mUnpairStarted == true) {
                        log("Discarding message " + message.what);
                    } else {
                        transitionTo(mOutgoingHid);
                    }
                    break;
                case CONNECT_HID_INCOMING:
                case DISCONNECT_HID_INCOMING:
                    if (mUnpairStarted == true) {
                        log("Discarding message " + message.what);
                    } else {
                        transitionTo(mIncomingHid);
                    }
                    break;
                case DISCONNECT_PBAP_OUTGOING:
                    processCommand(DISCONNECT_PBAP_OUTGOING);
                    break;
                case UNPAIR:
                    if (mHeadsetState != BluetoothHeadset.STATE_DISCONNECTED) {
                        sendMessage(DISCONNECT_HFP_OUTGOING);
                        deferMessage(message);
                        break;
                    } else if (mA2dpState != BluetoothA2dp.STATE_DISCONNECTED) {
                        sendMessage(DISCONNECT_A2DP_OUTGOING);
                        deferMessage(message);
                        break;
                    } else if (mService.getInputDeviceConnectionState(mDevice) !=
                            BluetoothInputDevice.STATE_DISCONNECTED) {
                        sendMessage(DISCONNECT_HID_OUTGOING);
                        deferMessage(message);
                        break;
                    }
                    processCommand(UNPAIR);
                    break;
                case AUTO_CONNECT_PROFILES:
                    if (isPhoneDocked(mDevice)) {
                        // Don't auto connect to docks.
                        break;
                    } else {
                        if (mHeadsetService == null) {
                              mAutoConnectionPending = true;
                              log("AUTO_CONNECT Waiting for HeadsetSerive bind " +
                                    mDevice.getAddress());
                              break;
                        } else if (mHeadsetService.getPriority(mDevice) ==
                              BluetoothHeadset.PRIORITY_AUTO_CONNECT &&
                              mHeadsetService.getDevicesMatchingConnectionStates(
                                  new int[] {BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING,
                                             BluetoothProfile.STATE_DISCONNECTING}).size() == 0) {
                            mHeadsetService.connect(mDevice);
                        }
                        if (mA2dpService != null &&
                              mA2dpService.getPriority(mDevice) ==
                              BluetoothA2dp.PRIORITY_AUTO_CONNECT &&
                              mA2dpService.getDevicesMatchingConnectionStates(
                                  new int[] {BluetoothA2dp.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING,
                                             BluetoothProfile.STATE_DISCONNECTING}).size() == 0) {
                            mA2dpService.connect(mDevice);
                        }
                        if (mService.getInputDevicePriority(mDevice) ==
                              BluetoothInputDevice.PRIORITY_AUTO_CONNECT) {
                            mService.connectInputDevice(mDevice);
                        }
                    }
                    break;
                case CONNECT_OTHER_PROFILES:
                    if (isPhoneDocked(mDevice)) {
                       break;
                    }
                    if (message.arg1 == CONNECT_A2DP_OUTGOING) {
                        if (mA2dpService != null &&
                            (mA2dpService.getPriority(mDevice) >
                             BluetoothProfile.PRIORITY_OFF) &&
                            mA2dpService.getDevicesMatchingConnectionStates(
                                new int[] {BluetoothProfile.STATE_CONNECTED,
                                           BluetoothProfile.STATE_CONNECTING}).size() ==0) {
                            Log.i(TAG, "A2dp:Connect Other Profiles");
                            mA2dpService.connect(mDevice);
                        }
                    } else if (message.arg1 == CONNECT_HFP_OUTGOING) {
                        if (mHeadsetService == null) {
                            deferMessage(message);
                        } else {
                            if ((mHeadsetService.getPriority(mDevice) >
                                 BluetoothProfile.PRIORITY_OFF) &&
                                 mHeadsetService.getDevicesMatchingConnectionStates(
                                    new int[] {BluetoothProfile.STATE_CONNECTED,
                                               BluetoothProfile.STATE_CONNECTING}).size() ==0) {
                                Log.i(TAG, "Headset:Connect Other Profiles");
                                mHeadsetService.connect(mDevice);
                            }
                        }
                    }
                    break;
                case TRANSITION_TO_STABLE:
                    // ignore.
                    break;
                case UNPAIR_COMPLETE:
                    processCommand(UNPAIR_COMPLETE);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    @Override
    //<MR1 change>
    protected void onQuitting() {
        mContext.unregisterReceiver(mBroadcastReceiver);
        mBroadcastReceiver = null;
        mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mHeadsetService);
        mBluetoothProfileServiceListener = null;
        mOutgoingHandsfree = null;
        mPbap = null;
        mPbapService.close();
        mPbapService = null;
        mIncomingHid = null;
        mOutgoingHid = null;
        mIncomingHandsfree = null;
        mOutgoingHandsfree = null;
        mIncomingA2dp = null;
        mOutgoingA2dp = null;
        mBondedDevice = null;
	//<MR1 change>
        super.onQuitting();
    }

    private class OutgoingHandsfree extends State {
        private boolean mStatus = false;
        private int mCommand;

        @Override
        public void enter() {
            Log.i(TAG, "Entering OutgoingHandsfree state with: " + getCurrentMessage().what);
            mCommand = getCurrentMessage().what;
            if (mCommand != CONNECT_HFP_OUTGOING &&
                mCommand != DISCONNECT_HFP_OUTGOING) {
                Log.e(TAG, "Error: OutgoingHandsfree state with command:" + mCommand);
            }
            mStatus = processCommand(mCommand);
            if (!mStatus) {
                sendMessage(TRANSITION_TO_STABLE);
                mService.sendProfileStateMessage(BluetoothProfileState.HFP,
                                                 BluetoothProfileState.TRANSITION_TO_STABLE);
            }
        }

        @Override
        public boolean processMessage(Message message) {
            log("OutgoingHandsfree State -> Processing Message: " + message.what);
            Message deferMsg = new Message();
            int command = message.what;
            switch(command) {
                case CONNECT_HFP_OUTGOING:
                    if (command != mCommand) {
                        // Disconnect followed by a connect - defer
                        deferMessage(message);
                    }
                    break;
                case CONNECT_HFP_INCOMING:
                    if (mCommand == CONNECT_HFP_OUTGOING) {
                        // Cancel outgoing connect, accept incoming
                        cancelCommand(CONNECT_HFP_OUTGOING);
                        transitionTo(mIncomingHandsfree);
                    } else {
                        // We have done the disconnect but we are not
                        // sure which state we are in at this point.
                        deferMessage(message);
                    }
                    break;
                case CONNECT_A2DP_INCOMING:
                    transitionTo(mIncomingA2dp);
                    break;
                case CONNECT_A2DP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_HFP_OUTGOING:
                    if (mCommand == CONNECT_HFP_OUTGOING) {
                        // Cancel outgoing connect
                        cancelCommand(CONNECT_HFP_OUTGOING);
                        processCommand(DISCONNECT_HFP_OUTGOING);
                    }
                    // else ignore
                    break;
                case DISCONNECT_HFP_INCOMING:
                    // When this happens the socket would be closed and the headset
                    // state moved to DISCONNECTED, cancel the outgoing thread.
                    // if it still is in CONNECTING state
                    cancelCommand(CONNECT_HFP_OUTGOING);
                    break;
                case DISCONNECT_A2DP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_A2DP_INCOMING:
                    // Bluez will handle the disconnect. If because of this the outgoing
                    // handsfree connection has failed, then retry.
                    if (mStatus) {
                       deferMsg.what = mCommand;
                       deferMessage(deferMsg);
                    }
                    break;
                case CONNECT_HID_OUTGOING:
                case DISCONNECT_HID_OUTGOING:
                    deferMessage(message);
                    break;
                case CONNECT_HID_INCOMING:
                    transitionTo(mIncomingHid);
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break;
                case DISCONNECT_HID_INCOMING:
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break; // ignore
                case DISCONNECT_PBAP_OUTGOING:
                case UNPAIR:
                case UNPAIR_COMPLETE:
                case AUTO_CONNECT_PROFILES:
                case CONNECT_OTHER_PROFILES:
                    deferMessage(message);
                    break;
                case TRANSITION_TO_STABLE:
                    transitionTo(mBondedDevice);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class IncomingHandsfree extends State {
        private boolean mStatus = false;
        private int mCommand;

        @Override
        public void enter() {
            Log.i(TAG, "Entering IncomingHandsfree state with: " + getCurrentMessage().what);
            mCommand = getCurrentMessage().what;
            if (mCommand != CONNECT_HFP_INCOMING &&
                mCommand != DISCONNECT_HFP_INCOMING) {
                Log.e(TAG, "Error: IncomingHandsfree state with command:" + mCommand);
            }
            mStatus = processCommand(mCommand);
            if (!mStatus) {
                sendMessage(TRANSITION_TO_STABLE);
                mService.sendProfileStateMessage(BluetoothProfileState.HFP,
                                                 BluetoothProfileState.TRANSITION_TO_STABLE);
            }
        }

        @Override
        public boolean processMessage(Message message) {
            log("IncomingHandsfree State -> Processing Message: " + message.what);
            switch(message.what) {
                case CONNECT_HFP_OUTGOING:
                    deferMessage(message);
                    break;
                case CONNECT_HFP_INCOMING:
                    // Ignore
                    Log.e(TAG, "Error: Incoming connection with a pending incoming connection");
                    break;
                case CONNECTION_ACCESS_REQUEST_REPLY:
                    int val = message.arg1;
                    mConnectionAccessReplyReceived = true;
                    boolean value = false;
                    if (val == BluetoothDevice.CONNECTION_ACCESS_YES) {
                        value = true;
                    }
                    setTrust(val);

                    handleIncomingConnection(CONNECT_HFP_INCOMING, value);
                    break;
                case CONNECTION_ACCESS_REQUEST_EXPIRY:
                    if (!mConnectionAccessReplyReceived) {
                        handleIncomingConnection(CONNECT_HFP_INCOMING, false);
                        sendConnectionAccessRemovalIntent();
                        sendMessage(TRANSITION_TO_STABLE);
                    }
                    break;
                case CONNECT_A2DP_INCOMING:
                    // Serialize the commands.
                    deferMessage(message);
                    break;
                case CONNECT_A2DP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_HFP_OUTGOING:
                    // We don't know at what state we are in the incoming HFP connection state.
                    // We can be changing from DISCONNECTED to CONNECTING, or
                    // from CONNECTING to CONNECTED, so serializing this command is
                    // the safest option.
                    deferMessage(message);
                    break;
                case DISCONNECT_HFP_INCOMING:
                    // Nothing to do here, we will already be DISCONNECTED
                    // by this point.
                    break;
                case DISCONNECT_A2DP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_A2DP_INCOMING:
                    // Bluez handles incoming A2DP disconnect.
                    // If this causes incoming HFP to fail, it is more of a headset problem
                    // since both connections are incoming ones.
                    break;
                case CONNECT_HID_OUTGOING:
                case DISCONNECT_HID_OUTGOING:
                    deferMessage(message);
                    break;
                case CONNECT_HID_INCOMING:
                case DISCONNECT_HID_INCOMING:
                     break; // ignore
                case DISCONNECT_PBAP_OUTGOING:
                case UNPAIR:
                case UNPAIR_COMPLETE:
                case AUTO_CONNECT_PROFILES:
                case CONNECT_OTHER_PROFILES:
                    deferMessage(message);
                    break;
                case TRANSITION_TO_STABLE:
                    transitionTo(mBondedDevice);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class OutgoingA2dp extends State {
        private boolean mStatus = false;
        private int mCommand;

        @Override
        public void enter() {
            Log.i(TAG, "Entering OutgoingA2dp state with: " + getCurrentMessage().what);
            mCommand = getCurrentMessage().what;
            if (mCommand != CONNECT_A2DP_OUTGOING &&
                mCommand != DISCONNECT_A2DP_OUTGOING) {
                Log.e(TAG, "Error: OutgoingA2DP state with command:" + mCommand);
            }
            mStatus = processCommand(mCommand);
            if (!mStatus) {
                sendMessage(TRANSITION_TO_STABLE);
                mService.sendProfileStateMessage(BluetoothProfileState.A2DP,
                                                 BluetoothProfileState.TRANSITION_TO_STABLE);
            }
        }

        @Override
        public boolean processMessage(Message message) {
            log("OutgoingA2dp State->Processing Message: " + message.what);
            Message deferMsg = new Message();
            switch(message.what) {
                case CONNECT_HFP_OUTGOING:
                    processCommand(CONNECT_HFP_OUTGOING);

                    // Don't cancel A2DP outgoing as there is no guarantee it
                    // will get canceled.
                    // It might already be connected but we might not have got the
                    // A2DP_SINK_STATE_CHANGE. Hence, no point disconnecting here.
                    // The worst case, the connection will fail, retry.
                    // The same applies to Disconnecting an A2DP connection.
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break;
                case CONNECT_HFP_INCOMING:
                    processCommand(CONNECT_HFP_INCOMING);

                    // Don't cancel A2DP outgoing as there is no guarantee
                    // it will get canceled.
                    // The worst case, the connection will fail, retry.
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break;
                case CONNECT_A2DP_INCOMING:
                    // Bluez will take care of conflicts between incoming and outgoing
                    // connections.
                    transitionTo(mIncomingA2dp);
                    break;
                case CONNECT_A2DP_OUTGOING:
                    // Ignore
                    break;
                case DISCONNECT_HFP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_HFP_INCOMING:
                    // At this point, we are already disconnected
                    // with HFP. Sometimes A2DP connection can
                    // fail due to the disconnection of HFP. So add a retry
                    // for the A2DP.
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break;
                case DISCONNECT_A2DP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_A2DP_INCOMING:
                    // Ignore, will be handled by Bluez
                    break;
                case CONNECT_HID_OUTGOING:
                case DISCONNECT_HID_OUTGOING:
                    deferMessage(message);
                    break;
                case CONNECT_HID_INCOMING:
                    transitionTo(mIncomingHid);
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break;
                case DISCONNECT_HID_INCOMING:
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break; // ignore
                case DISCONNECT_PBAP_OUTGOING:
                case UNPAIR:
                case UNPAIR_COMPLETE:
                case AUTO_CONNECT_PROFILES:
                case CONNECT_OTHER_PROFILES:
                    deferMessage(message);
                    break;
                case TRANSITION_TO_STABLE:
                    transitionTo(mBondedDevice);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class IncomingA2dp extends State {
        private boolean mStatus = false;
        private int mCommand;

        @Override
        public void enter() {
            Log.i(TAG, "Entering IncomingA2dp state with: " + getCurrentMessage().what);
            mCommand = getCurrentMessage().what;
            if (mCommand != CONNECT_A2DP_INCOMING &&
                mCommand != DISCONNECT_A2DP_INCOMING) {
                Log.e(TAG, "Error: IncomingA2DP state with command:" + mCommand);
            }
            mStatus = processCommand(mCommand);
            if (!mStatus) {
                sendMessage(TRANSITION_TO_STABLE);
                mService.sendProfileStateMessage(BluetoothProfileState.A2DP,
                                                 BluetoothProfileState.TRANSITION_TO_STABLE);
            }
        }

        @Override
        public boolean processMessage(Message message) {
            log("IncomingA2dp State->Processing Message: " + message.what);
            switch(message.what) {
                case CONNECT_HFP_OUTGOING:
                    deferMessage(message);
                    break;
                case CONNECT_HFP_INCOMING:
                    // Shouldn't happen, but serialize the commands.
                    deferMessage(message);
                    break;
                case CONNECT_A2DP_INCOMING:
                    // ignore
                    break;
                case CONNECTION_ACCESS_REQUEST_REPLY:
                    int val = message.arg1;
                    mConnectionAccessReplyReceived = true;
                    boolean value = false;
                    if (val == BluetoothDevice.CONNECTION_ACCESS_YES) {
                        value = true;
                    }
                    setTrust(val);
                    handleIncomingConnection(CONNECT_A2DP_INCOMING, value);
                    break;
                case CONNECTION_ACCESS_REQUEST_EXPIRY:
                    // The check protects the race condition between REQUEST_REPLY
                    // and the timer expiry.
                    if (!mConnectionAccessReplyReceived) {
                        handleIncomingConnection(CONNECT_A2DP_INCOMING, false);
                        sendConnectionAccessRemovalIntent();
                        sendMessage(TRANSITION_TO_STABLE);
                    }
                    break;
                case CONNECT_A2DP_OUTGOING:
                    // Defer message and retry
                    deferMessage(message);
                    break;
                case DISCONNECT_HFP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_HFP_INCOMING:
                    // Shouldn't happen but if does, we can handle it.
                    // Depends if the headset can handle it.
                    // Incoming A2DP will be handled by Bluez, Disconnect HFP
                    // the socket would have already been closed.
                    // ignore
                    break;
                case DISCONNECT_A2DP_OUTGOING:
                    deferMessage(message);
                    break;
                case DISCONNECT_A2DP_INCOMING:
                    // Ignore, will be handled by Bluez
                    break;
                case CONNECT_HID_OUTGOING:
                case DISCONNECT_HID_OUTGOING:
                    deferMessage(message);
                    break;
                case CONNECT_HID_INCOMING:
                case DISCONNECT_HID_INCOMING:
                     break; // ignore
                case DISCONNECT_PBAP_OUTGOING:
                case UNPAIR:
                case UNPAIR_COMPLETE:
                case AUTO_CONNECT_PROFILES:
                case CONNECT_OTHER_PROFILES:
                    deferMessage(message);
                    break;
                case TRANSITION_TO_STABLE:
                    transitionTo(mBondedDevice);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }


    private class OutgoingHid extends State {
        private boolean mStatus = false;
        private int mCommand;

        @Override
        public void enter() {
            log("Entering OutgoingHid state with: " + getCurrentMessage().what);
            mCommand = getCurrentMessage().what;
            if (mCommand != CONNECT_HID_OUTGOING &&
                mCommand != DISCONNECT_HID_OUTGOING) {
                Log.e(TAG, "Error: OutgoingHid state with command:" + mCommand);
            }
            mStatus = processCommand(mCommand);
            if (!mStatus) sendMessage(TRANSITION_TO_STABLE);
        }

        @Override
        public boolean processMessage(Message message) {
            log("OutgoingHid State->Processing Message: " + message.what);
            Message deferMsg = new Message();
            switch(message.what) {
                // defer all outgoing messages
                case CONNECT_HFP_OUTGOING:
                case CONNECT_A2DP_OUTGOING:
                case CONNECT_HID_OUTGOING:
                case DISCONNECT_HFP_OUTGOING:
                case DISCONNECT_A2DP_OUTGOING:
                case DISCONNECT_HID_OUTGOING:
                    deferMessage(message);
                    break;

                case CONNECT_HFP_INCOMING:
                    transitionTo(mIncomingHandsfree);
                    break;
                case CONNECT_A2DP_INCOMING:
                    transitionTo(mIncomingA2dp);

                    // Don't cancel HID outgoing as there is no guarantee it
                    // will get canceled.
                    // It might already be connected but we might not have got the
                    // INPUT_DEVICE_STATE_CHANGE. Hence, no point disconnecting here.
                    // The worst case, the connection will fail, retry.
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break;
                case CONNECT_HID_INCOMING:
                  // Bluez will take care of the conflicts
                    transitionTo(mIncomingHid);
                    break;

                case DISCONNECT_HFP_INCOMING:
                case DISCONNECT_A2DP_INCOMING:
                    // At this point, we are already disconnected
                    // with HFP. Sometimes HID connection can
                    // fail due to the disconnection of HFP. So add a retry
                    // for the HID.
                    if (mStatus) {
                        deferMsg.what = mCommand;
                        deferMessage(deferMsg);
                    }
                    break;
                case DISCONNECT_HID_INCOMING:
                    // Ignore, will be handled by Bluez
                    break;
                case DISCONNECT_PBAP_OUTGOING:
                case UNPAIR:
                case AUTO_CONNECT_PROFILES:
                    deferMessage(message);
                    break;
                case TRANSITION_TO_STABLE:
                    transitionTo(mBondedDevice);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

  private class IncomingHid extends State {
      private boolean mStatus = false;
      private int mCommand;

      @Override
    public void enter() {
          log("Entering IncomingHid state with: " + getCurrentMessage().what);
          mCommand = getCurrentMessage().what;
          if (mCommand != CONNECT_HID_INCOMING &&
              mCommand != DISCONNECT_HID_INCOMING) {
              Log.e(TAG, "Error: IncomingHid state with command:" + mCommand);
          }
          mStatus = processCommand(mCommand);
          if (!mStatus) sendMessage(TRANSITION_TO_STABLE);
      }

      @Override
    public boolean processMessage(Message message) {
          log("IncomingHid State->Processing Message: " + message.what);
          Message deferMsg = new Message();
          switch(message.what) {
              case CONNECT_HFP_OUTGOING:
              case CONNECT_HFP_INCOMING:
              case DISCONNECT_HFP_OUTGOING:
              case CONNECT_A2DP_INCOMING:
              case CONNECT_A2DP_OUTGOING:
              case DISCONNECT_A2DP_OUTGOING:
              case CONNECT_HID_OUTGOING:
              case CONNECT_HID_INCOMING:
              case DISCONNECT_HID_OUTGOING:
                  deferMessage(message);
                  break;
              case CONNECTION_ACCESS_REQUEST_REPLY:
                  mConnectionAccessReplyReceived = true;
                  int val = message.arg1;
                  setTrust(val);
                  handleIncomingConnection(CONNECT_HID_INCOMING,
                      val == BluetoothDevice.CONNECTION_ACCESS_YES);
                  break;
              case CONNECTION_ACCESS_REQUEST_EXPIRY:
                  if (!mConnectionAccessReplyReceived) {
                      handleIncomingConnection(CONNECT_HID_INCOMING, false);
                      sendConnectionAccessRemovalIntent();
                      sendMessage(TRANSITION_TO_STABLE);
                  }
                  break;
              case DISCONNECT_HFP_INCOMING:
                  // Shouldn't happen but if does, we can handle it.
                  // Depends if the headset can handle it.
                  // Incoming HID will be handled by Bluez, Disconnect HFP
                  // the socket would have already been closed.
                  // ignore
                  break;
              case DISCONNECT_HID_INCOMING:
              case DISCONNECT_A2DP_INCOMING:
                  // Ignore, will be handled by Bluez
                  break;
              case DISCONNECT_PBAP_OUTGOING:
              case UNPAIR:
              case AUTO_CONNECT_PROFILES:
                  deferMessage(message);
                  break;
              case TRANSITION_TO_STABLE:
                  transitionTo(mBondedDevice);
                  break;
              default:
                  return NOT_HANDLED;
          }
          return HANDLED;
      }
  }


    synchronized void cancelCommand(int command) {
        if (command == CONNECT_HFP_OUTGOING ) {
            // Cancel the outgoing thread.
            if (mHeadsetService != null) {
                mHeadsetService.cancelConnectThread();
            }
            // HeadsetService is down. Phone process most likely crashed.
            // The thread would have got killed.
        }
    }

    synchronized void deferProfileServiceMessage(int command) {
        Message msg = new Message();
        msg.what = command;
        deferMessage(msg);
    }

    private void updateIncomingAllowedTimer() {
        // Not doing a perfect exponential backoff because
        // we want two different rates. For all practical
        // purposes, this is good enough.
        if (mIncomingRejectTimer == 0) mIncomingRejectTimer = INIT_INCOMING_REJECT_TIMER;

        mIncomingRejectTimer *= 5;
        if (mIncomingRejectTimer > MAX_INCOMING_REJECT_TIMER) {
            mIncomingRejectTimer = MAX_INCOMING_REJECT_TIMER;
        }
        writeTimerValue(mIncomingRejectTimer);
    }

    private boolean handleIncomingConnection(int command, boolean accept) {
        boolean ret = false;
        Log.i(TAG, "handleIncomingConnection:" + command + ":" + accept);
        switch (command) {
            case CONNECT_HFP_INCOMING:
                if (!accept) {
                    ret = mHeadsetService.rejectIncomingConnect(mDevice);
                    sendMessage(TRANSITION_TO_STABLE);
                    updateIncomingAllowedTimer();
                } else if (mHeadsetState == BluetoothHeadset.STATE_CONNECTING) {
                    writeTimerValue(0);
                    ret =  mHeadsetService.acceptIncomingConnect(mDevice);
                } else if (mHeadsetState == BluetoothHeadset.STATE_DISCONNECTED) {
                    writeTimerValue(0);
                    if(!mAdapter.isHostPatchRequired(mDevice,
                         BluetoothAdapter.HOST_PATCH_AVOID_AUTO_CONNECT)) {
                         Log.d(TAG, "Avoid Connecting Other Profiles Incoming HFP");
                         handleConnectionOfOtherProfiles(command);
                    }
                    ret = mHeadsetService.createIncomingConnect(mDevice);
                }
                break;
            case CONNECT_A2DP_INCOMING:
                if (!accept) {
                    ret = mA2dpService.allowIncomingConnect(mDevice, false);
                    sendMessage(TRANSITION_TO_STABLE);
                    updateIncomingAllowedTimer();
                } else {
                    writeTimerValue(0);
                    ret = mA2dpService.allowIncomingConnect(mDevice, true);
                    if(!mAdapter.isHostPatchRequired(mDevice,
                         BluetoothAdapter.HOST_PATCH_AVOID_AUTO_CONNECT)) {
                         Log.d(TAG, "Avoid Connecting Other Profiles Incoming A2DP");
                         handleConnectionOfOtherProfiles(command);
                    }
                }
                break;
            case CONNECT_HID_INCOMING:
                if (!accept) {
                    ret = mService.allowIncomingProfileConnect(mDevice, false);
                    sendMessage(TRANSITION_TO_STABLE);
                    updateIncomingAllowedTimer();
                } else {
                    writeTimerValue(0);
                    ret = mService.allowIncomingProfileConnect(mDevice, true);
                }
                break;
            default:
                Log.e(TAG, "Waiting for incoming connection but state changed to:" + command);
                break;
       }
       return ret;
    }

    private void sendConnectionAccessIntent() {
        mConnectionAccessReplyReceived = false;

        if (!mPowerManager.isScreenOn()) mWakeLock.acquire();

        Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
        intent.setClassName(ACCESS_AUTHORITY_PACKAGE, ACCESS_AUTHORITY_CLASS);
        intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                        BluetoothDevice.REQUEST_TYPE_PROFILE_CONNECTION);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }

    private void sendConnectionAccessRemovalIntent() {
        mWakeLock.release();
        Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }

    private int getTrust() {
        String address = mDevice.getAddress();
        if (mIncomingConnections != null) return mIncomingConnections.first;
        return CONNECTION_ACCESS_UNDEFINED;
    }


    private String getStringValue(long value) {
        StringBuilder sbr = new StringBuilder();
        sbr.append(Long.toString(System.currentTimeMillis()));
        sbr.append("-");
        sbr.append(Long.toString(value));
        return sbr.toString();
    }

    private void setTrust(int value) {
        String second;
        if (mIncomingConnections == null) {
            second = getStringValue(INIT_INCOMING_REJECT_TIMER);
        } else {
            second = mIncomingConnections.second;
        }

        mIncomingConnections = new Pair(value, second);
        mService.writeIncomingConnectionState(mDevice.getAddress(), mIncomingConnections);
    }

    private void writeTimerValue(long value) {
        Integer first;
        if (mIncomingConnections == null) {
            first = CONNECTION_ACCESS_UNDEFINED;
        } else {
            first = mIncomingConnections.first;
        }
        mIncomingConnections = new Pair(first, getStringValue(value));
        mService.writeIncomingConnectionState(mDevice.getAddress(), mIncomingConnections);
    }

    private long readTimerValue() {
        if (mIncomingConnections == null)
            return 0;
        String value = mIncomingConnections.second;
        String[] splits = value.split("-");
        if (splits != null && splits.length == 2) {
            return Long.parseLong(splits[1]);
        }
        return 0;
    }

    private boolean readIncomingAllowedValue() {
        if (readTimerValue() == 0) return true;
        String value = mIncomingConnections.second;
        String[] splits = value.split("-");
        if (splits != null && splits.length == 2) {
            long val1 = Long.parseLong(splits[0]);
            long val2 = Long.parseLong(splits[1]);
            if (val1 + val2 <= System.currentTimeMillis()) {
                return true;
            }
        }
        return false;
    }

    synchronized boolean processCommand(int command) {
        log("Processing command:" + command);
        ParcelUuid[] uuids = null;
        switch(command) {
            case  CONNECT_HFP_OUTGOING:
                if (mHeadsetService == null) {
                    deferProfileServiceMessage(command);
                } else {
                    return mHeadsetService.connectHeadsetInternal(mDevice);
                }
                break;
            case CONNECT_HFP_INCOMING:
                if (mHeadsetService == null) {
                    deferProfileServiceMessage(command);
                } else {
                    uuids = mDevice.getUuids();
                    if (!BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HSP) &&
                        !BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Handsfree)) {
                        mDevice.fetchUuidsWithSdp();
                    }
                    processIncomingConnectCommand(command);
                    return true;
                }
                break;
            case CONNECT_A2DP_OUTGOING:
                if (mA2dpService != null) {
                    return mA2dpService.connectSinkInternal(mDevice);
                }
                break;
            case CONNECT_A2DP_INCOMING:
                uuids = mDevice.getUuids();
                if (!BluetoothUuid.isUuidPresent(uuids,  BluetoothUuid.AudioSink)) {
                    mDevice.fetchUuidsWithSdp();
                }
                processIncomingConnectCommand(command);
                return true;
            case CONNECT_HID_OUTGOING:
                return mService.connectInputDeviceInternal(mDevice);
            case CONNECT_HID_INCOMING:
                uuids = mDevice.getUuids();
                if (!BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hid)) {
                    mDevice.fetchUuidsWithSdp();
                }
                processIncomingConnectCommand(command);
                return true;
            case DISCONNECT_HFP_OUTGOING:
                if (mHeadsetService == null) {
                    deferProfileServiceMessage(command);
                } else {
                    // Disconnect PBAP
                    // TODO(): Add PBAP to the state machine.
                    Message m = new Message();
                    m.what = DISCONNECT_PBAP_OUTGOING;
                    deferMessage(m);
                    int bluetoothState = mService.getBluetoothState();
                    if ((mHeadsetService.getPriority(mDevice) ==
                        BluetoothHeadset.PRIORITY_AUTO_CONNECT) &&
                        (bluetoothState != BluetoothAdapter.STATE_TURNING_OFF)) {
                        mHeadsetService.setPriority(mDevice, BluetoothHeadset.PRIORITY_ON);
                    }
                    return mHeadsetService.disconnectHeadsetInternal(mDevice);
                }
                break;
            case DISCONNECT_HFP_INCOMING:
                // ignore
                return true;
            case DISCONNECT_A2DP_INCOMING:
                // ignore
                return true;
            case DISCONNECT_A2DP_OUTGOING:
                if (mA2dpService != null) {
                    if (mA2dpService.getPriority(mDevice) ==
                        BluetoothA2dp.PRIORITY_AUTO_CONNECT) {
                        mA2dpService.setPriority(mDevice, BluetoothHeadset.PRIORITY_ON);
                    }
                    return mA2dpService.disconnectSinkInternal(mDevice);
                }
                break;
            case DISCONNECT_HID_INCOMING:
                // ignore
                return true;
            case DISCONNECT_HID_OUTGOING:
                if (mService.getInputDevicePriority(mDevice) ==
                    BluetoothInputDevice.PRIORITY_AUTO_CONNECT) {
                    mService.setInputDevicePriority(mDevice, BluetoothInputDevice.PRIORITY_ON);
                }
                return mService.disconnectInputDeviceInternal(mDevice);
            case DISCONNECT_PBAP_OUTGOING:
                if (!mPbapServiceConnected) {
                    deferProfileServiceMessage(command);
                } else {
                    return mPbapService.disconnect();
                }
                break;
            case UNPAIR:
                writeTimerValue(INIT_INCOMING_REJECT_TIMER);
                setTrust(CONNECTION_ACCESS_UNDEFINED);
                Message msg = obtainMessage(UNPAIR_COMPLETE);
                sendMessageDelayed(msg, UNPAIR_COMPLETE_DELAY);
                mUnpairStarted = true;
                return mService.removeBondInternal(mDevice.getAddress());
            case UNPAIR_COMPLETE:
                // unpair process in bluez will get triggered, unblocking
                // UI requests
                mUnpairStarted = false;
                break;
            default:
                Log.e(TAG, "Error: Unknown Command");
        }
        return false;
    }

    private void processIncomingConnectCommand(int command) {
        // Check if device is already trusted
        int access = getTrust();
        if (access == BluetoothDevice.CONNECTION_ACCESS_YES) {
            handleIncomingConnection(command, true);
        } else if (access == BluetoothDevice.CONNECTION_ACCESS_NO &&
                   !readIncomingAllowedValue()) {
            handleIncomingConnection(command, false);
        } else {
            sendConnectionAccessIntent();
            Message msg = obtainMessage(CONNECTION_ACCESS_REQUEST_EXPIRY);
            sendMessageDelayed(msg,
                               CONNECTION_ACCESS_REQUEST_EXPIRY_TIMEOUT);
        }
    }

    private void handleConnectionOfOtherProfiles(int command) {
        // The white paper recommendations mentions that when there is a
        // link loss, it is the responsibility of the remote device to connect.
        // Many connect only 1 profile - and they connect the second profile on
        // some user action (like play being pressed) and so we need this code.
        // Auto Connect code only connects to the last connected device - which
        // is useful in cases like when the phone reboots. But consider the
        // following case:
        // User is connected to the car's phone and  A2DP profile.
        // User comes to the desk  and places the phone in the dock
        // (or any speaker or music system or even another headset) and thus
        // gets connected to the A2DP profile.  User goes back to the car.
        // Ideally the car's system is supposed to send incoming connections
        // from both Handsfree and A2DP profile. But they don't. The Auto
        // connect code, will not work here because we only auto connect to the
        // last connected device for that profile which in this case is the dock.
        // Now suppose a user is using 2 headsets simultaneously, one for the
        // phone profile one for the A2DP profile. If this is the use case, we
        // expect the user to use the preference to turn off the A2DP profile in
        // the Settings screen for the first headset. Else, after link loss,
        // there can be an incoming connection from the first headset which
        // might result in the connection of the A2DP profile (if the second
        // headset is slower) and thus the A2DP profile on the second headset
        // will never get connected.
        //
        // TODO(): Handle other profiles here.
        switch (command) {
            case CONNECT_HFP_INCOMING:
                // Connect A2DP if there is no incoming connection
                // If the priority is OFF - don't auto connect.
                if (mA2dpService.getPriority(mDevice) == BluetoothProfile.PRIORITY_ON ||
                        mA2dpService.getPriority(mDevice) ==
                            BluetoothProfile.PRIORITY_AUTO_CONNECT) {
                    List<BluetoothDevice> sinks =
                        mA2dpService.getDevicesMatchingConnectionStates(
                                new int[] {BluetoothProfile.STATE_CONNECTED,
                                           BluetoothProfile.STATE_CONNECTING});

                    if (sinks.contains(mDevice)) {
                        return; // Already profile connection in progress
                    }
                    Message msg = new Message();
                    msg.what = CONNECT_OTHER_PROFILES;
                    msg.arg1 = CONNECT_A2DP_OUTGOING;
                    sendMessageDelayed(msg, CONNECT_OTHER_PROFILES_DELAY);
                } else if (mExpectingSdpComplete) {
                    Message msg = new Message();
                    msg.what = CONNECT_OTHER_PROFILES;
                    msg.arg1 = CONNECT_A2DP_OUTGOING;
                    sendMessageDelayed(msg, CONNECT_OTHER_PROFILES_DELAY);
                }
                break;
            case CONNECT_A2DP_INCOMING:
                // This is again against spec. HFP incoming connections should be made
                // before A2DP, so we should not hit this case. But many devices
                // don't follow this.
                if (mHeadsetService != null &&
                    (mHeadsetService.getPriority(mDevice) == BluetoothProfile.PRIORITY_ON ||
                        mHeadsetService.getPriority(mDevice) ==
                            BluetoothProfile.PRIORITY_AUTO_CONNECT)) {
                    List<BluetoothDevice> headsets =
                        mHeadsetService.getDevicesMatchingConnectionStates(
                                new int[] {BluetoothProfile.STATE_CONNECTED,
                                           BluetoothProfile.STATE_CONNECTING});

                    if (headsets.contains(mDevice)) {
                        return; // Already profile connection in progress
                    }
                    Message msg = new Message();
                    msg.what = CONNECT_OTHER_PROFILES;
                    msg.arg1 = CONNECT_HFP_OUTGOING;
                    sendMessageDelayed(msg, CONNECT_OTHER_PROFILES_DELAY);
                } else if (mHeadsetService != null &&
                           mExpectingSdpComplete) {
                    Message msg = new Message();
                    msg.what = CONNECT_OTHER_PROFILES;
                    msg.arg1 = CONNECT_HFP_OUTGOING;
                    sendMessageDelayed(msg, CONNECT_OTHER_PROFILES_DELAY);
                }
                break;
            default:
                break;
        }

    }

    /*package*/ BluetoothDevice getDevice() {
        return mDevice;
    }

    private void log(String message) {
        if (DBG) {
            Log.i(TAG, "Device:" + mDevice + " Message:" + message);
        }
    }
    //<MR1 change>
    public void my_quit() {
        super.quit();
    }
}
