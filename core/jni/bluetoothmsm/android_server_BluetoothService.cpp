/*
** Copyright 2006, The Android Open Source Project
** Copyright (c) 2012, The Linux Foundation. All rights reserved
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define DBUS_ADAPTER_IFACE BLUEZ_DBUS_BASE_IFC ".Adapter"
#define DBUS_DEVICE_IFACE BLUEZ_DBUS_BASE_IFC ".Device"
#define DBUS_INPUT_IFACE BLUEZ_DBUS_BASE_IFC ".Input"
#define DBUS_NETWORK_IFACE BLUEZ_DBUS_BASE_IFC ".Network"
#define DBUS_NETWORKSERVER_IFACE BLUEZ_DBUS_BASE_IFC ".NetworkServer"
#define DBUS_HEALTH_MANAGER_PATH "/org/bluez"
#define DBUS_HEALTH_MANAGER_IFACE BLUEZ_DBUS_BASE_IFC ".HealthManager"
#define DBUS_HEALTH_DEVICE_IFACE BLUEZ_DBUS_BASE_IFC ".HealthDevice"
#define DBUS_HEALTH_CHANNEL_IFACE BLUEZ_DBUS_BASE_IFC ".HealthChannel"
#define DBUS_CHARACTERISTIC_IFACE BLUEZ_DBUS_BASE_IFC ".Characteristic"
#define DBUS_GATT_SERVER_INTERFACE  BLUEZ_DBUS_BASE_IFC ".GattServer"

#define LOG_TAG "BluetoothService.cpp"

#include "android_bluetooth_common.h"
#include "android_runtime/AndroidRuntime.h"
#include "android_util_Binder.h"
#include "JNIHelp.h"
#include "jni.h"


//#undef NDEBUG

//#define LOG_NIDEBUG 0
//#define LOG_NDEBUG 0
//#define LOG_NDDEBUG 0

#include "utils/Log.h"
#include "utils/misc.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>

#include <sys/socket.h>
#include <sys/ioctl.h>
#include <fcntl.h>

#ifdef HAVE_BLUETOOTH
#include <dbus/dbus.h>
#include <bluedroid/bluetooth.h>
#endif

#include <cutils/properties.h>

namespace android {

#define BLUETOOTH_CLASS_ERROR 0xFF000000
#define PROPERTIES_NREFS 10

#ifdef HAVE_BLUETOOTH
// We initialize these variables when we load class
// android.server.BluetoothService
static jfieldID field_mNativeData;
static jfieldID field_mEventLoop;

typedef struct {
    JNIEnv *env;
    DBusConnection *conn;
    const char *adapter;  // dbus object name of the local adapter
} native_data_t;

extern event_loop_native_data_t *get_EventLoop_native_data(JNIEnv *,
                                                           jobject);
extern DBusHandlerResult agent_event_filter(DBusConnection *conn,
                                            DBusMessage *msg,
                                            void *data);
void onCreatePairedDeviceResult(DBusMessage *msg, void *user, void *nat);
void onDiscoverServicesResult(DBusMessage *msg, void *user, void *nat);
void onCreateDeviceResult(DBusMessage *msg, void *user, void *nat);
void onInputDeviceConnectionResult(DBusMessage *msg, void *user, void *nat);
void onPanDeviceConnectionResult(DBusMessage *msg, void *user, void *nat);
void onHealthDeviceConnectionResult(DBusMessage *msg, void *user, void *nat);
void onDiscoverCharacteristicsResult(DBusMessage *msg, void *user, void *nat);
void onSetCharacteristicPropertyResult(DBusMessage *msg, void *user, void *nat);
void onUpdateCharacteristicValueResult(DBusMessage *msg, void *user, void *nat);
void onIndicateResponse(DBusMessage *msg, void *user, void *nat);

void onAddToPreferredDeviceListResult(DBusMessage *msg, void *user, void *nat);
void onRemoveFromPreferredDeviceListResult(DBusMessage *msg, void *user, void *nat);
void onClearPreferredDeviceListResult(DBusMessage *msg, void *user, void *nat);
void onGattConnectToPreferredDeviceListResult(DBusMessage *msg, void *user, void *nat);
void onGattCancelConnectToPreferredDeviceListResult(DBusMessage *msg, void *user, void *nat);


/** Get native data stored in the opaque (Java code maintained) pointer mNativeData
 *  Perform quick sanity check, if there are any problems return NULL
 */
static inline native_data_t * get_native_data(JNIEnv *env, jobject object) {
    native_data_t *nat =
            (native_data_t *)(env->GetIntField(object, field_mNativeData));
    if (nat == NULL || nat->conn == NULL) {
        ALOGE("Uninitialized native data\n");
        return NULL;
    }
    return nat;
}
#endif

static void classInitNative(JNIEnv* env, jclass clazz) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    field_mNativeData = get_field(env, clazz, "mNativeData", "I");
    field_mEventLoop = get_field(env, clazz, "mEventLoop",
            "Landroid/server/BluetoothEventLoop;");
#endif
}

/* Returns true on success (even if adapter is present but disabled).
 * Return false if dbus is down, or another serious error (out of memory)
*/
static bool initializeNativeDataNative(JNIEnv* env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = (native_data_t *)calloc(1, sizeof(native_data_t));
    if (NULL == nat) {
        ALOGE("%s: out of memory!", __FUNCTION__);
        return false;
    }
    nat->env = env;

    env->SetIntField(object, field_mNativeData, (jint)nat);
    DBusError err;
    dbus_error_init(&err);
    dbus_threads_init_default();
    nat->conn = dbus_bus_get(DBUS_BUS_SYSTEM, &err);
    if (dbus_error_is_set(&err)) {
        ALOGE("Could not get onto the system bus: %s", err.message);
        dbus_error_free(&err);
        return false;
    }
    dbus_connection_set_exit_on_disconnect(nat->conn, FALSE);
#endif  /*HAVE_BLUETOOTH*/
    return true;
}

static const char *get_adapter_path(JNIEnv* env, jobject object) {
#ifdef HAVE_BLUETOOTH
    event_loop_native_data_t *event_nat =
        get_EventLoop_native_data(env, env->GetObjectField(object,
                                                           field_mEventLoop));
    if (event_nat == NULL)
        return NULL;
    return event_nat->adapter;
#else
    return NULL;
#endif
}

// This function is called when the adapter is enabled.
static jboolean setupNativeDataNative(JNIEnv* env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat =
        (native_data_t *)env->GetIntField(object, field_mNativeData);
    event_loop_native_data_t *event_nat =
        get_EventLoop_native_data(env, env->GetObjectField(object,
                                                           field_mEventLoop));
    // Register agent for remote devices.
    const char *device_agent_path = "/android/bluetooth/remote_device_agent";
    static const DBusObjectPathVTable agent_vtable = {
                 NULL, agent_event_filter, NULL, NULL, NULL, NULL };

    if (!dbus_connection_register_object_path(nat->conn, device_agent_path,
                                              &agent_vtable, event_nat)) {
        ALOGE("%s: Can't register object path %s for remote device agent!",
                               __FUNCTION__, device_agent_path);
        return JNI_FALSE;
    }
#endif /*HAVE_BLUETOOTH*/
    return JNI_TRUE;
}

static jboolean tearDownNativeDataNative(JNIEnv *env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat =
               (native_data_t *)env->GetIntField(object, field_mNativeData);
    if (nat != NULL) {
        const char *device_agent_path =
            "/android/bluetooth/remote_device_agent";
        dbus_connection_unregister_object_path (nat->conn, device_agent_path);
    }
#endif /*HAVE_BLUETOOTH*/
    return JNI_TRUE;
}

static void cleanupNativeDataNative(JNIEnv* env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat =
        (native_data_t *)env->GetIntField(object, field_mNativeData);
    if (nat) {
        free(nat);
        nat = NULL;
    }
#endif
}

static jstring getAdapterPathNative(JNIEnv *env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        return (env->NewStringUTF(get_adapter_path(env, object)));
    }
#endif
    return NULL;
}


static jboolean startDiscoveryNative(JNIEnv *env, jobject object) {
    ALOGV("%s", __FUNCTION__);

#ifdef HAVE_BLUETOOTH
    DBusMessage *msg = NULL;
    DBusMessage *reply = NULL;
    DBusError err;
    const char *name;
    jboolean ret = JNI_FALSE;

    native_data_t *nat = get_native_data(env, object);
    if (nat == NULL) {
        goto done;
    }

    dbus_error_init(&err);

    /* Compose the command */
    msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                       get_adapter_path(env, object),
                                       DBUS_ADAPTER_IFACE, "StartDiscovery");

    if (msg == NULL) {
        if (dbus_error_is_set(&err)) {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }
        goto done;
    }

    /* Send the command. */
    reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
    if (dbus_error_is_set(&err)) {
         LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
         ret = JNI_FALSE;
         goto done;
    }

    ret = JNI_TRUE;
done:
    if (reply) dbus_message_unref(reply);
    if (msg) dbus_message_unref(msg);
    return ret;
#else
    return JNI_FALSE;
#endif
}

static jboolean stopDiscoveryNative(JNIEnv *env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    DBusMessage *msg = NULL;
    DBusMessage *reply = NULL;
    DBusError err;
    const char *name;
    native_data_t *nat;
    jboolean ret = JNI_FALSE;

    dbus_error_init(&err);

    nat = get_native_data(env, object);
    if (nat == NULL) {
        goto done;
    }

    /* Compose the command */
    msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                       get_adapter_path(env, object),
                                       DBUS_ADAPTER_IFACE, "StopDiscovery");
    if (msg == NULL) {
        if (dbus_error_is_set(&err))
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        goto done;
    }

    /* Send the command. */
    reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
    if (dbus_error_is_set(&err)) {
        if(strncmp(err.name, BLUEZ_DBUS_BASE_IFC ".Error.NotAuthorized",
                   strlen(BLUEZ_DBUS_BASE_IFC ".Error.NotAuthorized")) == 0) {
            // hcid sends this if there is no active discovery to cancel
            ALOGV("%s: There was no active discovery to cancel", __FUNCTION__);
            dbus_error_free(&err);
        } else {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }
        goto done;
    }

    ret = JNI_TRUE;
done:
    if (msg) dbus_message_unref(msg);
    if (reply) dbus_message_unref(reply);
    return ret;
#else
    return JNI_FALSE;
#endif
}

static jbyteArray readAdapterOutOfBandDataNative(JNIEnv *env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    DBusError err;
    jbyte *hash, *randomizer;
    jbyteArray byteArray = NULL;
    int hash_len, r_len;
    if (nat) {
       DBusMessage *reply = dbus_func_args(env, nat->conn,
                           get_adapter_path(env, object),
                           DBUS_ADAPTER_IFACE, "ReadLocalOutOfBandData",
                           DBUS_TYPE_INVALID);
       if (!reply) return NULL;

       dbus_error_init(&err);
       if (dbus_message_get_args(reply, &err,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &hash, &hash_len,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &randomizer, &r_len,
                                DBUS_TYPE_INVALID)) {
          if (hash_len == 16 && r_len == 16) {
               byteArray = env->NewByteArray(32);
               if (byteArray) {
                   env->SetByteArrayRegion(byteArray, 0, 16, hash);
                   env->SetByteArrayRegion(byteArray, 16, 16, randomizer);
               }
           } else {
               ALOGE("readAdapterOutOfBandDataNative: Hash len = %d, R len = %d",
                                                                  hash_len, r_len);
           }
       } else {
          LOG_AND_FREE_DBUS_ERROR(&err);
       }
       dbus_message_unref(reply);
       return byteArray;
    }
#endif
    return NULL;
}

static jboolean createPairedDeviceNative(JNIEnv *env, jobject object,
                                         jstring address, jint timeout_ms) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_address = env->GetStringUTFChars(address, NULL);
        ALOGV("... address = %s", c_address);
        char *context_address = (char *)calloc(BTADDR_SIZE, sizeof(char));
        const char *capabilities = "KeyboardDisplay";
        const char *agent_path = "/android/bluetooth/remote_device_agent";

        strlcpy(context_address, c_address, BTADDR_SIZE);  // for callback
        bool ret = dbus_func_args_async(env, nat->conn, (int)timeout_ms,
                                        onCreatePairedDeviceResult, // callback
                                        context_address,
                                        eventLoopNat,
                                        get_adapter_path(env, object),
                                        DBUS_ADAPTER_IFACE,
                                        "CreatePairedDevice",
                                        DBUS_TYPE_STRING, &c_address,
                                        DBUS_TYPE_OBJECT_PATH, &agent_path,
                                        DBUS_TYPE_STRING, &capabilities,
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(address, c_address);
        return ret ? JNI_TRUE : JNI_FALSE;

    }
#endif
    return JNI_FALSE;
}

static jboolean createPairedDeviceOutOfBandNative(JNIEnv *env, jobject object,
                                                jstring address, jint timeout_ms) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_address = env->GetStringUTFChars(address, NULL);
        ALOGV("... address = %s", c_address);
        char *context_address = (char *)calloc(BTADDR_SIZE, sizeof(char));
        const char *capabilities = "KeyboardDisplay";
        const char *agent_path = "/android/bluetooth/remote_device_agent";

        strlcpy(context_address, c_address, BTADDR_SIZE);  // for callback
        bool ret = dbus_func_args_async(env, nat->conn, (int)timeout_ms,
                                        onCreatePairedDeviceResult, // callback
                                        context_address,
                                        eventLoopNat,
                                        get_adapter_path(env, object),
                                        DBUS_ADAPTER_IFACE,
                                        "CreatePairedDeviceOutOfBand",
                                        DBUS_TYPE_STRING, &c_address,
                                        DBUS_TYPE_OBJECT_PATH, &agent_path,
                                        DBUS_TYPE_STRING, &capabilities,
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(address, c_address);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jint getDeviceServiceChannelNative(JNIEnv *env, jobject object,
                                          jstring path,
                                          jstring pattern, jint attr_id) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);
    if (nat && eventLoopNat) {
        const char *c_pattern = env->GetStringUTFChars(pattern, NULL);
        const char *c_path = env->GetStringUTFChars(path, NULL);
        ALOGV("... pattern = %s", c_pattern);
        ALOGV("... attr_id = %#X", attr_id);
        DBusMessage *reply =
            dbus_func_args(env, nat->conn, c_path,
                           DBUS_DEVICE_IFACE, "GetServiceAttributeValue",
                           DBUS_TYPE_STRING, &c_pattern,
                           DBUS_TYPE_UINT16, &attr_id,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(pattern, c_pattern);
        env->ReleaseStringUTFChars(path, c_path);
        return reply ? dbus_returns_int32(env, reply) : -1;
    }
#endif
    return -1;
}

static jstring getDeviceStringAttrValue(JNIEnv *env, jobject object,
                                          jstring path,
                                          jstring pattern, jint attr_id) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s",__FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);
    if (nat && eventLoopNat) {
        const char *c_pattern = env->GetStringUTFChars(pattern, NULL);
        const char *c_path = env->GetStringUTFChars(path, NULL);
        ALOGV("... pattern = %s", c_pattern);
        ALOGV("... attr_id = %#X", attr_id);
        DBusMessage *reply =
            dbus_func_args(env, nat->conn, c_path,
                           DBUS_DEVICE_IFACE, "GetServiceAttributeValue",
                           DBUS_TYPE_STRING, &c_pattern,
                           DBUS_TYPE_UINT16, &attr_id,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(pattern, c_pattern);
        env->ReleaseStringUTFChars(path, c_path);
        return reply ? dbus_returns_string(env, reply) : NULL;
    }
#endif
    return NULL;
}

static jboolean cancelDeviceCreationNative(JNIEnv *env, jobject object,
                                           jstring address) {
    ALOGV("%s", __FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_address = env->GetStringUTFChars(address, NULL);
        DBusError err;
        dbus_error_init(&err);
        ALOGV("... address = %s", c_address);
        DBusMessage *reply =
            dbus_func_args_timeout(env, nat->conn, -1,
                                   get_adapter_path(env, object),
                                   DBUS_ADAPTER_IFACE, "CancelDeviceCreation",
                                   DBUS_TYPE_STRING, &c_address,
                                   DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(address, c_address);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
                ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return JNI_FALSE;
        } else {
            result = JNI_TRUE;
        }
        dbus_message_unref(reply);
    }
#endif
    return JNI_FALSE;
}

static jboolean removeDeviceNative(JNIEnv *env, jobject object, jstring object_path) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_object_path = env->GetStringUTFChars(object_path, NULL);
        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        NULL,
                                        NULL,
                                        NULL,
                                        get_adapter_path(env, object),
                                        DBUS_ADAPTER_IFACE,
                                        "RemoveDevice",
                                        DBUS_TYPE_OBJECT_PATH, &c_object_path,
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(object_path, c_object_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jint enableNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    return bt_enable();
#endif
    return -1;
}

static jint disableNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    return bt_disable();
#endif
    return -1;
}

static jint isEnabledNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    return bt_is_enabled();
#endif
    return -1;
}

static jboolean setPairingConfirmationNative(JNIEnv *env, jobject object,
                                             jstring address, bool confirm,
                                             int nativeData) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        if (confirm) {
            reply = dbus_message_new_method_return(msg);
        } else {
            reply = dbus_message_new_error(msg,
                "org.bluez.Error.Rejected", "User rejected confirmation");
        }

        if (!reply) {
            ALOGE("%s: Cannot create message reply to RequestPasskeyConfirmation or"
                  "RequestPairingConsent to D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setPasskeyNative(JNIEnv *env, jobject object, jstring address,
                         int passkey, int nativeData) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply = dbus_message_new_method_return(msg);
        if (!reply) {
            ALOGE("%s: Cannot create message reply to return Passkey code to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_message_append_args(reply, DBUS_TYPE_UINT32, (uint32_t *)&passkey,
                                 DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setRemoteOutOfBandDataNative(JNIEnv *env, jobject object, jstring address,
                         jbyteArray hash, jbyteArray randomizer, int nativeData) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply = dbus_message_new_method_return(msg);
        jbyte *h_ptr = env->GetByteArrayElements(hash, NULL);
        jbyte *r_ptr = env->GetByteArrayElements(randomizer, NULL);
        if (!reply) {
            ALOGE("%s: Cannot create message reply to return remote OOB data to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_message_append_args(reply,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &h_ptr, 16,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &r_ptr, 16,
                                DBUS_TYPE_INVALID);

        env->ReleaseByteArrayElements(hash, h_ptr, 0);
        env->ReleaseByteArrayElements(randomizer, r_ptr, 0);

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setAuthorizationNative(JNIEnv *env, jobject object, jstring address,
                         jboolean val, int nativeData) {
#ifdef HAVE_BLUETOOTH
  ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        if (val) {
            reply = dbus_message_new_method_return(msg);
        } else {
            reply = dbus_message_new_error(msg,
                    "org.bluez.Error.Rejected", "Authorization rejected");
        }
        if (!reply) {
            ALOGE("%s: Cannot create message reply D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setPinNative(JNIEnv *env, jobject object, jstring address,
                         jstring pin, int nativeData) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply = dbus_message_new_method_return(msg);
        if (!reply) {
            ALOGE("%s: Cannot create message reply to return PIN code to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        const char *c_pin = env->GetStringUTFChars(pin, NULL);

        dbus_message_append_args(reply, DBUS_TYPE_STRING, &c_pin,
                                 DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        env->ReleaseStringUTFChars(pin, c_pin);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean sapAuthorizeNative(JNIEnv *env, jobject object, jstring address,
                         jboolean access, int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("sapAuthorizeNative %s %d", (char*)address, access);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        if (access) {
            reply = dbus_message_new_method_return(msg);
            if (!reply) {
               ALOGE("%s: Cannot create message reply to authorize sap "
                     "D-Bus\n", __FUNCTION__);
                dbus_message_unref(msg);
                return JNI_FALSE;
            }
        } else {
            reply = dbus_message_new_error(msg,
                    "org.bluez.Error.Rejected", "Authorization rejected");
            if (!reply) {
               ALOGE("%s: Cannot create message reply\n", __FUNCTION__);
                return JNI_FALSE;
            }

        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean DUNAuthorizeNative(JNIEnv *env, jobject object, jstring address,
                         jboolean access, int nativeData) {
#ifdef HAVE_BLUETOOTH
    ALOGV("DUNAuthorizeNative %s %d", (char*)address, access);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        if (access) {
            reply = dbus_message_new_method_return(msg);
            if (!reply) {
                ALOGE("%s: Cannot create message reply to authorize DUN "
                     "D-Bus\n", __FUNCTION__);
                dbus_message_unref(msg);
                return JNI_FALSE;
            }
        } else {
            reply = dbus_message_new_error(msg,
                    "org.bluez.Error.Rejected", "Authorization rejected");
            if (!reply) {
                ALOGE("%s: Cannot create message reply\n", __FUNCTION__);
                return JNI_FALSE;
            }

        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}



static jboolean cancelPairingUserInputNative(JNIEnv *env, jobject object,
                                            jstring address, int nativeData) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply = dbus_message_new_error(msg,
                "org.bluez.Error.Canceled", "Pairing User Input was canceled");
        if (!reply) {
            ALOGE("%s: Cannot create message reply to return cancelUserInput to"
                 "D-BUS\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jobjectArray getDevicePropertiesNative(JNIEnv *env, jobject object,
                                                    jstring path)
{
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        const char *c_path = env->GetStringUTFChars(path, NULL);
        reply = dbus_func_args_timeout(env,
                                   nat->conn, -1, c_path,
                                   DBUS_DEVICE_IFACE, "GetProperties",
                                   DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
                ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return NULL;
        }
        env->PushLocalFrame(PROPERTIES_NREFS);

        DBusMessageIter iter;
        jobjectArray str_array = NULL;
        if (dbus_message_iter_init(reply, &iter))
           str_array =  parse_remote_device_properties(env, &iter);
        dbus_message_unref(reply);

        return (jobjectArray) env->PopLocalFrame(str_array);
    }
#endif
    return NULL;
}

static jobjectArray getAdapterPropertiesNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        reply = dbus_func_args_timeout(env,
                                   nat->conn, -1, get_adapter_path(env, object),
                                   DBUS_ADAPTER_IFACE, "GetProperties",
                                   DBUS_TYPE_INVALID);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
                ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return NULL;
        }
        env->PushLocalFrame(PROPERTIES_NREFS);

        DBusMessageIter iter;
        jobjectArray str_array = NULL;
        if (dbus_message_iter_init(reply, &iter))
            str_array = parse_adapter_properties(env, &iter);
        dbus_message_unref(reply);

        return (jobjectArray) env->PopLocalFrame(str_array);
    }
#endif
    return NULL;
}

static jboolean setAdapterPropertyNative(JNIEnv *env, jobject object, jstring key,
                                         void *value, jint type) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg;
        DBusMessageIter iter;
        dbus_bool_t reply = JNI_FALSE;
        const char *c_key = env->GetStringUTFChars(key, NULL);

        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                           get_adapter_path(env, object),
                                           DBUS_ADAPTER_IFACE, "SetProperty");
        if (!msg) {
            ALOGE("%s: Can't allocate new method call for GetProperties!",
                  __FUNCTION__);
            env->ReleaseStringUTFChars(key, c_key);
            return JNI_FALSE;
        }

        dbus_message_append_args(msg, DBUS_TYPE_STRING, &c_key, DBUS_TYPE_INVALID);
        dbus_message_iter_init_append(msg, &iter);
        append_variant(&iter, type, value);

        // Asynchronous call - the callbacks come via propertyChange
        reply = dbus_connection_send_with_reply(nat->conn, msg, NULL, -1);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(key, c_key);
        return reply ? JNI_TRUE : JNI_FALSE;

    }
#endif
    return JNI_FALSE;
}

static jboolean setAdapterPropertyStringNative(JNIEnv *env, jobject object, jstring key,
                                               jstring value) {
#ifdef HAVE_BLUETOOTH
    const char *c_value = env->GetStringUTFChars(value, NULL);
    jboolean ret =  setAdapterPropertyNative(env, object, key, (void *)&c_value, DBUS_TYPE_STRING);
    env->ReleaseStringUTFChars(value, (char *)c_value);
    return ret;
#else
    return JNI_FALSE;
#endif
}

static jboolean setAdapterPropertyIntegerNative(JNIEnv *env, jobject object, jstring key,
                                               jint value) {
#ifdef HAVE_BLUETOOTH
    return setAdapterPropertyNative(env, object, key, (void *)&value, DBUS_TYPE_UINT32);
#else
    return JNI_FALSE;
#endif
}

static jboolean setAdapterPropertyBooleanNative(JNIEnv *env, jobject object, jstring key,
                                               jint value) {
#ifdef HAVE_BLUETOOTH
    return setAdapterPropertyNative(env, object, key, (void *)&value, DBUS_TYPE_BOOLEAN);
#else
    return JNI_FALSE;
#endif
}

static jboolean setDevicePropertyNative(JNIEnv *env, jobject object, jstring path,
                                               jstring key, void *value, jint type) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg;
        DBusMessageIter iter;
        dbus_bool_t reply = JNI_FALSE;

        const char *c_key = env->GetStringUTFChars(key, NULL);
        const char *c_path = env->GetStringUTFChars(path, NULL);

        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_DEVICE_IFACE, "SetProperty");
        if (!msg) {
            ALOGE("%s: Can't allocate new method call for device SetProperty!", __FUNCTION__);
            env->ReleaseStringUTFChars(key, c_key);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        dbus_message_append_args(msg, DBUS_TYPE_STRING, &c_key, DBUS_TYPE_INVALID);
        dbus_message_iter_init_append(msg, &iter);
        append_variant(&iter, type, value);

        // Asynchronous call - the callbacks come via Device propertyChange
        reply = dbus_connection_send_with_reply(nat->conn, msg, NULL, -1);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        env->ReleaseStringUTFChars(key, c_key);

        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setDevicePropertyBooleanNative(JNIEnv *env, jobject object,
                                                     jstring path, jstring key, jint value) {
#ifdef HAVE_BLUETOOTH
    return setDevicePropertyNative(env, object, path, key,
                                        (void *)&value, DBUS_TYPE_BOOLEAN);
#else
    return JNI_FALSE;
#endif
}

static jboolean setDevicePropertyStringNative(JNIEnv *env, jobject object,
                                              jstring path, jstring key, jstring value) {
#ifdef HAVE_BLUETOOTH
    const char *c_value = env->GetStringUTFChars(value, NULL);
    jboolean ret = setDevicePropertyNative(env, object, path, key,
                                           (void *)&c_value, DBUS_TYPE_STRING);
    env->ReleaseStringUTFChars(value, (char *)c_value);
    return ret;
#else
    return JNI_FALSE;
#endif
}

static jboolean setDevicePropertyIntegerNative(JNIEnv *env, jobject object,
                                                     jstring path, jstring key, jint value) {
#ifdef HAVE_BLUETOOTH
    return setDevicePropertyNative(env, object, path, key,
                                        (void *)&value, DBUS_TYPE_UINT32);
#else
    return JNI_FALSE;
#endif
}

static jboolean updateLEConnectionParametersNative(JNIEnv *env, jobject object,
                                              jstring path,
                                              jint prohibitRemoteChg,
                                              jint intervalMin,
                                              jint interValMax,
                                              jint slaveLatency,
                                              jint supervisionTimeout) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusMessageIter iter;
        DBusError err;

        const char *c_path = env->GetStringUTFChars(path, NULL);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_DEVICE_IFACE, "UpdateLEConnectionParams");
        if (!msg) {
           ALOGE("%s: Can't allocate new method call for device UpdateLEConnectionParams!", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        dbus_message_append_args(msg,
                                 DBUS_TYPE_BYTE, &prohibitRemoteChg,
                                 DBUS_TYPE_UINT16, (uint16_t *)&intervalMin,
                                 DBUS_TYPE_UINT16, (uint16_t *)&interValMax,
                                 DBUS_TYPE_UINT16, (uint16_t *)&slaveLatency,
                                 DBUS_TYPE_UINT16, (uint16_t *)&supervisionTimeout,
                                 DBUS_TYPE_INVALID);
        dbus_message_iter_init_append(msg, &iter);

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
           ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setLEConnectionParamNative(JNIEnv *env, jobject object,
                                           jstring path,
                                           jint prohibitRemoteChg,
                                           jint filterPolicy,
                                           jint scanInterval,
                                           jint scanWindow,
                                           jint intervalMin,
                                           jint intervalMax,
                                           jint latency,
                                           jint superVisionTimeout,
                                           jint minCeLen,
                                           jint maxCeLen,
                                           jint connTimeout) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusMessageIter iter;
        DBusError err;

        const char *c_path = env->GetStringUTFChars(path, NULL);
       ALOGE("the dbus object path: %s", c_path);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_DEVICE_IFACE, "SetLEConnectParams");
        if (!msg) {
           ALOGE("%s: Can't allocate new method call for device SetConnectionParams!", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        dbus_message_append_args(msg,
                                 DBUS_TYPE_BYTE, &prohibitRemoteChg,
                                 DBUS_TYPE_BYTE, &filterPolicy,
                                 DBUS_TYPE_UINT16, (uint16_t *)&scanInterval,
                                 DBUS_TYPE_UINT16, (uint16_t *)&scanWindow,
                                 DBUS_TYPE_UINT16, (uint16_t *)&intervalMin,
                                 DBUS_TYPE_UINT16, (uint16_t *)&intervalMax,
                                 DBUS_TYPE_UINT16, (uint16_t *)&latency,
                                 DBUS_TYPE_UINT16, (uint16_t *)&superVisionTimeout,
                                 DBUS_TYPE_UINT16, (uint16_t *)&minCeLen,
                                 DBUS_TYPE_UINT16, (uint16_t *)&maxCeLen,
                                 DBUS_TYPE_UINT16, (uint16_t *)&connTimeout,
                                 DBUS_TYPE_INVALID);
        dbus_message_iter_init_append(msg, &iter);

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
           ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean registerRssiUpdateWatcherNative(JNIEnv *env, jobject object,
                                              jstring path,
                                              jint rssiThreshold,
                                              jint interval,
                                              jboolean updateOnThreshExceed) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusMessageIter iter;
        DBusError err;

        const char *c_path = env->GetStringUTFChars(path, NULL);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                           c_path, DBUS_DEVICE_IFACE,
                                           "RegisterRssiUpdateWatcher");
        if (!msg) {
           ALOGE("%s: Can't allocate new method call", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        dbus_message_append_args(msg, DBUS_TYPE_INT16, (int16_t *)&rssiThreshold,
                                 DBUS_TYPE_UINT16, (uint16_t *)&interval,
                                 DBUS_TYPE_BOOLEAN, &updateOnThreshExceed,
                                 DBUS_TYPE_INVALID);
        dbus_message_iter_init_append(msg, &iter);

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
           ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean unregisterRssiUpdateWatcherNative(JNIEnv *env, jobject object,
                                              jstring path) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusMessageIter iter;
        DBusError err;

        const char *c_path = env->GetStringUTFChars(path, NULL);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_DEVICE_IFACE,
                                          "UnregisterRssiUpdateWatcher");
        if (!msg) {
           ALOGE("%s: Can't allocate new method call", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        dbus_message_iter_init_append(msg, &iter);

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
           ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean createDeviceNative(JNIEnv *env, jobject object,
                                                jstring address) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_address = env->GetStringUTFChars(address, NULL);
        ALOGV("... address = %s", c_address);
        char *context_address = (char *)calloc(BTADDR_SIZE, sizeof(char));
        strlcpy(context_address, c_address, BTADDR_SIZE);  // for callback

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onCreateDeviceResult,
                                        context_address,
                                        eventLoopNat,
                                        get_adapter_path(env, object),
                                        DBUS_ADAPTER_IFACE,
                                        "CreateDevice",
                                        DBUS_TYPE_STRING, &c_address,
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(address, c_address);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean addToPreferredDeviceListNative(JNIEnv *env, jobject object,
                                                jstring path) {
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        void *c_var;
        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        if (context_path != NULL) {
            strlcpy(context_path, c_path, len);  // for callback
        } else {
            return JNI_FALSE;
        }

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onAddToPreferredDeviceListResult,
                                        context_path,
                                        eventLoopNat,
                                        c_path,
                                        DBUS_DEVICE_IFACE,
                                        "AddToWhiteList",
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean removeFromPreferredDeviceListNative(JNIEnv *env, jobject object,
                                                jstring path) {
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        void *c_var;
        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        if (context_path != NULL) {
            strlcpy(context_path, c_path, len);  // for callback
        } else {
            return JNI_FALSE;
        }

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onRemoveFromPreferredDeviceListResult,
                                        context_path,
                                        eventLoopNat,
                                        c_path,
                                        DBUS_DEVICE_IFACE,
                                        "RemoveFromWhiteList",
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean clearPreferredDeviceListNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        char *context_path = NULL;

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onClearPreferredDeviceListResult,
                                        context_path,
                                        eventLoopNat,
                                        get_adapter_path(env, object),
                                        DBUS_ADAPTER_IFACE,
                                        "ClearLeWhiteList",
                                        DBUS_TYPE_INVALID);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean gattConnectToPreferredDeviceListNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        char *context_path = NULL;

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onGattConnectToPreferredDeviceListResult,
                                        context_path,
                                        eventLoopNat,
                                        get_adapter_path(env, object),
                                        DBUS_ADAPTER_IFACE,
                                        "CreateLeConnWhiteList",
                                        DBUS_TYPE_INVALID);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean gattCancelConnectToPreferredDeviceListNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        char *context_path = NULL;

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onGattCancelConnectToPreferredDeviceListResult,
                                        context_path,
                                        eventLoopNat,
                                        get_adapter_path(env, object),
                                        DBUS_ADAPTER_IFACE,
                                        "CancelCreateLeConnWhiteList",
                                        DBUS_TYPE_INVALID);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}


static jboolean discoverServicesNative(JNIEnv *env, jobject object,
                                               jstring path, jstring pattern) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        const char *c_pattern = env->GetStringUTFChars(pattern, NULL);
        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

        ALOGV("... Object Path = %s", c_path);
        ALOGV("... Pattern = %s, strlen = %d", c_pattern, strlen(c_pattern));

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onDiscoverServicesResult,
                                        context_path,
                                        eventLoopNat,
                                        c_path,
                                        DBUS_DEVICE_IFACE,
                                        "DiscoverServices",
                                        DBUS_TYPE_STRING, &c_pattern,
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        env->ReleaseStringUTFChars(pattern, c_pattern);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

#ifdef HAVE_BLUETOOTH
static jintArray extract_handles(JNIEnv *env, DBusMessage *reply) {
    jint *handles;
    jintArray handleArray = NULL;
    int len;

    DBusError err;
    dbus_error_init(&err);

    if (dbus_message_get_args(reply, &err,
                              DBUS_TYPE_ARRAY, DBUS_TYPE_UINT32, &handles, &len,
                              DBUS_TYPE_INVALID)) {
        handleArray = env->NewIntArray(len);
        if (handleArray) {
            env->SetIntArrayRegion(handleArray, 0, len, handles);
        } else {
            ALOGE("Null array in extract_handles");
        }
    } else {
        LOG_AND_FREE_DBUS_ERROR(&err);
    }
    return handleArray;
}
#endif

static jintArray addReservedServiceRecordsNative(JNIEnv *env, jobject object,
                                                jintArray uuids) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    DBusMessage *reply = NULL;

    native_data_t *nat = get_native_data(env, object);

    jint* svc_classes = env->GetIntArrayElements(uuids, NULL);
    if (!svc_classes) return NULL;

    int len = env->GetArrayLength(uuids);
    reply = dbus_func_args(env, nat->conn,
                            get_adapter_path(env, object),
                            DBUS_ADAPTER_IFACE, "AddReservedServiceRecords",
                            DBUS_TYPE_ARRAY, DBUS_TYPE_UINT32,
                            &svc_classes, len, DBUS_TYPE_INVALID);
    env->ReleaseIntArrayElements(uuids, svc_classes, 0);
    return reply ? extract_handles(env, reply) : NULL;

#endif
    return NULL;
}

static jboolean removeReservedServiceRecordsNative(JNIEnv *env, jobject object,
                                                   jintArray handles) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jint *values = env->GetIntArrayElements(handles, NULL);
    DBusMessage *msg = NULL;
    DBusMessage *reply = NULL;
    if (values == NULL) return JNI_FALSE;

    jsize len = env->GetArrayLength(handles);

    reply = dbus_func_args(env, nat->conn,
                            get_adapter_path(env, object),
                            DBUS_ADAPTER_IFACE, "RemoveReservedServiceRecords",
                            DBUS_TYPE_ARRAY, DBUS_TYPE_UINT32,
                            &values, len, DBUS_TYPE_INVALID);
    env->ReleaseIntArrayElements(handles, values, 0);
    return reply ? JNI_TRUE : JNI_FALSE;
#endif
    return JNI_FALSE;
}

static jstring findDeviceNative(JNIEnv *env, jobject object,
                                jstring address) {
    ALOGV(__FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);
    if (nat && eventLoopNat) {
        const char *c_address = env->GetStringUTFChars(address, NULL);
        ALOGV("... address = %s", c_address);
        DBusMessage *reply = dbus_func_args(env, nat->conn,
                           get_adapter_path(env, object),
                           DBUS_ADAPTER_IFACE, "FindDevice",
                           DBUS_TYPE_STRING, &c_address,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(address, c_address);
        if (reply == NULL) {
            return NULL;
        }
        char *object_path = NULL;
        if (dbus_message_get_args(reply, NULL,
                                  DBUS_TYPE_OBJECT_PATH, &object_path,
                                  DBUS_TYPE_INVALID)) {
            return (jstring) env->NewStringUTF(object_path);
        }
     }
#endif
       return NULL;
}

static jint addRfcommServiceRecordNative(JNIEnv *env, jobject object,
        jstring name, jlong uuidMsb, jlong uuidLsb, jshort channel) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_name = env->GetStringUTFChars(name, NULL);
        ALOGV("... name = %s", c_name);
        ALOGV("... uuid1 = %llX", uuidMsb);
        ALOGV("... uuid2 = %llX", uuidLsb);
        ALOGV("... channel = %d", channel);
        DBusMessage *reply = dbus_func_args(env, nat->conn,
                           get_adapter_path(env, object),
                           DBUS_ADAPTER_IFACE, "AddRfcommServiceRecord",
                           DBUS_TYPE_STRING, &c_name,
                           DBUS_TYPE_UINT64, &uuidMsb,
                           DBUS_TYPE_UINT64, &uuidLsb,
                           DBUS_TYPE_UINT16, &channel,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(name, c_name);
        return reply ? dbus_returns_uint32(env, reply) : -1;
    }
#endif
    return -1;
}

static jboolean removeServiceRecordNative(JNIEnv *env, jobject object, jint handle) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        ALOGV("... handle = %X", handle);
        DBusMessage *reply = dbus_func_args(env, nat->conn,
                           get_adapter_path(env, object),
                           DBUS_ADAPTER_IFACE, "RemoveServiceRecord",
                           DBUS_TYPE_UINT32, &handle,
                           DBUS_TYPE_INVALID);
        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setLinkTimeoutNative(JNIEnv *env, jobject object, jstring object_path,
                                     jint num_slots) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_object_path = env->GetStringUTFChars(object_path, NULL);
        DBusMessage *reply = dbus_func_args(env, nat->conn,
                           get_adapter_path(env, object),
                           DBUS_ADAPTER_IFACE, "SetLinkTimeout",
                           DBUS_TYPE_OBJECT_PATH, &c_object_path,
                           DBUS_TYPE_UINT32, &num_slots,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(object_path, c_object_path);
        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean connectInputDeviceNative(JNIEnv *env, jobject object, jstring path) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);

        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

        bool ret = dbus_func_args_async(env, nat->conn, -1, onInputDeviceConnectionResult,
                                        context_path, eventLoopNat, c_path, DBUS_INPUT_IFACE,
                                        "Connect",
                                        DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean disconnectInputDeviceNative(JNIEnv *env, jobject object,
                                     jstring path) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);

        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        if (context_path != NULL) {
            strlcpy(context_path, c_path, len);  // for callback
        } else {
            return JNI_FALSE;
        }

        bool ret = dbus_func_args_async(env, nat->conn, -1, onInputDeviceConnectionResult,
                                        context_path, eventLoopNat, c_path, DBUS_INPUT_IFACE,
                                        "Disconnect",
                                        DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean setBluetoothTetheringNative(JNIEnv *env, jobject object, jboolean value,
                                            jstring src_role, jstring bridge) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply;
        const char *c_role = env->GetStringUTFChars(src_role, NULL);
        const char *c_bridge = env->GetStringUTFChars(bridge, NULL);
        if (value) {
            ALOGE("setBluetoothTetheringNative true");
            reply = dbus_func_args(env, nat->conn,
                                  get_adapter_path(env, object),
                                  DBUS_NETWORKSERVER_IFACE,
                                  "Register",
                                  DBUS_TYPE_STRING, &c_role,
                                  DBUS_TYPE_STRING, &c_bridge,
                                  DBUS_TYPE_INVALID);
        } else {
            ALOGE("setBluetoothTetheringNative false");
            reply = dbus_func_args(env, nat->conn,
                                  get_adapter_path(env, object),
                                  DBUS_NETWORKSERVER_IFACE,
                                  "Unregister",
                                  DBUS_TYPE_STRING, &c_role,
                                  DBUS_TYPE_INVALID);
        }
        env->ReleaseStringUTFChars(src_role, c_role);
        env->ReleaseStringUTFChars(bridge, c_bridge);
        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean connectPanDeviceNative(JNIEnv *env, jobject object, jstring path,
                                       jstring dstRole) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    ALOGE("connectPanDeviceNative");
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        const char *dst = env->GetStringUTFChars(dstRole, NULL);

        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

        bool ret = dbus_func_args_async(env, nat->conn, -1,onPanDeviceConnectionResult,
                                    context_path, eventLoopNat, c_path,
                                    DBUS_NETWORK_IFACE, "Connect",
                                    DBUS_TYPE_STRING, &dst,
                                    DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        env->ReleaseStringUTFChars(dstRole, dst);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean disconnectPanDeviceNative(JNIEnv *env, jobject object,
                                     jstring path) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    ALOGE("disconnectPanDeviceNative");
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);

        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

        bool ret = dbus_func_args_async(env, nat->conn, -1,onPanDeviceConnectionResult,
                                        context_path, eventLoopNat, c_path,
                                        DBUS_NETWORK_IFACE, "Disconnect",
                                        DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean disconnectPanServerDeviceNative(JNIEnv *env, jobject object,
                                                jstring path, jstring address,
                                                jstring iface) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    ALOGE("disconnectPanServerDeviceNative");
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_address = env->GetStringUTFChars(address, NULL);
        const char *c_path = env->GetStringUTFChars(path, NULL);
        const char *c_iface = env->GetStringUTFChars(iface, NULL);

        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onPanDeviceConnectionResult,
                                        context_path, eventLoopNat,
                                        get_adapter_path(env, object),
                                        DBUS_NETWORKSERVER_IFACE,
                                        "DisconnectDevice",
                                        DBUS_TYPE_STRING, &c_address,
                                        DBUS_TYPE_STRING, &c_iface,
                                        DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(address, c_address);
        env->ReleaseStringUTFChars(iface, c_iface);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jstring registerHealthApplicationNative(JNIEnv *env, jobject object,
                                           jint dataType, jstring role,
                                           jstring name, jstring channelType) {
    ALOGV("%s", __FUNCTION__);
    jstring path = NULL;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_role = env->GetStringUTFChars(role, NULL);
        const char *c_name = env->GetStringUTFChars(name, NULL);
        const char *c_channel_type = env->GetStringUTFChars(channelType, NULL);
        char *c_path;
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                            DBUS_HEALTH_MANAGER_PATH,
                                            DBUS_HEALTH_MANAGER_IFACE,
                                            "CreateApplication");

        if (msg == NULL) {
            ALOGE("Could not allocate D-Bus message object!");
            return NULL;
        }

        /* append arguments */
        append_dict_args(msg,
                         "DataType", DBUS_TYPE_UINT16, &dataType,
                         "Role", DBUS_TYPE_STRING, &c_role,
                         "Description", DBUS_TYPE_STRING, &c_name,
                         "ChannelType", DBUS_TYPE_STRING, &c_channel_type,
                         DBUS_TYPE_INVALID);


        /* Make the call. */
        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);

        env->ReleaseStringUTFChars(role, c_role);
        env->ReleaseStringUTFChars(name, c_name);
        env->ReleaseStringUTFChars(channelType, c_channel_type);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            }
        } else {
            if (!dbus_message_get_args(reply, &err,
                                      DBUS_TYPE_OBJECT_PATH, &c_path,
                                      DBUS_TYPE_INVALID)) {
                if (dbus_error_is_set(&err)) {
                    LOG_AND_FREE_DBUS_ERROR(&err);
                }
            } else {
               path = env->NewStringUTF(c_path);
            }
            dbus_message_unref(reply);
        }
    }
#endif
    return path;
}

static jboolean discoverPrimaryServicesNative(JNIEnv *env, jobject object,
                                jstring path) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);
    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        DBusError err;

        dbus_error_init(&err);

        DBusMessage *reply = dbus_func_args(env, nat->conn,
                           c_path,
                           DBUS_DEVICE_IFACE, "LeDiscoverPrimaryServices",
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err))
                LOG_AND_FREE_DBUS_ERROR(&err);
            return JNI_FALSE;
        }
        return JNI_TRUE;
     }
#endif
       return JNI_FALSE;
}

static jstring createLeDeviceNative(JNIEnv *env, jobject object,
                                jstring address) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);
    if (nat && eventLoopNat) {
        const char *c_address = env->GetStringUTFChars(address, NULL);
        ALOGV("... address = %s", c_address);
        DBusMessage *reply = dbus_func_args(env, nat->conn,
                           get_adapter_path(env, object),
                           DBUS_ADAPTER_IFACE, "CreateLeDevice",
                           DBUS_TYPE_STRING, &c_address,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(address, c_address);
        if (reply == NULL) {
            return NULL;
        }
        char *object_path = NULL;
        if (dbus_message_get_args(reply, NULL,
                                  DBUS_TYPE_OBJECT_PATH, &object_path,
                                  DBUS_TYPE_INVALID)) {
            return (jstring) env->NewStringUTF(object_path);
        }
     }
#endif
       return NULL;
}

static jobjectArray getGattServersNative(JNIEnv *env, jobject object) {
   ALOGE("%s", __FUNCTION__);
    jobjectArray strArray = NULL;

#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_name = FRAMEWORKS_BASE_IFC;
        const char *c_obj_path;
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                           get_adapter_path(env, object),
                                           DBUS_GATT_SERVER_INTERFACE,
                                           "GetRegisteredServers");
        if (msg == NULL) {
           ALOGE("%s Could not allocate D-Bus message object!",  __FUNCTION__);
            return NULL;
        }

        /* Make the call. */
        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);

        if (!reply) {
            if (dbus_error_is_set(&err))
                LOG_AND_FREE_DBUS_ERROR(&err);
        } else {
            strArray = dbus_returns_array_of_object_path(env, reply);
        }
    }
#endif
    return strArray;
}

static jboolean registerGattServerNative(JNIEnv *env, jobject object,
                                         jstring objPath, jint handleCount,
                                         jboolean isNew) {
   ALOGV("%s", __FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_name = FRAMEWORKS_BASE_IFC;
        const char *c_obj_path;
        const char *c_restriction = "None";
        DBusMessage *msg, *reply;
        DBusError err;
        event_loop_native_data_t *event_nat;

        if (isNew) {
            dbus_error_init(&err);

            msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                               get_adapter_path(env, object),
                                               DBUS_GATT_SERVER_INTERFACE,
                                               "RegisterServer");

            if (msg == NULL) {
               ALOGE("Could not allocate D-Bus message object!");
                return JNI_FALSE;
            }
        }

        c_obj_path = env->GetStringUTFChars(objPath, NULL);

        if (isNew) {
            /* Append arguments */
            dbus_message_append_args(msg,
                                     DBUS_TYPE_STRING, &c_name,
                                     DBUS_TYPE_OBJECT_PATH, &c_obj_path,
                                     DBUS_TYPE_UINT16, &handleCount,
                                     DBUS_TYPE_STRING, &c_restriction,
                                     DBUS_TYPE_INVALID);

            /* Make the call. */
            reply = dbus_connection_send_with_reply_and_block(
                                         nat->conn, msg, -1, &err);

            if (!reply) {
                if (dbus_error_is_set(&err))
                    LOG_AND_FREE_DBUS_ERROR(&err);
                env->ReleaseStringUTFChars(objPath, c_obj_path);
                return JNI_FALSE;
            }
        }

        event_nat = get_EventLoop_native_data(env,
                                   env->GetObjectField(object, field_mEventLoop));
        if(register_gatt_path(event_nat, c_obj_path)) {
           ALOGE("%s: register_gatt_path failed!", __FUNCTION__);
            result = JNI_FALSE;
        } else {
            result = JNI_TRUE;
        }
        env->ReleaseStringUTFChars(objPath, c_obj_path);
    }
#endif
    return result;
}

static jboolean notifyNative(JNIEnv *env, jobject object,
                             jstring objPath, jint sessionHandle,
                             jint handle, jbyteArray payload,
                             jint cnt) {
   ALOGV("%s", __FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_obj_path = env->GetStringUTFChars(objPath, NULL);
        DBusMessage *msg, *reply;
        DBusError err;
        jbyte *payload_ptr = env->GetByteArrayElements(payload, NULL);
        dbus_error_init(&err);

        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                            get_adapter_path(env, object),
                                            DBUS_GATT_SERVER_INTERFACE,
                                            "Notify");

        if (msg == NULL) {
           ALOGE("Could not allocate D-Bus message object!");
            return NULL;
        }

        /* append arguments */
        dbus_message_append_args(msg,
                                 DBUS_TYPE_OBJECT_PATH, &c_obj_path,
                                 DBUS_TYPE_UINT32, &sessionHandle,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &payload_ptr, cnt,
                                 DBUS_TYPE_INVALID);

        /* Make the call. */
        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);

        env->ReleaseStringUTFChars(objPath, c_obj_path);
        env->ReleaseByteArrayElements(payload, payload_ptr, 0);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            }
        } else {
            result = JNI_TRUE;
        }
    }
#endif
    return result;
}

static jboolean indicateNative(JNIEnv *env, jobject object,
                               jstring objPath, jint sessionHandle,
                               jint handle, jbyteArray payload,
                               jint cnt) {
   ALOGV("%s", __FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_async_call_t *pending;
        dbus_bool_t ret = FALSE;

        const char *c_obj_path = env->GetStringUTFChars(objPath, NULL);

        int len = env->GetStringLength(objPath) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_obj_path, len);  // for callback

        jbyte *payload_ptr = env->GetByteArrayElements(payload, NULL);
        dbus_error_init(&err);

        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                            get_adapter_path(env, object),
                                            DBUS_GATT_SERVER_INTERFACE,
                                            "Indicate");

        if (msg == NULL) {
           ALOGE("Could not allocate D-Bus message object!");
            return NULL;
        }

        /* append arguments */
        dbus_message_append_args(msg,
                                 DBUS_TYPE_OBJECT_PATH, &c_obj_path,
                                 DBUS_TYPE_UINT32, &sessionHandle,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &payload_ptr, cnt,
                                 DBUS_TYPE_INVALID);
        // Setup the callback info
        struct set_indicate_info_t *prop;
        prop = (set_indicate_info_t *) calloc(1, sizeof(struct set_indicate_info_t));

        prop->path = (char *)calloc(strlen(c_obj_path) + 1, sizeof(char));
        strlcpy(prop->path, c_obj_path, strlen(c_obj_path) + 1);

        /* Make the call. */
        pending = (dbus_async_call_t *)malloc(sizeof(dbus_async_call_t));

        if (!pending) {
           ALOGE("!pending");
            return JNI_FALSE;
        }

        DBusPendingCall *call;

        pending->env = env;
        pending->user_cb = onIndicateResponse;
        pending->user = prop;
        pending->nat = eventLoopNat;

        ret = dbus_connection_send_with_reply(nat->conn, msg,
                                                    &call,
                                                    -1);

       if (ret == TRUE)
            dbus_pending_call_set_notify(call,
                                         dbus_func_args_async_callback,
                                         pending,
                                         NULL);

       if (!ret) {
           if (dbus_error_is_set(&err)) {
           LOG_AND_FREE_DBUS_ERROR(&err);
           }
       }

       env->ReleaseStringUTFChars(objPath, c_obj_path);
       env->ReleaseByteArrayElements(payload, payload_ptr, 0);

       return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
   return JNI_FALSE;
}

static jboolean discoverPrimaryResponseNative(JNIEnv *env, jobject object,
                                              jstring uuid,
                                              jstring errorString,
                                              jint handle,
                                              jint end,
                                              int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        jboolean isCopy;
        const char *c_uuid;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            c_uuid = env->GetStringUTFChars(uuid, &isCopy);
            dbus_message_append_args(reply,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_UINT16, &end,
                                 DBUS_TYPE_STRING, &c_uuid,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        if (isCopy == JNI_TRUE) {
            env->ReleaseStringUTFChars(uuid, c_uuid);
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean discoverPrimaryByUuidResponseNative(JNIEnv *env, jobject object,
                                              jstring errorString,
                                              jint handle,
                                              jint end,
                                              int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            dbus_message_append_args(reply,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_UINT16, &end,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean findIncludedResponseNative(JNIEnv *env, jobject object,
                                           jstring uuid,
                                           jstring errorString,
                                           jint handle,
                                           jint start,
                                           jint end,
                                           int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        jboolean isCopy;
        const char *c_uuid;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            c_uuid = env->GetStringUTFChars(uuid, &isCopy);

            dbus_message_append_args(reply,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_UINT16, &start,
                                 DBUS_TYPE_UINT16, &end,
                                 DBUS_TYPE_STRING, &c_uuid,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        if (isCopy == JNI_TRUE) {
            env->ReleaseStringUTFChars(uuid, c_uuid);
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean discoverCharacteristicsResponseNative(JNIEnv *env, jobject object,
                                           jstring uuid,
                                           jstring errorString,
                                           jint handle,
                                           jint property,
                                           jint valueHandle,
                                           int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        jboolean isCopy;
        const char *c_uuid;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            c_uuid = env->GetStringUTFChars(uuid, &isCopy);
            dbus_message_append_args(reply,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_BYTE, &property,
                                 DBUS_TYPE_UINT16, &valueHandle,
                                 DBUS_TYPE_STRING, &c_uuid,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        if (isCopy == JNI_TRUE) {
            env->ReleaseStringUTFChars(uuid, c_uuid);
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean findInfoResponseNative(JNIEnv *env, jobject object,
                                       jstring uuid,
                                       jstring errorString,
                                       jint handle,
                                       int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        jboolean isCopy;
        const char *c_uuid;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            c_uuid = env->GetStringUTFChars(uuid, &isCopy);
            dbus_message_append_args(reply,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_STRING, &c_uuid,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        if (isCopy == JNI_TRUE) {
            env->ReleaseStringUTFChars(uuid, c_uuid);
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean readByTypeResponseNative(JNIEnv *env, jobject object,
                                         jstring uuid,
                                         jstring errorString,
                                         jint handle,
                                         jbyteArray payload,
                                         jint cnt,
                                         int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        jboolean isCopy;
        jbyte *payload_ptr;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            payload_ptr = env->GetByteArrayElements(payload, &isCopy);
            dbus_message_append_args(reply,
                                 DBUS_TYPE_UINT16, &handle,
                                 DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &payload_ptr, cnt,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        if (isCopy == JNI_TRUE) {
           env->ReleaseByteArrayElements(payload, payload_ptr, 0);
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean readResponseNative(JNIEnv *env, jobject object,
                                   jstring uuid,
                                   jstring errorString,
                                   jbyteArray payload,
                                   jint cnt,
                                   int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        jboolean isUuidCopy;
        jboolean isCopy = JNI_FALSE;
        jbyte *payload_ptr = NULL;
        const char *c_uuid = NULL;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            if (payload != NULL)
                payload_ptr = env->GetByteArrayElements(payload, &isCopy);
            if(uuid != NULL)
                c_uuid = env->GetStringUTFChars(uuid, &isUuidCopy);
            dbus_message_append_args(reply,
                                 DBUS_TYPE_STRING, &c_uuid,
                                 DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &payload_ptr, cnt,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to discover primary "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        if (isCopy == JNI_TRUE) {
           env->ReleaseByteArrayElements(payload, payload_ptr, 0);
        }
        if (isUuidCopy == JNI_TRUE) {
            if(uuid != NULL) {
                env->ReleaseStringUTFChars(uuid, c_uuid);
            }
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean writeResponseNative(JNIEnv *env, jobject object,
                                   jstring uuid,
                                   jstring errorString,
                                   int nativeData) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg = (DBusMessage *)nativeData;
        DBusMessage *reply;
        jboolean isUuidCopy;
        const char *c_uuid = NULL;

        if (!errorString) {
            reply = dbus_message_new_method_return(msg);
            if(uuid != NULL)
                c_uuid = env->GetStringUTFChars(uuid, &isUuidCopy);

            dbus_message_append_args(reply,
                                 DBUS_TYPE_STRING, &c_uuid,
                                 DBUS_TYPE_INVALID);
        } else {
            const char *c_errorString = env->GetStringUTFChars(errorString, NULL);
           ALOGE("%s: status %s", __FUNCTION__, c_errorString);
            reply = dbus_message_new_error(msg,
                    DBUS_ERROR_FAILED, c_errorString);
            env->ReleaseStringUTFChars(errorString, c_errorString);
        }

        if (!reply) {
           ALOGE("%s: Cannot create message reply to discover primary "
                 "D-Bus\n", __FUNCTION__);
            dbus_message_unref(msg);
            return JNI_FALSE;
        }

        dbus_connection_send(nat->conn, reply, NULL);
        dbus_message_unref(msg);
        dbus_message_unref(reply);
        if (isUuidCopy == JNI_TRUE) {
            if(uuid != NULL) {
                env->ReleaseStringUTFChars(uuid, c_uuid);
            }
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jstring registerSinkHealthApplicationNative(JNIEnv *env, jobject object,
                                           jint dataType, jstring role,
                                           jstring name) {
    ALOGV("%s", __FUNCTION__);
    jstring path = NULL;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_role = env->GetStringUTFChars(role, NULL);
        const char *c_name = env->GetStringUTFChars(name, NULL);
        char *c_path;

        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                            DBUS_HEALTH_MANAGER_PATH,
                                            DBUS_HEALTH_MANAGER_IFACE,
                                            "CreateApplication");

        if (msg == NULL) {
            ALOGE("Could not allocate D-Bus message object!");
            return NULL;
        }

        /* append arguments */
        append_dict_args(msg,
                         "DataType", DBUS_TYPE_UINT16, &dataType,
                         "Role", DBUS_TYPE_STRING, &c_role,
                         "Description", DBUS_TYPE_STRING, &c_name,
                         DBUS_TYPE_INVALID);


        /* Make the call. */
        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);

        env->ReleaseStringUTFChars(role, c_role);
        env->ReleaseStringUTFChars(name, c_name);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            }
        } else {
            if (!dbus_message_get_args(reply, &err,
                                      DBUS_TYPE_OBJECT_PATH, &c_path,
                                      DBUS_TYPE_INVALID)) {
                if (dbus_error_is_set(&err)) {
                    LOG_AND_FREE_DBUS_ERROR(&err);
                }
            } else {
                path = env->NewStringUTF(c_path);
            }
            dbus_message_unref(reply);
        }
    }
#endif
    return path;
}

static jboolean unregisterHealthApplicationNative(JNIEnv *env, jobject object,
                                                    jstring path) {
    ALOGV("%s", __FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        DBusError err;
        dbus_error_init(&err);
        DBusMessage *reply =
            dbus_func_args_timeout(env, nat->conn, -1,
                                   DBUS_HEALTH_MANAGER_PATH,
                                   DBUS_HEALTH_MANAGER_IFACE, "DestroyApplication",
                                   DBUS_TYPE_OBJECT_PATH, &c_path,
                                   DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            }
        } else {
            result = JNI_TRUE;
        }
    }
#endif
    return result;
}

static jboolean unregisterGattServerNative(JNIEnv *env, jobject object,
                                           jstring ObjPath, jboolean complete) {
   ALOGV("%s", __FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_obj_path = env->GetStringUTFChars(ObjPath, NULL);

        if (complete == JNI_TRUE) {
            DBusError err;
            dbus_error_init(&err);
            DBusMessage *reply =
            dbus_func_args_timeout(env, nat->conn, -1,
                                   get_adapter_path(env, object),
                                   DBUS_GATT_SERVER_INTERFACE, "DeregisterServer",
                                   DBUS_TYPE_OBJECT_PATH, &c_obj_path,
                                   DBUS_TYPE_INVALID);
            if (!reply) {
                if (dbus_error_is_set(&err))
                    LOG_AND_FREE_DBUS_ERROR(&err);
                env->ReleaseStringUTFChars(ObjPath, c_obj_path);
                return JNI_FALSE;
            }
        }

        event_loop_native_data_t *event_nat =
        get_EventLoop_native_data(env, env->GetObjectField(object, field_mEventLoop));

        if(unregister_gatt_path(event_nat, c_obj_path)) {
           ALOGE("%s: Can't unregister gatt object path!",
                 __FUNCTION__);
            result = JNI_FALSE;
        } else {
            result = JNI_TRUE;
        }

        env->ReleaseStringUTFChars(ObjPath, c_obj_path);
    }
#endif
    return result;
}

static jboolean createChannelNative(JNIEnv *env, jobject object,
                                       jstring devicePath, jstring appPath, jstring config,
                                       jint code) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_device_path = env->GetStringUTFChars(devicePath, NULL);
        const char *c_app_path = env->GetStringUTFChars(appPath, NULL);
        const char *c_config = env->GetStringUTFChars(config, NULL);
        int *data = (int *) malloc(sizeof(int));
        if (data == NULL) return JNI_FALSE;

        *data = code;
        bool ret = dbus_func_args_async(env, nat->conn, -1, onHealthDeviceConnectionResult,
                                        data, eventLoopNat, c_device_path,
                                        DBUS_HEALTH_DEVICE_IFACE, "CreateChannel",
                                        DBUS_TYPE_OBJECT_PATH, &c_app_path,
                                        DBUS_TYPE_STRING, &c_config,
                                        DBUS_TYPE_INVALID);


        env->ReleaseStringUTFChars(devicePath, c_device_path);
        env->ReleaseStringUTFChars(appPath, c_app_path);
        env->ReleaseStringUTFChars(config, c_config);

        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean destroyChannelNative(JNIEnv *env, jobject object, jstring devicePath,
                                     jstring channelPath, jint code) {
    ALOGE("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_device_path = env->GetStringUTFChars(devicePath, NULL);
        const char *c_channel_path = env->GetStringUTFChars(channelPath, NULL);
        int *data = (int *) malloc(sizeof(int));
        if (data == NULL) return JNI_FALSE;

        *data = code;
        bool ret = dbus_func_args_async(env, nat->conn, -1, onHealthDeviceConnectionResult,
                                        data, eventLoopNat, c_device_path,
                                        DBUS_HEALTH_DEVICE_IFACE, "DestroyChannel",
                                        DBUS_TYPE_OBJECT_PATH, &c_channel_path,
                                        DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(devicePath, c_device_path);
        env->ReleaseStringUTFChars(channelPath, c_channel_path);

        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jstring getMainChannelNative(JNIEnv *env, jobject object, jstring devicePath) {
    ALOGE("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_device_path = env->GetStringUTFChars(devicePath, NULL);
        DBusError err;
        dbus_error_init(&err);

        DBusMessage *reply = dbus_func_args(env, nat->conn,
                           c_device_path,
                           DBUS_HEALTH_DEVICE_IFACE, "GetProperties",
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(devicePath, c_device_path);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            }
        } else {
            DBusMessageIter iter;
            jobjectArray str_array = NULL;
            if (dbus_message_iter_init(reply, &iter))
                str_array = parse_health_device_properties(env, &iter);
            dbus_message_unref(reply);
            jstring path = (jstring) env->GetObjectArrayElement(str_array, 1);

            return path;
        }
    }
#endif
    return NULL;
}

static jstring getChannelApplicationNative(JNIEnv *env, jobject object, jstring channelPath) {
    ALOGE("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_channel_path = env->GetStringUTFChars(channelPath, NULL);
        DBusError err;
        dbus_error_init(&err);

        DBusMessage *reply = dbus_func_args(env, nat->conn,
                                            c_channel_path,
                                            DBUS_HEALTH_CHANNEL_IFACE, "GetProperties",
                                            DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(channelPath, c_channel_path);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            }
        } else {
            DBusMessageIter iter;
            jobjectArray str_array = NULL;
            if (dbus_message_iter_init(reply, &iter))
                str_array = parse_health_channel_properties(env, &iter);
            dbus_message_unref(reply);

            jint len = env->GetArrayLength(str_array);

            jstring name, path;
            const char *c_name;

            for (int i = 0; i < len; i+=2) {
                name = (jstring) env->GetObjectArrayElement(str_array, i);
                c_name = env->GetStringUTFChars(name, NULL);

                if (!strcmp(c_name, "Application")) {
                    path = (jstring) env->GetObjectArrayElement(str_array, i+1);
                    env->ReleaseStringUTFChars(name, c_name);
                    return path;
                }
                env->ReleaseStringUTFChars(name, c_name);
            }
        }
    }
#endif
    return NULL;
}

static jboolean releaseChannelFdNative(JNIEnv *env, jobject object, jstring channelPath) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_channel_path = env->GetStringUTFChars(channelPath, NULL);
        DBusError err;
        dbus_error_init(&err);

        DBusMessage *reply = dbus_func_args(env, nat->conn,
                                            c_channel_path,
                                            DBUS_HEALTH_CHANNEL_IFACE, "Release",
                                            DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(channelPath, c_channel_path);

        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jobject getChannelFdNative(JNIEnv *env, jobject object, jstring channelPath) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_channel_path = env->GetStringUTFChars(channelPath, NULL);
        int32_t fd;
        DBusError err;
        dbus_error_init(&err);

        DBusMessage *reply = dbus_func_args(env, nat->conn,
                                            c_channel_path,
                                            DBUS_HEALTH_CHANNEL_IFACE, "Acquire",
                                            DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(channelPath, c_channel_path);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            }
            return NULL;
        }

        fd = dbus_returns_unixfd(env, reply);
        if (fd == -1) return NULL;

        int flags = fcntl(fd, F_GETFL);
        if (flags < 0) {
           ALOGE("Can't get flags with fcntl(): %s (%d)",
                                strerror(errno), errno);
           releaseChannelFdNative(env, object, channelPath);
           close(fd);
           return NULL;
        }

        flags &= ~O_NONBLOCK;
        int status = fcntl(fd, F_SETFL, flags);
        if (status < 0) {
           ALOGE("Can't set flags with fcntl(): %s (%d)",
               strerror(errno), errno);
           releaseChannelFdNative(env, object, channelPath);
           close(fd);
           return NULL;
        }

        // Create FileDescriptor object
        jobject fileDesc = jniCreateFileDescriptor(env, fd);
        if (fileDesc == NULL) {
            // FileDescriptor constructor has thrown an exception
            releaseChannelFdNative(env, object, channelPath);
            close(fd);
            return NULL;
        }

        // Wrap it in a ParcelFileDescriptor
        jobject parcelFileDesc = newParcelFileDescriptor(env, fileDesc);
        if (parcelFileDesc == NULL) {
            // ParcelFileDescriptor constructor has thrown an exception
            releaseChannelFdNative(env, object, channelPath);
            close(fd);
            return NULL;
        }

        return parcelFileDesc;
    }
#endif
    return NULL;
}

static jobjectArray getGattServicePropertiesNative(JNIEnv *env, jobject object, jstring path) {
   ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        const char *c_path = env->GetStringUTFChars(path, NULL);
        reply = dbus_func_args_timeout(env,
                                   nat->conn, -1, c_path,
                                   DBUS_CHARACTERISTIC_IFACE, "GetProperties",
                                   DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
               ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return NULL;
        }
        env->PushLocalFrame(PROPERTIES_NREFS);

        DBusMessageIter iter;
        jobjectArray str_array = NULL;
        if (dbus_message_iter_init(reply, &iter))
            str_array =  parse_gatt_service_properties(env, &iter);
        dbus_message_unref(reply);

        return (jobjectArray) env->PopLocalFrame(str_array);
    }
#endif
    return NULL;
}

static jboolean discoverCharacteristicsNative(JNIEnv *env, jobject object,
                                              jstring path) {
   ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

       ALOGV("... Object Path = %s", c_path);
       ALOGE(" %s .. Object Path = %s\n",  __FUNCTION__, c_path);

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onDiscoverCharacteristicsResult,
                                        context_path,
                                        eventLoopNat,
                                        c_path,
                                        DBUS_CHARACTERISTIC_IFACE,
                                        "DiscoverCharacteristics",
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static int gattLeConnectNative(JNIEnv *env, jobject object,
                                           jstring path,
                                           jint prohibitRemoteChg,
                                           jint filterPolicy,
                                           jint scanInterval,
                                           jint scanWindow,
                                           jint intervalMin,
                                           jint intervalMax,
                                           jint latency,
                                           jint superVisionTimeout,
                                           jint minCeLen,
                                           jint maxCeLen,
                                           jint connTimeout) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusMessageIter iter;
        DBusError err;
        int result;

        const char *c_path = env->GetStringUTFChars(path, NULL);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_DEVICE_IFACE, "LeConnectReq");
        if (!msg) {
            ALOGE("%s: Can't allocate new method call for GATT ConnectReq", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return GATT_OPERATION_GENERIC_FAILURE;
        }

        dbus_message_append_args(msg,
                                 DBUS_TYPE_BYTE, &prohibitRemoteChg,
                                 DBUS_TYPE_BYTE, &filterPolicy,
                                 DBUS_TYPE_UINT16, (uint16_t *)&scanInterval,
                                 DBUS_TYPE_UINT16, (uint16_t *)&scanWindow,
                                 DBUS_TYPE_UINT16, (uint16_t *)&intervalMin,
                                 DBUS_TYPE_UINT16, (uint16_t *)&intervalMax,
                                 DBUS_TYPE_UINT16, (uint16_t *)&latency,
                                 DBUS_TYPE_UINT16, (uint16_t *)&superVisionTimeout,
                                 DBUS_TYPE_UINT16, (uint16_t *)&minCeLen,
                                 DBUS_TYPE_UINT16, (uint16_t *)&maxCeLen,
                                 DBUS_TYPE_UINT16, (uint16_t *)&connTimeout,
                                 DBUS_TYPE_INVALID);
        dbus_message_iter_init_append(msg, &iter);

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);

        if (!reply) {
            result = GATT_OPERATION_GENERIC_FAILURE;
            if (dbus_error_is_set(&err)) {
                if (!strcmp(err.name, BLUEZ_ERROR_IFC ".InProgress"))
                    result = GATT_OPERATION_BUSY;
                else if (!strcmp(err.name, BLUEZ_ERROR_IFC ".AlreadyConnected"))
                    result = GATT_ALREADY_CONNECTED;

                LOG_AND_FREE_DBUS_ERROR(&err);
            } else {
                ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            }
        }  else {
            result = GATT_OPERATION_SUCCESS;
        }

        env->ReleaseStringUTFChars(path, c_path);
        dbus_message_unref(msg);

        ALOGE("%s result = %d", __FUNCTION__, result);
        return result;
    }

#endif
    return -1;
}

static jboolean gattLeConnectCancelNative(JNIEnv *env, jobject object,
                                        jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusError err;

        const char *c_path = env->GetStringUTFChars(path, NULL);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_DEVICE_IFACE, "LeConnectCancel");
        if (!msg) {
            ALOGE("%s: Can't allocate new method call for LeConnectCancel!", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else {
                ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            }
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean gattLeDisconnectRequestNative(JNIEnv *env, jobject object,
                                        jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusError err;

        const char *c_path = env->GetStringUTFChars(path, NULL);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_DEVICE_IFACE, "LeDisconnectReq");
        if (!msg) {
            ALOGE("%s: Can't allocate new method call for LeDisconnectReq!", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else {
                ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            }
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static int gattConnectNative(JNIEnv *env, jobject object,
                                           jstring path,
                                           jint prohibitRemoteChg,
                                           jint filterPolicy,
                                           jint scanInterval,
                                           jint scanWindow,
                                           jint intervalMin,
                                           jint intervalMax,
                                           jint latency,
                                           jint superVisionTimeout,
                                           jint minCeLen,
                                           jint maxCeLen,
                                           jint connTimeout) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusMessageIter iter;
        DBusError err;
        int result;

        const char *c_path = env->GetStringUTFChars(path, NULL);
        ALOGE("the dbus object path: %s", c_path);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_CHARACTERISTIC_IFACE, "ConnectReq");
        if (!msg) {
           ALOGE("%s: Can't allocate new method call for device ConnectReq!", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return GATT_OPERATION_GENERIC_FAILURE;
        }

        dbus_message_append_args(msg,
                                 DBUS_TYPE_BYTE, &prohibitRemoteChg,
                                 DBUS_TYPE_BYTE, &filterPolicy,
                                 DBUS_TYPE_UINT16, (uint16_t *)&scanInterval,
                                 DBUS_TYPE_UINT16, (uint16_t *)&scanWindow,
                                 DBUS_TYPE_UINT16, (uint16_t *)&intervalMin,
                                 DBUS_TYPE_UINT16, (uint16_t *)&intervalMax,
                                 DBUS_TYPE_UINT16, (uint16_t *)&latency,
                                 DBUS_TYPE_UINT16, (uint16_t *)&superVisionTimeout,
                                 DBUS_TYPE_UINT16, (uint16_t *)&minCeLen,
                                 DBUS_TYPE_UINT16, (uint16_t *)&maxCeLen,
                                 DBUS_TYPE_UINT16, (uint16_t *)&connTimeout,
                                 DBUS_TYPE_INVALID);
        dbus_message_iter_init_append(msg, &iter);

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            result = GATT_OPERATION_GENERIC_FAILURE;
            if (dbus_error_is_set(&err)) {
                if (!strcmp(err.name, BLUEZ_ERROR_IFC ".InProgress"))
                    result = GATT_OPERATION_BUSY;
                else if (!strcmp(err.name, BLUEZ_ERROR_IFC ".AlreadyConnected"))
                    result = GATT_ALREADY_CONNECTED;

                LOG_AND_FREE_DBUS_ERROR(&err);
            } else {
               ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            }
        } else {
            result = GATT_OPERATION_SUCCESS;
        }

        ALOGE("%s result = %d", __FUNCTION__, result);
        return result;
    }
#endif
    return -1;
}

static jboolean gattConnectCancelNative(JNIEnv *env, jobject object,
                                        jstring path) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply, *msg;
        DBusError err;

        const char *c_path = env->GetStringUTFChars(path, NULL);
       ALOGE("the dbus object path: %s", c_path);

        dbus_error_init(&err);
        msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                          c_path, DBUS_CHARACTERISTIC_IFACE, "ConnectCancel");
        if (!msg) {
           ALOGE("%s: Can't allocate new method call for device ConnectCancel!", __FUNCTION__);
            env->ReleaseStringUTFChars(path, c_path);
            return JNI_FALSE;
        }

        reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
        dbus_message_unref(msg);

        env->ReleaseStringUTFChars(path, c_path);
        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else {
               ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            }
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jobjectArray getCharacteristicPropertiesNative(JNIEnv *env, jobject object, jstring path) {
   ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        const char *c_path = env->GetStringUTFChars(path, NULL);
        reply = dbus_func_args_timeout(env,
                                   nat->conn, -1, c_path,
                                   DBUS_CHARACTERISTIC_IFACE, "GetProperties",
                                   DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);

        if (!reply) {
            if (dbus_error_is_set(&err)) {
                LOG_AND_FREE_DBUS_ERROR(&err);
            } else
               ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return NULL;
        }
        env->PushLocalFrame(PROPERTIES_NREFS);

        DBusMessageIter iter;
        jobjectArray str_array = NULL;
        if (dbus_message_iter_init(reply, &iter))
            str_array =  parse_gatt_characteristic_properties(env, &iter);
        dbus_message_unref(reply);

        return (jobjectArray) env->PopLocalFrame(str_array);
    }
#endif
    return NULL;
}

static jboolean setCharacteristicPropertyNative(JNIEnv *env, jobject object, jstring path,
                                                jstring key, jbyteArray value, int len, jboolean reliable) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
      get_EventLoop_native_data(env, eventLoop);
    if (nat) {
        DBusMessage *msg;
        DBusMessageIter iter;
        DBusError err;
        dbus_async_call_t *pending;

        const char *c_key = env->GetStringUTFChars(key, NULL);
        const char *c_path = env->GetStringUTFChars(path, NULL);
        jbyte *v_ptr = env->GetByteArrayElements(value, NULL);

        int sz = env->GetArrayLength(value);

        dbus_bool_t ret = FALSE;

        dbus_error_init(&err);
        if (reliable) {
            msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                               c_path, DBUS_CHARACTERISTIC_IFACE, "SetProperty");

            if (!msg) {
               ALOGE("%s: Can't allocate new method call for characteristic "
                     "SetProperty!", __FUNCTION__);
                env->ReleaseStringUTFChars(key, c_key);
                env->ReleaseStringUTFChars(path, c_path);
                env->ReleaseByteArrayElements(value, v_ptr, 0);
                return JNI_FALSE;
            }

            dbus_message_append_args(msg, DBUS_TYPE_STRING, &c_key, DBUS_TYPE_INVALID);
            dbus_message_iter_init_append(msg, &iter);

            append_array_variant(&iter, DBUS_TYPE_BYTE, (void*)v_ptr, len);

            // Setup the callback info
            struct set_characteristic_property_t *prop;
            prop = (set_characteristic_property_t *) calloc(1, sizeof(struct set_characteristic_property_t));

            prop->path = (char *)calloc(strlen(c_path) + 1, sizeof(char));
            strlcpy(prop->path, c_path, strlen(c_path) + 1);

            prop->property = (char *)calloc(strlen(c_key) + 1, sizeof(char));
            strlcpy(prop->property, c_key, strlen(c_key) + 1);

            // Make the call.
            pending = (dbus_async_call_t *)malloc(sizeof(dbus_async_call_t));

            if (!pending)
                return JNI_FALSE;

            DBusPendingCall *call;

            pending->env = env;
            pending->user_cb = onSetCharacteristicPropertyResult;
            pending->user = prop;
            pending->nat = eventLoopNat;

            ret = dbus_connection_send_with_reply(nat->conn, msg,
                                                    &call,
                                                    -1);
            if (ret == TRUE)
                dbus_pending_call_set_notify(call,
                                             dbus_func_args_async_callback,
                                             pending,
                                             NULL);

            if (!ret) {
                if (dbus_error_is_set(&err)) {
                    LOG_AND_FREE_DBUS_ERROR(&err);
                }
            }

        } else {
            msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                               c_path, DBUS_CHARACTERISTIC_IFACE, "SetPropertyCommand");
            if (!msg) {
               ALOGE("%s: Can't allocate new method call for characteristic "
                     "SetProperty!", __FUNCTION__);
                env->ReleaseStringUTFChars(key, c_key);
                env->ReleaseStringUTFChars(path, c_path);
                env->ReleaseByteArrayElements(value, v_ptr, 0);
                return JNI_FALSE;
            }

            dbus_message_append_args(msg, DBUS_TYPE_STRING, &c_key, DBUS_TYPE_INVALID);
            dbus_message_iter_init_append(msg, &iter);
            append_array_variant(&iter, DBUS_TYPE_BYTE, (void*)v_ptr, len);
            DBusMessage *reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
            dbus_message_unref(msg);
            ret = reply ? TRUE : FALSE;
        }

        env->ReleaseStringUTFChars(key, c_key);
        env->ReleaseStringUTFChars(path, c_path);
        env->ReleaseByteArrayElements(value, v_ptr, 0);

        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean updateCharacteristicValueNative(JNIEnv *env, jobject object,
                                                jstring path) {
   ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    jobject eventLoop = env->GetObjectField(object, field_mEventLoop);
    struct event_loop_native_data_t *eventLoopNat =
            get_EventLoop_native_data(env, eventLoop);

    if (nat && eventLoopNat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

       ALOGV("... Object Path = %s", c_path);

        bool ret = dbus_func_args_async(env, nat->conn, -1,
                                        onUpdateCharacteristicValueResult,
                                        context_path,
                                        eventLoopNat,
                                        c_path,
                                        DBUS_CHARACTERISTIC_IFACE,
                                        "UpdateValue",
                                        DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);

        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean registerCharacteristicsWatcherNative(JNIEnv *env, jobject object,
                                              jstring path) {
   ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        char *c_watcher_path = "/android/bluetooth/watcher";

       ALOGV("... Object Path = %s", c_path);

        DBusMessage *reply = dbus_func_args(env, nat->conn,
                                            c_path,
                                            DBUS_CHARACTERISTIC_IFACE, "RegisterCharacteristicsWatcher",
                                            DBUS_TYPE_OBJECT_PATH, &c_watcher_path,
                                            DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean disconnectGattNative(JNIEnv *env, jobject object,
                                              jstring path) {
   ALOGV(__FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        const char *c_path = env->GetStringUTFChars(path, NULL);
        reply = dbus_func_args(env,
                                   nat->conn, c_path,
                                   DBUS_CHARACTERISTIC_IFACE,
                                   "Disconnect",
                                   DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean deregisterCharacteristicsWatcherNative(JNIEnv *env, jobject object,
                                              jstring path) {
   ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        char *c_watcher_path = "/android/bluetooth/watcher";

       ALOGV("... Object Path = %s", c_path);

        DBusMessage *reply = dbus_func_args(env, nat->conn,
                                            c_path,
                                            DBUS_CHARACTERISTIC_IFACE, "UnregisterCharacteristicsWatcher",
                                            DBUS_TYPE_OBJECT_PATH, &c_watcher_path,
                                            DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        return reply ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean disConnectSapNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
   ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply = dbus_func_args_generic(env, nat->conn,
                           "org.qcom.sap",
                           "/SapService",
                           "org.qcom.sap", "DisConnect",
                           DBUS_TYPE_INVALID);

       ALOGV("%s: Sap Disconnect returned %s", reply);
        return reply ?  JNI_TRUE: JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jint listConnectionNative(JNIEnv *env, jobject object) {
   ALOGV(__FUNCTION__);

#ifdef HAVE_BLUETOOTH
    DBusMessage *msg = NULL;
    DBusMessage *reply = NULL;
    DBusError err;
    jint ret = -1;
    int32_t conn = 0;

    native_data_t *nat = get_native_data(env, object);
    if (nat == NULL) {
        goto done;
    }

    dbus_error_init(&err);

    /* Compose the command */
    msg = dbus_message_new_method_call(BLUEZ_DBUS_BASE_IFC,
                                       get_adapter_path(env, object),
                                       DBUS_ADAPTER_IFACE, "ListConnection");

    if (msg == NULL) {
        if (dbus_error_is_set(&err)) {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }
        goto done;
    }

    /* Send the command. */
    reply = dbus_connection_send_with_reply_and_block(nat->conn, msg, -1, &err);
    if (dbus_error_is_set(&err)) {
         LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
           if (reply) dbus_message_unref(reply);
         goto done;
    }
    conn =  reply ? dbus_returns_int32(env, reply) : -1;
    ret = conn;

done:
    if (msg) dbus_message_unref(msg);
    return ret;
#else
    return -1;
#endif
}

static jboolean disconnectAllConnectionsNative(JNIEnv *env, jobject object) {
   ALOGV(__FUNCTION__);

#ifdef HAVE_BLUETOOTH
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *msg, *reply;

        reply = dbus_func_args(env,
                                   nat->conn,
                                   get_adapter_path(env, object),
                                   DBUS_ADAPTER_IFACE,
                                   "DisconnectAllConnections",
                                   DBUS_TYPE_INVALID);
        return reply ? JNI_TRUE : JNI_FALSE;
   }
    #endif
    return JNI_FALSE;
}
static jboolean disConnectDUNNative(JNIEnv *env, jobject object) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat) {
        DBusMessage *reply = dbus_func_args_generic(env, nat->conn,
                           "org.qcom.bluetooth.dun",
                           "/DunService",
                           "org.qcom.bluetooth.dun", "DisConnect",
                           DBUS_TYPE_INVALID);

        ALOGV("%s: DUN Disconnect returned %s", reply);
        return reply ?  JNI_TRUE: JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static JNINativeMethod sMethods[] = {
     /* name, signature, funcPtr */
    {"classInitNative", "()V", (void*)classInitNative},
    {"initializeNativeDataNative", "()V", (void *)initializeNativeDataNative},
    {"setupNativeDataNative", "()Z", (void *)setupNativeDataNative},
    {"tearDownNativeDataNative", "()Z", (void *)tearDownNativeDataNative},
    {"cleanupNativeDataNative", "()V", (void *)cleanupNativeDataNative},
    {"getAdapterPathNative", "()Ljava/lang/String;", (void*)getAdapterPathNative},

    {"isEnabledNative", "()I", (void *)isEnabledNative},
    {"enableNative", "()I", (void *)enableNative},
    {"disableNative", "()I", (void *)disableNative},

    {"getAdapterPropertiesNative", "()[Ljava/lang/Object;", (void *)getAdapterPropertiesNative},
    {"getDevicePropertiesNative", "(Ljava/lang/String;)[Ljava/lang/Object;",
      (void *)getDevicePropertiesNative},
    {"setAdapterPropertyStringNative", "(Ljava/lang/String;Ljava/lang/String;)Z",
      (void *)setAdapterPropertyStringNative},
    {"setAdapterPropertyBooleanNative", "(Ljava/lang/String;I)Z",
      (void *)setAdapterPropertyBooleanNative},
    {"setAdapterPropertyIntegerNative", "(Ljava/lang/String;I)Z",
      (void *)setAdapterPropertyIntegerNative},

    {"startDiscoveryNative", "()Z", (void*)startDiscoveryNative},
    {"stopDiscoveryNative", "()Z", (void *)stopDiscoveryNative},

    {"readAdapterOutOfBandDataNative", "()[B", (void *)readAdapterOutOfBandDataNative},
    {"createPairedDeviceNative", "(Ljava/lang/String;I)Z", (void *)createPairedDeviceNative},
    {"createPairedDeviceOutOfBandNative", "(Ljava/lang/String;I)Z",
                                    (void *)createPairedDeviceOutOfBandNative},
    {"cancelDeviceCreationNative", "(Ljava/lang/String;)Z", (void *)cancelDeviceCreationNative},
    {"removeDeviceNative", "(Ljava/lang/String;)Z", (void *)removeDeviceNative},
    {"getDeviceServiceChannelNative", "(Ljava/lang/String;Ljava/lang/String;I)I",
      (void *)getDeviceServiceChannelNative},
    {"getDeviceStringAttrValue", "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;",
    (void *)getDeviceStringAttrValue},
    {"setPairingConfirmationNative", "(Ljava/lang/String;ZI)Z",
            (void *)setPairingConfirmationNative},
    {"setPasskeyNative", "(Ljava/lang/String;II)Z", (void *)setPasskeyNative},
    {"setRemoteOutOfBandDataNative", "(Ljava/lang/String;[B[BI)Z", (void *)setRemoteOutOfBandDataNative},
    {"setAuthorizationNative", "(Ljava/lang/String;ZI)Z", (void *)setAuthorizationNative},
    {"setPinNative", "(Ljava/lang/String;Ljava/lang/String;I)Z", (void *)setPinNative},
    {"cancelPairingUserInputNative", "(Ljava/lang/String;I)Z",
            (void *)cancelPairingUserInputNative},
    {"setDevicePropertyBooleanNative", "(Ljava/lang/String;Ljava/lang/String;I)Z",
            (void *)setDevicePropertyBooleanNative},
    {"setDevicePropertyStringNative", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",
            (void *)setDevicePropertyStringNative},
    {"setDevicePropertyIntegerNative", "(Ljava/lang/String;Ljava/lang/String;I)Z",
             (void *)setDevicePropertyIntegerNative},
    {"updateLEConnectionParametersNative", "(Ljava/lang/String;IIIII)Z",
             (void *)updateLEConnectionParametersNative},
    {"setLEConnectionParamNative", "(Ljava/lang/String;IIIIIIIIIII)Z",
             (void *)setLEConnectionParamNative},
    {"registerRssiUpdateWatcherNative", "(Ljava/lang/String;IIZ)Z",
             (void *)registerRssiUpdateWatcherNative},
    {"unregisterRssiUpdateWatcherNative", "(Ljava/lang/String;)Z",
             (void *)unregisterRssiUpdateWatcherNative},
    {"createDeviceNative", "(Ljava/lang/String;)Z", (void *)createDeviceNative},
    {"discoverServicesNative", "(Ljava/lang/String;Ljava/lang/String;)Z", (void *)discoverServicesNative},
    {"addRfcommServiceRecordNative", "(Ljava/lang/String;JJS)I", (void *)addRfcommServiceRecordNative},
    {"removeServiceRecordNative", "(I)Z", (void *)removeServiceRecordNative},
    {"addReservedServiceRecordsNative", "([I)[I", (void *) addReservedServiceRecordsNative},
    {"removeReservedServiceRecordsNative", "([I)Z", (void *) removeReservedServiceRecordsNative},
    {"findDeviceNative", "(Ljava/lang/String;)Ljava/lang/String;", (void*)findDeviceNative},
    {"setLinkTimeoutNative", "(Ljava/lang/String;I)Z", (void *)setLinkTimeoutNative},
    // HID functions
    {"connectInputDeviceNative", "(Ljava/lang/String;)Z", (void *)connectInputDeviceNative},
    {"disconnectInputDeviceNative", "(Ljava/lang/String;)Z", (void *)disconnectInputDeviceNative},

    {"setBluetoothTetheringNative", "(ZLjava/lang/String;Ljava/lang/String;)Z",
              (void *)setBluetoothTetheringNative},
    {"connectPanDeviceNative", "(Ljava/lang/String;Ljava/lang/String;)Z",
              (void *)connectPanDeviceNative},
    {"disconnectPanDeviceNative", "(Ljava/lang/String;)Z", (void *)disconnectPanDeviceNative},
    {"disconnectPanServerDeviceNative", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",
              (void *)disconnectPanServerDeviceNative},
    // Health function
    {"registerHealthApplicationNative",
              "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
              (void *)registerHealthApplicationNative},
    {"registerHealthApplicationNative",
            "(ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            (void *)registerSinkHealthApplicationNative},

    {"unregisterHealthApplicationNative", "(Ljava/lang/String;)Z",
              (void *)unregisterHealthApplicationNative},
    {"createChannelNative", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Z",
              (void *)createChannelNative},
    {"destroyChannelNative", "(Ljava/lang/String;Ljava/lang/String;I)Z",
              (void *)destroyChannelNative},
    {"getMainChannelNative", "(Ljava/lang/String;)Ljava/lang/String;", (void *)getMainChannelNative},
    {"getChannelApplicationNative", "(Ljava/lang/String;)Ljava/lang/String;",
              (void *)getChannelApplicationNative},
    {"getChannelFdNative", "(Ljava/lang/String;)Landroid/os/ParcelFileDescriptor;", (void *)getChannelFdNative},
    {"releaseChannelFdNative", "(Ljava/lang/String;)Z", (void *)releaseChannelFdNative},
    {"discoverPrimaryServicesNative", "(Ljava/lang/String;)Z", (void *)discoverPrimaryServicesNative},
    {"createLeDeviceNative", "(Ljava/lang/String;)Ljava/lang/String;", (void*)createLeDeviceNative},
    {"getGattServicePropertiesNative", "(Ljava/lang/String;)[Ljava/lang/Object;", (void*)getGattServicePropertiesNative},
    {"discoverCharacteristicsNative", "(Ljava/lang/String;)Z", (void*)discoverCharacteristicsNative},
    {"gattConnectCancelNative", "(Ljava/lang/String;)Z", (void *)gattConnectCancelNative},
    {"gattConnectNative", "(Ljava/lang/String;IIIIIIIIIII)I", (void *)gattConnectNative},
    {"gattLeConnectCancelNative", "(Ljava/lang/String;)Z", (void *)gattLeConnectCancelNative},
    {"gattLeConnectNative", "(Ljava/lang/String;IIIIIIIIIII)I", (void *)gattLeConnectNative},
    {"gattLeDisconnectRequestNative", "(Ljava/lang/String;)Z", (void *)gattLeDisconnectRequestNative},
    {"updateCharacteristicValueNative", "(Ljava/lang/String;)Z", (void*)updateCharacteristicValueNative},
    {"getCharacteristicPropertiesNative", "(Ljava/lang/String;)[Ljava/lang/Object;", (void*)getCharacteristicPropertiesNative},
    {"setCharacteristicPropertyNative", "(Ljava/lang/String;Ljava/lang/String;[BIZ)Z", (void*)setCharacteristicPropertyNative},
    {"registerCharacteristicsWatcherNative", "(Ljava/lang/String;)Z", (void*)registerCharacteristicsWatcherNative},
    {"deregisterCharacteristicsWatcherNative", "(Ljava/lang/String;)Z", (void*)deregisterCharacteristicsWatcherNative},
    {"disconnectGattNative", "(Ljava/lang/String;)Z", (void*)disconnectGattNative},
    {"sapAuthorizeNative", "(Ljava/lang/String;ZI)Z", (void *)sapAuthorizeNative},
    {"disConnectSapNative", "()I", (void *)disConnectSapNative},
    {"listConnectionNative", "()I", (void*)listConnectionNative},
    {"disconnectAllConnectionsNative", "()Z", (void*)disconnectAllConnectionsNative},
    //Gatt Server
    {"getGattServersNative", "()[Ljava/lang/Object;", (void *)getGattServersNative},
    {"registerGattServerNative", "(Ljava/lang/String;IZ)Z", (void *)registerGattServerNative},
    {"unregisterGattServerNative", "(Ljava/lang/String;Z)Z", (void *)unregisterGattServerNative},
    {"notifyNative", "(Ljava/lang/String;II[BI)Z", (void *)notifyNative},
    {"indicateNative", "(Ljava/lang/String;II[BI)Z", (void *)indicateNative},
    {"discoverPrimaryResponseNative", "(Ljava/lang/String;Ljava/lang/String;III)Z", (void *)discoverPrimaryResponseNative},
    {"discoverPrimaryByUuidResponseNative", "(Ljava/lang/String;III)Z", (void *)discoverPrimaryByUuidResponseNative},
    {"findIncludedResponseNative", "(Ljava/lang/String;Ljava/lang/String;IIII)Z", (void *)findIncludedResponseNative},
    {"discoverCharacteristicsResponseNative", "(Ljava/lang/String;Ljava/lang/String;IIII)Z", (void *)discoverCharacteristicsResponseNative},
    {"findInfoResponseNative", "(Ljava/lang/String;Ljava/lang/String;II)Z", (void *)findInfoResponseNative},
    {"readByTypeResponseNative", "(Ljava/lang/String;Ljava/lang/String;I[BII)Z", (void *)readByTypeResponseNative},
    {"readResponseNative", "(Ljava/lang/String;Ljava/lang/String;[BII)Z", (void *)readResponseNative},
    {"writeResponseNative", "(Ljava/lang/String;Ljava/lang/String;I)Z", (void *)writeResponseNative},
    {"DUNAuthorizeNative", "(Ljava/lang/String;ZI)Z", (void *)DUNAuthorizeNative},
    {"disConnectDUNNative", "()I", (void *)disConnectDUNNative},
    //WhiteList APIs
    {"addToPreferredDeviceListNative", "(Ljava/lang/String;)Z", (void *)addToPreferredDeviceListNative},
    {"removeFromPreferredDeviceListNative", "(Ljava/lang/String;)Z", (void *)removeFromPreferredDeviceListNative},
    {"clearPreferredDeviceListNative", "()Z", (void *)clearPreferredDeviceListNative},
    {"gattConnectToPreferredDeviceListNative", "()Z", (void *)gattConnectToPreferredDeviceListNative},
    {"gattCancelConnectToPreferredDeviceListNative", "()Z", (void *)gattCancelConnectToPreferredDeviceListNative},
};


int register_android_server_BluetoothService(JNIEnv *env) {
    return AndroidRuntime::registerNativeMethods(env,
                "android/server/BluetoothService", sMethods, NELEM(sMethods));
}

} /* namespace android */
