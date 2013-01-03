/*
** Copyright 2008, The Android Open Source Project
** Copyright (c) 2009-2010, The Linux Foundation, Inc. All rights reserved.
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

#define LOG_TAG "BluetoothA2dpService.cpp"

#include "android_bluetooth_common.h"
#include "android_runtime/AndroidRuntime.h"
#include "JNIHelp.h"
#include "jni.h"
#include "utils/Log.h"
#include "utils/misc.h"

#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

#ifdef HAVE_BLUETOOTH
#include <dbus/dbus.h>
#endif

namespace android {

#ifdef HAVE_BLUETOOTH
static jmethodID method_onSinkPropertyChanged;
static jmethodID method_onConnectSinkResult;
static jmethodID method_onGetPlayStatusRequest;
static jmethodID method_onListPlayerAttributeRequest;
static jmethodID method_onListPlayerAttributeValues;
static jmethodID method_onGetPlayerAttributeValues;
static jmethodID method_onSetPlayerAttributeValues;
static jmethodID method_onListPlayerAttributesText;
static jmethodID method_onListAttributeValuesText;
static jfieldID field_mTrackName;
static jfieldID field_mArtistName;
static jfieldID field_mAlbumName;
static jfieldID field_mMediaNumber;
static jfieldID field_mMediaCount;
static jfieldID field_mDuration;
static jfieldID field_mGenre;

typedef struct {
    JavaVM *vm;
    int envVer;
    DBusConnection *conn;
    jobject me;  // for callbacks to java
} native_data_t;

static native_data_t *nat = NULL;  // global native data
static void onConnectSinkResult(DBusMessage *msg, void *user, void *n);
static void onStatusReply(DBusMessage *msg, void *user, void *n);

static Properties sink_properties[] = {
        {"State", DBUS_TYPE_STRING},
        {"Connected", DBUS_TYPE_BOOLEAN},
        {"Playing", DBUS_TYPE_BOOLEAN},
        {"Protected", DBUS_TYPE_BOOLEAN},
      };
#endif

/* Returns true on success (even if adapter is present but disabled).
 * Return false if dbus is down, or another serious error (out of memory)
*/
static bool initNative(JNIEnv* env, jobject object) {
    ALOGV("%s", __FUNCTION__);
#ifdef HAVE_BLUETOOTH
    nat = (native_data_t *)calloc(1, sizeof(native_data_t));
    if (NULL == nat) {
        ALOGE("%s: out of memory!", __FUNCTION__);
        return false;
    }
    env->GetJavaVM( &(nat->vm) );
    nat->envVer = env->GetVersion();
    nat->me = env->NewGlobalRef(object);

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

static void cleanupNative(JNIEnv* env, jobject object) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        dbus_connection_close(nat->conn);
        env->DeleteGlobalRef(nat->me);
        free(nat);
        nat = NULL;
    }
#endif
}

static jobjectArray getSinkPropertiesNative(JNIEnv *env, jobject object,
                                            jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        const char *c_path = env->GetStringUTFChars(path, NULL);
        reply = dbus_func_args_timeout(env,
                                   nat->conn, -1, c_path,
                                   "org.bluez.AudioSink", "GetProperties",
                                   DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        if (!reply && dbus_error_is_set(&err)) {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, reply);
            return NULL;
        } else if (!reply) {
            ALOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return NULL;
        }
        DBusMessageIter iter;
        if (dbus_message_iter_init(reply, &iter))
            return parse_properties(env, &iter, (Properties *)&sink_properties,
                                 sizeof(sink_properties) / sizeof(Properties));
    }
#endif
    return NULL;
}


static jboolean connectSinkNative(JNIEnv *env, jobject object, jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        int len = env->GetStringLength(path) + 1;
        char *context_path = (char *)calloc(len, sizeof(char));
        strlcpy(context_path, c_path, len);  // for callback

        bool ret = dbus_func_args_async(env, nat->conn, -1, onConnectSinkResult, context_path,
                                    nat, c_path, "org.bluez.AudioSink", "Connect",
                                    DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean disconnectSinkNative(JNIEnv *env, jobject object,
                                     jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);

        bool ret = dbus_func_args_async(env, nat->conn, -1, NULL, NULL, nat,
                                    c_path, "org.bluez.AudioSink", "Disconnect",
                                    DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean suspendSinkNative(JNIEnv *env, jobject object,
                                     jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        bool ret = dbus_func_args_async(env, nat->conn, -1, NULL, NULL, nat,
                           c_path, "org.bluez.AudioSink", "Suspend",
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean resumeSinkNative(JNIEnv *env, jobject object,
                                     jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        bool ret = dbus_func_args_async(env, nat->conn, -1, NULL, NULL, nat,
                           c_path, "org.bluez.AudioSink", "Resume",
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean avrcpVolumeUpNative(JNIEnv *env, jobject object,
                                     jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        bool ret = dbus_func_args_async(env, nat->conn, -1, NULL, NULL, nat,
                           c_path, "org.bluez.Control", "VolumeUp",
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean sendMetaDataNative(JNIEnv *env, jobject obj,
                                     jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV(__FUNCTION__);
    if (nat) {
        jstring title, artist, album, media_number, total_media_count, playing_time;
        jstring genre;
        const char *c_title, *c_artist, *c_album, *c_media_number, *c_genre;
        const char *c_total_media_count, *c_playing_time;
        const char *c_path = env->GetStringUTFChars(path, NULL);
        title = (jstring) env->GetObjectField(obj, field_mTrackName);
        artist = (jstring) env->GetObjectField(obj, field_mArtistName);
        album = (jstring) env->GetObjectField(obj, field_mAlbumName);
        media_number = (jstring) env->GetObjectField(obj, field_mMediaNumber);
        total_media_count = (jstring) env->GetObjectField(obj, field_mMediaCount);
        playing_time = (jstring) env->GetObjectField(obj, field_mDuration);
        genre = (jstring) env->GetObjectField(obj, field_mGenre);

        c_title = env->GetStringUTFChars(title, NULL);
        c_artist = env->GetStringUTFChars(artist, NULL);
        c_album = env->GetStringUTFChars(album, NULL);
        c_media_number = env->GetStringUTFChars(media_number, NULL);
        c_total_media_count = env->GetStringUTFChars(total_media_count, NULL);
        c_playing_time = env->GetStringUTFChars(playing_time, NULL);
        c_genre = env->GetStringUTFChars(genre, NULL);

        bool ret = dbus_func_args_async(env, nat->conn, -1, onStatusReply, NULL, nat,
                           c_path, "org.bluez.Control", "UpdateMetaData",
                           DBUS_TYPE_STRING, &c_title,
                           DBUS_TYPE_STRING, &c_artist,
                           DBUS_TYPE_STRING, &c_album,
                           DBUS_TYPE_STRING, &c_media_number,
                           DBUS_TYPE_STRING, &c_total_media_count,
                           DBUS_TYPE_STRING, &c_playing_time,
                           DBUS_TYPE_STRING, &c_genre,
                           DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        env->ReleaseStringUTFChars(title, c_title);
        env->ReleaseStringUTFChars(artist, c_artist);
        env->ReleaseStringUTFChars(album, c_album);
        env->ReleaseStringUTFChars(media_number, c_media_number);
        env->ReleaseStringUTFChars(total_media_count, c_total_media_count);
        env->ReleaseStringUTFChars(playing_time, c_playing_time);
        env->ReleaseStringUTFChars(genre, c_genre);

        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}


static jboolean sendPlayStatusNative(JNIEnv *env, jobject object, jstring path,
                                        jint duration, jint position, jint play_status) {
#ifdef HAVE_BLUETOOTH
    ALOGV(__FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        bool ret = dbus_func_args_async(env, nat->conn, -1, onStatusReply, NULL, nat,
                           c_path, "org.bluez.Control", "UpdatePlayStatus",
                           DBUS_TYPE_UINT32, &duration,
                           DBUS_TYPE_UINT32, &position,
                           DBUS_TYPE_UINT32, &play_status,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean sendPlayerSettingsNative(JNIEnv *env, jobject object, jstring path,
                          jstring response, jint len, jbyteArray values) {
#ifdef HAVE_BLUETOOTH
    ALOGV(__FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        const char *c_response = env->GetStringUTFChars(response, NULL);
        jbyte *u_values = env->GetByteArrayElements(values, NULL);
        bool ret = dbus_func_args_async(env, nat->conn, -1, onStatusReply, NULL, nat,
                           c_path, "org.bluez.Control", c_response,
                           DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &u_values, len,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        env->ReleaseStringUTFChars(response, c_response);
        env->ReleaseByteArrayElements(values, u_values, 0);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean sendSettingsTextNative(JNIEnv *env, jobject object, jstring path,
                   jstring response, jint len, jbyteArray values, jobjectArray strings) {
#ifdef HAVE_BLUETOOTH
    ALOGV(__FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        const char *c_response = env->GetStringUTFChars(response, NULL);
        jbyte *u_values = env->GetByteArrayElements(values, NULL);
        char const** c_strings = (char const**)malloc(sizeof(char*)*len);
        for (int i = 0; i < len; i++) {
            c_strings[i] = env->GetStringUTFChars(
                            (jstring)env->GetObjectArrayElement(strings, i),
                             NULL);
        }
        bool ret = dbus_func_args_async(env, nat->conn, -1, onStatusReply, NULL, nat,
                           c_path, "org.bluez.Control", c_response,
                           DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE, &u_values, len,
                           DBUS_TYPE_ARRAY, DBUS_TYPE_STRING, &c_strings, len,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        env->ReleaseStringUTFChars(response, c_response);
        env->ReleaseByteArrayElements(values, u_values, 0);
        for (int i = 0; i < len; i++) {
            env->ReleaseStringUTFChars((jstring)env->GetObjectArrayElement(strings, i), c_strings[i]);
        }

        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean sendEventNative(JNIEnv *env, jobject object,
                                     jstring path, jint event_id, jlong data) {
#ifdef HAVE_BLUETOOTH
    ALOGV(__FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);

        bool ret = dbus_func_args_async(env, nat->conn, -1, onStatusReply, NULL, nat,
                           c_path, "org.bluez.Control", "UpdateNotification",
                           DBUS_TYPE_UINT16, &event_id,
                           DBUS_TYPE_UINT64, &data,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

static jboolean avrcpVolumeDownNative(JNIEnv *env, jobject object,
                                     jstring path) {
#ifdef HAVE_BLUETOOTH
    ALOGV("%s", __FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        bool ret = dbus_func_args_async(env, nat->conn, -1, NULL, NULL, nat,
                           c_path, "org.bluez.Control", "VolumeDown",
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}

#ifdef HAVE_BLUETOOTH
DBusHandlerResult a2dp_event_filter(DBusMessage *msg, JNIEnv *env) {
    DBusError err;

    if (!nat) {
        ALOGV("... skipping %s\n", __FUNCTION__);
        ALOGV("... ignored\n");
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    dbus_error_init(&err);

    if (dbus_message_get_type(msg) != DBUS_MESSAGE_TYPE_SIGNAL) {
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    DBusHandlerResult result = DBUS_HANDLER_RESULT_NOT_YET_HANDLED;

    if (dbus_message_is_signal(msg, "org.bluez.AudioSink",
                                      "PropertyChanged")) {
        jobjectArray str_array =
                    parse_property_change(env, msg, (Properties *)&sink_properties,
                                sizeof(sink_properties) / sizeof(Properties));
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);
        env->CallVoidMethod(nat->me,
                            method_onSinkPropertyChanged,
                            path,
                            str_array);
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else if (dbus_message_is_signal(msg, "org.bluez.Control",
                                      "GetPlayStatus")) {
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);

        env->CallVoidMethod(nat->me, method_onGetPlayStatusRequest, path);
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else if (dbus_message_is_signal(msg, "org.bluez.Control",
                                      "ListPlayerAttributes")) {
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);

        env->CallVoidMethod(nat->me, method_onListPlayerAttributeRequest, path);
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else if (dbus_message_is_signal(msg, "org.bluez.Control",
                                      "ListAttributeValues")) {
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);
        jbyte attrib;
        if (dbus_message_get_args(msg, &err,
                                DBUS_TYPE_BYTE, &attrib,
                                DBUS_TYPE_INVALID)) {
            env->CallVoidMethod(nat->me,
                                method_onListPlayerAttributeValues,
                                path,
                                attrib);
        }
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else if (dbus_message_is_signal(msg, "org.bluez.Control",
                                      "GetAttributeValues")) {
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);
        uint8_t *attribArray;
        int len;
        jbyteArray jAttribs;
        if (dbus_message_get_args(msg, &err,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE,
                                &attribArray, &len,
                                DBUS_TYPE_INVALID)) {
            jAttribs = env->NewByteArray(len);
            env->SetByteArrayRegion(jAttribs, 0, len,(jbyte *)attribArray);

            env->CallVoidMethod(nat->me,
                                method_onGetPlayerAttributeValues,
                                path,
                                jAttribs);
            env->DeleteLocalRef(jAttribs);
        }
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else if (dbus_message_is_signal(msg, "org.bluez.Control",
                                      "SetAttributeValues")) {
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);
        jbyte* attribArray;
        int len;
        jbyteArray jAttribs;
        if (dbus_message_get_args(msg, &err,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE,
                                &attribArray, &len,
                                DBUS_TYPE_INVALID)) {
            jAttribs = env->NewByteArray(len);
            env->SetByteArrayRegion(jAttribs, 0, len,(jbyte *)attribArray);
            env->CallVoidMethod(nat->me,
                                method_onSetPlayerAttributeValues,
                                path,
                                jAttribs);
            env->DeleteLocalRef(jAttribs);
        }
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else if (dbus_message_is_signal(msg, "org.bluez.Control",
                                      "ListPlayerAttributesText")) {
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);
        uint8_t *attribArray;
        int len;
        jbyteArray jAttribs;
        if (dbus_message_get_args(msg, &err,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE,
                                &attribArray, &len,
                                DBUS_TYPE_INVALID)) {
            jAttribs = env->NewByteArray(len);
            env->SetByteArrayRegion(jAttribs, 0, len,(jbyte *)attribArray);

            env->CallVoidMethod(nat->me,
                                method_onListPlayerAttributesText,
                                path,
                                jAttribs);
            env->DeleteLocalRef(jAttribs);
        }
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else if (dbus_message_is_signal(msg, "org.bluez.Control",
                                      "ListAttributeValuesText")) {
        const char *c_path = dbus_message_get_path(msg);
        jstring path = env->NewStringUTF(c_path);
        uint8_t *valueArray;
        int len;
        jbyteArray jValues;
        jbyte attrib;
        if (dbus_message_get_args(msg, &err,
                                DBUS_TYPE_BYTE, &attrib,
                                DBUS_TYPE_ARRAY, DBUS_TYPE_BYTE,
                                &valueArray, &len,
                                DBUS_TYPE_INVALID)) {
            jValues = env->NewByteArray(len);
            env->SetByteArrayRegion(jValues, 0, len,(jbyte *)valueArray);

            env->CallVoidMethod(nat->me,
                                method_onListAttributeValuesText,
                                path,
                                attrib,
                                jValues);
            env->DeleteLocalRef(jValues);
        }
        env->DeleteLocalRef(path);
        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    }else {
        ALOGV("... ignored");
    }
    if (env->ExceptionCheck()) {
        ALOGE("VM Exception occurred while handling %s.%s (%s) in %s,"
             " leaving for VM",
             dbus_message_get_interface(msg), dbus_message_get_member(msg),
             dbus_message_get_path(msg), __FUNCTION__);
        env->ExceptionDescribe();
    }

    return result;
}

void onConnectSinkResult(DBusMessage *msg, void *user, void *n) {
    ALOGV("%s", __FUNCTION__);

    native_data_t *nat = (native_data_t *)n;
    const char *path = (const char *)user;
    DBusError err;
    dbus_error_init(&err);
    JNIEnv *env;
    nat->vm->GetEnv((void**)&env, nat->envVer);


    bool result = JNI_TRUE;
    if (dbus_set_error_from_message(&err, msg)) {
        LOG_AND_FREE_DBUS_ERROR(&err);
        result = JNI_FALSE;
    }
    ALOGV("... Device Path = %s, result = %d", path, result);

    jstring jPath = env->NewStringUTF(path);
    env->CallVoidMethod(nat->me,
                        method_onConnectSinkResult,
                        jPath,
                        result);
    env->DeleteLocalRef(jPath);
    free(user);
}

void onStatusReply(DBusMessage *msg, void *user, void *n) {
    ALOGV(__FUNCTION__);

    native_data_t *nat = (native_data_t *)n;
    DBusError err;
    dbus_error_init(&err);
    if (dbus_set_error_from_message(&err, msg)) {
        LOG_AND_FREE_DBUS_ERROR(&err);
    }
}


#endif


static JNINativeMethod sMethods[] = {
    {"initNative", "()Z", (void *)initNative},
    {"cleanupNative", "()V", (void *)cleanupNative},

    /* Bluez audio 4.47 API */
    {"connectSinkNative", "(Ljava/lang/String;)Z", (void *)connectSinkNative},
    {"disconnectSinkNative", "(Ljava/lang/String;)Z", (void *)disconnectSinkNative},
    {"suspendSinkNative", "(Ljava/lang/String;)Z", (void*)suspendSinkNative},
    {"resumeSinkNative", "(Ljava/lang/String;)Z", (void*)resumeSinkNative},
    {"getSinkPropertiesNative", "(Ljava/lang/String;)[Ljava/lang/Object;",
                                    (void *)getSinkPropertiesNative},
    {"avrcpVolumeUpNative", "(Ljava/lang/String;)Z", (void*)avrcpVolumeUpNative},
    {"avrcpVolumeDownNative", "(Ljava/lang/String;)Z", (void*)avrcpVolumeDownNative},
    {"sendMetaDataNative", "(Ljava/lang/String;)Z", (void*)sendMetaDataNative},
    {"sendEventNative", "(Ljava/lang/String;IJ)Z", (void*)sendEventNative},
    {"sendPlayStatusNative", "(Ljava/lang/String;III)Z", (void*)sendPlayStatusNative},
    {"sendPlayerSettingsNative", "(Ljava/lang/String;Ljava/lang/String;I[B)Z", (void*)sendPlayerSettingsNative},
    {"sendSettingsTextNative", "(Ljava/lang/String;Ljava/lang/String;I[B[Ljava/lang/String;)Z", (void*)sendSettingsTextNative},
};

int register_android_server_BluetoothA2dpService(JNIEnv *env) {
    jclass clazz = env->FindClass("android/server/BluetoothA2dpService");
    if (clazz == NULL) {
        ALOGE("Can't find android/server/BluetoothA2dpService");
        return -1;
    }

#ifdef HAVE_BLUETOOTH
    method_onSinkPropertyChanged = env->GetMethodID(clazz, "onSinkPropertyChanged",
                                          "(Ljava/lang/String;[Ljava/lang/String;)V");
    method_onConnectSinkResult = env->GetMethodID(clazz, "onConnectSinkResult",
                                                         "(Ljava/lang/String;Z)V");
    method_onGetPlayStatusRequest = env->GetMethodID(clazz, "onGetPlayStatusRequest",
                                          "(Ljava/lang/String;)V");
    method_onListPlayerAttributeRequest = env->GetMethodID(clazz, "onListPlayerAttributeRequest",
                                          "(Ljava/lang/String;)V");
    method_onListPlayerAttributeValues = env->GetMethodID(clazz, "onListPlayerAttributeValues",
                                          "(Ljava/lang/String;B)V");
    method_onGetPlayerAttributeValues = env->GetMethodID(clazz, "onGetPlayerAttributeValues",
                                          "(Ljava/lang/String;[B)V");
    method_onSetPlayerAttributeValues = env->GetMethodID(clazz, "onSetPlayerAttributeValues",
                                          "(Ljava/lang/String;[B)V");
    method_onListPlayerAttributesText = env->GetMethodID(clazz, "onListPlayerAttributesText",
                                          "(Ljava/lang/String;[B)V");
    method_onListAttributeValuesText = env->GetMethodID(clazz, "onListAttributeValuesText",
                                          "(Ljava/lang/String;B[B)V");
    field_mTrackName = env->GetFieldID(clazz, "mTrackName", "Ljava/lang/String;");
    field_mArtistName = env->GetFieldID(clazz, "mArtistName", "Ljava/lang/String;");
    field_mAlbumName = env->GetFieldID(clazz, "mAlbumName", "Ljava/lang/String;");
    field_mMediaNumber = env->GetFieldID(clazz, "mMediaNumber", "Ljava/lang/String;");
    field_mMediaCount = env->GetFieldID(clazz, "mMediaCount", "Ljava/lang/String;");
    field_mDuration = env->GetFieldID(clazz, "mDuration", "Ljava/lang/String;");
    field_mGenre = env->GetFieldID(clazz, "mGenre", "Ljava/lang/String;");
#endif

    return AndroidRuntime::registerNativeMethods(env,
                "android/server/BluetoothA2dpService", sMethods, NELEM(sMethods));
}

} /* namespace android */
