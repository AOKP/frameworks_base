/*
 * Copyright (C) 2008 The Android Open Source Project
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

/**
 * TODO: Move this to services.jar
 * and make the constructor package private again.
 * @hide
 */

package android.server;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.media.Metadata;
import android.media.MediaPlayer;
import android.bluetooth.IBluetoothA2dp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.util.Log;
import android.net.Uri;
import android.telephony.TelephonyManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import android.database.Cursor;
import android.provider.MediaStore;
import android.media.MediaMetadataRetriever;


public class BluetoothA2dpService extends IBluetoothA2dp.Stub {
    private static final String TAG = "BluetoothA2dpService";
    private static final boolean DBG = true;

    public static final String BLUETOOTH_A2DP_SERVICE = "bluetooth_a2dp";

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    private static final String BLUETOOTH_ENABLED = "bluetooth_enabled";

    private static final String PROPERTY_STATE = "State";

    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private HashMap<BluetoothDevice, Integer> mAudioDevices;
    private final AudioManager mAudioManager;
    private final BluetoothService mBluetoothService;
    private final BluetoothAdapter mAdapter;
    private int   mTargetA2dpState;
    private BluetoothDevice mPlayingA2dpDevice;
    private IntentBroadcastHandler mIntentBroadcastHandler;
    private final WakeLock mWakeLock;

    private static final int MSG_CONNECTION_STATE_CHANGED = 0;

    /* AVRCP1.3 Metadata variables */
    private String mTrackName = DEFAULT_METADATA_STRING;
    private String mArtistName = DEFAULT_METADATA_STRING;
    private String mAlbumName = DEFAULT_METADATA_STRING;
    private String mMediaNumber = DEFAULT_METADATA_NUMBER;
    private String mMediaCount = DEFAULT_METADATA_NUMBER;
    private String mDuration = DEFAULT_METADATA_NUMBER;
    private String mGenre = DEFAULT_METADATA_STRING;
    private Long mReportTime = System.currentTimeMillis();
    private Uri mUri = null;
    private int mPlayStatus = STATUS_STOPPED;
    private long mPosition = (long)Long.valueOf(DEFAULT_METADATA_NUMBER);

    /* AVRCP1.3 Events */
    private final static int EVENT_PLAYSTATUS_CHANGED = 0x1;
    private final static int EVENT_TRACK_CHANGED = 0x2;

    /*AVRCP 1.3 Music App Intents */
    private static final String PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    private static final String META_CHANGED = "com.android.music.metachanged";
    private static final String PLAYSTATUS_REQUEST = "com.qualcomm.avrcp.playstatusrequest";
    private static final String PLAYSTATUS_RESPONSE = "com.qualcomm.avrcp.playstatusresponse";

    private final static String DEFAULT_METADATA_STRING = "Unknown";
    private final static String DEFAULT_METADATA_NUMBER = "0";

    /* AVRCP 1.3 PlayStatus */
    private final static int STATUS_STOPPED = 0X00;
    private final static int STATUS_PLAYING = 0X01;
    private final static int STATUS_PAUSED = 0X02;
    private final static int STATUS_FWD_SEEK = 0X03;
    private final static int STATUS_REV_SEEK = 0X04;
    private final static int STATUS_ERROR = 0XFF;
    private String mPlayStatusRequestPath = "/";

    private final static int MESSAGE_PLAYSTATUS_TIMEOUT = 1;
    private final static int MESSAGE_PLAYERSETTINGS_TIMEOUT = 2;

    private String[] mCursorCols = new String[] {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE,
    };

    TelephonyManager tmgr;
    private static final String ACTION_METADATA_CHANGED  =
        "qualcomm.MediaPlayer.action.METADATA_CHANGED";

    private static final String PLAYERSETTINGS_REQUEST = "com.qualcomm.avrcp.playersettingsrequest";
    private static final String PLAYERSETTINGS_RESPONSE = "com.qualcomm.avrcp.playersettingsresponse";

    private class PlayerSettings {
        public byte attr;
        public byte [] attrIds;
        public String path;
    };

    private PlayerSettings mPlayerSettings = new PlayerSettings();
    private class localPlayerSettings {
        public byte eq_value;
        public byte repeat_value;
        public byte shuffle_value;
        public byte scan_value;
    };
    private localPlayerSettings settingValues = new localPlayerSettings();
    private static final String COMMAND = "command";
    private static final String CMDGET = "get";
    private static final String CMDSET = "set";
    private static final String EXTRA_GET_COMMAND = "commandExtra";
    private static final String EXTRA_GET_RESPONSE = "Response";

    private static final int GET_ATTRIBUTE_IDS = 0;
    private static final int GET_VALUE_IDS = 1;
    private static final int GET_ATTRIBUTE_TEXT = 2;
    private static final int GET_VALUE_TEXT     = 3;
    private static final int GET_ATTRIBUTE_VALUES = 4;
    private static final int NOTIFY_ATTRIBUTE_VALUES = 5;
    private static final int GET_INVALID = 0xff;

    private static final String EXTRA_ATTRIBUTE_ID = "Attribute";
    private static final String EXTRA_VALUE_STRING_ARRAY = "ValueStrings";
    private static final String EXTRA_ATTRIB_VALUE_PAIRS = "AttribValuePairs";
    private static final String EXTRA_ATTRIBUTE_STRING_ARRAY = "AttributeStrings";
    private static final String EXTRA_VALUE_ID_ARRAY = "Values";
    private static final String EXTRA_ATTIBUTE_ID_ARRAY = "Attributes";

    public static final int VALUE_SHUFFLEMODE_OFF = 1;
    public static final int VALUE_SHUFFLEMODE_ALL = 2;
    public static final int VALUE_REPEATMODE_OFF = 1;
    public static final int VALUE_REPEATMODE_SINGLE = 2;
    public static final int VALUE_REPEATMODE_ALL = 3;
    public static final int VALUE_INVALID = 0;

    public static final int ATTRIBUTE_EQUALIZER = 1;
    public static final int ATTRIBUTE_REPEATMODE = 2;
    public static final int ATTRIBUTE_SHUFFLEMODE = 3;
    public static final int ATTRIBUTE_SCANMODE = 4;


    private byte [] def_attrib = new byte [] {ATTRIBUTE_REPEATMODE, ATTRIBUTE_SHUFFLEMODE};
    private byte [] value_repmode = new byte [] { VALUE_REPEATMODE_OFF,
                                                  VALUE_REPEATMODE_SINGLE,
                                                  VALUE_REPEATMODE_ALL };

    private byte [] value_shufmode = new byte [] { VALUE_SHUFFLEMODE_OFF,
                                                  VALUE_SHUFFLEMODE_ALL };
    private byte [] value_default = new byte [] {0};
    private final String UPDATE_ATTRIBUTES = "UpdateSupportedAttributes";
    private final String UPDATE_VALUES = "UpdateSupportedValues";
    private final String UPDATE_ATTRIB_VALUE = "UpdateCurrentValues";
    private final String UPDATE_ATTRIB_TEXT = "UpdateAttributesText";
    private final String UPDATE_VALUE_TEXT = "UpdateValuesText";
    private ArrayList <Integer> mPendingCmds;


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_PLAYSTATUS_TIMEOUT:
                    Log.i(TAG, "Timed outM - Sending Playstatus");
                    sendPlayStatus(mPlayStatusRequestPath);
                    break;
                case MESSAGE_PLAYERSETTINGS_TIMEOUT:
                    synchronized (mPendingCmds) {
                        Integer val = new Integer(msg.arg1);
                        if (!mPendingCmds.contains(val)) {
                            break;
                        }
                        mPendingCmds.remove(val);
                    }
                    switch (msg.arg1) {
                        case GET_ATTRIBUTE_IDS:
                            if (mPlayerSettings.path != null) {
                                sendPlayerSettingsNative(mPlayerSettings.path,
                                     UPDATE_ATTRIBUTES, def_attrib.length, def_attrib);
                            }
                            break;
                        case GET_VALUE_IDS:
                            switch (mPlayerSettings.attr) {
                                case ATTRIBUTE_REPEATMODE:
                                    if (mPlayerSettings.path != null) {
                                        sendPlayerSettingsNative(mPlayerSettings.path,
                                          UPDATE_VALUES, value_repmode.length, value_repmode);
                                    }
                                    break;
                                case ATTRIBUTE_SHUFFLEMODE:
                                    if (mPlayerSettings.path != null) {
                                        sendPlayerSettingsNative(mPlayerSettings.path,
                                             UPDATE_VALUES, value_shufmode.length, value_shufmode);
                                    }
                                    break;
                                default:
                                    if (mPlayerSettings.path != null) {
                                        sendPlayerSettingsNative(mPlayerSettings.path,
                                            UPDATE_VALUES, value_default.length, value_default);
                                    }
                                    break;
                            }
                        break;
                        case GET_ATTRIBUTE_VALUES:
                            int j = 0;
                            byte [] retVal = new byte [mPlayerSettings.attrIds.length*2];
                            for (int i = 0; i < mPlayerSettings.attrIds.length; i++) {
                                 retVal[j++] = mPlayerSettings.attrIds[i];
                                 if (mPlayerSettings.attrIds[i] == ATTRIBUTE_REPEATMODE) {
                                     retVal[j++] = settingValues.repeat_value;
                                 } else if (mPlayerSettings.attrIds[i] == ATTRIBUTE_SHUFFLEMODE) {
                                     retVal[j++] = settingValues.shuffle_value;
                                 } else {
                                     retVal[j++] = 0x0;
                                 }
                            }
                            if (mPlayerSettings.path != null) {
                                sendPlayerSettingsNative(mPlayerSettings.path,
                                          UPDATE_ATTRIB_VALUE, retVal.length, retVal);
                            }
                        break;
                        case GET_ATTRIBUTE_TEXT:
                        case GET_VALUE_TEXT:
                            String [] values = new String [mPlayerSettings.attrIds.length];
                            String msgVal = (msg.what == GET_ATTRIBUTE_TEXT) ? UPDATE_ATTRIB_TEXT :
                                             UPDATE_VALUE_TEXT;
                            for (int i = 0; i < mPlayerSettings.attrIds.length; i++) {
                                values[i] = "";
                            }
                            if (mPlayerSettings.path != null) {
                                sendSettingsTextNative(mPlayerSettings.path,
                                                   msgVal, values.length,
                                                   mPlayerSettings.attrIds, values);
                            }
                        break;
                    }
                    break;
                default :
                    break;
            }
        }
    };

    private String getValidUtf8String ( String str)
    {
        int Char, i;
        String temp;

        if (str == null) {
            return null;
        }

        for (i = 0; i < str.length(); i++) {
            Char = str.codePointAt(i);
            if ((Char > 0x10FFFF) ||
                ((Char & 0xFFFFF800) == 0xD800) ||
                ((Char >= 0xFDD0) && (Char <= 0xFDEF)) ||
                ((Char & 0xFFFE) == 0xFFFE)) {
                break;
            }
        }

        if (i != str.length()) {
            try {
                temp = new String(str.getBytes(), 0, i, "UTF-8");
                str = temp;
            } catch (Exception e) {
                Log.e(TAG, "Exception" + e);
            }
        }
        return str;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                               BluetoothAdapter.ERROR);
                switch (state) {
                case BluetoothAdapter.STATE_ON:
                    onBluetoothEnable();
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    onBluetoothDisable();
                    break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                synchronized (this) {
                    if (device == null) {
                        Log.e(TAG, "Error! device is null");
                        return;
                    }
                    if (mAudioDevices.containsKey(device)) {
                        int state = mAudioDevices.get(device);
                        handleSinkStateChange(device, state, BluetoothA2dp.STATE_DISCONNECTED);
                    }
                }
            } else if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == AudioManager.STREAM_MUSIC) {
                    List<BluetoothDevice> sinks = getConnectedDevices();

                    if (sinks.size() != 0 && isPhoneDocked(sinks.get(0))) {
                        String address = sinks.get(0).getAddress();
                        int newVolLevel =
                          intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, 0);
                        int oldVolLevel =
                          intent.getIntExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, 0);
                        String path = mBluetoothService.getObjectPathFromAddress(address);
                        if (newVolLevel > oldVolLevel) {
                            avrcpVolumeUpNative(path);
                        } else if (newVolLevel < oldVolLevel) {
                            avrcpVolumeDownNative(path);
                        }
                    }
                }
            } else if (action.equals(META_CHANGED)) {
                mTrackName = intent.getStringExtra("track");
                mArtistName = intent.getStringExtra("artist");
                mAlbumName = intent.getStringExtra("album");
                if (mTrackName == null)
                    mTrackName = DEFAULT_METADATA_STRING;
                if (mArtistName == null)
                    mArtistName = DEFAULT_METADATA_STRING;
                if (mAlbumName == null)
                    mAlbumName = DEFAULT_METADATA_STRING;
                long extra = intent.getLongExtra("id", 0);
                if (extra < 0)
                    extra = 0;
                mMediaNumber = String.valueOf(extra);
                extra = intent.getLongExtra("ListSize", 0);;
                if (extra < 0)
                    extra = 0;
                mMediaCount = String.valueOf(extra);
                extra = intent.getLongExtra("duration", 0);
                if (extra < 0)
                    extra = 0;
                mDuration = String.valueOf(extra);
                extra = intent.getLongExtra("position", 0);
                if (extra < 0)
                    extra = 0;
                mPosition = extra;
                if(DBG) {
                    Log.d(TAG, "Meta data info is trackname: "+ mTrackName+" artist: "+mArtistName);
                    Log.d(TAG, "mMediaNumber: "+mMediaNumber+" mediaCount "+mMediaCount);
                    Log.d(TAG, "mPostion "+ mPosition+" album: "+mAlbumName+ "duration "+mDuration);
                }
                for (String path: getConnectedSinksPaths()) {
                    sendMetaData(path);
                    sendEvent(path, EVENT_TRACK_CHANGED, Long.valueOf(mMediaNumber));
                }
            } else if (action.equals(PLAYSTATE_CHANGED)) {
                String currentTrackName = intent.getStringExtra("track");
                if ((currentTrackName != null) && (!currentTrackName.equals(mTrackName))) {
                    mTrackName = currentTrackName;
                    mArtistName = intent.getStringExtra("artist");
                    mAlbumName = intent.getStringExtra("album");
                    if (mTrackName == null)
                        mTrackName = DEFAULT_METADATA_STRING;
                    if (mArtistName == null)
                        mArtistName = DEFAULT_METADATA_STRING;
                    if (mAlbumName == null)
                        mAlbumName = DEFAULT_METADATA_STRING;
                    long extra = intent.getLongExtra("id", 0);
                    if (extra < 0)
                        extra = 0;
                    mMediaNumber = String.valueOf(extra);
                    extra = intent.getLongExtra("ListSize", 0);;
                    if (extra < 0)
                        extra = 0;
                    mMediaCount = String.valueOf(extra);
                    extra = intent.getLongExtra("duration", 0);
                    if (extra < 0)
                        extra = 0;
                    mDuration = String.valueOf(extra);
                    extra = intent.getLongExtra("position", 0);
                    if (extra < 0)
                        extra = 0;
                    mPosition = extra;
                    for (String path: getConnectedSinksPaths())
                        sendMetaData(path);
                }
                boolean playStatus = intent.getBooleanExtra("playing", false);
                mPosition = intent.getLongExtra("position", 0);
                if (mPosition < 0)
                    mPosition = 0;
                mPlayStatus = convertedPlayStatus(playStatus, mPosition);
                if(DBG) Log.d(TAG, "PlayState changed "+ mPlayStatus);
                for (String path: getConnectedSinksPaths()) {
                    sendEvent(path, EVENT_PLAYSTATUS_CHANGED, (long)mPlayStatus);
                }
            } else if (action.equals(PLAYSTATUS_RESPONSE)) {
                if(DBG) Log.d(TAG, "Received PLAYSTATUS_RESPONSE");
                long extra = intent.getLongExtra("duration", 0);
                if (extra < 0)
                    extra = 0;
                mDuration = String.valueOf(extra);
                mPosition = intent.getLongExtra("position", 0);
                if (mPosition < 0)
                    mPosition = 0;
                boolean playStatus = intent.getBooleanExtra("playing", false);
                mPlayStatus = convertedPlayStatus(playStatus, mPosition);
                if(DBG) Log.d(TAG, "Sending Playstatus");
                sendPlayStatus(mPlayStatusRequestPath);
            } else if (action.equals(ACTION_METADATA_CHANGED)) {
                Uri uri = intent.getParcelableExtra("uripath");
                log("uri is " + uri + "mUri is " + mUri);

                if (uri == null)
                    return;
                /* Ignore posting of track change intent for uri location
                   content://media/internal/ content://settings/system/alarm_alertmUri */
                String uriPath = uri.toString();
                String[] value = uriPath.split("//");

                if (value != null && value.length > 1) {
                    String[] value1 = value[1].split("/");
                    if(value1 != null && value1.length > 1) {
                       if (((value1[0].equals("media")) && (!value1[1].equals("external"))) ||
                           (value1[0].equals("settings"))) {
                           log("Internal audio file data, ignoring");
                           return;
                       }
                    }
                }

                String tempMediaNumber = mMediaNumber;

                mReportTime = intent.getLongExtra("time", 0);
                mDuration = String.valueOf(intent.getIntExtra("duration", 0));
                mPosition = intent.getIntExtra("position", 0);
                int playStatus = intent.getIntExtra("playstate", 0);
                log("PlaySatus is " + playStatus);

                if (playStatus != mPlayStatus) {
                    mPlayStatus = playStatus;
                    for (String path: getConnectedSinksPaths()) {
                        sendEvent(path, EVENT_PLAYSTATUS_CHANGED, (long)mPlayStatus);
                    }
                }

                log("Metadata received");
                log("Duration " + mDuration);
                log("position " + mPosition);
                log("playstate is " + mPlayStatus);

                if (uri.equals(mUri)) {
                    log("Update for same Uri, ignoring");
                    return;
                }

                mUri = uri;
                Cursor mCursor = null;
                try {
                    String temp;
                    mCursor = mContext.getContentResolver().query(mUri, mCursorCols,
                                        MediaStore.Audio.Media.IS_MUSIC + "=1", null,
                                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
                    mCursor.moveToFirst();
                    temp = mCursor.getString(
                        mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));

                    mTrackName = getValidUtf8String(temp);
                    temp = mCursor.getString(
                        mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));

                    mArtistName = getValidUtf8String(temp);
                    temp = mCursor.getString(
                        mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                    mAlbumName = getValidUtf8String(temp);

                    long mediaNumber = mCursor.getLong(
                        mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                    mMediaNumber = String.valueOf(mediaNumber);
                    log("Title is " + mTrackName);
                    log("Artist is " + mArtistName);
                    log("Album is " + mAlbumName);
                    log("ID is " + mMediaNumber);
                    mCursor.close();
                    mCursor = null;
                    Long tmpId = (Long)getTrackId(mTrackName);
                    log("tmpId is " + tmpId);
                    mMediaNumber = String.valueOf(tmpId);
                    log("ID is " + mMediaNumber);
                    if (!tempMediaNumber.equals(mMediaNumber)) {
                        /* file change happened */
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(mContext, mUri);
                        temp = mmr.extractMetadata(mmr.METADATA_KEY_GENRE);
                        mGenre = getValidUtf8String(temp);
                        log("Genre is " + mGenre);
                    }
                    mCursor = mContext.getContentResolver().query(
                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                            new String [] { MediaStore.Audio.Media._ID},
                                            MediaStore.Audio.Media.IS_MUSIC + "=1", null,
                                            MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
                    mMediaCount = String.valueOf(mCursor.getCount());
                    mCursor.close();
                    mCursor = null;
                    log("Track count is " + mMediaCount);
                } catch(Exception e) {
                    log("Exc is " + e);
                    // e.printStackTrace(); for debugging enable this
                    if (mCursor != null) {
                        mCursor.close();
                    }
                    mTrackName = null;
                    mArtistName = null;
                    mAlbumName = null;
                    mGenre = null;
                }

                log("end of parsing mData");
                for (String path: getConnectedSinksPaths()) {
                    sendMetaData(path);
                    sendEvent(path, EVENT_TRACK_CHANGED, Long.valueOf(mMediaNumber));
                }
            } else if (action.equals(PLAYERSETTINGS_RESPONSE)) {
                int getResponse = intent.getIntExtra(EXTRA_GET_RESPONSE,
                                                      GET_INVALID);
                byte [] data;
                String [] text;
                synchronized (mPendingCmds) {
                    Integer val = new Integer(getResponse);
                    if (mPendingCmds.contains(val)) {
                        mHandler.removeMessages(MESSAGE_PLAYERSETTINGS_TIMEOUT);
                        mPendingCmds.remove(val);
                    }
                }
                switch (getResponse) {
                    case GET_ATTRIBUTE_IDS:
                        data = intent.getByteArrayExtra(EXTRA_ATTIBUTE_ID_ARRAY);
                        if (mPlayerSettings.path != null) {
                            sendPlayerSettingsNative(mPlayerSettings.path,
                                           UPDATE_ATTRIBUTES, data.length, data);
                        }
                    break;
                    case GET_VALUE_IDS:
                        data = intent.getByteArrayExtra(EXTRA_VALUE_ID_ARRAY);
                        if (mPlayerSettings.path != null) {
                            sendPlayerSettingsNative(mPlayerSettings.path,
                                               UPDATE_VALUES, data.length, data);
                        }
                    break;
                    case GET_ATTRIBUTE_VALUES:
                    case NOTIFY_ATTRIBUTE_VALUES:
                        data = intent.getByteArrayExtra(EXTRA_ATTRIB_VALUE_PAIRS);
                        updateLocalPlayerSettings(data);
                        if (mPlayerSettings.path != null) {
                            sendPlayerSettingsNative(mPlayerSettings.path,
                                             UPDATE_ATTRIB_VALUE, data.length, data);
                        } else { //only for notification there can be no path set
                            for (String path: getConnectedSinksPaths()) {
                                sendPlayerSettingsNative(path,
                                             UPDATE_ATTRIB_VALUE, data.length, data);
                            }
                        }
                    break;
                    case GET_ATTRIBUTE_TEXT:
                        text = intent.getStringArrayExtra(EXTRA_ATTRIBUTE_STRING_ARRAY);
                        if (mPlayerSettings.path != null) {
                           sendSettingsTextNative(mPlayerSettings.path,
                                            UPDATE_ATTRIB_TEXT, text.length,
                                            mPlayerSettings.attrIds, text);
                        }
                    break;
                    case GET_VALUE_TEXT:
                        text = intent.getStringArrayExtra(EXTRA_VALUE_STRING_ARRAY);
                        if (mPlayerSettings.path != null) {
                            sendSettingsTextNative(mPlayerSettings.path,
                                             UPDATE_VALUE_TEXT, text.length,
                                             mPlayerSettings.attrIds, text);
                        }
                    break;
                }
            }
        }
    };

    private synchronized int convertedPlayStatus(boolean playing, long position) {
        if (playing == false && position == 0)
            return STATUS_STOPPED;
        if (playing == false)
            return STATUS_PAUSED;
        if (playing == true)
            return STATUS_PLAYING;
        return STATUS_ERROR;
    }

    private synchronized void sendMetaData(String path) {

        if (mTrackName == null || mTrackName.isEmpty())
            mTrackName = DEFAULT_METADATA_STRING;
        if (mArtistName == null || mArtistName.isEmpty())
            mArtistName = DEFAULT_METADATA_STRING;
        if (mAlbumName == null || mAlbumName.isEmpty())
            mAlbumName = DEFAULT_METADATA_STRING;
        if (mGenre == null || mGenre.isEmpty())
            mGenre = DEFAULT_METADATA_STRING;

        if(DBG) {
            Log.d(TAG, "sendMetaData "+ path);
            Log.d(TAG, "Meta data info is trackname: "+ mTrackName+" artist: "+mArtistName);
            Log.d(TAG, "mMediaNumber: "+mMediaNumber+" mediaCount "+mMediaCount);
            Log.d(TAG, "mPostion "+ mPosition+" album: "+mAlbumName+ "duration "+mDuration);
            Log.d(TAG, "mGenre "+ mGenre);
        }
        sendMetaDataNative(path);
    }

    private synchronized void sendEvent(String path, int eventId, long data) {
        if(DBG) Log.d(TAG, "sendEvent "+path+ " data "+ data);
        sendEventNative(path, eventId, data);
    }

    private synchronized void sendPlayStatus(String path) {
        if(DBG) Log.d(TAG, "sendPlayStatus"+ path);
        sendPlayStatusNative(path, (int)Integer.valueOf(mDuration), (int)mPosition, mPlayStatus);
    }

    private void onGetPlayStatusRequest(String path) {
        if(DBG) Log.d(TAG, "onGetPlayStatusRequest"+path);
        mPlayStatusRequestPath = path;
        int playStatus = mPlayStatus;
        log("onGetPlayStatus Request position is " + mPosition);
        if ((mPlayingA2dpDevice == null) && (mPlayStatus == STATUS_PLAYING)) {
            log("Some error in Player to update proper status");
            playStatus = STATUS_PAUSED;
        } else if (mPlayStatus == STATUS_PLAYING) {
            long curTime = System.currentTimeMillis();
            long timeElapsed = curTime - mReportTime;
            log("TimeElapsed is " + timeElapsed);
            mPosition += timeElapsed;
            mReportTime = curTime;
        }
        log("Updated position " + mPosition);
        sendPlayStatusNative(path, (int)Integer.valueOf(mDuration), (int)mPosition, playStatus);
    }

    private void onListPlayerAttributeRequest(String path) {
        if(DBG) Log.d(TAG, "onListPlayerAttributeRequest"+path);
        mPlayerSettings.path = path;
        Intent intent = new Intent(PLAYERSETTINGS_REQUEST);
        intent.putExtra(COMMAND, CMDGET);
        intent.putExtra(EXTRA_GET_COMMAND, GET_ATTRIBUTE_IDS);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);

        Message msg = mHandler.obtainMessage();
        msg.what = MESSAGE_PLAYERSETTINGS_TIMEOUT;
        msg.arg1 = GET_ATTRIBUTE_IDS;
        mPendingCmds.add(new Integer(msg.arg1));
        mHandler.sendMessageDelayed(msg, 130);
    }

    private void onListPlayerAttributeValues(String path, byte attr ) {
        if(DBG) Log.d(TAG, "onListPlayerAttributeValues"+path);
        mPlayerSettings.path = path;
        mPlayerSettings.attr = attr;

        Intent intent = new Intent(PLAYERSETTINGS_REQUEST);
        Message msg = mHandler.obtainMessage();

        intent.putExtra(COMMAND, CMDGET);
        intent.putExtra(EXTRA_GET_COMMAND, GET_VALUE_IDS);
        intent.putExtra(EXTRA_ATTRIBUTE_ID, attr);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);

        msg.what = MESSAGE_PLAYERSETTINGS_TIMEOUT;
        msg.arg1 = GET_VALUE_IDS;
        mPendingCmds.add(new Integer(msg.arg1));
        mHandler.sendMessageDelayed(msg, 130);
    }

    private void onGetPlayerAttributeValues(String path, byte[] attrIds ) {
        if(DBG) Log.d(TAG, "onGetPlayerAttributeValues"+path);
        mPlayerSettings.path = path;
        mPlayerSettings.attrIds = new byte [attrIds.length];

        Intent intent = new Intent(PLAYERSETTINGS_REQUEST);
        Message msg = mHandler.obtainMessage();

        intent.putExtra(COMMAND, CMDGET);
        intent.putExtra(EXTRA_GET_COMMAND, GET_ATTRIBUTE_VALUES);
        intent.putExtra(EXTRA_ATTIBUTE_ID_ARRAY, attrIds);
        for (int i = 0; i < attrIds.length; i++)
            mPlayerSettings.attrIds[i] = attrIds[i];
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);

        msg.what = MESSAGE_PLAYERSETTINGS_TIMEOUT;
        msg.arg1 = GET_ATTRIBUTE_VALUES;
        mPendingCmds.add(new Integer(msg.arg1));
        mHandler.sendMessageDelayed(msg, 130);
    }

    private void onSetPlayerAttributeValues(String path, byte[] attrValues ) {
        if(DBG) Log.d(TAG, "onListPlayerAttributeValues"+path);
        Intent intent = new Intent(PLAYERSETTINGS_REQUEST);
        Message msg = mHandler.obtainMessage();

        intent.putExtra(COMMAND, CMDSET);
        intent.putExtra(EXTRA_ATTRIB_VALUE_PAIRS, attrValues);
        mPlayerSettings.path = path;

        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void onListPlayerAttributesText(String path, byte[] attrIds ) {
        if(DBG) Log.d(TAG, "onListPlayerAttributesText"+path);
        Intent intent = new Intent(PLAYERSETTINGS_REQUEST);
        Message msg = mHandler.obtainMessage();

        intent.putExtra(COMMAND, CMDGET);
        intent.putExtra(EXTRA_GET_COMMAND, GET_ATTRIBUTE_TEXT);
        intent.putExtra(EXTRA_ATTIBUTE_ID_ARRAY, attrIds);
        mPlayerSettings.path = path;
        mPlayerSettings.attrIds = new byte [attrIds.length];
        for (int i = 0; i < attrIds.length; i++)
            mPlayerSettings.attrIds[i] = attrIds[i];

        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        msg.what = MESSAGE_PLAYERSETTINGS_TIMEOUT;
        msg.arg1 = GET_ATTRIBUTE_TEXT;
        mPendingCmds.add(new Integer(msg.arg1));
        mHandler.sendMessageDelayed(msg, 130);
    }

    private void onListAttributeValuesText(String path, byte attr, byte[] valIds ) {
        if(DBG) Log.d(TAG, "onListattributeValuesText"+path);
        Intent intent = new Intent(PLAYERSETTINGS_REQUEST);
        Message msg = mHandler.obtainMessage();

        intent.putExtra(COMMAND, CMDGET);
        intent.putExtra(EXTRA_GET_COMMAND, GET_VALUE_TEXT);
        intent.putExtra(EXTRA_ATTRIBUTE_ID, attr);
        intent.putExtra(EXTRA_VALUE_ID_ARRAY, valIds);
        mPlayerSettings.path = path;
        mPlayerSettings.attrIds = new byte [valIds.length];
        for (int i = 0; i < valIds.length; i++)
            mPlayerSettings.attrIds[i] = valIds[i];

        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        msg.what = MESSAGE_PLAYERSETTINGS_TIMEOUT;
        msg.arg1 = GET_VALUE_TEXT;
        mPendingCmds.add(new Integer(msg.arg1));
        mHandler.sendMessageDelayed(msg, 130);
    }

    private boolean isPhoneDocked(BluetoothDevice device) {
        // This works only because these broadcast intents are "sticky"
        Intent i = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_DOCK_EVENT));
        if (i != null) {
            int state = i.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED);
            if (state != Intent.EXTRA_DOCK_STATE_UNDOCKED) {
                BluetoothDevice dockDevice = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (dockDevice != null && device.equals(dockDevice)) {
                    return true;
                }
            }
        }
        return false;
    }

    public BluetoothA2dpService(Context context, BluetoothService bluetoothService) {
        mContext = context;

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BluetoothA2dpService");

        mIntentBroadcastHandler = new IntentBroadcastHandler();

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mBluetoothService = bluetoothService;
        if (mBluetoothService == null) {
            throw new RuntimeException("Platform does not support Bluetooth");
        }

        if (!initNative()) {
            throw new RuntimeException("Could not init BluetoothA2dpService");
        }

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mIntentFilter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
        mIntentFilter.addAction(ACTION_METADATA_CHANGED);
        mIntentFilter.addAction(PLAYERSETTINGS_RESPONSE);
        mContext.registerReceiver(mReceiver, mIntentFilter);

        mAudioDevices = new HashMap<BluetoothDevice, Integer>();
        mPendingCmds = new ArrayList<Integer>();

        if (mBluetoothService.isEnabled())
            onBluetoothEnable();
        mTargetA2dpState = -1;
        tmgr = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mBluetoothService.setA2dpService(this);
        settingValues.repeat_value = 1;
        settingValues.shuffle_value = 1;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            cleanupNative();
        } finally {
            super.finalize();
        }
    }

    private int convertBluezSinkStringToState(String value) {
        if (value.equalsIgnoreCase("disconnected"))
            return BluetoothA2dp.STATE_DISCONNECTED;
        if (value.equalsIgnoreCase("connecting"))
            return BluetoothA2dp.STATE_CONNECTING;
        if (value.equalsIgnoreCase("connected"))
            return BluetoothA2dp.STATE_CONNECTED;
        if (value.equalsIgnoreCase("playing"))
            return BluetoothA2dp.STATE_PLAYING;
        if (value.equalsIgnoreCase("disconnecting"))
            return BluetoothA2dp.STATE_DISCONNECTING;
        return -1;
    }

    private boolean isSinkDevice(BluetoothDevice device) {
        ParcelUuid[] uuids = mBluetoothService.getRemoteUuids(device.getAddress());
        if (uuids != null && BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.AudioSink)) {
            return true;
        }
        return false;
    }

    private synchronized void addAudioSink(BluetoothDevice device) {
        if (mAudioDevices.get(device) == null) {
            mAudioDevices.put(device, BluetoothA2dp.STATE_DISCONNECTED);
        }
    }

    private synchronized void onBluetoothEnable() {
        String devices = mBluetoothService.getProperty("Devices", true);
        if (devices != null) {
            String [] paths = devices.split(",");
            for (String path: paths) {
                String address = mBluetoothService.getAddressFromObjectPath(path);
                BluetoothDevice device = mAdapter.getRemoteDevice(address);
                ParcelUuid[] remoteUuids = mBluetoothService.getRemoteUuids(address);
                if (remoteUuids != null)
                    if (BluetoothUuid.containsAnyUuid(remoteUuids,
                            new ParcelUuid[] {BluetoothUuid.AudioSink,
                                                BluetoothUuid.AdvAudioDist})) {
                        addAudioSink(device);
                    }
                }
        }
        mAudioManager.setParameters(BLUETOOTH_ENABLED+"=true");
        mAudioManager.setParameters("A2dpSuspended=false");
        mPlayingA2dpDevice = null;
    }

    private synchronized void onBluetoothDisable() {
        mAudioManager.setParameters(BLUETOOTH_ENABLED + "=false");
        if (!mAudioDevices.isEmpty()) {
            BluetoothDevice[] devices = new BluetoothDevice[mAudioDevices.size()];
            devices = mAudioDevices.keySet().toArray(devices);
            for (BluetoothDevice device : devices) {
                int state = getConnectionState(device);
                switch (state) {
                    case BluetoothA2dp.STATE_CONNECTING:
                    case BluetoothA2dp.STATE_CONNECTED:
                    case BluetoothA2dp.STATE_PLAYING:
                        disconnectSinkNative(mBluetoothService.getObjectPathFromAddress(
                                device.getAddress()));
                        handleSinkStateChange(device, state,
                                              BluetoothA2dp.STATE_DISCONNECTING);
                        break;
                    case BluetoothA2dp.STATE_DISCONNECTING:
                        handleSinkStateChange(device, BluetoothA2dp.STATE_DISCONNECTING,
                                              BluetoothA2dp.STATE_DISCONNECTED);
                        break;
                }
            }
            mAudioDevices.clear();
        }

    }

    private synchronized boolean isConnectSinkFeasible(BluetoothDevice device) {
        if (!mBluetoothService.isEnabled() || !isSinkDevice(device) ||
                getPriority(device) == BluetoothA2dp.PRIORITY_OFF) {
            return false;
        }

        addAudioSink(device);

        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        if (path == null) {
            return false;
        }
        return true;
    }

    public synchronized boolean isA2dpPlaying(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
            "Need BLUETOOTH_ADMIN permission");
        if (DBG) log("isA2dpPlaying(" + device + ")");
        if (device.equals(mPlayingA2dpDevice)) return true;
        return false;
    }

    public synchronized boolean connect(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH_ADMIN permission");
        if (DBG) log("connectSink(" + device + ")");
        if (!isConnectSinkFeasible(device)) return false;

        int state;
        for (BluetoothDevice sinkDevice : mAudioDevices.keySet()) {
            state = getConnectionState(sinkDevice);
            if (state != BluetoothProfile.STATE_DISCONNECTED) {
                if (device.equals(sinkDevice) &&
                    ((state == BluetoothProfile.STATE_CONNECTING) ||
                     (state == BluetoothProfile.STATE_CONNECTED))) {
                     return true; // already connecting to same device.
                }
                disconnect(sinkDevice);
            }
        }

        return mBluetoothService.connectSink(device.getAddress());
    }

    public synchronized boolean connectSinkInternal(BluetoothDevice device) {
        if (!mBluetoothService.isEnabled()) return false;

        int state = mAudioDevices.get(device);
        // ignore if there are any active sinks
        if (getDevicesMatchingConnectionStates(new int[] {
                BluetoothA2dp.STATE_CONNECTING,
                BluetoothA2dp.STATE_CONNECTED,
                BluetoothA2dp.STATE_DISCONNECTING}).size() != 0) {
            return false;
        }

        switch (state) {
        case BluetoothA2dp.STATE_CONNECTED:
        case BluetoothA2dp.STATE_DISCONNECTING:
            return false;
        case BluetoothA2dp.STATE_CONNECTING:
            return true;
        }

        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());

        // State is DISCONNECTED and we are connecting.
        if (getPriority(device) < BluetoothA2dp.PRIORITY_AUTO_CONNECT) {
            setPriority(device, BluetoothA2dp.PRIORITY_AUTO_CONNECT);
        }
        handleSinkStateChange(device, state, BluetoothA2dp.STATE_CONNECTING);

        if (!connectSinkNative(path)) {
            // Restore previous state
            handleSinkStateChange(device, mAudioDevices.get(device), state);
            return false;
        }
        return true;
    }

    private synchronized boolean isDisconnectSinkFeasible(BluetoothDevice device) {
        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        if (path == null) {
            return false;
        }

        int state = getConnectionState(device);
        switch (state) {
        case BluetoothA2dp.STATE_DISCONNECTED:
        case BluetoothA2dp.STATE_DISCONNECTING:
            return false;
        }
        return true;
    }

    public synchronized boolean disconnect(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH_ADMIN permission");
        if (DBG) log("disconnectSink(" + device + ")");
        if (!isDisconnectSinkFeasible(device)) return false;
        return mBluetoothService.disconnectSink(device.getAddress());
    }

    public synchronized boolean disconnectSinkInternal(BluetoothDevice device) {
        int state = getConnectionState(device);
        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());

        switch (state) {
            case BluetoothA2dp.STATE_DISCONNECTED:
                return false;
              // already in disconnecting state not a failure case.
            case BluetoothA2dp.STATE_DISCONNECTING:
                return true;
        }
        // State is CONNECTING or CONNECTED or PLAYING
        handleSinkStateChange(device, state, BluetoothA2dp.STATE_DISCONNECTING);
        if (!disconnectSinkNative(path)) {
            // Restore previous state
            handleSinkStateChange(device, mAudioDevices.get(device), state);
            return false;
        }
        return true;
    }

    public synchronized boolean suspendSink(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                            "Need BLUETOOTH_ADMIN permission");
        if (DBG) log("suspendSink(" + device + "), mTargetA2dpState: "+mTargetA2dpState);
        if (device == null || mAudioDevices == null) {
            return false;
        }
        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        Integer state = mAudioDevices.get(device);
        if (path == null || state == null) {
            return false;
        }
        // device state will not reflect playback state
        if(isA2dpPlaying(device)) {
                state = BluetoothA2dp.STATE_PLAYING;
        }

        mTargetA2dpState = BluetoothA2dp.STATE_CONNECTED;
        return checkSinkSuspendState(state.intValue());
    }

    public synchronized boolean resumeSink(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                            "Need BLUETOOTH_ADMIN permission");
        if (DBG) log("resumeSink(" + device + "), mTargetA2dpState: "+mTargetA2dpState);
        if (device == null || mAudioDevices == null) {
            return false;
        }
        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        Integer state = mAudioDevices.get(device);
        if (path == null || state == null) {
            return false;
        }
        // device state will not reflect playback state
        if(isA2dpPlaying(device)) {
                state = BluetoothA2dp.STATE_PLAYING;
        }

        mTargetA2dpState = BluetoothA2dp.STATE_PLAYING;
        return checkSinkSuspendState(state.intValue());
    }

    public synchronized int getConnectionState(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Integer state = mAudioDevices.get(device);
        if (state == null)
            return BluetoothA2dp.STATE_DISCONNECTED;
        return state;
    }

    public synchronized String[] getConnectedSinksPaths() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> btDevices = getConnectedDevices();
        String[] paths = new String[btDevices.size()];
        int index = 0;
        for(BluetoothDevice device:btDevices) {
            paths[index++] = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        }
        return paths;
    }

    public synchronized List<BluetoothDevice> getConnectedDevices() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> sinks = getDevicesMatchingConnectionStates(
                new int[] {BluetoothA2dp.STATE_CONNECTED});
        return sinks;
    }

    public synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        ArrayList<BluetoothDevice> sinks = new ArrayList<BluetoothDevice>();
        for (BluetoothDevice device: mAudioDevices.keySet()) {
            int sinkState = getConnectionState(device);
            for (int state : states) {
                if (state == sinkState) {
                    sinks.add(device);
                    break;
                }
            }
        }
        return sinks;
    }
    // MR1 Change
    public synchronized int getPriority(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()),
                BluetoothA2dp.PRIORITY_UNDEFINED);
    }
    // MR1 Change
    public synchronized boolean setPriority(BluetoothDevice device, int priority) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH_ADMIN permission");
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()), priority);
    }

    public synchronized boolean allowIncomingConnect(BluetoothDevice device, boolean value) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH_ADMIN permission");
        String address = device.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return false;
        }
        Integer data = mBluetoothService.getAuthorizationAgentRequestData(address);
        if (data == null) {
            Log.w(TAG, "allowIncomingConnect(" + device + ") called but no native data available");
            return false;
        }
        log("allowIncomingConnect: A2DP: " + device + ":" + value);
        return mBluetoothService.setAuthorizationNative(address, value, data.intValue());
    }

    /**
     * Called by native code on a PropertyChanged signal from
     * org.bluez.AudioSink.
     *
     * @param path the object path for the changed device
     * @param propValues a string array containing the key and one or more
     *  values.
     */
    private synchronized void onSinkPropertyChanged(String path, String[] propValues) {
        if (!mBluetoothService.isEnabled()) {
            return;
        }

        String name = propValues[0];
        String address = mBluetoothService.getAddressFromObjectPath(path);
        if (address == null) {
            Log.e(TAG, "onSinkPropertyChanged: Address of the remote device in null");
            return;
        }

        BluetoothDevice device = mAdapter.getRemoteDevice(address);

        if (name.equals(PROPERTY_STATE)) {
            int state = convertBluezSinkStringToState(propValues[1]);
            log("A2DP: onSinkPropertyChanged newState is: " + state + "mPlayingA2dpDevice: " + mPlayingA2dpDevice);

            if (mAudioDevices.get(device) == null) {
                // This is for an incoming connection for a device not known to us.
                // We have authorized it and bluez state has changed.
                addAudioSink(device);
                handleSinkStateChange(device, BluetoothA2dp.STATE_DISCONNECTED, state);
            } else {
                if (state == BluetoothA2dp.STATE_PLAYING && mPlayingA2dpDevice == null) {
                    if (tmgr.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                        mPlayingA2dpDevice = device;
                        handleSinkPlayingStateChange(device, state, BluetoothA2dp.STATE_NOT_PLAYING);
                    } else {
                       log("suspend Sink");
                       // During call active a2dp device is in suspended state
                       // so audio will not be routed to A2dp. To avoid IOP
                       // issues send a SUSPEND on A2dp if remote device asks
                       // for PLAY during call active state.
                       suspendSinkNative(mBluetoothService.getObjectPathFromAddress(
                                device.getAddress()));
                    }
                } else if (state == BluetoothA2dp.STATE_CONNECTED && mPlayingA2dpDevice != null) {
                    mPlayingA2dpDevice = null;
                    handleSinkPlayingStateChange(device, BluetoothA2dp.STATE_NOT_PLAYING,
                        BluetoothA2dp.STATE_PLAYING);
                } else {
                   mPlayingA2dpDevice = null;
                   int prevState = mAudioDevices.get(device);
                   handleSinkStateChange(device, prevState, state);
                }
            }
        }
    }

    private void handleSinkStateChange(BluetoothDevice device, int prevState, int state) {
        if (state != prevState) {
            mAudioDevices.put(device, state);

            checkSinkSuspendState(state);
            mTargetA2dpState = -1;

            if (getPriority(device) > BluetoothA2dp.PRIORITY_OFF &&
                    state == BluetoothA2dp.STATE_CONNECTED) {
                // We have connected or attempting to connect.
                // Bump priority
                setPriority(device, BluetoothA2dp.PRIORITY_AUTO_CONNECT);
                // We will only have 1 device with AUTO_CONNECT priority
                // To be backward compatible set everyone else to have PRIORITY_ON
                adjustOtherSinkPriorities(device);
                mPlayerSettings.path =
                  mBluetoothService.getObjectPathFromAddress(device.getAddress());
                updateLocalSettingsToBluez();
            }

            int delay = mAudioManager.setBluetoothA2dpDeviceConnectionState(device, state);

            mWakeLock.acquire();
            mIntentBroadcastHandler.sendMessageDelayed(mIntentBroadcastHandler.obtainMessage(
                                                            MSG_CONNECTION_STATE_CHANGED,
                                                            prevState,
                                                            state,
                                                            device),
                                                       delay);
        }
        if (prevState == BluetoothA2dp.STATE_CONNECTING &&
             state == BluetoothA2dp.STATE_CONNECTED) {
            for (String path: getConnectedSinksPaths()) {
                sendMetaData(path);
                sendEvent(path, EVENT_PLAYSTATUS_CHANGED, (long)mPlayStatus);
            }
        }
    }

    private void handleSinkPlayingStateChange(BluetoothDevice device, int state, int prevState) {
        Intent intent = new Intent(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);

        if (DBG) log("A2DP Playing state : device: " + device + " State:" + prevState + "->" + state);
    }

    private void adjustOtherSinkPriorities(BluetoothDevice connectedDevice) {
        for (BluetoothDevice device : mAdapter.getBondedDevices()) {
            if (getPriority(device) >= BluetoothA2dp.PRIORITY_AUTO_CONNECT &&
                !device.equals(connectedDevice)) {
                setPriority(device, BluetoothA2dp.PRIORITY_ON);
            }
        }
    }

    private boolean checkSinkSuspendState(int state) {
        boolean result = true;

        if (state != mTargetA2dpState) {
            if (state == BluetoothA2dp.STATE_PLAYING &&
                mTargetA2dpState == BluetoothA2dp.STATE_CONNECTED) {
                mAudioManager.setParameters("A2dpSuspended=true");
            } else if (state == BluetoothA2dp.STATE_CONNECTED &&
                mTargetA2dpState == BluetoothA2dp.STATE_PLAYING) {
                mAudioManager.setParameters("A2dpSuspended=false");
            } else {
                result = false;
            }
        }
        return result;
    }

    /**
     * Called by native code for the async response to a Connect
     * method call to org.bluez.AudioSink.
     *
     * @param deviceObjectPath the object path for the connecting device
     * @param result true on success; false on error
     */
    private void onConnectSinkResult(String deviceObjectPath, boolean result) {
        // If the call was a success, ignore we will update the state
        // when we a Sink Property Change
        if (!result) {
            if (deviceObjectPath != null) {
                String address = mBluetoothService.getAddressFromObjectPath(deviceObjectPath);
                if (address == null) return;
                BluetoothDevice device = mAdapter.getRemoteDevice(address);
                int state = getConnectionState(device);
                handleSinkStateChange(device, state, BluetoothA2dp.STATE_DISCONNECTED);
            }
        }
    }

    /** Handles A2DP connection state change intent broadcasts. */
    private class IntentBroadcastHandler extends Handler {

        private void onConnectionStateChanged(BluetoothDevice device, int prevState, int state) {
            Intent intent = new Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);

            if (DBG) log("A2DP state : device: " + device + " State:" + prevState + "->" + state);

            mBluetoothService.sendConnectionStateChange(device, BluetoothProfile.A2DP, state,
                                                        prevState);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONNECTION_STATE_CHANGED:
                    onConnectionStateChanged((BluetoothDevice) msg.obj, msg.arg1, msg.arg2);
                    mWakeLock.release();
                    break;
            }
        }
    }

    private long getTrackId(String trackName) {
        long trackId = 0;

        if (trackName == null)
            return trackId;

        try {
            Cursor musicCursor = mContext.getContentResolver().query(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    new String[] {MediaStore.Audio.Media.TITLE},
                                    MediaStore.Audio.Media.IS_MUSIC + "=1",
                                    null, null);
            int totalTracks = musicCursor.getCount();
            musicCursor.moveToFirst();
            int index = 0;
            for (; index < totalTracks; index++){
                trackId++;
                String title = musicCursor.getString(
                        musicCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                if (title == null)
                    continue;

                if(title.equals(trackName)){
                    musicCursor.close();
                    break;
                }
                musicCursor.moveToNext();
            }
            if (index == totalTracks) {
                log("Record not found");
                musicCursor.close();
                trackId = 0;
            }
        } catch(Exception e) {log("Exception is " + e);}
        log("trackId is " + trackId);
        return trackId;
    }

    private void updateLocalPlayerSettings( byte[] data) {
        for (int i = 0; i < data.length; i += 2) {
           switch (data[i]) {
               case ATTRIBUTE_EQUALIZER:
                    settingValues.eq_value = data[i+1];
               break;
               case ATTRIBUTE_REPEATMODE:
                    settingValues.repeat_value = data[i+1];
               break;
               case ATTRIBUTE_SHUFFLEMODE:
                    settingValues.shuffle_value = data[i+1];
               break;
               case ATTRIBUTE_SCANMODE:
                    settingValues.scan_value = data[i+1];
               break;
           }
        }
    }

    private void updateLocalSettingsToBluez() {
       int validSettings = 0;
       if (settingValues.eq_value > VALUE_INVALID)  validSettings++;
       if (settingValues.repeat_value > VALUE_REPEATMODE_OFF)  validSettings++;
       if (settingValues.shuffle_value > VALUE_SHUFFLEMODE_OFF)  validSettings++;
       if (settingValues.scan_value > VALUE_INVALID)  validSettings++;
       if (validSettings == 0) return;

       byte [] retValarray = new byte[validSettings * 2];
       int i = 0;

       if (settingValues.repeat_value > VALUE_REPEATMODE_OFF) {
           retValarray[i++] = ATTRIBUTE_REPEATMODE;
           retValarray[i++] = settingValues.repeat_value;
       }
       if (settingValues.shuffle_value > VALUE_SHUFFLEMODE_OFF) {
           retValarray[i++] = ATTRIBUTE_SHUFFLEMODE;
           retValarray[i++] = settingValues.shuffle_value;
       }
       if (settingValues.eq_value > VALUE_INVALID) {
           retValarray[i++] = ATTRIBUTE_EQUALIZER;
           retValarray[i++] = settingValues.eq_value;
       }
       if (settingValues.scan_value > VALUE_INVALID) {
           retValarray[i++] = ATTRIBUTE_SCANMODE;
           retValarray[i++] = settingValues.scan_value;
       }
       Intent updateIntent = new Intent(PLAYERSETTINGS_RESPONSE);
       updateIntent.putExtra(EXTRA_GET_RESPONSE, NOTIFY_ATTRIBUTE_VALUES);
       updateIntent.putExtra(EXTRA_ATTRIB_VALUE_PAIRS, retValarray);
       mContext.sendBroadcast(updateIntent);
    }


    @Override
    protected synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);

        if (mAudioDevices.isEmpty()) return;
        pw.println("Cached audio devices:");
        for (BluetoothDevice device : mAudioDevices.keySet()) {
            int state = mAudioDevices.get(device);
            pw.println(device + " " + BluetoothA2dp.stateToString(state));
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private native boolean initNative();
    private native void cleanupNative();
    private synchronized native boolean connectSinkNative(String path);
    private synchronized native boolean disconnectSinkNative(String path);
    private synchronized native boolean suspendSinkNative(String path);
    private synchronized native boolean resumeSinkNative(String path);
    private synchronized native Object []getSinkPropertiesNative(String path);
    private synchronized native boolean avrcpVolumeUpNative(String path);
    private synchronized native boolean avrcpVolumeDownNative(String path);
    private synchronized native boolean sendMetaDataNative(String path);
    private synchronized native boolean sendEventNative(String path, int eventId, long data);
    private synchronized native boolean sendPlayStatusNative(String path, int duration,
                                                             int position, int playStatus);
    private synchronized native boolean sendPlayerSettingsNative(String path,
                                                           String response, int len, byte [] data);
    private synchronized native boolean sendSettingsTextNative(String path,
                                                      String response, int len, byte [] data, String []text);
}
